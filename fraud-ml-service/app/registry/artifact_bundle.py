from __future__ import annotations

import hashlib
import json
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, Mapping


@dataclass(frozen=True)
class ArtifactFile:
    path: str
    sizeBytes: int
    sha256: str


@dataclass(frozen=True)
class ArtifactBundle:
    artifactPath: str
    artifactChecksum: str
    artifactSizeBytes: int
    manifestPath: str
    featureSchemaChecksum: str


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as artifact:
        for chunk in iter(lambda: artifact.read(64 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def feature_schema_checksum(feature_columns: Iterable[str]) -> str:
    normalized = "\n".join(str(column) for column in feature_columns)
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def bundle_checksum(bundle_path: Path) -> str:
    bundle_path = bundle_path.resolve()
    files = sorted(
        (path for path in bundle_path.rglob("*") if path.is_file()),
        key=lambda path: path.relative_to(bundle_path).as_posix(),
    )
    if not files:
        raise ValueError(f"Artifact directory is empty: {bundle_path}")
    digest_source = "".join(
        f"{path.relative_to(bundle_path).as_posix()}:{path.stat().st_size}:{sha256_file(path)}\n"
        for path in files
    )
    return hashlib.sha256(digest_source.encode("utf-8")).hexdigest()


def write_artifact_manifest(
    bundle_path: Path,
    *,
    model_version: str,
    model_type: str,
    model_segment: str | None,
    feature_version: str,
    feature_columns: Iterable[str],
    training_run_id: str,
    dataset_checksum: str,
    base_model_version: str | None,
    learned_row_count: int,
    parameters: Mapping[str, Any] | None = None,
    metrics: Mapping[str, Any] | None = None,
) -> ArtifactBundle:
    bundle_path = bundle_path.resolve()
    if not bundle_path.is_dir():
        raise ValueError(f"Artifact bundle directory does not exist: {bundle_path}")
    if learned_row_count <= 0:
        raise ValueError("learned_row_count must be positive")
    feature_columns = list(feature_columns)
    schema_checksum = feature_schema_checksum(feature_columns)
    manifest_path = bundle_path / "artifact-manifest.json"
    existing_files = sorted(
        (path for path in bundle_path.rglob("*") if path.is_file() and path != manifest_path),
        key=lambda path: path.relative_to(bundle_path).as_posix(),
    )
    if not existing_files:
        raise ValueError("Artifact bundle must contain model state before its manifest is written")
    manifest = {
        "modelVersion": model_version,
        "modelType": model_type,
        "modelSegment": model_segment,
        "featureVersion": feature_version,
        "featureColumns": feature_columns,
        "featureSchemaChecksum": schema_checksum,
        "trainingRunId": training_run_id,
        "datasetChecksum": dataset_checksum,
        "baseModelVersion": base_model_version,
        "learnedRowCount": learned_row_count,
        "parameters": dict(parameters or {}),
        "metrics": dict(metrics or {}),
        "files": [
            asdict(
                ArtifactFile(
                    path=path.relative_to(bundle_path).as_posix(),
                    sizeBytes=path.stat().st_size,
                    sha256=sha256_file(path),
                )
            )
            for path in existing_files
        ],
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }
    temporary_manifest = bundle_path / ".artifact-manifest.json.tmp"
    temporary_manifest.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    temporary_manifest.replace(manifest_path)
    return ArtifactBundle(
        artifactPath=str(bundle_path),
        artifactChecksum=bundle_checksum(bundle_path),
        artifactSizeBytes=sum(path.stat().st_size for path in bundle_path.rglob("*") if path.is_file()),
        manifestPath=str(manifest_path),
        featureSchemaChecksum=schema_checksum,
    )
