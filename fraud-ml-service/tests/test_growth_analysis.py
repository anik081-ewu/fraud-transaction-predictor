from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import pyarrow as arrow
import pyarrow.parquet as parquet

from app.research.growth_analysis import (
    DETECTORS,
    GrowthAnalysisOptions,
    _partition_sizes,
    analyze_detector_growth,
)


class GrowthAnalysisTest(unittest.TestCase):
    def test_partition_sizes_stop_below_minimum_and_include_full_dataset(self) -> None:
        self.assertEqual([(25, 250), (50, 500), (100, 1_000)], _partition_sizes(1_000, (10, 25, 50, 100), 200))
        self.assertEqual([(100, 240)], _partition_sizes(240, (10, 25, 50, 100), 200))

    def test_all_detectors_are_compared_on_oldest_partitions(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            dataset_path, checksum = self._dataset(Path(temporary), 260)
            report = analyze_detector_growth(
                str(dataset_path),
                checksum,
                GrowthAnalysisOptions(
                    percentages=(100,),
                    minimum_rows=200,
                    maximum_evaluation_rows=30,
                    isolation_forest_max_training_rows=100,
                ),
            )

        self.assertEqual(list(DETECTORS), report["detectors"])
        self.assertEqual(4, len(report["results"]))
        evaluation_counts = [(result["detector"], result["evaluationRows"]) for result in report["results"]]
        self.assertTrue(all(count == 30 for _, count in evaluation_counts), evaluation_counts)
        isolation_forest = next(result for result in report["results"] if result["detector"] == "ISOLATION_FOREST")
        self.assertTrue(isolation_forest["boundedTrainingSample"])
        self.assertEqual("OLDEST_FIRST", report["methodology"]["ordering"])
        self.assertFalse(report["methodology"]["labelsAvailable"])

    def _dataset(self, root: Path, rows: int) -> tuple[Path, str]:
        dataset_path = root / "dataset"
        dataset_path.mkdir()
        part_path = dataset_path / "part-00001.parquet"
        values = [json.dumps({"amount": float(index % 17), "velocity": float(index % 5)}) for index in range(rows)]
        parquet.write_table(arrow.table({"model_features_json": values}), part_path)
        checksum = hashlib.sha256(part_path.read_bytes()).hexdigest()
        aggregate = hashlib.sha256(f"part-00001.parquet:{rows}:{checksum}\n".encode()).hexdigest()
        manifest = {
            "datasetChecksum": aggregate,
            "rowCount": rows,
            "featureVersion": "TEST_V1",
            "modelType": "RESEARCH_GROWTH_ANALYSIS",
            "modelFeatureColumns": ["amount", "velocity"],
            "files": [{"path": part_path.name, "rowCount": rows, "sha256": checksum}],
        }
        (dataset_path / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        return dataset_path, aggregate


if __name__ == "__main__":
    unittest.main()
