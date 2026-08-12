import os
import unittest

from app.incremental.parquet_dataset import _resolve_dataset_path


class PersistedFeatureDatasetPathTest(unittest.TestCase):
    @unittest.skipUnless(os.name == "nt", "Windows long-path behavior")
    def test_adds_extended_prefix_to_long_windows_path(self) -> None:
        long_path = "C:\\" + "\\".join(["historical-training-dataset"] * 12)

        resolved = _resolve_dataset_path(long_path)

        self.assertTrue(str(resolved).startswith("\\\\?\\"))


if __name__ == "__main__":
    unittest.main()
