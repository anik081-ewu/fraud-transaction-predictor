import json
from pathlib import Path
import tempfile
import unittest

from app.registry.artifact_bundle import bundle_checksum, write_artifact_manifest


class ArtifactBundleTest(unittest.TestCase):
    def test_writes_reproducible_candidate_bundle_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle = Path(temporary_directory) / "HST-RETAIL-20260804-01"
            bundle.mkdir()
            (bundle / "model.bin").write_bytes(b"candidate-state")
            (bundle / "preprocessor.json").write_text('{"version": 1}', encoding="utf-8")

            metadata = write_artifact_manifest(
                bundle,
                model_version="HST-RETAIL-20260804-01",
                model_type="HALF_SPACE_TREES",
                model_segment="RETAIL_GENERAL",
                feature_version="AML_FEATURES_V2",
                feature_columns=["current_amount", "amount_vs_last_30_avg"],
                training_run_id="6a22b74a-53a1-4b6a-a805-b81fe7aceed1",
                dataset_checksum="d" * 64,
                base_model_version=None,
                learned_row_count=2000,
                parameters={"trees": 25},
                metrics={"anomalyRate": 0.02},
            )

            manifest = json.loads((bundle / "artifact-manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(metadata.artifactChecksum, bundle_checksum(bundle))
            self.assertEqual(metadata.featureSchemaChecksum, manifest["featureSchemaChecksum"])
            self.assertEqual("6a22b74a-53a1-4b6a-a805-b81fe7aceed1", manifest["trainingRunId"])
            self.assertEqual(["model.bin", "preprocessor.json"], [file["path"] for file in manifest["files"]])


if __name__ == "__main__":
    unittest.main()
