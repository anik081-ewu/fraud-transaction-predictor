from __future__ import annotations

import hashlib
import json
import math
import os
from pathlib import Path
from itertools import islice
from typing import Iterator

import pyarrow.parquet as parquet


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(64 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


class PersistedFeatureDataset:
    def __init__(self, dataset_path: str, expected_checksum: str, batch_size: int = 65_536):
        self.path = _resolve_dataset_path(dataset_path)
        self.batch_size = max(1, batch_size)
        manifest_path = self.path / "manifest.json"
        if not manifest_path.is_file():
            raise ValueError(f"Dataset manifest does not exist: {manifest_path}")
        self.manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        self._validate(expected_checksum)

    @property
    def row_count(self) -> int:
        return int(self.manifest["rowCount"])

    @property
    def feature_version(self) -> str:
        return str(self.manifest["featureVersion"])

    @property
    def model_type(self) -> str:
        return str(self.manifest["modelType"])

    def collect_feature_columns(self) -> list[str]:
        declared = self.manifest.get("modelFeatureColumns")
        if declared:
            return sorted(str(column) for column in declared)
        columns: set[str] = set()
        observed_rows = 0
        for features in self.iter_features():
            columns.update(features)
            observed_rows += 1
        if observed_rows != self.row_count:
            raise ValueError(f"Dataset row count changed: expected={self.row_count}, observed={observed_rows}")
        if not columns:
            raise ValueError("Dataset contains no model features")
        return sorted(columns)

    def iter_features(self) -> Iterator[dict[str, float]]:
        for part in self.manifest["files"]:
            part_path = self.path / part["path"]
            source = parquet.ParquetFile(part_path)
            for batch in source.iter_batches(
                batch_size=self.batch_size,
                columns=["model_features_json"],
                use_threads=True,
            ):
                for raw in batch.column(0).to_pylist():
                    values = json.loads(raw)
                    features: dict[str, float] = {}
                    for name, value in values.items():
                        numeric = float(value)
                        if not math.isfinite(numeric):
                            raise ValueError(f"Non-finite feature {name!r} encountered in training dataset")
                        features[str(name)] = numeric
                    yield features

    def iter_feature_range(self, start: int, stop: int) -> Iterator[dict[str, float]]:
        if start < 0 or stop < start or stop > self.row_count:
            raise ValueError("Invalid persisted-feature row range")
        return islice(self.iter_features(), start, stop)

    def iter_labelled_features(self) -> Iterator[tuple[dict[str, float], int]]:
        for part in self.manifest["files"]:
            part_path = self.path / part["path"]
            source = parquet.ParquetFile(part_path)
            for batch in source.iter_batches(
                batch_size=self.batch_size,
                columns=["model_features_json", "fraud_label"],
                use_threads=True,
            ):
                features_values = batch.column(0).to_pylist()
                labels = batch.column(1).to_pylist()
                for raw, label in zip(features_values, labels):
                    if label is None:
                        continue
                    values = json.loads(raw)
                    yield ({str(name): float(value) for name, value in values.items()}, int(bool(label)))

    def _validate(self, expected_checksum: str) -> None:
        if self.manifest.get("datasetChecksum") != expected_checksum:
            raise ValueError("Dataset checksum does not match the training request")
        checksum_source: list[str] = []
        rows = 0
        for part in self.manifest.get("files", []):
            part_path = (self.path / part["path"]).resolve()
            if not part_path.is_file() or not part_path.is_relative_to(self.path):
                raise ValueError(f"Unsafe or missing Parquet part: {part_path}")
            checksum = sha256_file(part_path)
            if checksum != part["sha256"]:
                raise ValueError(f"Parquet checksum mismatch: {part['path']}")
            parquet_rows = parquet.ParquetFile(part_path).metadata.num_rows
            if parquet_rows != int(part["rowCount"]):
                raise ValueError(f"Parquet row-count mismatch: {part['path']}")
            rows += parquet_rows
            checksum_source.append(f"{part['path']}:{part['rowCount']}:{checksum}\n")
        aggregate = hashlib.sha256("".join(checksum_source).encode("utf-8")).hexdigest()
        if aggregate != expected_checksum or rows != int(self.manifest.get("rowCount", -1)):
            raise ValueError("Dataset manifest aggregate validation failed")


def _resolve_dataset_path(dataset_path: str) -> Path:
    resolved = Path(dataset_path).resolve()
    raw = str(resolved)
    if os.name != "nt" or raw.startswith("\\\\?\\") or len(raw) < 240:
        return resolved
    if raw.startswith("\\\\"):
        return Path("\\\\?\\UNC\\" + raw.removeprefix("\\\\"))
    return Path("\\\\?\\" + raw)
