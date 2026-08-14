from __future__ import annotations

import math
import time
from dataclasses import dataclass
from typing import Any, Iterable, NamedTuple

import numpy as np
from scipy.stats import skew as scipy_skew
from sklearn.ensemble import IsolationForest
from sklearn.neural_network import MLPRegressor
from sklearn.neighbors import LocalOutlierFactor
from sklearn.preprocessing import StandardScaler

from app.incremental.parquet_dataset import PersistedFeatureDataset
from app.quality_metrics import excess_mass_auc, uniform_reference_matrix


DETECTORS = (
    "ISOLATION_FOREST",
    "AUTOENCODER",
    "LOCAL_OUTLIER_FACTOR",
)


class DetectorScores(NamedTuple):
    """
    What one detector produced at one partition.

    ``reference_scores`` are this detector's scores for uniform samples over the
    evaluation feature box. They are what makes a real Excess-Mass estimate possible:
    without a feature-space volume term, EM-AUC collapses to ~0 for every detector.
    All scores here are anomaly-oriented (higher = more anomalous).
    """

    scores: np.ndarray
    threshold: float
    learned_rows: int
    reference_scores: np.ndarray


@dataclass(frozen=True)
class GrowthAnalysisOptions:
    percentages: tuple[int, ...] = (10, 25, 50, 100)
    minimum_rows: int = 200
    holdout_fraction: float = 0.20
    maximum_evaluation_rows: int = 20_000
    isolation_forest_max_training_rows: int = 100_000
    isolation_forest_estimators: int = 200
    isolation_forest_max_samples: int = 10_000
    autoencoder_max_training_rows: int = 50_000
    autoencoder_hidden_layer_sizes: tuple[int, ...] = (32, 8, 32)
    autoencoder_max_iter: int = 200
    lof_max_training_rows: int = 50_000
    lof_n_neighbors: int = 35
    lof_contamination: float = 0.05
    random_seed: int = 42


def analyze_detector_growth(
    dataset_path: str,
    dataset_checksum: str,
    options: GrowthAnalysisOptions,
) -> dict[str, Any]:
    dataset = PersistedFeatureDataset(dataset_path, dataset_checksum)
    columns = dataset.collect_feature_columns()
    partitions = _partition_sizes(dataset.row_count, options.percentages, options.minimum_rows)
    results: list[dict[str, Any]] = []
    for percentage, rows in partitions:
        training_rows = max(options.minimum_rows, int(rows * (1.0 - options.holdout_fraction)))
        training_rows = min(training_rows, rows - 1)
        evaluation_start = training_rows
        evaluation_rows = min(rows - training_rows, options.maximum_evaluation_rows)
        evaluation = _matrix(
            dataset.iter_feature_range(evaluation_start, evaluation_start + evaluation_rows), columns
        )
        for detector in DETECTORS:
            results.append(_evaluate_detector(
                detector, dataset, columns, training_rows, evaluation, percentage, rows, options
            ))
    rank_stabilities = _compute_rank_stabilities(results)
    for row in results:
        row["rankStability"] = round(rank_stabilities[row["detector"]], 6)
    return {
        "status": "COMPLETED",
        "datasetRows": dataset.row_count,
        "featureCount": len(columns),
        "featureVersion": dataset.feature_version,
        "partitionPercentages": [percentage for percentage, _ in partitions],
        "detectors": list(DETECTORS),
        "methodology": {
            "ordering": "OLDEST_FIRST",
            "validation": "CHRONOLOGICAL_HOLDOUT_WITHIN_EACH_PARTITION",
            "holdoutFraction": options.holdout_fraction,
            "maximumEvaluationRows": options.maximum_evaluation_rows,
            "isolationForestMaximumTrainingRows": options.isolation_forest_max_training_rows,
            "isolationForestEstimators": options.isolation_forest_estimators,
            "autoencoderMaxTrainingRows": options.autoencoder_max_training_rows,
            "autoencoderHiddenLayers": list(options.autoencoder_hidden_layer_sizes),
            "localOutlierFactorMaxTrainingRows": options.lof_max_training_rows,
            "localOutlierFactorNeighbors": options.lof_n_neighbors,
            "labelsAvailable": False,
            "scoringVersion": "2.0",
            "qualityStatement": (
                "EM-AUC (Goix et al., 2016), score skewness, and Kendall-tau rank stability "
                "are label-free diagnostics. They measure score distribution quality, not fraud accuracy."
            ),
        },
        "results": results,
    }


def _evaluate_detector(
    detector: str,
    dataset: PersistedFeatureDataset,
    columns: list[str],
    training_rows: int,
    evaluation: np.ndarray,
    percentage: int,
    partition_rows: int,
    options: GrowthAnalysisOptions,
) -> dict[str, Any]:
    started = time.perf_counter()
    # Uniform samples over the evaluation feature box, scored by the same detector, give
    # EM-AUC its level-set volume term. Built once per cell so every detector at this
    # partition is measured against an identical reference.
    reference = uniform_reference_matrix(evaluation, seed=options.random_seed)
    if detector == "ISOLATION_FOREST":
        result = _isolation_forest(dataset, columns, training_rows, evaluation, reference, options)
    elif detector == "AUTOENCODER":
        result = _autoencoder(dataset, columns, training_rows, evaluation, reference, options)
    else:
        result = _local_outlier_factor(dataset, columns, training_rows, evaluation, reference, options)
    scores, threshold, learned_rows = result.scores, result.threshold, result.learned_rows
    duration_ms = (time.perf_counter() - started) * 1000.0
    anomaly_flags = scores >= threshold
    return {
        "partitionPercentage": percentage,
        "partitionRows": partition_rows,
        "trainingRows": training_rows,
        "learnedRows": learned_rows,
        "evaluationRows": int(scores.size),
        "detector": detector,
        "anomalyRate": float(np.mean(anomaly_flags)),
        "alertCount": int(np.sum(anomaly_flags)),
        "threshold": float(threshold),
        "averageScore": float(np.mean(scores)),
        "scoreP50": float(np.quantile(scores, 0.50)),
        "scoreP95": float(np.quantile(scores, 0.95)),
        "scoreP99": float(np.quantile(scores, 0.99)),
        # negated: excess_mass_auc needs normality-oriented scores, these are anomaly-oriented
        "excessMassAuc": excess_mass_auc(-scores, -result.reference_scores),
        "scoreSkewness": _score_skewness_normalized(scores),
        "rankStability": 0.0,
        "trainingDurationMs": round(duration_ms, 2),
        "rowsPerSecond": round(learned_rows / max(duration_ms / 1000.0, 0.001), 2),
        "boundedTrainingSample": learned_rows < training_rows,
    }


def _isolation_forest(
    dataset: PersistedFeatureDataset,
    columns: list[str],
    training_rows: int,
    evaluation: np.ndarray,
    reference: np.ndarray,
    options: GrowthAnalysisOptions,
) -> DetectorScores:
    learned_rows = min(training_rows, options.isolation_forest_max_training_rows)
    training = _matrix(dataset.iter_feature_range(0, learned_rows), columns)
    scaler = StandardScaler().fit(training)
    max_samples = min(learned_rows, options.isolation_forest_max_samples)
    model = IsolationForest(
        n_estimators=options.isolation_forest_estimators,
        max_samples=max_samples,
        contamination="auto",
        random_state=options.random_seed,
        n_jobs=-1,
    ).fit(scaler.transform(training))
    training_scores = -model.score_samples(scaler.transform(training))
    scores = -model.score_samples(scaler.transform(evaluation))
    reference_scores = -model.score_samples(scaler.transform(reference))
    return DetectorScores(
        scores, float(np.quantile(training_scores, 0.99)), learned_rows, reference_scores
    )


def _local_outlier_factor(
    dataset: PersistedFeatureDataset,
    columns: list[str],
    training_rows: int,
    evaluation: np.ndarray,
    reference: np.ndarray,
    options: GrowthAnalysisOptions,
) -> DetectorScores:
    learned_rows = min(training_rows, options.lof_max_training_rows)
    training = _matrix(dataset.iter_feature_range(0, learned_rows), columns)
    scaler = StandardScaler().fit(training)
    scaled_training = scaler.transform(training)
    neighbors = max(2, min(options.lof_n_neighbors, learned_rows - 1))
    model = LocalOutlierFactor(
        n_neighbors=neighbors,
        contamination=options.lof_contamination,
        novelty=True,
        n_jobs=-1,
    ).fit(scaled_training)
    training_scores = -model.decision_function(scaled_training)
    scores = -model.decision_function(scaler.transform(evaluation))
    reference_scores = -model.decision_function(scaler.transform(reference))
    return DetectorScores(
        scores,
        float(np.quantile(training_scores, 1.0 - options.lof_contamination)),
        learned_rows,
        reference_scores,
    )


def _autoencoder(
    dataset: PersistedFeatureDataset,
    columns: list[str],
    training_rows: int,
    evaluation: np.ndarray,
    reference: np.ndarray,
    options: GrowthAnalysisOptions,
) -> DetectorScores:
    learned_rows = min(training_rows, options.autoencoder_max_training_rows)
    training = _matrix(dataset.iter_feature_range(0, learned_rows), columns)
    scaler = StandardScaler().fit(training)
    scaled_training = scaler.transform(training)
    # MLPRegressor trained to reconstruct its own input acts as an autoencoder
    model = MLPRegressor(
        hidden_layer_sizes=options.autoencoder_hidden_layer_sizes,
        activation="relu",
        max_iter=options.autoencoder_max_iter,
        random_state=options.random_seed,
        early_stopping=True,
        validation_fraction=0.1,
        n_iter_no_change=10,
    )
    model.fit(scaled_training, scaled_training)
    training_reconstructed = model.predict(scaled_training)
    training_scores = np.mean(np.square(scaled_training - training_reconstructed), axis=1)

    def reconstruction_error(matrix: np.ndarray) -> np.ndarray:
        scaled = scaler.transform(matrix)
        return np.mean(np.square(scaled - model.predict(scaled)), axis=1)

    return DetectorScores(
        reconstruction_error(evaluation),
        float(np.quantile(training_scores, 0.99)),
        learned_rows,
        reconstruction_error(reference),
    )

def _partition_sizes(total: int, percentages: tuple[int, ...], minimum: int) -> list[tuple[int, int]]:
    if total < minimum:
        raise ValueError(f"At least {minimum} eligible rows are required for growth analysis")
    result: list[tuple[int, int]] = []
    for percentage in sorted(set(percentages)):
        if percentage <= 0 or percentage > 100:
            raise ValueError("Partition percentages must be between 1 and 100")
        rows = total if percentage == 100 else math.floor(total * percentage / 100)
        if rows >= minimum and (not result or rows != result[-1][1]):
            result.append((percentage, rows))
    if not result or result[-1][1] != total:
        result.append((100, total))
    return result


def _matrix(rows: Iterable[dict[str, float]], columns: list[str]) -> np.ndarray:
    matrix = np.asarray([[float(row.get(name, 0.0)) for name in columns] for row in rows], dtype=float)
    if matrix.ndim != 2 or matrix.shape[0] == 0:
        raise ValueError("Growth-analysis partition contains no rows")
    return matrix


def _score_skewness_normalized(scores: np.ndarray) -> float:
    """
    Normalised score skewness.

    Positive skewness means anomaly scores form a right-hand tail — the
    hallmark of a well-behaved detector. Negative skewness means the model
    inverted the score polarity or is not separating outliers at all.

    Raw Fisher skewness is mapped through tanh(raw / 3) to (-1, 1) then
    shifted to [0, 1] so the metric integrates cleanly with others.
    """
    if len(scores) < 3:
        return 0.0
    raw = float(scipy_skew(scores))
    if not math.isfinite(raw):
        return 0.0
    return float((np.tanh(raw / 3.0) + 1.0) / 2.0)


def _compute_rank_stabilities(results: list[dict[str, Any]]) -> dict[str, float]:
    """
    Per-detector rank stability based on mean absolute consecutive rank change.

    At each partition, detectors are ranked by EM-AUC (rank 0 = best).
    For each detector, the rank trajectory across partitions is recorded and
    consecutive absolute rank changes are averaged, then normalised by the
    maximum possible change (n_detectors - 1) so the result is in [0, 1].

        stability = 1 - mean(|rank_t+1 - rank_t| / (n - 1))

    A detector that holds rank #2 across all partitions scores 1.0.
    A detector that swings from #1 to #7 every partition scores near 0.0.
    """
    percentages = sorted({r["partitionPercentage"] for r in results})
    detectors = list(DETECTORS)
    n = len(detectors)

    if len(percentages) < 2 or n < 2:
        return {d: 1.0 for d in detectors}

    em_by_pct: dict[int, dict[str, float]] = {}
    for row in results:
        em_by_pct.setdefault(row["partitionPercentage"], {})[row["detector"]] = row["excessMassAuc"]

    rankings: dict[int, dict[str, int]] = {}
    for pct in percentages:
        em_vals = em_by_pct[pct]
        ordered = sorted(detectors, key=lambda d: em_vals.get(d, 0.0), reverse=True)
        rankings[pct] = {d: rank for rank, d in enumerate(ordered)}

    stabilities: dict[str, float] = {}
    for det in detectors:
        trajectory = [rankings[pct][det] for pct in percentages]
        changes = [
            abs(trajectory[i + 1] - trajectory[i]) / (n - 1)
            for i in range(len(trajectory) - 1)
        ]
        stabilities[det] = 1.0 - (sum(changes) / len(changes))

    return stabilities
