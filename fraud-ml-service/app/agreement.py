"""
Do the trained models flag the same transactions, or different ones?

Scores one exported snapshot with every available model and reports how much their flagged
sets overlap. Needs no labels — it only compares which rows each model picks, which is
enough to tell whether an ensemble adds coverage or just pays repeatedly for one signal.
"""
from __future__ import annotations

import pickle
import time
from pathlib import Path
from typing import Any

import joblib
import numpy as np

from app.incremental.parquet_dataset import PersistedFeatureDataset

# Registry model type -> label used in the response
MODEL_FILES = {
    "ISOLATION_FOREST": "iso_model.pkl",
    "AUTOENCODER": "autoencoder.pkl",
    "BEHAVIORAL_CLUSTER_OUTLIER": "behavioral_cluster_outlier.pkl",
}


def _flagged_for_model(model_type: str, model: Any, x: np.ndarray) -> np.ndarray:
    if model_type == "AUTOENCODER":
        reconstruction = np.mean(np.square(x - model["autoencoder"].predict(x)), axis=1)
        return reconstruction > float(model["threshold"])
    # Isolation Forest and the cluster-conditional detector mark anomalies with -1.
    return np.asarray(model.predict(x)) == -1


def analyze_model_agreement(
    dataset_path: str,
    dataset_checksum: str,
    model_bundle_path: str | None,
) -> dict[str, Any]:
    started = time.perf_counter()
    dataset = PersistedFeatureDataset(dataset_path, dataset_checksum)
    rows = list(dataset.iter_features())
    if not rows:
        raise ValueError("Snapshot contains no feature rows")

    flagged: dict[str, np.ndarray] = {}
    skipped: dict[str, str] = {}

    if model_bundle_path:
        models_dir = Path(model_bundle_path)
        columns_path = models_dir / "feature_columns.pkl"
        scaler_path = models_dir / "scaler.pkl"
        if columns_path.is_file() and scaler_path.is_file():
            with columns_path.open("rb") as handle:
                feature_columns = pickle.load(handle)
            scaler = joblib.load(scaler_path)
            matrix = np.array(
                [[float(row.get(name, 0.0)) for name in feature_columns] for row in rows],
                dtype=float,
            )
            x = scaler.transform(matrix)
            for model_type, file_name in MODEL_FILES.items():
                path = models_dir / file_name
                if not path.is_file():
                    skipped[model_type] = "artifact not found"
                    continue
                try:
                    flagged[model_type] = _flagged_for_model(model_type, joblib.load(path), x)
                except Exception as exception:  # noqa: BLE001
                    skipped[model_type] = str(exception)
        else:
            for model_type in MODEL_FILES:
                skipped[model_type] = "scaler or feature columns missing"
    else:
        for model_type in MODEL_FILES:
            skipped[model_type] = "no model artifact directory"

    if len(flagged) < 2:
        raise ValueError("At least two models with usable artifacts are required to compare")

    total_rows = len(rows)
    names = list(flagged)

    models = [
        {
            "modelType": name,
            "flaggedCount": int(flagged[name].sum()),
            "flaggedRate": float(flagged[name].sum()) / total_rows,
        }
        for name in names
    ]

    pairs = []
    for index, first in enumerate(names):
        for second in names[index + 1:]:
            both = int((flagged[first] & flagged[second]).sum())
            either = int((flagged[first] | flagged[second]).sum())
            pairs.append({
                "modelA": first,
                "modelB": second,
                "bothCount": both,
                "eitherCount": either,
                # Jaccard: agreement independent of how many each model flags overall
                "jaccard": (both / either) if either else 0.0,
            })

    votes = np.vstack([flagged[name] for name in names]).sum(axis=0)
    consensus = [
        {"models": level, "rowCount": int((votes == level).sum())}
        for level in range(1, len(names) + 1)
    ]

    return {
        "status": "COMPLETED",
        "evaluatedRows": total_rows,
        "modelCount": len(names),
        "models": models,
        "pairs": pairs,
        "consensus": consensus,
        "flaggedByAny": int((votes >= 1).sum()),
        "flaggedByMajority": int((votes >= max(2, (len(names) + 1) // 2)).sum()),
        "unanimousCount": int((votes == len(names)).sum()),
        "skippedModels": skipped,
        "durationMs": round((time.perf_counter() - started) * 1000, 2),
    }
