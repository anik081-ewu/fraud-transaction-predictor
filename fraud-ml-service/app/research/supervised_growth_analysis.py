from __future__ import annotations

import time
from typing import Any, Optional

import numpy as np
import pandas as pd
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    balanced_accuracy_score,
    brier_score_loss,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)

from app.incremental.parquet_dataset import PersistedFeatureDataset
from app.supervised_training import (
    SUPPORTED_MODELS,
    WEAK_NEGATIVE_SOURCE,
    _boolean_value,
    _build_pipeline,
    _fit_with_weights,
    _model_hyperparams,
    _normalized_source,
    _select_hyperparams,
    _value,
    best_fbeta_threshold,
    precision_constrained_threshold,
)


def analyze_supervised_growth(
    dataset_path: str,
    dataset_checksum: str,
    percentages: list[int],
    minimum_rows: int,
    maximum_rows: int,
    random_seed: int,
    hyperparams: Optional[dict[str, Any]] = None,
) -> dict[str, Any]:
    dataset = PersistedFeatureDataset(dataset_path, dataset_checksum)
    rows = []
    labels = []
    label_sources = []
    for features, label, label_source in dataset.iter_labelled_features_with_sources():
        rows.append(features)
        labels.append(label)
        label_sources.append(label_source)
        if len(rows) >= maximum_rows:
            break
    if len(rows) < minimum_rows:
        raise ValueError(f"At least {minimum_rows} labelled rows are required; found {len(rows)}")
    if set(labels) != {0, 1}:
        raise ValueError("Labelled comparison data must contain FraudLabel 0 and 1")

    feature_frame = pd.DataFrame(rows).fillna(0.0).reindex(sorted({key for row in rows for key in row}), axis=1, fill_value=0.0)
    label_array = np.asarray(labels, dtype=int)
    automatic_weight = _value(hyperparams, "ml.supervised.auto_no_case_weight", 0.35, float)
    weight_array = np.asarray([
        automatic_weight if _normalized_source(source) == WEAK_NEGATIVE_SOURCE else 1.0
        for source in label_sources
    ], dtype=float)
    tuning_enabled = _boolean_value(hyperparams, "ml.supervised.tuning_enabled", True)
    tuning_candidates = max(1, _value(hyperparams, "ml.supervised.tuning_candidates", 12, int))
    threshold_beta = _value(hyperparams, "ml.supervised.threshold_beta", 1.0, float)
    minimum_precision = _value(hyperparams, "ml.supervised.minimum_precision", 0.0, float)
    usable = sorted({value for value in percentages if 0 < value <= 100})
    results = []
    ensemble_results = []
    for percentage in usable:
        partition_rows = max(1, int(len(rows) * percentage / 100))
        if partition_rows < minimum_rows and percentage != 100:
            continue
        x_partition = feature_frame.iloc[:partition_rows]
        y_partition = label_array[:partition_rows]
        fit_end = max(1, min(partition_rows - 3, int(partition_rows * 0.6)))
        tuning_end = max(fit_end + 1, min(partition_rows - 2, int(partition_rows * 0.7)))
        calibration_end = max(tuning_end + 1, min(partition_rows - 1, int(partition_rows * 0.8)))
        x_fit_frame = x_partition.iloc[:fit_end]
        x_tuning_frame = x_partition.iloc[fit_end:tuning_end]
        x_train_frame = x_partition.iloc[:tuning_end]
        x_validation_frame = x_partition.iloc[tuning_end:calibration_end]
        x_test_frame = x_partition.iloc[calibration_end:]
        y_fit = y_partition[:fit_end]
        y_tuning = y_partition[fit_end:tuning_end]
        y_train = y_partition[:tuning_end]
        y_validation = y_partition[tuning_end:calibration_end]
        y_test = y_partition[calibration_end:]
        weights = weight_array[:partition_rows]
        if len(set(y_fit)) < 2 or len(set(y_validation)) < 2 or len(set(y_test)) < 2:
            continue
        validation_probabilities: dict[str, np.ndarray] = {}
        evaluation_probabilities: dict[str, np.ndarray] = {}
        evaluation_predictions: dict[str, np.ndarray] = {}
        tuning_scores: dict[str, float] = {}
        for model_name in SUPPORTED_MODELS:
            tuned_hyperparams, tuning_pr_auc = _select_hyperparams(
                model_name,
                x_fit_frame,
                pd.Series(y_fit),
                pd.Series(weights[:fit_end]),
                x_tuning_frame,
                pd.Series(y_tuning),
                pd.Series(weights[fit_end:tuning_end]),
                random_seed,
                hyperparams,
                tuning_enabled and len(set(y_tuning)) == 2,
                tuning_candidates,
            )
            model = _build_pipeline(model_name, random_seed, pd.Series(y_train), tuned_hyperparams)
            started = time.perf_counter()
            _fit_with_weights(model, x_train_frame, y_train, weights[:tuning_end])
            training_ms = (time.perf_counter() - started) * 1000
            validation_probability = model.predict_proba(x_validation_frame)[:, 1]
            threshold, calibration = precision_constrained_threshold(
                y_validation,
                validation_probability,
                minimum_precision=minimum_precision,
                fallback_beta=threshold_beta,
                sample_weight=weights[tuning_end:calibration_end],
            )
            scoring_started = time.perf_counter()
            probability = model.predict_proba(x_test_frame)[:, 1]
            prediction = (probability >= threshold).astype(int)
            scoring_ms = (time.perf_counter() - scoring_started) * 1000
            validation_probabilities[model_name] = validation_probability
            evaluation_probabilities[model_name] = probability
            evaluation_predictions[model_name] = prediction
            tuning_scores[model_name] = tuning_pr_auc
            matrix = confusion_matrix(y_test, prediction, labels=[0, 1])
            positive_rate = float(np.mean(y_test))
            pr_auc = float(average_precision_score(y_test, probability))
            results.append({
                "partitionPercentage": percentage,
                "partitionRows": partition_rows,
                "trainingRows": len(y_train),
                "tuningRows": len(y_tuning),
                "validationRows": len(y_validation),
                "evaluationRows": len(y_test),
                "detector": model_name,
                "prAuc": pr_auc,
                "prAucLift": pr_auc / max(positive_rate, 0.000001),
                "rocAuc": float(roc_auc_score(y_test, probability)),
                "accuracy": float(accuracy_score(y_test, prediction)),
                "balancedAccuracy": float(balanced_accuracy_score(y_test, prediction)),
                "precision": float(precision_score(y_test, prediction, zero_division=0)),
                "recall": float(recall_score(y_test, prediction, zero_division=0)),
                "f1": float(f1_score(y_test, prediction, zero_division=0)),
                "brierScore": float(brier_score_loss(y_test, probability)),
                "positiveRate": positive_rate,
                "trueNegative": int(matrix[0, 0]),
                "falsePositive": int(matrix[0, 1]),
                "falseNegative": int(matrix[1, 0]),
                "truePositive": int(matrix[1, 1]),
                "decisionThreshold": threshold,
                "minimumPrecisionTarget": minimum_precision,
                "calibrationThresholdPolicy": calibration,
                "tuningPrAuc": tuning_pr_auc,
                "bestHyperparams": _model_hyperparams(model_name, tuned_hyperparams),
                "trainingDurationMs": round(training_ms, 2),
                "rowsPerSecond": round(len(y_test) / max(scoring_ms / 1000, 0.000001), 2),
            })
        ensemble_results.extend(_evaluate_ensembles(
            percentage=percentage,
            partition_rows=partition_rows,
            training_rows=len(y_train),
            tuning_rows=len(y_tuning),
            validation_rows=len(y_validation),
            evaluation_rows=len(y_test),
            validation_labels=y_validation,
            evaluation_labels=y_test,
            validation_probabilities=validation_probabilities,
            evaluation_probabilities=evaluation_probabilities,
            evaluation_predictions=evaluation_predictions,
            tuning_scores=tuning_scores,
            minimum_precision=minimum_precision,
            threshold_beta=threshold_beta,
            calibration_weights=weights[tuning_end:calibration_end],
        ))
    if not results:
        raise ValueError("No chronological partition contained both FraudLabel classes in train and evaluation windows")
    return {
        "status": "SUCCESS",
        "datasetRows": len(rows),
        "featureCount": len(feature_frame.columns),
        "featureVersion": dataset.feature_version,
        "partitionPercentages": sorted({item["partitionPercentage"] for item in results}),
        "detectors": list(SUPPORTED_MODELS),
        "methodology": {
            "learningMode": "SUPERVISED",
            "ordering": "oldest labelled transactions first",
            "split": "chronological 60% fit / 10% tuning / 10% threshold calibration / 20% evaluation",
            "threshold": (
                f"selected on calibration rows to maximize F-{threshold_beta:g}; evaluation rows remain untouched"
                if minimum_precision <= 0
                else f"selected on calibration rows to maximize recall at precision >= {minimum_precision:.0%}; "
                     f"falls back to F-{threshold_beta:g} only when unattainable; evaluation rows remain untouched"
            ),
            "labelWeighting": {"AUTO_NO_CASE": automatic_weight, "confirmedOrImported": 1.0},
            "hyperparameterTuning": (
                "joint deterministic candidates selected by period-stable PR-AUC on the tuning window"
            ),
            "ensembleEvaluation": (
                "any-model, majority and unanimous decisions use calibrated model votes; weighted soft voting "
                "uses tuning-only PR-AUC weights and a calibration-only decision threshold"
            ),
            "maximumRows": maximum_rows,
            "blankLabels": "excluded",
        },
        "results": results,
        "ensembles": ensemble_results,
    }


def _evaluate_ensembles(
    percentage: int,
    partition_rows: int,
    training_rows: int,
    tuning_rows: int,
    validation_rows: int,
    evaluation_rows: int,
    validation_labels: np.ndarray,
    evaluation_labels: np.ndarray,
    validation_probabilities: dict[str, np.ndarray],
    evaluation_probabilities: dict[str, np.ndarray],
    evaluation_predictions: dict[str, np.ndarray],
    tuning_scores: dict[str, float],
    minimum_precision: float,
    threshold_beta: float,
    calibration_weights: np.ndarray,
) -> list[dict[str, Any]]:
    model_names = list(SUPPORTED_MODELS)
    if any(model_name not in evaluation_predictions for model_name in model_names):
        return []

    started = time.perf_counter()
    vote_count = np.sum([evaluation_predictions[name] for name in model_names], axis=0)
    vote_fraction = vote_count / len(model_names)
    strategies = [
        ("ANY_MODEL", "Any model (1 of 3)", (vote_count >= 1).astype(int), 1 / len(model_names)),
        ("MAJORITY_VOTE", "Majority vote (2 of 3)", (vote_count >= 2).astype(int), 2 / len(model_names)),
        ("UNANIMOUS_VOTE", "Unanimous vote (3 of 3)", (vote_count == len(model_names)).astype(int), 1.0),
    ]
    output = [
        _ensemble_metric(
            percentage,
            partition_rows,
            training_rows,
            tuning_rows,
            validation_rows,
            evaluation_rows,
            strategy,
            label,
            evaluation_labels,
            vote_fraction,
            prediction,
            threshold,
            "MODEL_VOTE_FRACTION",
            {},
            None,
            started,
        )
        for strategy, label, prediction, threshold in strategies
    ]

    model_weights = _normalized_ensemble_weights(tuning_scores, model_names)
    validation_probability = np.average(
        np.vstack([validation_probabilities[name] for name in model_names]),
        axis=0,
        weights=[model_weights[name] for name in model_names],
    )
    evaluation_probability = np.average(
        np.vstack([evaluation_probabilities[name] for name in model_names]),
        axis=0,
        weights=[model_weights[name] for name in model_names],
    )
    threshold, calibration = precision_constrained_threshold(
        validation_labels,
        validation_probability,
        minimum_precision=minimum_precision,
        fallback_beta=threshold_beta,
        sample_weight=calibration_weights,
    )
    output.append(_ensemble_metric(
        percentage,
        partition_rows,
        training_rows,
        tuning_rows,
        validation_rows,
        evaluation_rows,
        "WEIGHTED_SOFT_VOTE",
        "Weighted probability ensemble",
        evaluation_labels,
        evaluation_probability,
        (evaluation_probability >= threshold).astype(int),
        threshold,
        "TUNING_WEIGHTED_PROBABILITY",
        model_weights,
        calibration,
        started,
    ))
    return output


def _normalized_ensemble_weights(
    tuning_scores: dict[str, float],
    model_names: list[str],
) -> dict[str, float]:
    scores = np.asarray([max(0.000001, float(tuning_scores.get(name, 0.0))) for name in model_names])
    normalized = scores / scores.sum()
    return {name: float(normalized[index]) for index, name in enumerate(model_names)}


def _ensemble_metric(
    percentage: int,
    partition_rows: int,
    training_rows: int,
    tuning_rows: int,
    validation_rows: int,
    evaluation_rows: int,
    strategy: str,
    label: str,
    evaluation_labels: np.ndarray,
    score: np.ndarray,
    prediction: np.ndarray,
    threshold: float,
    score_type: str,
    model_weights: dict[str, float],
    calibration: Optional[dict[str, Any]],
    started: float,
) -> dict[str, Any]:
    matrix = confusion_matrix(evaluation_labels, prediction, labels=[0, 1])
    positive_rate = float(np.mean(evaluation_labels))
    pr_auc = float(average_precision_score(evaluation_labels, score))
    scoring_ms = (time.perf_counter() - started) * 1000
    return {
        "partitionPercentage": percentage,
        "partitionRows": partition_rows,
        "trainingRows": training_rows,
        "tuningRows": tuning_rows,
        "validationRows": validation_rows,
        "evaluationRows": evaluation_rows,
        "strategy": strategy,
        "label": label,
        "memberCount": len(SUPPORTED_MODELS),
        "members": list(SUPPORTED_MODELS),
        "scoreType": score_type,
        "prAuc": pr_auc,
        "prAucLift": pr_auc / max(positive_rate, 0.000001),
        "rocAuc": float(roc_auc_score(evaluation_labels, score)),
        "accuracy": float(accuracy_score(evaluation_labels, prediction)),
        "balancedAccuracy": float(balanced_accuracy_score(evaluation_labels, prediction)),
        "precision": float(precision_score(evaluation_labels, prediction, zero_division=0)),
        "recall": float(recall_score(evaluation_labels, prediction, zero_division=0)),
        "f1": float(f1_score(evaluation_labels, prediction, zero_division=0)),
        "brierScore": float(brier_score_loss(evaluation_labels, score)),
        "positiveRate": positive_rate,
        "trueNegative": int(matrix[0, 0]),
        "falsePositive": int(matrix[0, 1]),
        "falseNegative": int(matrix[1, 0]),
        "truePositive": int(matrix[1, 1]),
        "decisionThreshold": float(threshold),
        "modelWeights": model_weights,
        "calibrationThresholdPolicy": calibration,
        "rowsPerSecond": round(evaluation_rows / max(scoring_ms / 1000, 0.000001), 2),
    }


def _best_f1_threshold(labels: np.ndarray, probabilities: np.ndarray) -> float:
    return best_fbeta_threshold(labels, probabilities, beta=1.0)
