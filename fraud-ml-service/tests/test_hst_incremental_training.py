import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import pyarrow as pa
import pyarrow.parquet as pq

from app.incremental.half_space_trees import load_hst_artifact, train_half_space_trees
from app.incremental.parquet_dataset import sha256_file


class HalfSpaceTreesTrainingTest(unittest.TestCase):
    def test_trains_from_verified_parquet_and_publishes_candidate(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            dataset = root / "dataset"
            artifacts = root / "artifacts"
            dataset.mkdir()
            rows = [
                json.dumps({"amount": float(index), "velocity": float(index % 3)})
                for index in range(24)
            ]
            part = dataset / "part-00001.parquet"
            pq.write_table(pa.table({"model_features_json": rows}), part)
            part_checksum = sha256_file(part)
            checksum_source = f"part-00001.parquet:24:{part_checksum}\n"
            dataset_checksum = hashlib.sha256(checksum_source.encode("utf-8")).hexdigest()
            (dataset / "manifest.json").write_text(json.dumps({
                "trainingRunId": "6a22b74a-53a1-4b6a-a805-b81fe7aceed1",
                "featureVersion": "AML_FEATURES_V2",
                "modelType": "HALF_SPACE_TREES",
                "modelSegment": "RETAIL_GENERAL",
                "rowCount": 24,
                "datasetChecksum": dataset_checksum,
                "modelFeatureColumns": ["amount", "velocity"],
                "files": [{
                    "path": "part-00001.parquet",
                    "rowCount": 24,
                    "sizeBytes": part.stat().st_size,
                    "sha256": part_checksum,
                }],
            }), encoding="utf-8")

            result = train_half_space_trees(
                dataset_path=str(dataset),
                dataset_checksum=dataset_checksum,
                artifact_base_path=str(artifacts),
                model_version="HST-RETAIL-20260804-01",
                model_segment="RETAIL_GENERAL",
                feature_version="AML_FEATURES_V2",
                training_run_id="6a22b74a-53a1-4b6a-a805-b81fe7aceed1",
                base_model_path=None,
                parameters={
                    "nTrees": 3,
                    "height": 3,
                    "windowSize": 4,
                    "thresholdQuantile": 0.95,
                    "batchSize": 8,
                    "seed": 42,
                },
            )

            candidate_path = artifacts / "HST-RETAIL-20260804-01"
            candidate = load_hst_artifact(str(candidate_path))
            self.assertEqual("CANDIDATE_READY", result["status"])
            self.assertEqual(24, result["learnedRowCount"])
            self.assertTrue((candidate_path / "artifact-manifest.json").is_file())
            self.assertGreaterEqual(candidate.score({"amount": 100.0, "velocity": 2.0}), 0.0)
            self.assertGreaterEqual(candidate.normalized_score({"amount": 100.0, "velocity": 2.0}), 0.0)
            self.assertLessEqual(candidate.normalized_score({"amount": 100.0, "velocity": 2.0}), 1.0)


if __name__ == "__main__":
    unittest.main()
