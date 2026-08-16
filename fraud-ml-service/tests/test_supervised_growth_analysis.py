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
from app.research.risk_policy_backtest import evaluate_risk_policy
from app.supervised_training import (
    SupervisedFeatureAugmenter,
    _bounded_temporal_sample,
    _train_supervised_feature_frame,
    _candidate_hyperparams,
    _resource_bounded_search_params,
    _robust_tuning_score,
    precision_constrained_threshold,
)


class SupervisedGrowthAnalysisTest(unittest.TestCase):
    def test_single_selected_fraud_decision_consumes_its_ml_allocation(self):
        result = evaluate_risk_policy(
            percentage=100,
            partition_rows=10,
            evaluation_labels=np.asarray([0, 1], dtype=int),
            evaluation_features=pd.DataFrame({"profile_confidence": [1.0, 1.0]}),
            evaluation_probabilities={"XGBoost": np.asarray([0.10, 0.80])},
            evaluation_predictions={"XGBoost": np.asarray([0, 1], dtype=int)},
            tuning_weights={"XGBoost": 1.0},
            hyperparams={
                "aml.risk.policy.version": "SINGLE_MODEL_POLICY",
                "aml.risk.weight.customer_behaviour": 0.24,
                "aml.risk.weight.peer_behaviour": 0.09,
                "aml.risk.weight.ml_ensemble": 0.65,
                "aml.risk.weight.rules": 0.02,
                "aml.risk.threshold.low": 0.40,
                "aml.risk.threshold.medium": 0.65,
                "aml.risk.threshold.high": 0.80,
                "aml.risk.ml_model_allocations": {"XGBOOST_CLASSIFIER": 1.0},
            },
            ml_baseline={"strategy": "XGBOOST", "precision": 0, "recall": 0, "f1": 0, "balancedAccuracy": 0},
        )

        self.assertEqual(1, result["caseDecision"]["truePositive"])

    def test_full_policy_turns_unanimous_ml_vote_into_configured_case_weight(self):
        labels = np.asarray([0, 1], dtype=int)
        features = pd.DataFrame({
            "profile_confidence": [1.0, 1.0],
            "peer_group_GLOBAL": [1.0, 1.0],
        })
        probabilities = {
            "XGBoost": np.asarray([0.10, 0.80]),
            "RandomForestClassifier": np.asarray([0.10, 0.75]),
            "ExtraTreesClassifier": np.asarray([0.10, 0.70]),
        }
        predictions = {
            name: np.asarray([0, 1], dtype=int) for name in probabilities
        }

        result = evaluate_risk_policy(
            percentage=100,
            partition_rows=10,
            evaluation_labels=labels,
            evaluation_features=features,
            evaluation_probabilities=probabilities,
            evaluation_predictions=predictions,
            tuning_weights={name: 1 / 3 for name in probabilities},
            hyperparams={
                "aml.risk.policy.version": "TEST_POLICY",
                "aml.risk.weight.customer_behaviour": 0.24,
                "aml.risk.weight.peer_behaviour": 0.09,
                "aml.risk.weight.ml_ensemble": 0.65,
                "aml.risk.weight.rules": 0.02,
                "aml.risk.threshold.low": 0.40,
                "aml.risk.threshold.medium": 0.65,
                "aml.risk.threshold.high": 0.80,
                "aml.risk.ml_model_allocations": {
                    "XGBOOST_CLASSIFIER": 1 / 3,
                    "RANDOM_FOREST_CLASSIFIER": 1 / 3,
                    "EXTRA_TREES_CLASSIFIER": 1 / 3,
                },
            },
            ml_baseline={
                "strategy": "WEIGHTED_SOFT_VOTE",
                "precision": 1.0,
                "recall": 1.0,
                "f1": 1.0,
                "balancedAccuracy": 1.0,
            },
        )

        self.assertEqual("FULL_RISK_POLICY_BACKTEST_V2", result["protocol"])
        self.assertEqual("FROZEN_PRODUCTION_CONFIG", result["modelAllocationSource"])
        self.assertEqual(1, result["caseDecision"]["truePositive"])
        self.assertEqual(0, result["caseDecision"]["falsePositive"])
        self.assertEqual(0, result["strDecision"]["truePositive"])

    def test_full_policy_replays_customer_peer_and_rule_scores(self):
        labels = np.asarray([0, 1], dtype=int)
        features = pd.DataFrame({
            "amount_vs_last_30_avg": [1.0, 9.0],
            "amount_vs_last_30_median": [1.0, 9.0],
            "amount_z_score_last_30": [0.0, 4.0],
            "profile_confidence": [1.0, 1.0],
            "amount_vs_peer_avg": [1.0, 9.0],
            "peer_amount_z_score": [0.0, 4.0],
            "peer_frequency_percentile": [0.5, 0.99],
            "amount_vs_expected_turnover": [0.01, 0.60],
            "peer_group_DOCTOR_AGE_65_74": [1.0, 1.0],
            "transaction_count_10m": [1.0, 5.0],
            "transaction_count_1h": [1.0, 10.0],
            "transaction_count_24h": [1.0, 10.0],
            "unique_beneficiaries_1h": [1.0, 4.0],
            "below_threshold_count_24h": [0.0, 3.0],
            "below_threshold_amount_sum_24h": [0.0, 12_000.0],
        })
        probabilities = {
            "XGBoost": np.asarray([0.1, 0.1]),
            "RandomForestClassifier": np.asarray([0.1, 0.1]),
            "ExtraTreesClassifier": np.asarray([0.1, 0.1]),
        }
        predictions = {name: np.zeros(2, dtype=int) for name in probabilities}

        result = evaluate_risk_policy(
            percentage=100,
            partition_rows=10,
            evaluation_labels=labels,
            evaluation_features=features,
            evaluation_probabilities=probabilities,
            evaluation_predictions=predictions,
            tuning_weights={name: 1 / 3 for name in probabilities},
            hyperparams=None,
            ml_baseline={"strategy": "WEIGHTED_SOFT_VOTE"},
        )

        self.assertGreater(result["componentAverages"]["customerBehaviour"], 0.0)
        self.assertGreater(result["componentAverages"]["peerBehaviour"], 0.0)
        self.assertGreater(result["componentAverages"]["rules"], 0.0)

    def test_ensemble_reports_any_majority_unanimous_and_weighted_strategies(self):
        labels = np.asarray([0, 0, 1, 1])
        probabilities = {
            "XGBoost": np.asarray([0.1, 0.7, 0.8, 0.9]),
            "RandomForestClassifier": np.asarray([0.2, 0.3, 0.7, 0.8]),
            "ExtraTreesClassifier": np.asarray([0.4, 0.6, 0.4, 0.7]),
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
            {"XGBoost": 0.6, "RandomForestClassifier": 0.3, "ExtraTreesClassifier": 0.1},
            ["XGBoost", "RandomForestClassifier", "ExtraTreesClassifier"],
        )

        self.assertAlmostEqual(1.0, sum(weights.values()))
        self.assertGreater(weights["XGBoost"], weights["RandomForestClassifier"])

    def test_temporal_stack_uses_separate_meta_and_threshold_calibration_halves(self):
        validation_labels = np.asarray(([0, 0, 0, 1, 1] * 8), dtype=int)
        evaluation_labels = np.asarray(([0, 0, 1, 0, 1] * 4), dtype=int)
        validation_probabilities = {
            "XGBoost": np.where(validation_labels == 1, 0.82, 0.12),
            "RandomForestClassifier": np.where(validation_labels == 1, 0.72, 0.18),
            "ExtraTreesClassifier": np.where(validation_labels == 1, 0.65, 0.25),
        }
        evaluation_probabilities = {
            "XGBoost": np.where(evaluation_labels == 1, 0.80, 0.15),
            "RandomForestClassifier": np.where(evaluation_labels == 1, 0.70, 0.20),
            "ExtraTreesClassifier": np.where(evaluation_labels == 1, 0.62, 0.28),
        }
        evaluation_predictions = {
            name: (probabilities >= 0.5).astype(int)
            for name, probabilities in evaluation_probabilities.items()
        }

        results = _evaluate_ensembles(
            percentage=100,
            partition_rows=100,
            training_rows=70,
            tuning_rows=10,
            validation_rows=40,
            evaluation_rows=20,
            validation_labels=validation_labels,
            evaluation_labels=evaluation_labels,
            validation_probabilities=validation_probabilities,
            evaluation_probabilities=evaluation_probabilities,
            evaluation_predictions=evaluation_predictions,
            tuning_scores={name: 0.8 for name in validation_probabilities},
            minimum_precision=0.0,
            threshold_beta=1.0,
            calibration_weights=np.ones(40),
        )

        stacked = next(row for row in results if row["strategy"] == "TEMPORAL_STACKED_ENSEMBLE")
        self.assertEqual("CALIBRATION_SPLIT_META_PROBABILITY", stacked["scoreType"])
        self.assertEqual(20, stacked["calibrationThresholdPolicy"]["metaTrainingRows"])
        self.assertEqual(20, stacked["calibrationThresholdPolicy"]["thresholdCalibrationRows"])

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
        for model_name in ("XGBoost", "RandomForestClassifier", "ExtraTreesClassifier"):
            candidates = _candidate_hyperparams(model_name, {
                "ml.supervised.class_weight_multiplier": 1.0,
            })

            self.assertGreaterEqual(len(candidates), 6)
            self.assertTrue(any(
                candidate.get("ml.supervised.class_weight_multiplier", 1.0) < 1.0
                for candidate in candidates
            ))

    def test_tuning_search_is_resource_bounded_without_changing_final_candidate(self):
        candidate = {"ml.extra_trees.n_estimators": 600, "ml.extra_trees.min_samples_leaf": 2}
        bounded = _resource_bounded_search_params(
            "ExtraTreesClassifier",
            candidate,
            {"ml.supervised.tuning_tree_cap": 120},
        )

        self.assertEqual(120, bounded["ml.extra_trees.n_estimators"])
        self.assertEqual(600, candidate["ml.extra_trees.n_estimators"])

    def test_temporal_tuning_sample_preserves_full_time_span(self):
        features = pd.DataFrame({"sequence": np.arange(1_000)})
        labels = pd.Series(np.arange(1_000) % 2, dtype=int)
        weights = pd.Series(np.ones(1_000))

        sampled_features, sampled_labels, sampled_weights = _bounded_temporal_sample(
            features, labels, weights, 100
        )

        self.assertEqual(100, len(sampled_features))
        self.assertEqual(0, sampled_features.iloc[0]["sequence"])
        self.assertEqual(999, sampled_features.iloc[-1]["sequence"])
        self.assertEqual(100, len(sampled_labels))
        self.assertEqual(100, len(sampled_weights))

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
                {"ml.extra_trees.n_estimators": 50, "ml.supervised.minimum_precision": 0.90},
                ["ExtraTreesClassifier"],
                "TEST_V1",
                "UNIT_TEST",
                label_sources,
            )

            self.assertEqual(120, rows)
            self.assertEqual(2, columns)
            self.assertEqual(24, metrics["ExtraTreesClassifier"]["evaluationRows"])
            self.assertEqual(12, metrics["ExtraTreesClassifier"]["tuningRows"])
            self.assertEqual(2, metrics["ExtraTreesClassifier"]["automaticNoCaseTrainingRows"])
            self.assertIsNotNone(metrics["ExtraTreesClassifier"]["trustedEvaluation"])
            self.assertEqual(0.90, metrics["ExtraTreesClassifier"]["minimumPrecisionTarget"])
            self.assertIn("targetMet", metrics["ExtraTreesClassifier"]["calibrationThresholdPolicy"])
            self.assertIn("ml.extra_trees.n_estimators", metrics["ExtraTreesClassifier"]["bestHyperparams"])
            self.assertTrue(Path(directory, "supervised_thresholds.json").is_file())
            self.assertTrue(Path(directory, "extra_trees_classifier.pkl").is_file())
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
            "transaction_count_10m": [2.0],
            "transaction_count_7d": [12.0],
            "amount_sum_24h": [600.0],
            "amount_sum_1h": [300.0],
            "amount_sum_7d": [1200.0],
            "new_beneficiary": [1.0],
            "new_location": [1.0],
            "new_channel": [1.0],
            "amount_vs_last_30_avg": [5.0],
            "profile_confidence": [0.25],
        })

        augmented = SupervisedFeatureAugmenter().fit_transform(source)

        self.assertAlmostEqual(0.5, augmented.loc[0, "velocity_1h_share"])
        self.assertAlmostEqual(100.0, augmented.loc[0, "average_amount_24h"])
        self.assertEqual(3.0, augmented.loc[0, "novelty_signal_count"])
        self.assertGreater(augmented.loc[0, "amount_new_beneficiary_interaction"], 0.0)
        self.assertAlmostEqual(2 / 3, augmented.loc[0, "velocity_10m_share"])
        self.assertAlmostEqual(0.5, augmented.loc[0, "amount_velocity_1h_share"])
        self.assertEqual(4.0, augmented.loc[0, "customer_amount_multiplier_excess"])
        self.assertNotIn("handbook_high_amount_scenario", augmented.columns)

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
            "ml.extra_trees.n_estimators": 50,
        }

        with tempfile.TemporaryDirectory() as directory:
            _, _, metrics = _train_supervised_feature_frame(
                features,
                labels,
                directory,
                hyperparams,
                ["XGBoost", "RandomForestClassifier", "ExtraTreesClassifier"],
                "TEST_V1",
                "UNIT_TEST",
                sources,
            )

            self.assertEqual(
                {"XGBoost", "RandomForestClassifier", "ExtraTreesClassifier"},
                set(metrics),
            )
            self.assertTrue(Path(directory, "xgboost_classifier.pkl").is_file())
            self.assertTrue(Path(directory, "random_forest_classifier.pkl").is_file())


if __name__ == "__main__":
    unittest.main()
