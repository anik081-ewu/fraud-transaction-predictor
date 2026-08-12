import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import pyarrow as pa
import pyarrow.parquet as pq

from app.incremental.online_one_class_svm import (
    load_online_ocsvm_artifact,
    train_online_one_class_svm,
)
from app.incremental.parquet_dataset import sha256_file


class OnlineOneClassSvmTrainingTest(unittest.TestCase):
    def test_trains_serializes_and_replays_bounded_normalized_scores(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            dataset_path, dataset_checksum = self._dataset(root, "dataset-1", 0.0)
            artifacts = root / "artifacts"
            parameters = self._parameters()

            result = train_online_one_class_svm(
                dataset_path=str(dataset_path),
                dataset_checksum=dataset_checksum,
                artifact_base_path=str(artifacts),
                model_version="OCSVM-RETAIL-20260806-01",
                model_segment="RETAIL_GENERAL",
                feature_version="AML_FEATURES_V2",
                training_run_id="6a22b74a-53a1-4b6a-a805-b81fe7aceed1",
                base_model_path=None,
                parameters=parameters,
            )

            artifact_path = artifacts / "OCSVM-RETAIL-20260806-01"
            artifact = load_online_ocsvm_artifact(str(artifact_path))
            score = artifact.normalized_score({"amount": 200.0, "velocity": 8.0})
            replay = load_online_ocsvm_artifact(str(artifact_path)).normalized_score(
                {"amount": 200.0, "velocity": 8.0}
            )

            self.assertEqual("CANDIDATE_READY", result["status"])
            self.assertEqual(64, result["learnedRowCount"])
            self.assertTrue((artifact_path / "artifact-manifest.json").is_file())
            self.assertGreaterEqual(score, 0.0)
            self.assertLessEqual(score, 1.0)
            self.assertEqual(score, replay)

    def test_continues_from_compatible_base_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            artifacts = root / "artifacts"
            first_dataset, first_checksum = self._dataset(root, "dataset-1", 0.0)
            second_dataset, second_checksum = self._dataset(root, "dataset-2", 5.0)
            parameters = self._parameters()
            first_version = "OCSVM-RETAIL-20260805-01"
            second_version = "OCSVM-RETAIL-20260806-01"

            train_online_one_class_svm(
                dataset_path=str(first_dataset), dataset_checksum=first_checksum,
                artifact_base_path=str(artifacts), model_version=first_version,
                model_segment="RETAIL_GENERAL", feature_version="AML_FEATURES_V2",
                training_run_id="6a22b74a-53a1-4b6a-a805-b81fe7aceed1",
                base_model_path=None, parameters=parameters,
            )
            result = train_online_one_class_svm(
                dataset_path=str(second_dataset), dataset_checksum=second_checksum,
                artifact_base_path=str(artifacts), model_version=second_version,
                model_segment="RETAIL_GENERAL", feature_version="AML_FEATURES_V2",
                training_run_id="7b33c85b-64b2-5c7b-b916-c92fe8bdfed2",
                base_model_path=str(artifacts / first_version), parameters=parameters,
            )

            manifest = json.loads(
                (artifacts / second_version / "artifact-manifest.json").read_text(encoding="utf-8")
            )
            self.assertEqual(first_version, manifest["baseModelVersion"])
            self.assertEqual(64, result["learnedRowCount"])

    def _dataset(self, root: Path, name: str, offset: float) -> tuple[Path, str]:
        dataset = root / name
        dataset.mkdir()
        rows = [
            json.dumps({
                "amount": offset + float(index % 16),
                "velocity": float(index % 5),
            })
            for index in range(64)
        ]
        part = dataset / "part-00001.parquet"
        pq.write_table(pa.table({"model_features_json": rows}), part)
        part_checksum = sha256_file(part)
        checksum_source = f"part-00001.parquet:64:{part_checksum}\n"
        dataset_checksum = hashlib.sha256(checksum_source.encode("utf-8")).hexdigest()
        (dataset / "manifest.json").write_text(json.dumps({
            "trainingRunId": "6a22b74a-53a1-4b6a-a805-b81fe7aceed1",
            "featureVersion": "AML_FEATURES_V2",
            "modelType": "ONLINE_ONE_CLASS_SVM",
            "modelSegment": "RETAIL_GENERAL",
            "rowCount": 64,
            "datasetChecksum": dataset_checksum,
            "modelFeatureColumns": ["amount", "velocity"],
            "files": [{
                "path": "part-00001.parquet",
                "rowCount": 64,
                "sizeBytes": part.stat().st_size,
                "sha256": part_checksum,
            }],
        }), encoding="utf-8")
        return dataset, dataset_checksum

    def _parameters(self) -> dict[str, object]:
        return {
            "nu": 0.05,
            "learningRate": 0.01,
            "interceptLearningRate": 0.01,
            "gamma": 0.5,
            "nComponents": 16,
            "thresholdQuantile": 0.95,
            "minimumCalibrationRows": 20,
            "batchSize": 16,
            "seed": 42,
        }


if __name__ == "__main__":
    unittest.main()
