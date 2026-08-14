import unittest

import numpy as np

from app.research.supervised_growth_analysis import _best_f1_threshold


class SupervisedGrowthAnalysisTest(unittest.TestCase):
    def test_selects_threshold_that_separates_calibration_labels(self):
        labels = np.asarray([0, 0, 0, 1, 1])
        probabilities = np.asarray([0.05, 0.10, 0.30, 0.45, 0.90])

        threshold = _best_f1_threshold(labels, probabilities)

        self.assertGreater(threshold, 0.30)
        self.assertLessEqual(threshold, 0.45)

    def test_threshold_remains_inside_safe_probability_bounds(self):
        labels = np.asarray([0, 1])
        probabilities = np.asarray([0.0, 1.0])

        threshold = _best_f1_threshold(labels, probabilities)

        self.assertGreaterEqual(threshold, 0.01)
        self.assertLessEqual(threshold, 0.99)


if __name__ == "__main__":
    unittest.main()
