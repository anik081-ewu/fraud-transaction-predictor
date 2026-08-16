from __future__ import annotations

import json
import os
import pickle
import tempfile
import time
from typing import Any, Dict, List, Optional, Tuple

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import ExtraTreesClassifier, RandomForestClassifier
from sklearn.base import BaseEstimator, TransformerMixin
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    balanced_accuracy_score,
    brier_score_loss,
    confusion_matrix,
    f1_score,
    precision_recall_curve,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import RobustScaler, StandardScaler

from app.incremental.parquet_dataset import PersistedFeatureDataset
from app.models.stacked_fraud_ensemble import StackedFraudEnsemble


SUPPORTED_MODELS = ["XGBoost", "RandomForestClassifier", "ExtraTreesClassifier"]
STACKED_MODEL = "StackedEnsemble"
MODEL_FILES = {
    "XGBoost": "xgboost_classifier.pkl",
    "RandomForestClassifier": "random_forest_classifier.pkl",
    "ExtraTreesClassifier": "extra_trees_classifier.pkl",
    STACKED_MODEL: "stacked_ensemble.pkl",
}

WEAK_NEGATIVE_SOURCE = "AUTO_NO_CASE"


class SupervisedFeatureAugmenter(BaseEstimator, TransformerMixin):
    def fit(self, features, labels=None):
        return self

    def transform(self, features):
        frame = features.copy() if isinstance(features, pd.DataFrame) else pd.DataFrame(features)
        self._log_feature(frame, "current_amount")
        self._log_feature(frame, "current_balance")
        self._log_feature(frame, "amount_sum_24h")
        self._log_feature(frame, "amount_sum_7d")
        self._log_feature(frame, "expected_monthly_turnover")
        self._safe_ratio(frame, "transaction_count_10m", "transaction_count_1h", "velocity_10m_share")
        self._safe_ratio(frame, "transaction_count_1h", "transaction_count_24h", "velocity_1h_share")
        self._safe_ratio(frame, "transaction_count_24h", "transaction_count_7d", "velocity_24h_share")
        self._safe_ratio(frame, "amount_sum_1h", "amount_sum_24h", "amount_velocity_1h_share")
        self._safe_ratio(frame, "amount_sum_24h", "amount_sum_7d", "amount_velocity_24h_share")
        self._safe_ratio(frame, "amount_sum_24h", "transaction_count_24h", "average_amount_24h")
        self._safe_ratio(frame, "repeated_amount_count_24h", "transaction_count_24h", "repeated_amount_density_24h")
        self._safe_ratio(frame, "unique_beneficiaries_1h", "unique_beneficiaries_24h", "beneficiary_burst_share")
        novelty = [name for name in ("new_beneficiary", "new_location", "new_channel", "new_device") if name in frame]
        if novelty:
            frame["novelty_signal_count"] = frame[novelty].fillna(0.0).sum(axis=1)
        if "profile_confidence" in frame and "novelty_signal_count" in frame:
            frame["low_confidence_novelty"] = (1.0 - frame["profile_confidence"].clip(0.0, 1.0)) * frame["novelty_signal_count"]
        if "amount_z_score_last_30" in frame:
            frame["absolute_customer_amount_z_score"] = frame["amount_z_score_last_30"].abs()
        if "peer_amount_z_score" in frame:
            frame["absolute_peer_amount_z_score"] = frame["peer_amount_z_score"].abs()
        if "current_amount_log1p" in frame and "new_beneficiary" in frame:
            frame["amount_new_beneficiary_interaction"] = frame["current_amount_log1p"] * frame["new_beneficiary"].fillna(0.0)
        if "current_amount_log1p" in frame and "new_location" in frame:
            frame["amount_new_location_interaction"] = frame["current_amount_log1p"] * frame["new_location"].fillna(0.0)
        if "current_amount_log1p" in frame and "new_channel" in frame:
            frame["amount_new_channel_interaction"] = frame["current_amount_log1p"] * frame["new_channel"].fillna(0.0)
        if "amount_vs_last_30_avg" in frame:
            frame["customer_amount_multiplier_excess"] = np.maximum(
                frame["amount_vs_last_30_avg"].fillna(0.0) - 1.0,
                0.0,
            )
        return frame

    @staticmethod
    def _log_feature(frame: pd.DataFrame, name: str) -> None:
        if name in frame:
            frame[f"{name}_log1p"] = np.log1p(frame[name].fillna(0.0).clip(lower=0.0))

    @staticmethod
    def _safe_ratio(frame: pd.DataFrame, numerator: str, denominator: str, output: str) -> None:
        if numerator in frame and denominator in frame:
            frame[output] = frame[numerator].fillna(0.0) / frame[denominator].fillna(0.0).clip(lower=1.0)


def train_supervised_from_persisted_dataset(
    dataset_path: str,
    dataset_checksum: str,
    models_dir: str,
    hyperparams: Optional[Dict[str, Any]] = None,
    model_names: Optional[List[str]] = None,
) -> Tuple[int, int, Dict[str, Dict[str, Any]]]:
    dataset = PersistedFeatureDataset(dataset_path, dataset_checksum)
    columns = dataset.collect_feature_columns()
    feature_batches: list[pd.DataFrame] = []
    rows: list[dict[str, float]] = []
    labels: list[int] = []
    label_sources: list[str | None] = []
    for features, label, label_source in dataset.iter_labelled_features_with_sources():
        rows.append(features)
        labels.append(label)
        label_sources.append(label_source)
        if len(rows) >= 8_192:
            feature_batches.append(_dense_feature_batch(rows, columns))
            rows.clear()
    if not rows:
        if not feature_batches:
            raise ValueError("Supervised training snapshot contains no labelled rows")
    else:
        feature_batches.append(_dense_feature_batch(rows, columns))
    features = pd.concat(feature_batches, ignore_index=True)
    return _train_supervised_feature_frame(
        features,
        pd.Series(labels, dtype=int),
        models_dir,
        hyperparams,
        model_names,
        feature_version=dataset.feature_version,
        source="PERSISTED_SNAPSHOT",
        label_sources=label_sources,
    )


def _dense_feature_batch(rows: list[dict[str, float]], columns: list[str]) -> pd.DataFrame:
    return pd.DataFrame.from_records(rows, columns=columns).fillna(0.0).astype(np.float32, copy=False)


def train_supervised_from_transactions_df(
    transactions_df: pd.DataFrame,
    models_dir: str,
    hyperparams: Optional[Dict[str, Any]] = None,
    model_names: Optional[List[str]] = None,
) -> Tuple[int, int, Dict[str, Dict[str, Any]]]:
    from app.legacy.feature_engineering import build_training_features

    if "fraud_label" not in transactions_df.columns:
        raise ValueError("Supervised training requires FraudLabel")
    labelled = transactions_df.loc[transactions_df["fraud_label"].notna()].copy()
    labelled = labelled.sort_values(["transaction_date", "transaction_id"], kind="stable").reset_index(drop=True)
    features = build_training_features(labelled).reindex(range(len(labelled)))
    return _train_supervised_feature_frame(
        features,
        labelled["fraud_label"].astype(int).reset_index(drop=True),
        models_dir,
        hyperparams,
        model_names,
        feature_version="LEGACY_RAW_TRANSACTION_FEATURES",
        source="DEPRECATED_RAW_TRANSACTIONS",
        label_sources=labelled["label_source"].tolist() if "label_source" in labelled else None,
    )


def _train_supervised_feature_frame(
    features: pd.DataFrame,
    labels: pd.Series,
    models_dir: str,
    hyperparams: Optional[Dict[str, Any]],
    model_names: Optional[List[str]],
    feature_version: str,
    source: str,
    label_sources: Optional[List[Optional[str]]] = None,
) -> Tuple[int, int, Dict[str, Dict[str, Any]]]:
    if len(features) < 100:
        raise ValueError("Supervised training requires at least 100 labelled rows")
    labels = labels.astype(int).reset_index(drop=True)
    features = features.reset_index(drop=True).replace([np.inf, -np.inf], np.nan)
    if set(labels.unique()) != {0, 1}:
        raise ValueError("Supervised training requires both FraudLabel classes 0 and 1")

    sources = pd.Series(label_sources or [None] * len(features), dtype="object").reset_index(drop=True)
    if len(sources) != len(features):
        raise ValueError("Label-source count must match labelled feature rows")
    automatic_weight = _value(hyperparams, "ml.supervised.auto_no_case_weight", 0.35, float)
    sample_weights = sources.map(lambda value: automatic_weight if _normalized_source(value) == WEAK_NEGATIVE_SOURCE else 1.0)

    fit_end = max(1, min(len(features) - 3, int(len(features) * 0.6)))
    tuning_end = max(fit_end + 1, min(len(features) - 2, int(len(features) * 0.7)))
    calibration_end = max(tuning_end + 1, min(len(features) - 1, int(len(features) * 0.8)))
    x_fit = features.iloc[:fit_end]
    x_tuning = features.iloc[fit_end:tuning_end]
    x_train = features.iloc[:tuning_end]
    x_calibration = features.iloc[tuning_end:calibration_end]
    x_evaluation = features.iloc[calibration_end:]
    y_fit = labels.iloc[:fit_end]
    y_tuning = labels.iloc[fit_end:tuning_end]
    y_train = labels.iloc[:tuning_end]
    y_calibration = labels.iloc[tuning_end:calibration_end]
    y_evaluation = labels.iloc[calibration_end:]
    for name, partition in (
        ("training", y_fit),
        ("threshold-calibration", y_calibration),
        ("evaluation", y_evaluation),
    ):
        if partition.nunique() < 2:
            raise ValueError(f"Chronological {name} partition must contain FraudLabel 0 and 1")

    stack_requested = bool(model_names and STACKED_MODEL in model_names)
    selected = _normalize_models(model_names)
    metrics: Dict[str, Dict[str, Any]] = {}
    models: Dict[str, Any] = {}
    thresholds: Dict[str, float] = {}
    random_state = int((hyperparams or {}).get("ml.random_state", 42))
    threshold_beta = _value(hyperparams, "ml.supervised.threshold_beta", 1.0, float)
    minimum_precision = _value(hyperparams, "ml.supervised.minimum_precision", 0.0, float)
    tuning_enabled = _boolean_value(hyperparams, "ml.supervised.tuning_enabled", True)
    tuning_candidates = max(1, _value(hyperparams, "ml.supervised.tuning_candidates", 12, int))

    for model_name in selected:
        tuned_hyperparams, tuning_pr_auc = _select_hyperparams(
            model_name,
            x_fit,
            y_fit,
            sample_weights.iloc[:fit_end],
            x_tuning,
            y_tuning,
            sample_weights.iloc[fit_end:tuning_end],
            random_state,
            hyperparams,
            tuning_enabled and y_tuning.nunique() == 2,
            tuning_candidates,
        )
        model = _build_pipeline(model_name, random_state, y_train, tuned_hyperparams)
        started = time.perf_counter()
        _fit_with_weights(model, x_train, y_train, sample_weights.iloc[:tuning_end])
        training_ms = (time.perf_counter() - started) * 1000.0
        calibration_probability = model.predict_proba(x_calibration)[:, 1]
        threshold, calibration = precision_constrained_threshold(
            y_calibration.to_numpy(),
            calibration_probability,
            minimum_precision=minimum_precision,
            fallback_beta=threshold_beta,
            sample_weight=sample_weights.iloc[tuning_end:calibration_end].to_numpy(),
        )
        prediction_started = time.perf_counter()
        probabilities = model.predict_proba(x_evaluation)[:, 1]
        predictions = (probabilities >= threshold).astype(int)
        prediction_ms = (time.perf_counter() - prediction_started) * 1000.0
        models[model_name] = model
        thresholds[model_name] = threshold
        metrics[model_name] = _classification_metrics(
            y_evaluation.to_numpy(), probabilities, predictions, threshold,
            len(y_train), len(y_calibration), training_ms, prediction_ms,
        )
        metrics[model_name].update({
            "tuningRows": int(len(y_tuning)),
            "tuningPrAuc": tuning_pr_auc,
            "thresholdBeta": float(threshold_beta),
            "minimumPrecisionTarget": float(minimum_precision),
            "calibrationThresholdPolicy": calibration,
            "automaticNoCaseWeight": float(automatic_weight),
            "automaticNoCaseTrainingRows": int(
                (sources.iloc[:tuning_end].map(_normalized_source) == WEAK_NEGATIVE_SOURCE).sum()
            ),
            "bestHyperparams": _model_hyperparams(model_name, tuned_hyperparams),
            "trustedEvaluation": _trusted_evaluation_metrics(
                y_evaluation.to_numpy(), probabilities, predictions, sources.iloc[calibration_end:].tolist()
            ),
        })

    if set(selected) == set(SUPPORTED_MODELS):
        stacked_model, stacked_threshold, stacked_metrics = _train_stacked_ensemble(
            models,
            x_calibration,
            y_calibration,
            sample_weights.iloc[tuning_end:calibration_end],
            x_evaluation,
            y_evaluation,
            minimum_precision,
            threshold_beta,
            random_state,
        )
        if stacked_model is not None:
            models[STACKED_MODEL] = stacked_model
            thresholds[STACKED_MODEL] = stacked_threshold
            metrics[STACKED_MODEL] = stacked_metrics
        elif stack_requested:
            raise ValueError(
                "Temporal Stacked Ensemble requires both FraudLabel classes in each half of the chronological calibration window"
            )

    os.makedirs(models_dir, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="fraud-supervised-train-", dir=models_dir) as temporary:
        for model_name, model in models.items():
            joblib.dump(model, os.path.join(temporary, MODEL_FILES[model_name]))
        with open(os.path.join(temporary, "supervised_feature_columns.pkl"), "wb") as handle:
            pickle.dump(list(features.columns), handle)
        with open(os.path.join(temporary, "supervised_thresholds.json"), "w", encoding="utf-8") as handle:
            json.dump(thresholds, handle, indent=2)
        with open(os.path.join(temporary, "learning_mode.json"), "w", encoding="utf-8") as handle:
            json.dump({
                "learningMode": "SUPERVISED",
                "models": list(models),
                "featureVersion": feature_version,
                "trainingSource": source,
                "split": "chronological 60% fit / 10% tuning / 10% threshold calibration / 20% evaluation",
                "labelWeighting": {"AUTO_NO_CASE": automatic_weight, "confirmedOrImported": 1.0},
                "tuningEnabled": tuning_enabled,
                "thresholdPolicy": {
                    "objective": "maximize recall subject to minimum calibration precision",
                    "minimumPrecision": minimum_precision,
                    "fallback": f"maximize F-{threshold_beta:g} when the precision target is unattainable",
                },
            }, handle, indent=2)
        for name in os.listdir(temporary):
            os.replace(os.path.join(temporary, name), os.path.join(models_dir, name))
    legacy_scaler = os.path.join(models_dir, "supervised_scaler.pkl")
    if os.path.exists(legacy_scaler):
        os.remove(legacy_scaler)

    return int(len(features)), int(len(features.columns)), metrics


def _train_stacked_ensemble(
    base_models: Dict[str, Any],
    calibration_features: pd.DataFrame,
    calibration_labels: pd.Series,
    calibration_weights: pd.Series,
    evaluation_features: pd.DataFrame,
    evaluation_labels: pd.Series,
    minimum_precision: float,
    threshold_beta: float,
    random_state: int,
) -> tuple[Optional[StackedFraudEnsemble], float, Dict[str, Any]]:
    midpoint = len(calibration_labels) // 2
    meta_labels = calibration_labels.iloc[:midpoint]
    threshold_labels = calibration_labels.iloc[midpoint:]
    if midpoint < 10 or len(threshold_labels) < 10 \
            or meta_labels.nunique() < 2 or threshold_labels.nunique() < 2:
        return None, 0.5, {}
    member_names = list(SUPPORTED_MODELS)
    meta_features = np.column_stack([
        base_models[name].predict_proba(calibration_features.iloc[:midpoint])[:, 1]
        for name in member_names
    ])
    threshold_features = np.column_stack([
        base_models[name].predict_proba(calibration_features.iloc[midpoint:])[:, 1]
        for name in member_names
    ])
    meta_model = LogisticRegression(
        C=1.0,
        class_weight="balanced",
        max_iter=1000,
        solver="liblinear",
        random_state=random_state,
    )
    started = time.perf_counter()
    meta_model.fit(
        meta_features,
        meta_labels,
        sample_weight=calibration_weights.iloc[:midpoint].to_numpy(dtype=float),
    )
    ensemble = StackedFraudEnsemble(
        {name: base_models[name] for name in member_names},
        meta_model,
    )
    training_ms = (time.perf_counter() - started) * 1000.0
    threshold_probability = meta_model.predict_proba(threshold_features)[:, 1]
    threshold, calibration = precision_constrained_threshold(
        threshold_labels.to_numpy(),
        threshold_probability,
        minimum_precision=minimum_precision,
        fallback_beta=threshold_beta,
        sample_weight=calibration_weights.iloc[midpoint:].to_numpy(dtype=float),
    )
    prediction_started = time.perf_counter()
    probabilities = ensemble.predict_proba(evaluation_features)[:, 1]
    predictions = (probabilities >= threshold).astype(int)
    prediction_ms = (time.perf_counter() - prediction_started) * 1000.0
    metrics = _classification_metrics(
        evaluation_labels.to_numpy(),
        probabilities,
        predictions,
        threshold,
        int(midpoint),
        int(len(threshold_labels)),
        training_ms,
        prediction_ms,
    )
    metrics.update({
        "members": member_names,
        "stackingProtocol": "FIRST_HALF_CALIBRATION_META_FIT_SECOND_HALF_THRESHOLD",
        "metaCoefficients": {
            name: float(coefficient)
            for name, coefficient in zip(member_names, meta_model.coef_[0])
        },
        "metaIntercept": float(meta_model.intercept_[0]),
        "calibrationThresholdPolicy": calibration,
    })
    return ensemble, threshold, metrics


def _classification_metrics(
    labels: np.ndarray,
    probabilities: np.ndarray,
    predictions: np.ndarray,
    threshold: float,
    training_rows: int,
    calibration_rows: int,
    training_ms: float,
    prediction_ms: float,
) -> Dict[str, Any]:
    matrix = confusion_matrix(labels, predictions, labels=[0, 1])
    positive_rate = float(np.mean(labels))
    pr_auc = float(average_precision_score(labels, probabilities))
    return {
        "trainingRows": int(training_rows),
        "calibrationRows": int(calibration_rows),
        "evaluationRows": int(len(labels)),
        "positiveRows": int(labels.sum()),
        "positiveRate": positive_rate,
        "prAuc": pr_auc,
        "prAucLift": pr_auc / max(positive_rate, 0.000001),
        "rocAuc": float(roc_auc_score(labels, probabilities)),
        "accuracy": float(accuracy_score(labels, predictions)),
        "balancedAccuracy": float(balanced_accuracy_score(labels, predictions)),
        "precision": float(precision_score(labels, predictions, zero_division=0)),
        "recall": float(recall_score(labels, predictions, zero_division=0)),
        "f1": float(f1_score(labels, predictions, zero_division=0)),
        "brierScore": float(brier_score_loss(labels, probabilities)),
        "trueNegative": int(matrix[0, 0]),
        "falsePositive": int(matrix[0, 1]),
        "falseNegative": int(matrix[1, 0]),
        "truePositive": int(matrix[1, 1]),
        "decisionThreshold": float(threshold),
        "trainingDurationMs": round(training_ms, 2),
        "predictionDurationMs": round(prediction_ms, 2),
        "throughputRowsPerSecond": round(len(labels) / max(prediction_ms / 1000.0, 0.000001), 2),
    }


def best_fbeta_threshold(
    labels: np.ndarray,
    probabilities: np.ndarray,
    beta: float = 1.0,
    sample_weight: Optional[np.ndarray] = None,
) -> float:
    precision, recall, thresholds = precision_recall_curve(labels, probabilities, sample_weight=sample_weight)
    if not len(thresholds):
        return 0.5
    beta_squared = max(float(beta), 0.01) ** 2
    score = (1 + beta_squared) * precision[:-1] * recall[:-1] / np.maximum(
        beta_squared * precision[:-1] + recall[:-1], 1e-12
    )
    best = np.flatnonzero(score == np.nanmax(score))
    if not len(best):
        return 0.5
    return float(np.clip(thresholds[int(best[-1])], 0.01, 0.99))


def precision_constrained_threshold(
    labels: np.ndarray,
    probabilities: np.ndarray,
    minimum_precision: float = 0.0,
    fallback_beta: float = 1.0,
    sample_weight: Optional[np.ndarray] = None,
) -> tuple[float, Dict[str, Any]]:
    target = float(np.clip(minimum_precision, 0.0, 1.0))
    if target <= 0.0:
        threshold = best_fbeta_threshold(labels, probabilities, fallback_beta, sample_weight)
        predictions = probabilities >= threshold
        true_positive = float(np.sum((labels == 1) & predictions))
        false_positive = float(np.sum((labels == 0) & predictions))
        false_negative = float(np.sum((labels == 1) & ~predictions))
        return threshold, {
            "targetMet": None,
            "targetPrecision": None,
            "precision": true_positive / max(true_positive + false_positive, 1.0),
            "recall": true_positive / max(true_positive + false_negative, 1.0),
            "objective": f"MAX_F_{fallback_beta:g}",
        }
    precision, recall, thresholds = precision_recall_curve(
        labels, probabilities, sample_weight=sample_weight
    )
    if len(thresholds):
        eligible = np.flatnonzero(precision[:-1] >= target)
        if len(eligible):
            maximum_recall = np.nanmax(recall[:-1][eligible])
            best = eligible[np.flatnonzero(recall[:-1][eligible] == maximum_recall)[-1]]
            return float(np.clip(thresholds[int(best)], 0.01, 0.99)), {
                "targetMet": True,
                "targetPrecision": target,
                "precision": float(precision[int(best)]),
                "recall": float(recall[int(best)]),
                "objective": "MAX_RECALL_AT_MINIMUM_PRECISION",
            }
    threshold = best_fbeta_threshold(labels, probabilities, fallback_beta, sample_weight)
    predictions = probabilities >= threshold
    true_positive = float(np.sum((labels == 1) & predictions))
    false_positive = float(np.sum((labels == 0) & predictions))
    false_negative = float(np.sum((labels == 1) & ~predictions))
    return threshold, {
        "targetMet": False,
        "targetPrecision": target,
        "precision": true_positive / max(true_positive + false_positive, 1.0),
        "recall": true_positive / max(true_positive + false_negative, 1.0),
        "objective": f"FALLBACK_MAX_F_{fallback_beta:g}",
    }


def _fit_with_weights(model: Pipeline, features, labels, sample_weights) -> None:
    model.fit(features, labels, classifier__sample_weight=np.asarray(sample_weights, dtype=float))


def _select_hyperparams(
    model_name: str,
    x_fit: pd.DataFrame,
    y_fit: pd.Series,
    fit_weights: pd.Series,
    x_tuning: pd.DataFrame,
    y_tuning: pd.Series,
    tuning_weights: pd.Series,
    random_state: int,
    hyperparams: Optional[Dict[str, Any]],
    tuning_enabled: bool,
    maximum_candidates: int,
) -> tuple[Dict[str, Any], Optional[float]]:
    base = dict(hyperparams or {})
    candidates = _candidate_hyperparams(model_name, base)[:maximum_candidates] if tuning_enabled else [base]
    tuning_row_cap = max(1_000, _value(base, "ml.supervised.tuning_row_cap", 50_000, int))
    tuning_validation_cap = max(1_000, _value(base, "ml.supervised.tuning_validation_cap", 25_000, int))
    search_fit, search_labels, search_weights = _bounded_temporal_sample(
        x_fit, y_fit, fit_weights, tuning_row_cap
    )
    search_tuning, search_tuning_labels, search_tuning_weights = _bounded_temporal_sample(
        x_tuning, y_tuning, tuning_weights, tuning_validation_cap
    )
    best_params = candidates[0]
    best_score = -1.0
    best_pr_auc = -1.0
    for candidate in candidates:
        search_candidate = _resource_bounded_search_params(model_name, candidate, base)
        model = _build_pipeline(model_name, random_state, search_labels, search_candidate)
        _fit_with_weights(model, search_fit, search_labels, search_weights)
        probabilities = model.predict_proba(search_tuning)[:, 1]
        pr_auc = float(average_precision_score(
            search_tuning_labels, probabilities, sample_weight=search_tuning_weights
        ))
        score = _robust_tuning_score(
            search_tuning_labels, probabilities, search_tuning_weights, pr_auc
        )
        if score > best_score:
            best_score = score
            best_pr_auc = pr_auc
            best_params = candidate
    return best_params, None if not tuning_enabled else best_pr_auc


def _bounded_temporal_sample(
    features: pd.DataFrame,
    labels: pd.Series,
    weights: pd.Series,
    maximum_rows: int,
) -> tuple[pd.DataFrame, pd.Series, pd.Series]:
    if len(features) <= maximum_rows:
        return features, labels, weights
    positions = np.linspace(0, len(features) - 1, num=maximum_rows, dtype=int)
    return (
        features.iloc[positions].reset_index(drop=True),
        labels.iloc[positions].reset_index(drop=True),
        weights.iloc[positions].reset_index(drop=True),
    )


def _resource_bounded_search_params(
    model_name: str,
    candidate: Dict[str, Any],
    base: Dict[str, Any],
) -> Dict[str, Any]:
    bounded = dict(candidate)
    tree_cap = max(50, _value(base, "ml.supervised.tuning_tree_cap", 160, int))
    estimator_key = {
        "ExtraTreesClassifier": "ml.extra_trees.n_estimators",
        "RandomForestClassifier": "ml.random_forest.n_estimators",
        "XGBoost": "ml.xgboost.n_estimators",
    }[model_name]
    bounded[estimator_key] = min(_value(candidate, estimator_key, tree_cap, int), tree_cap)
    return bounded


def _robust_tuning_score(
    labels: pd.Series,
    probabilities: np.ndarray,
    sample_weights: pd.Series,
    full_pr_auc: float,
) -> float:
    midpoint = len(labels) // 2
    if midpoint < 20:
        return full_pr_auc
    period_scores = []
    for start, stop in ((0, midpoint), (midpoint, len(labels))):
        period_labels = labels.iloc[start:stop]
        if period_labels.nunique() < 2:
            return full_pr_auc
        period_scores.append(float(average_precision_score(
            period_labels,
            probabilities[start:stop],
            sample_weight=sample_weights.iloc[start:stop],
        )))
    stability_penalty = 0.15 * abs(period_scores[0] - period_scores[1])
    return float(np.mean(period_scores) - stability_penalty)


def _candidate_hyperparams(model_name: str, base: Dict[str, Any]) -> list[Dict[str, Any]]:
    candidates: list[Dict[str, Any]] = [dict(base)]
    imbalance = _value(base, "ml.supervised.class_weight_multiplier", 1.0, float)
    if model_name == "ExtraTreesClassifier":
        trees = _value(base, "ml.extra_trees.n_estimators", 400, int)
        leaf = _value(base, "ml.extra_trees.min_samples_leaf", 2, int)
        candidates.extend([
            {**base, "ml.extra_trees.n_estimators": max(200, trees),
             "ml.extra_trees.min_samples_leaf": 1, "ml.extra_trees.max_features": 0.7},
            {**base, "ml.extra_trees.n_estimators": max(300, trees),
             "ml.extra_trees.min_samples_leaf": 2, "ml.extra_trees.max_features": 0.8},
            {**base, "ml.extra_trees.n_estimators": max(400, trees),
             "ml.extra_trees.min_samples_leaf": 3, "ml.extra_trees.max_features": 1.0},
            {**base, "ml.extra_trees.n_estimators": max(500, trees),
             "ml.extra_trees.min_samples_leaf": max(2, leaf), "ml.extra_trees.max_features": "sqrt"},
            {**base, "ml.extra_trees.n_estimators": max(400, trees),
             "ml.extra_trees.min_samples_leaf": 4, "ml.extra_trees.max_features": 0.6,
             "ml.supervised.class_weight_multiplier": max(0.10, imbalance * 0.75)},
            {**base, "ml.extra_trees.n_estimators": max(600, trees),
             "ml.extra_trees.min_samples_leaf": 2, "ml.extra_trees.max_features": 1.0,
             "ml.supervised.class_weight_multiplier": max(0.10, imbalance * 0.50)},
        ])
    elif model_name == "RandomForestClassifier":
        depth = _value(base, "ml.random_forest.max_depth", 16, int)
        leaf = _value(base, "ml.random_forest.min_samples_leaf", 2, int)
        candidates.extend([
            {**base, "ml.random_forest.max_depth": max(6, depth // 2),
             "ml.random_forest.min_samples_leaf": max(2, leaf * 2),
             "ml.supervised.class_weight_multiplier": max(0.10, imbalance * 0.50)},
            {**base, "ml.random_forest.max_depth": max(8, depth - 4),
             "ml.random_forest.min_samples_leaf": max(1, leaf),
             "ml.random_forest.max_features": 0.5,
             "ml.supervised.class_weight_multiplier": max(0.10, imbalance * 0.50)},
            {**base, "ml.random_forest.max_depth": min(50, depth + 8),
             "ml.random_forest.min_samples_leaf": max(1, leaf // 2),
             "ml.random_forest.max_features": 0.7,
             "ml.supervised.class_weight_multiplier": max(0.10, imbalance * 0.50)},
            {**base, "ml.random_forest.max_depth": min(50, depth + 8),
             "ml.random_forest.min_samples_leaf": max(1, leaf),
             "ml.random_forest.max_features": 0.5,
             "ml.supervised.class_weight_multiplier": max(0.10, imbalance * 0.75)},
            {**base, "ml.random_forest.max_depth": depth,
             "ml.random_forest.min_samples_leaf": max(1, leaf * 3),
             "ml.random_forest.max_features": 0.8,
             "ml.supervised.class_weight_multiplier": max(0.10, imbalance * 0.75)},
            {**base, "ml.random_forest.max_depth": min(50, depth + 12),
             "ml.random_forest.min_samples_leaf": max(1, leaf),
             "ml.random_forest.max_features": 0.8},
        ])
    elif model_name == "XGBoost":
        depth = _value(base, "ml.xgboost.max_depth", 4, int)
        rate = _value(base, "ml.xgboost.learning_rate", 0.03, float)
        child = _value(base, "ml.xgboost.min_child_weight", 3.0, float)
        candidates.extend([
            {**base, "ml.xgboost.max_depth": max(2, depth - 1),
             "ml.xgboost.learning_rate": min(0.5, rate * 1.5),
             "ml.xgboost.min_child_weight": max(1.0, child / 2.0),
             "ml.supervised.class_weight_multiplier": max(0.10, imbalance * 0.50)},
            {**base, "ml.xgboost.max_depth": depth,
             "ml.xgboost.learning_rate": min(0.5, rate * 2.0),
             "ml.xgboost.min_child_weight": max(1.0, child),
             "ml.supervised.class_weight_multiplier": max(0.10, imbalance * 0.50)},
            {**base, "ml.xgboost.max_depth": min(16, depth + 2),
             "ml.xgboost.learning_rate": max(0.005, rate / 2.0),
             "ml.xgboost.min_child_weight": max(1.0, child * 2.0),
             "ml.supervised.class_weight_multiplier": max(0.10, imbalance * 0.50)},
            {**base, "ml.xgboost.max_depth": max(2, depth - 1),
             "ml.xgboost.learning_rate": min(0.5, rate * 2.0),
             "ml.xgboost.min_child_weight": max(1.0, child),
             "ml.xgboost.subsample": 1.0,
             "ml.xgboost.colsample_bytree": 0.7,
             "ml.supervised.class_weight_multiplier": max(0.10, imbalance * 0.75)},
            {**base, "ml.xgboost.max_depth": depth,
             "ml.xgboost.learning_rate": min(0.5, rate * 1.5),
             "ml.xgboost.min_child_weight": max(1.0, child * 2.0),
             "ml.xgboost.subsample": 0.75,
             "ml.xgboost.colsample_bytree": 1.0,
             "ml.supervised.class_weight_multiplier": max(0.10, imbalance * 0.75)},
            {**base, "ml.xgboost.max_depth": min(16, depth + 2),
             "ml.xgboost.learning_rate": max(0.005, rate / 2.0),
             "ml.xgboost.min_child_weight": max(1.0, child * 3.0),
             "ml.xgboost.reg_alpha": 0.2,
             "ml.xgboost.reg_lambda": 4.0},
        ])
    unique: list[Dict[str, Any]] = []
    seen: set[str] = set()
    for candidate in candidates:
        marker = json.dumps(candidate, sort_keys=True, default=str)
        if marker not in seen:
            seen.add(marker)
            unique.append(candidate)
    return unique


def _model_hyperparams(model_name: str, hyperparams: Dict[str, Any]) -> Dict[str, Any]:
    if model_name == "ExtraTreesClassifier":
        return {
            "ml.extra_trees.n_estimators": _value(hyperparams, "ml.extra_trees.n_estimators", 400, int),
            "ml.extra_trees.min_samples_leaf": _value(hyperparams, "ml.extra_trees.min_samples_leaf", 2, int),
            "ml.extra_trees.max_features": _max_features(hyperparams, "ml.extra_trees.max_features", 0.8),
        }
    if model_name == "RandomForestClassifier":
        return {
            "ml.random_forest.n_estimators": _value(hyperparams, "ml.random_forest.n_estimators", 500, int),
            "ml.random_forest.max_depth": _value(hyperparams, "ml.random_forest.max_depth", 16, int),
            "ml.random_forest.min_samples_leaf": _value(hyperparams, "ml.random_forest.min_samples_leaf", 2, int),
            "ml.random_forest.max_features": hyperparams.get("ml.random_forest.max_features", "sqrt"),
            "ml.supervised.class_weight_multiplier": _value(
                hyperparams, "ml.supervised.class_weight_multiplier", 1.0, float
            ),
        }
    return {
        "ml.xgboost.n_estimators": _value(hyperparams, "ml.xgboost.n_estimators", 500, int),
        "ml.xgboost.max_depth": _value(hyperparams, "ml.xgboost.max_depth", 4, int),
        "ml.xgboost.learning_rate": _value(hyperparams, "ml.xgboost.learning_rate", 0.03, float),
        "ml.xgboost.subsample": _value(hyperparams, "ml.xgboost.subsample", 0.85, float),
        "ml.xgboost.colsample_bytree": _value(hyperparams, "ml.xgboost.colsample_bytree", 0.85, float),
        "ml.xgboost.min_child_weight": _value(hyperparams, "ml.xgboost.min_child_weight", 3.0, float),
        "ml.xgboost.reg_alpha": _value(hyperparams, "ml.xgboost.reg_alpha", 0.05, float),
        "ml.xgboost.reg_lambda": _value(hyperparams, "ml.xgboost.reg_lambda", 2.0, float),
        "ml.supervised.class_weight_multiplier": _value(
            hyperparams, "ml.supervised.class_weight_multiplier", 1.0, float
        ),
    }


def _trusted_evaluation_metrics(
    labels: np.ndarray,
    probabilities: np.ndarray,
    predictions: np.ndarray,
    label_sources: List[Optional[str]],
) -> Optional[Dict[str, Any]]:
    trusted = np.asarray([_normalized_source(source) != WEAK_NEGATIVE_SOURCE for source in label_sources], dtype=bool)
    if trusted.sum() < 20 or len(np.unique(labels[trusted])) < 2:
        return None
    trusted_labels = labels[trusted]
    trusted_probabilities = probabilities[trusted]
    trusted_predictions = predictions[trusted]
    return {
        "rows": int(trusted.sum()),
        "prAuc": float(average_precision_score(trusted_labels, trusted_probabilities)),
        "precision": float(precision_score(trusted_labels, trusted_predictions, zero_division=0)),
        "recall": float(recall_score(trusted_labels, trusted_predictions, zero_division=0)),
        "f1": float(f1_score(trusted_labels, trusted_predictions, zero_division=0)),
        "balancedAccuracy": float(balanced_accuracy_score(trusted_labels, trusted_predictions)),
    }


def _normalized_source(value: Optional[str]) -> str:
    return "" if value is None else str(value).strip().upper()


def _boolean_value(hyperparams: Optional[Dict[str, Any]], key: str, default: bool) -> bool:
    value = (hyperparams or {}).get(key, default)
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"true", "1", "yes", "on"}


def _normalize_models(model_names: Optional[List[str]]) -> List[str]:
    selected = model_names or SUPPORTED_MODELS
    normalized = list(dict.fromkeys(str(name).strip() for name in selected if name))
    unsupported = [name for name in normalized if name not in {*SUPPORTED_MODELS, STACKED_MODEL}]
    if unsupported:
        raise ValueError(f"Unsupported supervised model names: {unsupported}")
    if STACKED_MODEL in normalized:
        return list(SUPPORTED_MODELS)
    return normalized or list(SUPPORTED_MODELS)


def _value(hyperparams: Optional[Dict[str, Any]], key: str, default: Any, converter) -> Any:
    try:
        return converter((hyperparams or {}).get(key, default))
    except (TypeError, ValueError):
        return converter(default)


def _max_features(hyperparams: Optional[Dict[str, Any]], key: str, default: Any) -> Any:
    value = (hyperparams or {}).get(key, default)
    if isinstance(value, str) and value.strip().lower() in {"sqrt", "log2"}:
        return value.strip().lower()
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _build_pipeline(
    model_name: str,
    random_state: int,
    labels: pd.Series,
    hyperparams: Optional[Dict[str, Any]] = None,
) -> Pipeline:
    steps: list[tuple[str, Any]] = [
        ("supervised_features", SupervisedFeatureAugmenter()),
        ("missing", SimpleImputer(strategy="median", add_indicator=True)),
    ]
    steps.append(("classifier", _build_model(model_name, random_state, labels, hyperparams)))
    return Pipeline(steps)


def _build_model(
    model_name: str,
    random_state: int,
    labels: pd.Series,
    hyperparams: Optional[Dict[str, Any]] = None,
) -> Any:
    positives = max(1, int(labels.sum()))
    negatives = max(1, int(len(labels) - positives))
    imbalance_multiplier = _value(hyperparams, "ml.supervised.class_weight_multiplier", 1.0, float)
    positive_class_weight = max(0.01, (negatives / positives) * imbalance_multiplier)
    parallel_jobs = max(1, _value(hyperparams, "ml.supervised.parallel_jobs", min(4, os.cpu_count() or 1), int))
    if model_name == "ExtraTreesClassifier":
        return ExtraTreesClassifier(
            n_estimators=_value(hyperparams, "ml.extra_trees.n_estimators", 400, int),
            min_samples_leaf=_value(hyperparams, "ml.extra_trees.min_samples_leaf", 2, int),
            max_features=_max_features(hyperparams, "ml.extra_trees.max_features", 0.8),
            class_weight="balanced_subsample",
            n_jobs=parallel_jobs,
            random_state=random_state,
        )
    if model_name == "RandomForestClassifier":
        return RandomForestClassifier(
            n_estimators=_value(hyperparams, "ml.random_forest.n_estimators", 500, int),
            max_depth=_value(hyperparams, "ml.random_forest.max_depth", 16, int),
            min_samples_leaf=_value(hyperparams, "ml.random_forest.min_samples_leaf", 2, int),
            max_features=(hyperparams or {}).get("ml.random_forest.max_features", "sqrt"),
            class_weight="balanced_subsample",
            bootstrap=True,
            n_jobs=parallel_jobs,
            random_state=random_state,
        )
    if model_name == "XGBoost":
        try:
            from xgboost import XGBClassifier
        except ImportError as exception:
            raise ValueError("XGBoost is not installed; run pip install -r requirements.txt") from exception
        return XGBClassifier(
            n_estimators=_value(hyperparams, "ml.xgboost.n_estimators", 500, int),
            max_depth=_value(hyperparams, "ml.xgboost.max_depth", 4, int),
            learning_rate=_value(hyperparams, "ml.xgboost.learning_rate", 0.03, float),
            subsample=_value(hyperparams, "ml.xgboost.subsample", 0.85, float),
            colsample_bytree=_value(hyperparams, "ml.xgboost.colsample_bytree", 0.85, float),
            min_child_weight=_value(hyperparams, "ml.xgboost.min_child_weight", 3.0, float),
            reg_alpha=_value(hyperparams, "ml.xgboost.reg_alpha", 0.05, float),
            reg_lambda=_value(hyperparams, "ml.xgboost.reg_lambda", 2.0, float),
            scale_pos_weight=positive_class_weight,
            eval_metric="logloss",
            tree_method="hist",
            n_jobs=parallel_jobs,
            random_state=random_state,
        )
    raise ValueError(f"Unsupported supervised model name: {model_name}")
