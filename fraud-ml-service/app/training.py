from __future__ import annotations

import os
import pickle
import tempfile
import time
import warnings
import hashlib
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple
import json

import joblib
import numpy as np
import pandas as pd
from sklearn.covariance import EllipticEnvelope
from sklearn.decomposition import PCA
from sklearn.ensemble import IsolationForest
from sklearn.metrics import average_precision_score, roc_auc_score
from sklearn.neighbors import LocalOutlierFactor
from sklearn.neural_network import MLPRegressor
from sklearn.preprocessing import StandardScaler
from sklearn.svm import OneClassSVM

from app.quality_metrics import excess_mass_auc, score_skewness, uniform_reference_matrix



@dataclass(frozen=True)
class TrainArtifacts:
    models: Dict[str, Any]
    scaler: StandardScaler
    feature_columns: List[str]
    optimization_metrics: Dict[str, Dict[str, Any]]


DEFAULT_MODEL_NAMES = ["IsolationForest", "Autoencoder", "LOF"]
SUPPORTED_MODEL_NAMES = list(DEFAULT_MODEL_NAMES)

# Models whose fitted artifact is a bundle dict rather than a bare sklearn estimator.
# They reconstruct their input and flag rows whose reconstruction error exceeds a
# percentile threshold learned on the training set.
RECONSTRUCTION_MODELS = {"PCAReconstruction", "Autoencoder"}


def _parse_hidden_layers(value: Any, default: Tuple[int, ...] = (32, 8, 32)) -> Tuple[int, ...]:
    """Parses '32,8,32' (or a list) into a layer-size tuple, falling back on bad input."""
    if value is None:
        return default
    if isinstance(value, (list, tuple)):
        candidates = value
    else:
        candidates = str(value).split(",")
    sizes = []
    for candidate in candidates:
        try:
            size = int(str(candidate).strip())
        except (TypeError, ValueError):
            continue
        if size > 0:
            sizes.append(size)
    return tuple(sizes) if sizes else default


def _get(hp: Optional[Dict[str, Any]], key: str, default: Any) -> Any:
    if not hp:
        return default
    return hp.get(key, default)


def _parse_contamination(value: Any) -> Any:
    if value is None:
        return "auto"
    if isinstance(value, (int, float)):
        return float(value)
    s = str(value).strip().lower()
    if s == "auto":
        return "auto"
    try:
        return float(s)
    except ValueError:
        return "auto"


def _normalize_model_names(model_names: Optional[List[str]]) -> List[str]:
    if not model_names:
        return list(DEFAULT_MODEL_NAMES)
    normalized: List[str] = []
    for name in model_names:
        if not name:
            continue
        candidate = str(name).strip()
        if candidate not in SUPPORTED_MODEL_NAMES:
            raise ValueError(f"Unsupported model name: {candidate}")
        if candidate not in normalized:
            normalized.append(candidate)
    if not normalized:
        return list(DEFAULT_MODEL_NAMES)
    return normalized


def _boolish(value: Any, default: bool) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"true", "1", "yes", "on"}


def _parse_max_samples(value: Any) -> Any:
    if value is None or str(value).strip().lower() == "auto":
        return "auto"
    parsed = float(value)
    return int(parsed) if parsed > 1 and parsed.is_integer() else parsed


def _parse_optional_fraction(value: Any) -> Optional[float]:
    if value is None or str(value).strip().lower() in {"", "auto", "none"}:
        return None
    return float(value)


def _candidate_params(model_name: str, hyperparams: Optional[Dict[str, Any]], row_count: int) -> List[Dict[str, Any]]:
    target_anomaly_rate = float(_get(hyperparams, "ml.optimization.target_anomaly_rate", 0.05))
    random_state = int(_get(hyperparams, "ml.random_state", 42))
    if model_name == "IsolationForest":
        contamination = float(_get(hyperparams, "ml.iso.contamination", target_anomaly_rate))
        configured_trees = int(_get(hyperparams, "ml.iso.n_estimators", 200))
        configured_samples = _parse_max_samples(_get(hyperparams, "ml.iso.max_samples", "auto"))
        return [
            {"n_estimators": trees, "max_samples": samples, "contamination": contamination, "random_state": random_state}
            for trees in sorted({200, 400, configured_trees})
            for samples in list(dict.fromkeys(["auto", 0.8, configured_samples]))
        ]
    if model_name == "LOF":
        contamination = float(_get(hyperparams, "ml.lof.contamination", target_anomaly_rate))
        configured_neighbors = int(_get(hyperparams, "ml.lof.n_neighbors", 35))
        neighbors = sorted({
            max(5, min(row_count - 1, value))
            for value in (20, 35, 50, configured_neighbors)
        })
        return [{"n_neighbors": value, "contamination": contamination, "novelty": True} for value in neighbors]
    if model_name == "OneClassSVM":
        configured_kernel = str(_get(hyperparams, "ml.svm.kernel", "rbf"))
        configured_gamma = _get(hyperparams, "ml.svm.gamma", "scale")
        try:
            configured_gamma = float(configured_gamma)
        except (TypeError, ValueError):
            configured_gamma = str(configured_gamma)
        configured_nu = float(_get(hyperparams, "ml.svm.nu", 0.05))
        return [
            {"kernel": kernel, "gamma": gamma, "nu": nu}
            for kernel in list(dict.fromkeys(["rbf", configured_kernel]))
            for gamma in list(dict.fromkeys(["scale", 0.01, 0.05, configured_gamma]))
            for nu in sorted({0.03, 0.05, configured_nu})
        ]
    if model_name == "EllipticEnvelope":
        contamination = float(_get(hyperparams, "ml.elliptic.contamination", target_anomaly_rate))
        configured_support = _parse_optional_fraction(
            _get(hyperparams, "ml.elliptic.support_fraction", None)
        )
        return [
            {"contamination": contamination, "support_fraction": support, "random_state": random_state}
            for support in list(dict.fromkeys([None, 0.8, 0.95, configured_support]))
        ]
    if model_name == "PCAReconstruction":
        max_components = max(1, min(row_count - 1, 20))
        configured_components = int(_get(hyperparams, "ml.pca.n_components", 10))
        configured_percentile = float(_get(hyperparams, "ml.pca.reconstruction_percentile", 95.0))
        component_values = sorted({
            max(1, min(max_components, value))
            for value in (5, 10, 15, configured_components)
        })
        return [
            {"n_components": components, "percentile": percentile, "random_state": random_state}
            for components in component_values
            for percentile in sorted({95.0, 97.5, configured_percentile})
        ]
    if model_name == "Autoencoder":
        configured_layers = _parse_hidden_layers(_get(hyperparams, "ml.autoencoder.hidden_layer_sizes", None))
        configured_iter = int(_get(hyperparams, "ml.autoencoder.max_iter", 200))
        configured_percentile = float(_get(hyperparams, "ml.autoencoder.reconstruction_percentile", 99.0))
        layer_options = list(dict.fromkeys([(32, 8, 32), (16, 4, 16), configured_layers]))
        return [
            {
                "hidden_layer_sizes": layers,
                "max_iter": configured_iter,
                "percentile": percentile,
                "random_state": random_state,
            }
            for layers in layer_options
            for percentile in sorted({97.5, 99.0, configured_percentile})
        ]
    raise ValueError(f"Unsupported model name: {model_name}")


def _configured_params(
    model_name: str, hyperparams: Optional[Dict[str, Any]], row_count: int, feature_count: int
) -> Dict[str, Any]:
    random_state = int(_get(hyperparams, "ml.random_state", 42))
    if model_name == "IsolationForest":
        return {
            "n_estimators": int(_get(hyperparams, "ml.iso.n_estimators", 200)),
            "max_samples": _parse_max_samples(_get(hyperparams, "ml.iso.max_samples", "auto")),
            "contamination": _parse_contamination(_get(hyperparams, "ml.iso.contamination", 0.05)),
            "random_state": random_state,
        }
    if model_name == "LOF":
        return {
            "n_neighbors": max(2, min(row_count - 1, int(_get(hyperparams, "ml.lof.n_neighbors", 35)))),
            "novelty": True,
            "contamination": _parse_contamination(_get(hyperparams, "ml.lof.contamination", 0.05)),
        }
    if model_name == "OneClassSVM":
        return {
            "kernel": str(_get(hyperparams, "ml.svm.kernel", "rbf")),
            "gamma": _get(hyperparams, "ml.svm.gamma", "scale"),
            "nu": float(_get(hyperparams, "ml.svm.nu", 0.05)),
        }
    if model_name == "EllipticEnvelope":
        return {
            "contamination": _parse_contamination(_get(hyperparams, "ml.elliptic.contamination", 0.05)),
            "support_fraction": _parse_optional_fraction(
                _get(hyperparams, "ml.elliptic.support_fraction", None)
            ),
            "random_state": random_state,
        }
    if model_name == "PCAReconstruction":
        default_components = max(1, min(feature_count - 1, 10))
        return {
            "n_components": max(1, min(feature_count, int(_get(hyperparams, "ml.pca.n_components", default_components)))),
            "percentile": float(_get(hyperparams, "ml.pca.reconstruction_percentile", 95.0)),
            "random_state": random_state,
        }
    if model_name == "Autoencoder":
        return {
            "hidden_layer_sizes": _parse_hidden_layers(
                _get(hyperparams, "ml.autoencoder.hidden_layer_sizes", None)
            ),
            "max_iter": int(_get(hyperparams, "ml.autoencoder.max_iter", 200)),
            "percentile": float(_get(hyperparams, "ml.autoencoder.reconstruction_percentile", 99.0)),
            "random_state": random_state,
        }
    raise ValueError(f"Unsupported model name: {model_name}")


def _fit_model(model_name: str, x: np.ndarray, params: Dict[str, Any]) -> Any:
    if model_name == "IsolationForest":
        model = IsolationForest(**params)
    elif model_name == "LOF":
        model = LocalOutlierFactor(**params)
    elif model_name == "OneClassSVM":
        model = OneClassSVM(**params)
    elif model_name == "EllipticEnvelope":
        model = EllipticEnvelope(**params)
    elif model_name == "PCAReconstruction":
        pca_params = {key: value for key, value in params.items() if key in {"n_components", "random_state"}}
        pca = PCA(**pca_params)
        transformed = pca.fit_transform(x)
        reconstructed = pca.inverse_transform(transformed)
        reconstruction_error = np.mean(np.square(x - reconstructed), axis=1)
        return {
            "pca": pca,
            "threshold": float(np.percentile(reconstruction_error, float(params["percentile"]))),
            "percentile": float(params["percentile"]),
        }
    elif model_name == "Autoencoder":
        # An MLPRegressor fitted to predict its own input is an autoencoder: the narrow
        # middle layer forces a compressed representation, so rows the network cannot
        # reconstruct well are the anomalies. x arrives already scaled by the caller.
        autoencoder = MLPRegressor(
            hidden_layer_sizes=tuple(params["hidden_layer_sizes"]),
            activation="relu",
            max_iter=int(params["max_iter"]),
            random_state=params.get("random_state", 42),
            early_stopping=True,
            validation_fraction=0.1,
            n_iter_no_change=10,
        )
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", category=RuntimeWarning)
            warnings.simplefilter("ignore", category=UserWarning)
            autoencoder.fit(x, x)
        reconstruction_error = np.mean(np.square(x - autoencoder.predict(x)), axis=1)
        return {
            "autoencoder": autoencoder,
            "threshold": float(np.percentile(reconstruction_error, float(params["percentile"]))),
            "percentile": float(params["percentile"]),
            "hiddenLayerSizes": list(params["hidden_layer_sizes"]),
        }
    else:
        raise ValueError(f"Unsupported model name: {model_name}")
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", category=RuntimeWarning)
        warnings.simplefilter("ignore", category=UserWarning)
        model.fit(x)
    return model


def _predict_and_anomaly_score(model_name: str, model: Any, x: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
    if model_name == "PCAReconstruction":
        transformed = model["pca"].transform(x)
        reconstructed = model["pca"].inverse_transform(transformed)
        anomaly_scores = np.mean(np.square(x - reconstructed), axis=1)
        predictions = np.where(anomaly_scores > float(model["threshold"]), -1, 1)
        return predictions, anomaly_scores
    if model_name == "Autoencoder":
        reconstructed = model["autoencoder"].predict(x)
        anomaly_scores = np.mean(np.square(x - reconstructed), axis=1)
        predictions = np.where(anomaly_scores > float(model["threshold"]), -1, 1)
        return predictions, anomaly_scores
    predictions = model.predict(x)
    return predictions, -np.asarray(model.decision_function(x), dtype=float)


def _synthetic_anomalies(x: np.ndarray, random_state: int) -> np.ndarray:
    if len(x) == 0:
        return x.copy()
    rng = np.random.default_rng(random_state)
    synthetic = x.copy()
    feature_count = synthetic.shape[1]
    changed_per_row = max(1, int(np.ceil(feature_count * 0.2)))
    for row_index in range(len(synthetic)):
        columns = rng.choice(feature_count, size=changed_per_row, replace=False)
        direction = rng.choice(np.array([-1.0, 1.0]), size=changed_per_row)
        magnitude = rng.uniform(3.0, 6.0, size=changed_per_row)
        synthetic[row_index, columns] += direction * magnitude
    return synthetic


def _evaluate_candidate(
    model_name: str,
    params: Dict[str, Any],
    x_train: np.ndarray,
    x_validation: np.ndarray,
    target_anomaly_rate: float,
    random_state: int,
) -> Dict[str, Any]:
    started = time.perf_counter()
    model = _fit_model(model_name, x_train, params)
    training_duration_ms = round((time.perf_counter() - started) * 1000, 2)
    synthetic = _synthetic_anomalies(x_validation, random_state)
    evaluation_x = np.vstack([x_validation, synthetic])
    labels = np.concatenate([np.zeros(len(x_validation)), np.ones(len(synthetic))])
    prediction_started = time.perf_counter()
    normal_predictions, _ = _predict_and_anomaly_score(model_name, model, x_validation)
    _, anomaly_scores = _predict_and_anomaly_score(model_name, model, evaluation_x)
    prediction_duration_ms = round((time.perf_counter() - prediction_started) * 1000, 2)
    roc_auc = float(roc_auc_score(labels, anomaly_scores))
    average_precision = float(average_precision_score(labels, anomaly_scores))
    normal_false_positive_rate = float(np.mean(normal_predictions == -1))
    rate_control = max(0.0, 1.0 - abs(normal_false_positive_rate - target_anomaly_rate) / max(target_anomaly_rate, 0.01))
    proxy_score = 100.0 * (0.50 * average_precision + 0.35 * roc_auc + 0.15 * rate_control)
    return {
        "params": params,
        "proxyRocAuc": round(roc_auc, 6),
        "proxyAveragePrecision": round(average_precision, 6),
        "validationAnomalyRate": round(normal_false_positive_rate, 6),
        "rateControlScore": round(rate_control * 100.0, 2),
        "proxyScore": round(proxy_score, 2),
        "trainingDurationMs": training_duration_ms,
        "validationPredictionDurationMs": prediction_duration_ms,
    }


def _stability_score(
    model_name: str,
    params: Dict[str, Any],
    x_train: np.ndarray,
    x_validation: np.ndarray,
    random_state: int,
) -> float:
    rng = np.random.default_rng(random_state)
    predictions: List[np.ndarray] = []
    repeats = 3
    sample_size = max(20, int(len(x_train) * 0.85))
    for _ in range(repeats):
        indices = rng.choice(len(x_train), size=min(sample_size, len(x_train)), replace=True)
        model = _fit_model(model_name, x_train[indices], params)
        current_predictions, _ = _predict_and_anomaly_score(model_name, model, x_validation)
        predictions.append(current_predictions == -1)
    agreements: List[float] = []
    for left in range(repeats):
        for right in range(left + 1, repeats):
            agreements.append(float(np.mean(predictions[left] == predictions[right])))
    return float(np.mean(agreements)) if agreements else 1.0


def _select_params(
    model_name: str,
    features: pd.DataFrame,
    hyperparams: Optional[Dict[str, Any]],
    evaluation_features: Optional[pd.DataFrame] = None,
) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    row_count = len(features)
    feature_count = len(features.columns)
    optimization_enabled = _boolish(_get(hyperparams, "ml.optimization.enabled", True), True)
    minimum_rows = int(_get(hyperparams, "ml.optimization.min_rows", 200))
    if not optimization_enabled or row_count < minimum_rows:
        params = _configured_params(model_name, hyperparams, row_count, feature_count)
        return params, {
            "optimizationApplied": False,
            "optimizationReason": "disabled" if not optimization_enabled else f"requires at least {minimum_rows} rows",
            "selectedHyperparams": params,
        }

    maximum_optimization_rows = int(_get(hyperparams, "ml.optimization.max_training_rows", 5000))
    if evaluation_features is not None and len(evaluation_features) > 0:
        raw_train = features.iloc[-maximum_optimization_rows:].to_numpy(dtype=float)
        raw_validation = evaluation_features.to_numpy(dtype=float)
        evaluation_strategy = "COMMON_FUTURE_HOLDOUT"
    else:
        validation_fraction = float(_get(hyperparams, "ml.optimization.validation_fraction", 0.2))
        validation_count = max(50, min(2000, int(row_count * validation_fraction)))
        split_at = max(20, row_count - validation_count)
        optimization_start = max(0, split_at - maximum_optimization_rows)
        raw_train = features.iloc[optimization_start:split_at].to_numpy(dtype=float)
        raw_validation = features.iloc[split_at:].to_numpy(dtype=float)
        evaluation_strategy = "PARTITION_CHRONOLOGICAL_HOLDOUT"
    validation_scaler = StandardScaler()
    x_train = validation_scaler.fit_transform(raw_train)
    x_validation = validation_scaler.transform(raw_validation)
    target_anomaly_rate = float(_get(hyperparams, "ml.optimization.target_anomaly_rate", 0.05))
    random_state = int(_get(hyperparams, "ml.random_state", 42))

    evaluations: List[Dict[str, Any]] = []
    for params in _candidate_params(model_name, hyperparams, len(x_train)):
        try:
            evaluations.append(
                _evaluate_candidate(
                    model_name, params, x_train, x_validation, target_anomaly_rate, random_state
                )
            )
        except (ValueError, np.linalg.LinAlgError):
            continue
    if not evaluations:
        params = _configured_params(model_name, hyperparams, row_count, feature_count)
        return params, {
            "optimizationApplied": False,
            "optimizationReason": "all candidate configurations failed",
            "selectedHyperparams": params,
        }

    best = max(evaluations, key=lambda item: float(item["proxyScore"]))
    stability = _stability_score(model_name, best["params"], x_train, x_validation, random_state)
    fit_score = (
        0.50 * float(best["proxyScore"])
        + 0.35 * stability * 100.0
        + 0.15 * float(best["rateControlScore"])
    )
    return best["params"], {
        "optimizationApplied": True,
        "methodology": "shared future holdout with synthetic-anomaly proxy validation",
        "evaluationStrategy": evaluation_strategy,
        "groundTruthAvailable": False,
        "validationRows": len(x_validation),
        "syntheticAnomalyRows": len(x_validation),
        "candidateCount": len(evaluations),
        "selectedHyperparams": best["params"],
        "proxyRocAuc": best["proxyRocAuc"],
        "proxyAveragePrecision": best["proxyAveragePrecision"],
        "proxyQualityScore": best["proxyScore"],
        "validationAnomalyRate": best["validationAnomalyRate"],
        "stabilityScore": round(stability * 100.0, 2),
        "qualityScore": best["proxyScore"],
        "fitScore": round(fit_score, 2),
        "fitScoreFormula": "0.50*ProxyQuality + 0.35*Stability + 0.15*RateControl",
        "trainingDurationMs": best["trainingDurationMs"],
        "validationPredictionDurationMs": best["validationPredictionDurationMs"],
        "metricCaveat": "Proxy metrics use generated anomalies and must not be reported as real fraud accuracy.",
    }


def train_models(
    features: pd.DataFrame,
    hyperparams: Optional[Dict[str, Any]] = None,
    model_names: Optional[List[str]] = None,
    evaluation_features: Optional[pd.DataFrame] = None,
) -> TrainArtifacts:
    feature_columns = list(features.columns)
    scaler = StandardScaler()
    x = scaler.fit_transform(features.to_numpy(dtype=float))
    selected_models = _normalize_model_names(model_names)

    trained_models: Dict[str, Any] = {}
    optimization_metrics: Dict[str, Dict[str, Any]] = {}
    for model_name in selected_models:
        selected_params, model_optimization = _select_params(
            model_name, features, hyperparams, evaluation_features=evaluation_features
        )
        started = time.perf_counter()
        trained_models[model_name] = _fit_model(model_name, x, selected_params)
        model_optimization["finalTrainingDurationMs"] = round((time.perf_counter() - started) * 1000, 2)
        optimization_metrics[model_name] = model_optimization

    return TrainArtifacts(
        models=trained_models,
        scaler=scaler,
        feature_columns=feature_columns,
        optimization_metrics=optimization_metrics,
    )


def _save_artifacts_to_dir(artifacts: TrainArtifacts, models_dir: str) -> None:
    os.makedirs(models_dir, exist_ok=True)
    model_file_map = {
        "IsolationForest": "iso_model.pkl",
        "LOF": "lof_model.pkl",
        "OneClassSVM": "svm_model.pkl",
        "EllipticEnvelope": "elliptic_envelope.pkl",
        "PCAReconstruction": "pca_reconstruction.pkl",
        "Autoencoder": "autoencoder.pkl",
    }
    for model_name, model in artifacts.models.items():
        joblib.dump(model, os.path.join(models_dir, model_file_map[model_name]))
    joblib.dump(artifacts.scaler, os.path.join(models_dir, "scaler.pkl"))
    with open(os.path.join(models_dir, "feature_columns.pkl"), "wb") as f:
        pickle.dump(artifacts.feature_columns, f)

def _save_hyperparams_used(hyperparams: Optional[Dict[str, Any]], models_dir: str) -> None:
    hp = hyperparams or {}
    with open(os.path.join(models_dir, "training_hyperparams.json"), "w", encoding="utf-8") as f:
        json.dump(hp, f, ensure_ascii=False, indent=2)


def _atomic_replace_dir(src_dir: str, dst_dir: str) -> None:
    """
    Publishes everything the training run produced.

    Deliberately moves whatever is in the staging directory rather than a hardcoded file
    list: the previous fixed list silently dropped any artifact it did not know about, so
    adding a model meant its .pkl was written to the temp directory and then discarded,
    leaving a model that trained and reported metrics but could never be loaded for scoring.
    """
    os.makedirs(dst_dir, exist_ok=True)
    for name in sorted(os.listdir(src_dir)):
        src_path = os.path.join(src_dir, name)
        if os.path.isfile(src_path):
            os.replace(src_path, os.path.join(dst_dir, name))


def train_from_transactions_df(
    transactions_df: pd.DataFrame,
    models_dir: str,
    hyperparams: Optional[Dict[str, Any]] = None,
    model_names: Optional[List[str]] = None,
    evaluation_transactions_df: Optional[pd.DataFrame] = None,
) -> Tuple[int, int, Dict[str, Dict[str, Any]]]:
    from app.legacy.feature_engineering import build_training_features

    evaluation_features: Optional[pd.DataFrame] = None
    if evaluation_transactions_df is not None and len(evaluation_transactions_df) > 0:
        features = build_training_features(transactions_df)
        combined = pd.concat([transactions_df, evaluation_transactions_df], ignore_index=True)
        combined_features = build_training_features(combined)
        training_row_count = len(transactions_df)
        evaluation_features = combined_features.loc[combined_features.index >= training_row_count].copy()
        evaluation_features = evaluation_features.reindex(columns=features.columns, fill_value=0.0)
    else:
        features = build_training_features(transactions_df)
    if features.isna().any().any():
        features = features.fillna(0.0)
    if evaluation_features is not None and evaluation_features.isna().any().any():
        evaluation_features = evaluation_features.fillna(0.0)

    artifacts = train_models(
        features,
        hyperparams=hyperparams,
        model_names=model_names,
        evaluation_features=evaluation_features,
    )
    metrics = build_training_metrics(artifacts, features, evaluation_features=evaluation_features)

    # Create temp dir on the same filesystem as models_dir so atomic replace works on Windows
    os.makedirs(models_dir, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="fraud-ml-train-", dir=models_dir) as tmp:
        _save_artifacts_to_dir(artifacts, tmp)
        _save_hyperparams_used(hyperparams, tmp)
        _atomic_replace_dir(tmp, models_dir)
    _add_artifact_sizes(metrics, models_dir)

    return int(len(transactions_df)), int(len(artifacts.feature_columns)), metrics


def build_training_metrics(
    artifacts: TrainArtifacts,
    features: pd.DataFrame,
    evaluation_features: Optional[pd.DataFrame] = None,
) -> Dict[str, Dict[str, Any]]:
    metric_features = evaluation_features if evaluation_features is not None and len(evaluation_features) > 0 else features
    x = artifacts.scaler.transform(metric_features.to_numpy(dtype=float))
    metrics: Dict[str, Dict[str, Any]] = {}
    total_rows = len(metric_features)
    feature_schema_hash = hashlib.sha256(
        "\n".join(artifacts.feature_columns).encode("utf-8")
    ).hexdigest()
    # Uniform samples over the feature bounding box; scoring these estimates level-set
    # volume for EM-AUC. Built once and reused for every model so the volume term is
    # measured against an identical reference.
    reference_x = uniform_reference_matrix(x)

    for model_name, model in artifacts.models.items():
        prediction_started = time.perf_counter()
        if model_name == "IsolationForest":
            raw_predictions = model.predict(x)
            decision_values = model.decision_function(x)
        elif model_name == "LOF":
            raw_predictions = model.predict(x)
            decision_values = model.decision_function(x)
        elif model_name == "OneClassSVM":
            raw_predictions = model.predict(x)
            decision_values = model.decision_function(x)
        elif model_name == "EllipticEnvelope":
            raw_predictions = model.predict(x)
            decision_values = model.decision_function(x)
        elif model_name == "PCAReconstruction":
            pca = model["pca"]
            threshold = float(model["threshold"])
            transformed = pca.transform(x)
            reconstructed = pca.inverse_transform(transformed)
            reconstruction_error = np.mean(np.square(x - reconstructed), axis=1)
            raw_predictions = np.where(reconstruction_error > threshold, -1, 1)
            decision_values = threshold - reconstruction_error
        elif model_name == "Autoencoder":
            threshold = float(model["threshold"])
            reconstructed = model["autoencoder"].predict(x)
            reconstruction_error = np.mean(np.square(x - reconstructed), axis=1)
            raw_predictions = np.where(reconstruction_error > threshold, -1, 1)
            decision_values = threshold - reconstruction_error
        else:
            continue
        prediction_duration_ms = (time.perf_counter() - prediction_started) * 1000.0

        anomaly_count = int(np.sum(raw_predictions == -1))
        anomaly_rate = float(anomaly_count / total_rows) if total_rows else 0.0
        # decision_values are "higher = more normal"; negate so these agree in orientation
        # with the other models' scores (higher = more anomalous) and stay comparable
        # on the model-comparison page.
        anomaly_scores = -np.asarray(decision_values, dtype=float)
        # Real and reference rows must be scored by the same function, or the shared score
        # levels EM-AUC compares them at would not line up.
        em_auc = 0.0
        if reference_x.size:
            try:
                _, em_real = _predict_and_anomaly_score(model_name, model, x)
                _, em_reference = _predict_and_anomaly_score(model_name, model, reference_x)
                # negated: excess_mass_auc needs normality-oriented scores
                em_auc = excess_mass_auc(-np.asarray(em_real), -np.asarray(em_reference))
            except Exception:
                em_auc = 0.0
        metrics[model_name] = {
            "anomalyCount": anomaly_count,
            "anomalyRate": anomaly_rate,
            "averageScore": float(np.mean(anomaly_scores)),
            "scoreP95": float(np.percentile(anomaly_scores, 95)),
            "scoreP99": float(np.percentile(anomaly_scores, 99)),
            # Label-free quality metrics — rank-based, so unlike the raw scores above these
            # are comparable across detectors.
            "excessMassAuc": em_auc,
            "scoreSkewness": score_skewness(anomaly_scores),
            "evaluationRows": total_rows,
            "featureCount": len(artifacts.feature_columns),
            "featureSchemaHash": feature_schema_hash,
            "modelCodeVersion": os.getenv("MODEL_CODE_VERSION", "local-development"),
            "evaluationStrategy": (
                "COMMON_FUTURE_HOLDOUT"
                if evaluation_features is not None and len(evaluation_features) > 0
                else "TRAINING_ROWS"
            ),
            "predictionDurationMs": round(prediction_duration_ms, 2),
            "predictionLatencyMsPerRow": round(prediction_duration_ms / max(total_rows, 1), 6),
            "decisionPercentiles": {
                "p1": float(np.percentile(decision_values, 1)),
                "p5": float(np.percentile(decision_values, 5)),
                "p50": float(np.percentile(decision_values, 50)),
                "p95": float(np.percentile(decision_values, 95)),
                "p99": float(np.percentile(decision_values, 99)),
            },
        }
        metrics[model_name].update(artifacts.optimization_metrics.get(model_name, {}))
        # The optimizer's trainingDurationMs times one candidate fit on a ~5k-row subsample,
        # not the real thing. Reporting that as "training duration" made One-Class SVM — by
        # far the slowest model — look like the fastest. Promote the full-dataset fit time so
        # this column means the same for every supported model.
        final_duration = metrics[model_name].pop("finalTrainingDurationMs", None)
        if final_duration is not None:
            metrics[model_name]["candidateSearchDurationMs"] = metrics[model_name].get("trainingDurationMs")
            metrics[model_name]["trainingDurationMs"] = final_duration
        if model_name in RECONSTRUCTION_MODELS:
            metrics[model_name]["threshold"] = float(model["threshold"])
            metrics[model_name]["percentile"] = float(model["percentile"])

    return metrics


def _add_artifact_sizes(metrics: Dict[str, Dict[str, Any]], models_dir: str) -> None:
    model_files = {
        "IsolationForest": "iso_model.pkl",
        "LOF": "lof_model.pkl",
        "OneClassSVM": "svm_model.pkl",
        "EllipticEnvelope": "elliptic_envelope.pkl",
        "PCAReconstruction": "pca_reconstruction.pkl",
        "Autoencoder": "autoencoder.pkl",
    }
    shared_files = ["scaler.pkl", "feature_columns.pkl", "training_hyperparams.json"]
    shared_bytes = sum(
        os.path.getsize(os.path.join(models_dir, name))
        for name in shared_files
        if os.path.exists(os.path.join(models_dir, name))
    )
    for model_name, file_name in model_files.items():
        if model_name not in metrics:
            continue
        model_path = os.path.join(models_dir, file_name)
        model_bytes = os.path.getsize(model_path) if os.path.exists(model_path) else 0
        metrics[model_name]["modelArtifactBytes"] = model_bytes
        metrics[model_name]["bundleArtifactBytes"] = model_bytes + shared_bytes
