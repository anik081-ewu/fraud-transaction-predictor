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
    precision_recall_curve,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.preprocessing import StandardScaler

from app.incremental.parquet_dataset import PersistedFeatureDataset
from app.supervised_training import SUPPORTED_MODELS, _build_model


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
    for features, label in dataset.iter_labelled_features():
        rows.append(features)
        labels.append(label)
        if len(rows) >= maximum_rows:
            break
    if len(rows) < minimum_rows:
        raise ValueError(f"At least {minimum_rows} labelled rows are required; found {len(rows)}")
    if set(labels) != {0, 1}:
        raise ValueError("Labelled comparison data must contain FraudLabel 0 and 1")

    feature_frame = pd.DataFrame(rows).fillna(0.0).reindex(sorted({key for row in rows for key in row}), axis=1, fill_value=0.0)
    label_array = np.asarray(labels, dtype=int)
    usable = sorted({value for value in percentages if 0 < value <= 100})
    results = []
    for percentage in usable:
        partition_rows = max(1, int(len(rows) * percentage / 100))
        if partition_rows < minimum_rows and percentage != 100:
            continue
        x_partition = feature_frame.iloc[:partition_rows]
        y_partition = label_array[:partition_rows]
        train_end = max(1, min(partition_rows - 2, int(partition_rows * 0.7)))
        validation_end = max(train_end + 1, min(partition_rows - 1, int(partition_rows * 0.8)))
        x_train_frame = x_partition.iloc[:train_end]
        x_validation_frame = x_partition.iloc[train_end:validation_end]
        x_test_frame = x_partition.iloc[validation_end:]
        y_train = y_partition[:train_end]
        y_validation = y_partition[train_end:validation_end]
        y_test = y_partition[validation_end:]
        if len(set(y_train)) < 2 or len(set(y_validation)) < 2 or len(set(y_test)) < 2:
            continue
        scaler = StandardScaler()
        x_train = scaler.fit_transform(x_train_frame.to_numpy(dtype=float))
        x_validation = scaler.transform(x_validation_frame.to_numpy(dtype=float))
        x_test = scaler.transform(x_test_frame.to_numpy(dtype=float))
        for model_name in SUPPORTED_MODELS:
            model = _build_model(model_name, random_seed, pd.Series(y_train), hyperparams)
            started = time.perf_counter()
            model.fit(x_train, y_train)
            training_ms = (time.perf_counter() - started) * 1000
            validation_probability = model.predict_proba(x_validation)[:, 1]
            threshold = _best_f1_threshold(y_validation, validation_probability)
            scoring_started = time.perf_counter()
            probability = model.predict_proba(x_test)[:, 1]
            prediction = (probability >= threshold).astype(int)
            scoring_ms = (time.perf_counter() - scoring_started) * 1000
            matrix = confusion_matrix(y_test, prediction, labels=[0, 1])
            positive_rate = float(np.mean(y_test))
            pr_auc = float(average_precision_score(y_test, probability))
            results.append({
                "partitionPercentage": percentage,
                "partitionRows": partition_rows,
                "trainingRows": len(y_train),
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
                "trainingDurationMs": round(training_ms, 2),
                "rowsPerSecond": round(len(y_test) / max(scoring_ms / 1000, 0.000001), 2),
            })
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
            "split": "chronological 70% train / 10% threshold calibration / 20% evaluation",
            "threshold": "selected on calibration rows to maximize F1; evaluation rows remain untouched",
            "maximumRows": maximum_rows,
            "blankLabels": "excluded",
        },
        "results": results,
    }


def _best_f1_threshold(labels: np.ndarray, probabilities: np.ndarray) -> float:
    precision, recall, thresholds = precision_recall_curve(labels, probabilities)
    if not len(thresholds):
        return 0.5
    f1 = 2 * precision[:-1] * recall[:-1] / np.maximum(precision[:-1] + recall[:-1], 1e-12)
    best = np.flatnonzero(f1 == np.nanmax(f1))
    if not len(best):
        return 0.5
    return float(np.clip(thresholds[int(best[-1])], 0.01, 0.99))
