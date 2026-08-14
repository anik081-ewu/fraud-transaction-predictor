import math
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import numpy as np
from fastapi import HTTPException

from app.main import (
    _models,
    _cached_models,
    _evaluate_models,
    _persisted_feature_matrix,
    _sunset_header,
    app,
    predict_persisted_features,
)
from app.schemas import PersistedFeaturePredictRequest


class IdentityScaler:
    def transform(self, values):
        return values


class PersistedFeatureMatrixTest(unittest.TestCase):
    def tearDown(self):
        _cached_models.cache_clear()

    @patch("app.main.os.path.isdir", return_value=True)
    @patch("app.main.load_models")
    def test_reuses_loaded_artifacts_for_same_versioned_directory(self, load_models, _isdir):
        loaded = object()
        load_models.return_value = loaded

        self.assertIs(loaded, _models("C:/models/version-1"))
        self.assertIs(loaded, _models("C:/models/version-1"))

        load_models.assert_called_once_with("C:/models/version-1")

    def test_isolation_forest_uses_one_tree_scoring_pass(self):
        class IsolationForestProbe:
            offset_ = -0.5

            def __init__(self):
                self.score_calls = 0
                self.predict_calls = 0

            def score_samples(self, _values):
                self.score_calls += 1
                return np.array([-0.6])

            def predict(self, _values):
                self.predict_calls += 1
                return np.array([-1])

        model = IsolationForestProbe()
        loaded = SimpleNamespace(iso_model=model)

        result = _evaluate_models(loaded, np.array([[1.0]]), ["IsolationForest"])

        self.assertTrue(result["IsolationForest"]["anomaly"])
        self.assertAlmostEqual(-0.1, result["IsolationForest"]["decisionFunction"])
        self.assertEqual(1, model.score_calls)
        self.assertEqual(0, model.predict_calls)

    def test_legacy_routes_publish_deprecation_metadata(self):
        operation = app.openapi()["paths"]["/api/v1/fraud/predict"]["post"]

        self.assertTrue(operation["deprecated"])
        self.assertEqual("Thu, 31 Dec 2026 23:59:59 GMT", _sunset_header())

    def test_aligns_persisted_values_to_artifact_columns(self):
        request = PersistedFeaturePredictRequest(
            transactionId="T-1",
            accountId="A-1",
            featureVersion="AML_FEATURES_V2",
            modelFeatureSchema="LEGACY_MODEL_INPUT_V1",
            features={"amount_balance_ratio": 0.25, "transaction_hour": 14.0},
        )
        loaded = SimpleNamespace(
            feature_columns=["transaction_hour", "missing_column", "amount_balance_ratio"],
            scaler=IdentityScaler(),
        )

        matrix = _persisted_feature_matrix(request, loaded)

        np.testing.assert_allclose(matrix, np.array([[14.0, 0.0, 0.25]]))

    def test_rejects_non_finite_values(self):
        request = PersistedFeaturePredictRequest(
            transactionId="T-1",
            accountId="A-1",
            featureVersion="AML_FEATURES_V2",
            modelFeatureSchema="LEGACY_MODEL_INPUT_V1",
            features={"bad": math.inf},
        )
        loaded = SimpleNamespace(feature_columns=["bad"], scaler=IdentityScaler())

        with self.assertRaises(HTTPException) as error:
            _persisted_feature_matrix(request, loaded)

        self.assertEqual(400, error.exception.status_code)

    @patch("app.main.load_models")
    def test_v2_rejects_offline_only_models(self, load_models):
        load_models.return_value = SimpleNamespace(
            available_models={"IsolationForest": object()}
        )
        request = PersistedFeaturePredictRequest(
            transactionId="T-1",
            accountId="A-1",
            featureVersion="AML_FEATURES_V2",
            modelFeatureSchema="LEGACY_MODEL_INPUT_V1",
            features={"amount": 1.0},
            modelsDir="batch",
            modelNames=["ResearchOnlyDetector"],
        )

        with self.assertRaises(HTTPException) as error:
            predict_persisted_features(request)

        self.assertEqual(409, error.exception.status_code)
        self.assertIn("offline-comparison only", error.exception.detail)


if __name__ == "__main__":
    unittest.main()
