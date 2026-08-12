from __future__ import annotations

import math
import os
import shutil
import tempfile
import time
from functools import lru_cache
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib
from river import anomaly, compose, preprocessing, stats

from app.incremental.parquet_dataset import PersistedFeatureDataset
from app.quality_metrics import excess_mass_auc, score_skewness, uniform_reference_scores
from app.registry.artifact_bundle import write_artifact_manifest


MODEL_FILE = "hst_model.pkl"


REFERENCE_ROW_SAMPLE = 2048


def _uniform_reference_scores(
    pipeline: compose.Pipeline,
    feature_columns: list[str],
    minimums: dict[str, float],
    maximums: dict[str, float],
    observed_rows: list[dict[str, float]],
) -> list[float]:
    return uniform_reference_scores(
        pipeline.score_one, feature_columns, minimums, maximums, observed_rows
    )


@dataclass
class HalfSpaceTreesArtifact:
    model_version: str
    feature_version: str
    model_segment: str | None
    feature_columns: list[str]
    feature_schema_checksum: str
    threshold: float
    pipeline: compose.Pipeline
    parameters: dict[str, Any]
    normalization_floor: float = 0.0

    def aligned(self, features: dict[str, float]) -> dict[str, float]:
        return {name: float(features.get(name, 0.0)) for name in self.feature_columns}

    def score(self, features: dict[str, float]) -> float:
        return float(self.pipeline.score_one(self.aligned(features)))

    def normalized_score(self, features: dict[str, float]) -> float:
        score = self.score(features)
        floor = float(getattr(self, "normalization_floor", 0.0))
        width = self.threshold - floor
        if width <= 0.0:
            return float(score >= self.threshold)
        return max(0.0, min(1.0, (score - floor) / width))


@lru_cache(maxsize=32)
def load_hst_artifact(bundle_path: str) -> HalfSpaceTreesArtifact:
    return _load_hst_artifact_uncached(bundle_path)


def _load_hst_artifact_uncached(bundle_path: str) -> HalfSpaceTreesArtifact:
    artifact = joblib.load(Path(bundle_path).resolve() / MODEL_FILE)
    if not isinstance(artifact, HalfSpaceTreesArtifact):
        raise ValueError("Artifact is not a Half-Space Trees candidate bundle")
    return artifact


def train_half_space_trees(
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
        artifact = _load_hst_artifact_uncached(base_model_path)
        if artifact.feature_version != feature_version or artifact.model_segment != model_segment:
            raise ValueError("Base HST artifact is incompatible with the training request")
        unexpected = sorted(set(observed_columns) - set(artifact.feature_columns))
        if unexpected:
            raise ValueError(f"Feature schema drift detected: {', '.join(unexpected[:10])}")
        feature_columns = artifact.feature_columns
        pipeline = artifact.pipeline
        base_model_version = artifact.model_version
    else:
        feature_columns = observed_columns
        pipeline = preprocessing.MinMaxScaler() | anomaly.HalfSpaceTrees(
            n_trees=int(parameters.get("nTrees", 25)),
            height=int(parameters.get("height", 8)),
            window_size=int(parameters.get("windowSize", 250)),
            seed=int(parameters.get("seed", 42)),
        )

    quantile_value = float(parameters.get("thresholdQuantile", 0.99))
    if not 0.9 <= quantile_value < 1:
        raise ValueError("thresholdQuantile must be between 0.90 and 1.0")
    threshold_estimator = stats.Quantile(quantile_value)
    warmup = int(parameters.get("windowSize", 250))
    learned_rows = 0
    for features in dataset.iter_features():
        aligned = {name: float(features.get(name, 0.0)) for name in feature_columns}
        if learned_rows >= warmup:
            threshold_estimator.update(float(pipeline.score_one(aligned)))
        pipeline.learn_one(aligned)
        learned_rows += 1
    if learned_rows != dataset.row_count:
        raise ValueError(f"Training row count changed: expected={dataset.row_count}, learned={learned_rows}")
    threshold = threshold_estimator.get()
    if threshold is None or not math.isfinite(float(threshold)):
        raise ValueError("Insufficient rows to establish an HST anomaly threshold")

    mean = stats.Mean()
    p95 = stats.Quantile(0.95)
    p99 = stats.Quantile(0.99)
    alerts = 0
    validation_rows = 0
    # Collected so the label-free quality metrics can be computed over the full score
    # distribution; running estimators cannot provide excess mass or skewness.
    calibration_scores: list[float] = []
    observed_minimums: dict[str, float] = {}
    observed_maximums: dict[str, float] = {}
    # Real rows kept so the EM-AUC reference can perturb them rather than sampling the whole
    # feature box, which degenerates once there are many features
    observed_rows: list[dict[str, float]] = []
    for features in dataset.iter_features():
        aligned = {name: float(features.get(name, 0.0)) for name in feature_columns}
        score = float(pipeline.score_one(aligned))
        mean.update(score)
        p95.update(score)
        p99.update(score)
        calibration_scores.append(score)
        if len(observed_rows) < REFERENCE_ROW_SAMPLE:
            observed_rows.append(aligned)
        for name, value in aligned.items():
            if name not in observed_minimums or value < observed_minimums[name]:
                observed_minimums[name] = value
            if name not in observed_maximums or value > observed_maximums[name]:
                observed_maximums[name] = value
        alerts += int(score >= threshold)
        validation_rows += 1

    reference_scores = _uniform_reference_scores(
        pipeline, feature_columns, observed_minimums, observed_maximums, observed_rows
    )
    metrics = {
        "anomalyRate": alerts / max(validation_rows, 1),
        "validationRowCount": validation_rows,
        "alertCount": alerts,
        "averageScore": float(mean.get()),
        "scoreP95": float(p95.get()),
        "scoreP99": float(p99.get()),
        # negated: HST scores are anomaly-oriented, excess_mass_auc needs normality
        "excessMassAuc": excess_mass_auc(
            [-score for score in calibration_scores],
            [-score for score in reference_scores],
        ),
        "scoreSkewness": score_skewness(calibration_scores),
        "threshold": float(threshold),
        "trainingDurationMs": round((time.perf_counter() - started) * 1000, 2),
    }
    from app.registry.artifact_bundle import feature_schema_checksum

    schema_checksum = feature_schema_checksum(feature_columns)
    candidate = HalfSpaceTreesArtifact(
        model_version=model_version,
        feature_version=feature_version,
        model_segment=model_segment,
        feature_columns=feature_columns,
        feature_schema_checksum=schema_checksum,
        threshold=float(threshold),
        pipeline=pipeline,
        parameters=dict(parameters),
        normalization_floor=float(mean.get()),
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
            model_type="HALF_SPACE_TREES",
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
