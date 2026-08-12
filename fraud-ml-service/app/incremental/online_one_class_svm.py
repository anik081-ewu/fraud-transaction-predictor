from __future__ import annotations

import math
import os
import shutil
import tempfile
import time
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any

import joblib
from river import anomaly, compose, feature_extraction, optim, preprocessing, stats

from app.incremental.parquet_dataset import PersistedFeatureDataset
from app.quality_metrics import excess_mass_auc, score_skewness, uniform_reference_scores
from app.registry.artifact_bundle import feature_schema_checksum, write_artifact_manifest


MODEL_FILE = "online_ocsvm_model.pkl"

REFERENCE_ROW_SAMPLE = 2048
MODEL_TYPE = "ONLINE_ONE_CLASS_SVM"


@dataclass
class OnlineOneClassSvmArtifact:
    model_version: str
    feature_version: str
    model_segment: str | None
    feature_columns: list[str]
    feature_schema_checksum: str
    raw_threshold: float
    normalization_floor: float
    normalization_ceiling: float
    pipeline: compose.Pipeline
    parameters: dict[str, Any]

    def aligned(self, features: dict[str, float]) -> dict[str, float]:
        return {name: float(features.get(name, 0.0)) for name in self.feature_columns}

    def raw_score(self, features: dict[str, float]) -> float:
        return float(self.pipeline.score_one(self.aligned(features)))

    def normalized_score(self, features: dict[str, float]) -> float:
        raw_score = self.raw_score(features)
        width = self.normalization_ceiling - self.normalization_floor
        if width <= 0.0:
            return float(raw_score >= self.raw_threshold)
        return _clamp((raw_score - self.normalization_floor) / width)

    def is_anomaly(self, features: dict[str, float]) -> bool:
        return self.raw_score(features) >= self.raw_threshold


@lru_cache(maxsize=32)
def load_online_ocsvm_artifact(bundle_path: str) -> OnlineOneClassSvmArtifact:
    return _load_online_ocsvm_artifact_uncached(bundle_path)


def _load_online_ocsvm_artifact_uncached(bundle_path: str) -> OnlineOneClassSvmArtifact:
    artifact = joblib.load(Path(bundle_path).resolve() / MODEL_FILE)
    if not isinstance(artifact, OnlineOneClassSvmArtifact):
        raise ValueError("Artifact is not an Online One-Class SVM candidate bundle")
    return artifact


def train_online_one_class_svm(
    *,
    dataset_path: str,
    dataset_checksum: str,
    artifact_base_path: str,
    model_version: str,
    model_segment: str | None,
    feature_version: str,
    training_run_id: str,
    base_model_path: str | None,
    parameters: dict[str, Any],
) -> dict[str, Any]:
    started = time.perf_counter()
    batch_size = int(parameters.get("batchSize", 65_536))
    dataset = PersistedFeatureDataset(dataset_path, dataset_checksum, batch_size)
    if dataset.feature_version != feature_version:
        raise ValueError("Dataset feature version is incompatible with the training request")
    observed_columns = dataset.collect_feature_columns()
    base_model_version = None
    if base_model_path:
        artifact = _load_online_ocsvm_artifact_uncached(base_model_path)
        _validate_base_artifact(artifact, feature_version, model_segment, observed_columns, parameters)
        feature_columns = artifact.feature_columns
        pipeline = artifact.pipeline
        base_model_version = artifact.model_version
    else:
        feature_columns = observed_columns
        pipeline = _new_pipeline(parameters)

    learned_rows = 0
    for features in dataset.iter_features():
        pipeline.learn_one(_align(features, feature_columns))
        learned_rows += 1
    if learned_rows != dataset.row_count:
        raise ValueError(f"Training row count changed: expected={dataset.row_count}, learned={learned_rows}")

    threshold_quantile = float(parameters.get("thresholdQuantile", 0.99))
    if not 0.90 <= threshold_quantile < 1.0:
        raise ValueError("thresholdQuantile must be between 0.90 and 1.0")
    minimum_calibration_rows = int(parameters.get("minimumCalibrationRows", 20))
    if minimum_calibration_rows < 10 or learned_rows < minimum_calibration_rows:
        raise ValueError(
            f"At least {minimum_calibration_rows} rows are required to calibrate Online One-Class SVM"
        )

    raw_mean = stats.Mean()
    raw_p50 = stats.Quantile(0.50)
    raw_p95 = stats.Quantile(0.95)
    raw_p99 = stats.Quantile(0.99)
    raw_threshold_estimator = stats.Quantile(threshold_quantile)
    calibration_rows = 0
    for features in dataset.iter_features():
        raw_score = float(pipeline.score_one(_align(features, feature_columns)))
        if not math.isfinite(raw_score):
            raise ValueError("Online One-Class SVM produced a non-finite calibration score")
        raw_mean.update(raw_score)
        raw_p50.update(raw_score)
        raw_p95.update(raw_score)
        raw_p99.update(raw_score)
        raw_threshold_estimator.update(raw_score)
        calibration_rows += 1

    normalization_floor = _required_stat(raw_p50.get(), "median calibration score")
    raw_threshold = _required_stat(raw_threshold_estimator.get(), "anomaly threshold")
    if raw_threshold <= normalization_floor:
        raise ValueError("Online One-Class SVM calibration scores have insufficient spread")

    alerts = 0
    normalized_mean = stats.Mean()
    # Collected so the label-free quality metrics can be computed over the full score
    # distribution; running estimators cannot provide excess mass or skewness.
    calibration_scores: list[float] = []
    observed_minimums: dict[str, float] = {}
    observed_maximums: dict[str, float] = {}
    # Real rows kept so the EM-AUC reference can perturb them rather than sampling the whole
    # feature box, which degenerates once there are many features
    observed_rows: list[dict[str, float]] = []
    for features in dataset.iter_features():
        aligned = _align(features, feature_columns)
        raw_score = float(pipeline.score_one(aligned))
        if len(observed_rows) < REFERENCE_ROW_SAMPLE:
            observed_rows.append(dict(aligned))
        normalized_mean.update(_clamp(
            (raw_score - normalization_floor) / (raw_threshold - normalization_floor)
        ))
        calibration_scores.append(raw_score)
        for name, value in aligned.items():
            numeric = float(value)
            if name not in observed_minimums or numeric < observed_minimums[name]:
                observed_minimums[name] = numeric
            if name not in observed_maximums or numeric > observed_maximums[name]:
                observed_maximums[name] = numeric
        alerts += int(raw_score >= raw_threshold)

    reference_scores = uniform_reference_scores(
        pipeline.score_one, feature_columns, observed_minimums, observed_maximums, observed_rows
    )

    metrics = {
        "anomalyRate": alerts / max(calibration_rows, 1),
        "validationRowCount": calibration_rows,
        "alertCount": alerts,
        # averageScore must share units with scoreP95/scoreP99, which are raw. Reporting the
        # normalized mean here made the triplet incoherent (e.g. avg 0.18 beside P95 34203).
        # The normalized mean is still exposed separately for the scoring-side view.
        "averageScore": float(raw_mean.get()),
        "scoreP95": _required_stat(raw_p95.get(), "95th percentile score"),
        "scoreP99": _required_stat(raw_p99.get(), "99th percentile score"),
        # negated: these scores are anomaly-oriented, excess_mass_auc needs normality
        "excessMassAuc": excess_mass_auc(
            [-score for score in calibration_scores],
            [-score for score in reference_scores],
        ),
        "scoreSkewness": score_skewness(calibration_scores),
        "threshold": raw_threshold,
        "normalizedAverageScore": float(normalized_mean.get()),
        "normalizationFloor": normalization_floor,
        "normalizationCeiling": raw_threshold,
        "normalizationVersion": "ONLINE_OCSVM_EMPIRICAL_V1",
        "trainingDurationMs": round((time.perf_counter() - started) * 1000, 2),
    }
    candidate = OnlineOneClassSvmArtifact(
        model_version=model_version,
        feature_version=feature_version,
        model_segment=model_segment,
        feature_columns=feature_columns,
        feature_schema_checksum=feature_schema_checksum(feature_columns),
        raw_threshold=raw_threshold,
        normalization_floor=normalization_floor,
        normalization_ceiling=raw_threshold,
        pipeline=pipeline,
        parameters=dict(parameters),
    )
    artifact_base = Path(artifact_base_path).resolve()
    artifact_base.mkdir(parents=True, exist_ok=True)
    final_path = artifact_base / model_version
    if final_path.exists():
        raise ValueError(f"Candidate artifact already exists: {final_path}")
    temporary_path = Path(tempfile.mkdtemp(prefix=f".{model_version}-", dir=artifact_base))
    try:
        joblib.dump(candidate, temporary_path / MODEL_FILE)
        bundle = write_artifact_manifest(
            temporary_path,
            model_version=model_version,
            model_type=MODEL_TYPE,
            model_segment=model_segment,
            feature_version=feature_version,
            feature_columns=feature_columns,
            training_run_id=training_run_id,
            dataset_checksum=dataset_checksum,
            base_model_version=base_model_version,
            learned_row_count=learned_rows,
            parameters=parameters,
            metrics=metrics,
        )
        os.replace(temporary_path, final_path)
        return {
            "status": "CANDIDATE_READY",
            "modelVersion": model_version,
            "artifactPath": str(final_path),
            "artifactChecksum": bundle.artifactChecksum,
            "featureSchemaChecksum": bundle.featureSchemaChecksum,
            "learnedRowCount": learned_rows,
            **metrics,
            "parameters": parameters,
            "metrics": metrics,
        }
    except Exception:
        shutil.rmtree(temporary_path, ignore_errors=True)
        raise


def _new_pipeline(parameters: dict[str, Any]) -> compose.Pipeline:
    nu = float(parameters.get("nu", 0.05))
    learning_rate = float(parameters.get("learningRate", 0.01))
    intercept_learning_rate = float(parameters.get("interceptLearningRate", 0.01))
    gamma = float(parameters.get("gamma", 0.5))
    n_components = int(parameters.get("nComponents", 64))
    seed = int(parameters.get("seed", 42))
    if not 0.0 < nu < 1.0:
        raise ValueError("nu must be between 0.0 and 1.0")
    if learning_rate <= 0.0 or intercept_learning_rate < 0.0 or gamma <= 0.0:
        raise ValueError("Online One-Class SVM learning rates and gamma are invalid")
    if not 8 <= n_components <= 4096:
        raise ValueError("nComponents must be between 8 and 4096")
    return (
        preprocessing.StandardScaler()
        | feature_extraction.RBFSampler(gamma=gamma, n_components=n_components, seed=seed)
        | anomaly.OneClassSVM(
            nu=nu,
            optimizer=optim.SGD(learning_rate),
            intercept_lr=intercept_learning_rate,
        )
    )


def _validate_base_artifact(
    artifact: OnlineOneClassSvmArtifact,
    feature_version: str,
    model_segment: str | None,
    observed_columns: list[str],
    parameters: dict[str, Any],
) -> None:
    if artifact.feature_version != feature_version or artifact.model_segment != model_segment:
        raise ValueError("Base Online One-Class SVM artifact is incompatible with the training request")
    unexpected = sorted(set(observed_columns) - set(artifact.feature_columns))
    if unexpected:
        raise ValueError(f"Feature schema drift detected: {', '.join(unexpected[:10])}")
    structural_defaults = {
        "nu": 0.05,
        "learningRate": 0.01,
        "interceptLearningRate": 0.01,
        "gamma": 0.5,
        "nComponents": 64,
        "seed": 42,
    }
    for name, default in structural_defaults.items():
        requested = parameters.get(name, default)
        existing = artifact.parameters.get(name, default)
        if requested != existing:
            raise ValueError(f"Base Online One-Class SVM parameter is incompatible: {name}")


def _align(features: dict[str, float], columns: list[str]) -> dict[str, float]:
    return {name: float(features.get(name, 0.0)) for name in columns}


def _required_stat(value: float | None, label: str) -> float:
    if value is None or not math.isfinite(float(value)):
        raise ValueError(f"Could not establish {label}")
    return float(value)


def _clamp(value: float) -> float:
    if not math.isfinite(value):
        return 0.0
    return max(0.0, min(1.0, float(value)))
