import unittest
import tempfile
import json
from pathlib import Path

import numpy as np
import pandas as pd

from app.research.supervised_growth_analysis import (
    _best_f1_threshold,
    _evaluate_ensembles,
    _normalized_ensemble_weights,
)
from app.supervised_training import (
    SupervisedFeatureAugmenter,
    _train_supervised_feature_frame,
    _candidate_hyperparams,
    _robust_tuning_score,
    precision_constrained_threshold,
)


class SupervisedGrowthAnalysisTest(unittest.TestCase):
    def test_ensemble_reports_any_majority_unanimous_and_weighted_strategies(self):
        labels = np.asarray([0, 0, 1, 1])
        probabilities = {
            "XGBoost": np.asarray([0.1, 0.7, 0.8, 0.9]),
            "RandomForestClassifier": np.asarray([0.2, 0.3, 0.7, 0.8]),
            "LogisticRegression": np.asarray([0.4, 0.6, 0.4, 0.7]),
        }
        predictions = {name: (values >= 0.5).astype(int) for name, values in probabilities.items()}

        results = _evaluate_ensembles(
            percentage=100,
            partition_rows=20,
            training_rows=14,
            tuning_rows=2,
            validation_rows=4,
            evaluation_rows=4,
            validation_labels=labels,
            evaluation_labels=labels,
            validation_probabilities=probabilities,
            evaluation_probabilities=probabilities,
            evaluation_predictions=predictions,
            tuning_scores={name: 0.5 for name in probabilities},
            minimum_precision=0.0,
            threshold_beta=1.0,
            calibration_weights=np.ones(4),
        )

        self.assertEqual(
            {"ANY_MODEL", "MAJORITY_VOTE", "UNANIMOUS_VOTE", "WEIGHTED_SOFT_VOTE"},
            {result["strategy"] for result in results},
        )
        majority = next(result for result in results if result["strategy"] == "MAJORITY_VOTE")
        self.assertEqual(1, majority["falsePositive"])
        self.assertEqual(2, majority["truePositive"])

    def test_ensemble_weights_are_normalized_from_tuning_scores(self):
        weights = _normalized_ensemble_weights(
            {"XGBoost": 0.6, "RandomForestClassifier": 0.3, "LogisticRegression": 0.1},
            ["XGBoost", "RandomForestClassifier", "LogisticRegression"],
        )

        self.assertAlmostEqual(1.0, sum(weights.values()))
        self.assertGreater(weights["XGBoost"], weights["RandomForestClassifier"])

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

    def test_precision_constrained_threshold_maximizes_recall_at_target(self):
        labels = np.asarray([0, 0, 0, 0, 1, 1, 1])
        probabilities = np.asarray([0.05, 0.20, 0.40, 0.70, 0.65, 0.80, 0.95])

        threshold, policy = precision_constrained_threshold(
            labels, probabilities, minimum_precision=0.90
        )

        predictions = probabilities >= threshold
        self.assertTrue(policy["targetMet"])
        self.assertEqual(2, int(((labels == 1) & predictions).sum()))
        self.assertEqual(0, int(((labels == 0) & predictions).sum()))

    def test_joint_candidates_explore_lower_imbalance_weight_and_model_complexity(self):
        for model_name in ("XGBoost", "RandomForestClassifier", "LogisticRegression"):
            candidates = _candidate_hyperparams(model_name, {
                "ml.supervised.class_weight_multiplier": 1.0,
            })

            self.assertGreaterEqual(len(candidates), 6)
            self.assertTrue(any(
                candidate.get("ml.supervised.class_weight_multiplier", 1.0) < 1.0
                for candidate in candidates
            ))

    def test_robust_tuning_score_penalizes_period_instability(self):
        labels = pd.Series(([0] * 9 + [1]) * 4, dtype=int)
        stable = np.asarray([0.05] * 9 + [0.9] + [0.05] * 9 + [0.9] + [0.05] * 9 + [0.9] + [0.05] * 9 + [0.9])
        unstable = stable.copy()
        unstable[20:] = 1.0 - unstable[20:]
        weights = pd.Series(np.ones(len(labels)))

        stable_score = _robust_tuning_score(labels, stable, weights, 0.9)
        unstable_score = _robust_tuning_score(labels, unstable, weights, 0.9)

        self.assertGreater(stable_score, unstable_score)

    def test_training_persists_calibrated_threshold_and_held_out_metrics(self):
        labels = pd.Series(([0] * 9 + [1]) * 12, dtype=int)
        features = pd.DataFrame({
            "amount_ratio": np.where(labels == 1, 8.0, 1.0),
            "velocity": np.arange(len(labels), dtype=float) % 7,
        })
        label_sources = ["IMPORTED_DATASET"] * len(labels)
        label_sources[1] = "AUTO_NO_CASE"
        label_sources[11] = "AUTO_NO_CASE"
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "supervised_scaler.pkl").write_bytes(b"stale")
            rows, columns, metrics = _train_supervised_feature_frame(
                features,
                labels,
                directory,
                {"ml.logistic_regression.max_iter": 200, "ml.supervised.minimum_precision": 0.90},
                ["LogisticRegression"],
                "TEST_V1",
                "UNIT_TEST",
                label_sources,
            )

            self.assertEqual(120, rows)
            self.assertEqual(2, columns)
            self.assertEqual(24, metrics["LogisticRegression"]["evaluationRows"])
            self.assertEqual(12, metrics["LogisticRegression"]["tuningRows"])
            self.assertEqual(2, metrics["LogisticRegression"]["automaticNoCaseTrainingRows"])
            self.assertIsNotNone(metrics["LogisticRegression"]["trustedEvaluation"])
            self.assertEqual(0.90, metrics["LogisticRegression"]["minimumPrecisionTarget"])
            self.assertIn("targetMet", metrics["LogisticRegression"]["calibrationThresholdPolicy"])
            self.assertIn("ml.logistic_regression.c", metrics["LogisticRegression"]["bestHyperparams"])
            self.assertTrue(Path(directory, "supervised_thresholds.json").is_file())
            self.assertTrue(Path(directory, "logistic_regression.pkl").is_file())
            self.assertFalse(Path(directory, "supervised_scaler.pkl").exists())
            metadata = json.loads(Path(directory, "learning_mode.json").read_text(encoding="utf-8"))
            self.assertIn("10% tuning", metadata["split"])

    def test_zero_minimum_precision_uses_unbiased_fbeta_calibration(self):
        labels = np.asarray([0, 0, 0, 1, 1])
        probabilities = np.asarray([0.05, 0.10, 0.30, 0.45, 0.90])

        threshold, policy = precision_constrained_threshold(
            labels, probabilities, minimum_precision=0.0, fallback_beta=1.0
        )

        self.assertGreater(threshold, 0.30)
        self.assertEqual("MAX_F_1", policy["objective"])
        self.assertIsNone(policy["targetPrecision"])

    def test_feature_augmenter_builds_velocity_novelty_and_amount_interactions(self):
        source = pd.DataFrame({
            "current_amount": [100.0],
            "transaction_count_1h": [3.0],
            "transaction_count_24h": [6.0],
            "amount_sum_24h": [600.0],
            "new_beneficiary": [1.0],
            "new_location": [1.0],
            "profile_confidence": [0.25],
        })

        augmented = SupervisedFeatureAugmenter().fit_transform(source)

        self.assertAlmostEqual(0.5, augmented.loc[0, "velocity_1h_share"])
        self.assertAlmostEqual(100.0, augmented.loc[0, "average_amount_24h"])
        self.assertEqual(2.0, augmented.loc[0, "novelty_signal_count"])
        self.assertGreater(augmented.loc[0, "amount_new_beneficiary_interaction"], 0.0)

    def test_all_supervised_models_accept_source_weighted_training(self):
        labels = pd.Series(([0] * 9 + [1]) * 12, dtype=int)
        features = pd.DataFrame({
            "current_amount": np.where(labels == 1, 9000.0, 500.0),
            "transaction_count_1h": np.where(labels == 1, 8.0, 1.0),
            "transaction_count_24h": np.where(labels == 1, 12.0, 3.0),
            "new_beneficiary": np.where(labels == 1, 1.0, 0.0),
        })
        sources = ["AUTO_NO_CASE" if label == 0 else "STR_GENERATED" for label in labels]
        hyperparams = {
            "ml.supervised.tuning_enabled": False,
            "ml.random_forest.n_estimators": 10,
            "ml.xgboost.n_estimators": 10,
            "ml.logistic_regression.max_iter": 200,
        }

        with tempfile.TemporaryDirectory() as directory:
            _, _, metrics = _train_supervised_feature_frame(
                features,
                labels,
                directory,
                hyperparams,
                ["XGBoost", "RandomForestClassifier", "LogisticRegression"],
                "TEST_V1",
                "UNIT_TEST",
                sources,
            )

            self.assertEqual(
                {"XGBoost", "RandomForestClassifier", "LogisticRegression"},
                set(metrics),
            )
            self.assertTrue(Path(directory, "xgboost_classifier.pkl").is_file())
            self.assertTrue(Path(directory, "random_forest_classifier.pkl").is_file())


if __name__ == "__main__":
    unittest.main()
