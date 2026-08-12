import math
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import numpy as np
from fastapi import HTTPException

from app.main import _persisted_feature_matrix, _sunset_header, app, predict_persisted_features
from app.schemas import PersistedFeaturePredictRequest


class IdentityScaler:
    def transform(self, values):
        return values


class PersistedFeatureMatrixTest(unittest.TestCase):
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

    @patch("app.main.load_hst_artifact")
    @patch("app.main._evaluate_models")
    @patch("app.main._persisted_feature_matrix")
    @patch("app.main.load_models")
    def test_active_hst_is_returned_as_production_decision(
        self, load_models, feature_matrix, evaluate_models, load_hst_artifact
    ):
        load_models.return_value = SimpleNamespace(available_models={"IsolationForest": object()})
        feature_matrix.return_value = np.array([[1.0]])
        evaluate_models.return_value = {"IsolationForest": {"anomaly": False}}
        load_hst_artifact.return_value = SimpleNamespace(
            feature_version="AML_FEATURES_V2",
            model_version="HST-2",
            model_segment="RETAIL_GENERAL",
            threshold=0.8,
            score=lambda features: 0.9,
            normalized_score=lambda features: 1.0,
        )
        request = PersistedFeaturePredictRequest(
            transactionId="T-1",
            accountId="A-1",
            featureVersion="AML_FEATURES_V2",
            modelFeatureSchema="LEGACY_MODEL_INPUT_V1",
            features={"amount": 1.0},
            modelsDir="batch",
            modelNames=["IsolationForest"],
            activeModelsDir="hst",
            activeModelVersion="HST-2",
        )

        response = predict_persisted_features(request)

        self.assertTrue(response.modelResults["HalfSpaceTrees"]["anomaly"])
        self.assertTrue(response.modelResults["HalfSpaceTrees"]["affectsProductionDecision"])
        self.assertEqual("HST-2", response.featureSummary["activeModel"]["modelVersion"])

    @patch("app.main.load_online_ocsvm_artifact")
    @patch("app.main._evaluate_models")
    @patch("app.main._persisted_feature_matrix")
    @patch("app.main.load_models")
    def test_online_ocsvm_is_returned_as_non_decision_shadow_score(
        self, load_models, feature_matrix, evaluate_models, load_online_ocsvm_artifact
    ):
        load_models.return_value = SimpleNamespace(available_models={"IsolationForest": object()})
        feature_matrix.return_value = np.array([[1.0]])
        evaluate_models.return_value = {"IsolationForest": {"anomaly": False}}
        load_online_ocsvm_artifact.return_value = SimpleNamespace(
            feature_version="AML_FEATURES_V2",
            model_version="OCSVM-2",
            model_segment="RETAIL_GENERAL",
            raw_threshold=0.8,
            raw_score=lambda features: 0.9,
            normalized_score=lambda features: 1.0,
        )
        request = PersistedFeaturePredictRequest(
            transactionId="T-1",
            accountId="A-1",
            featureVersion="AML_FEATURES_V2",
            modelFeatureSchema="LEGACY_MODEL_INPUT_V1",
            features={"amount": 1.0},
            modelsDir="batch",
            modelNames=["IsolationForest"],
            shadowOnlineSvmDir="online-svm",
            shadowOnlineSvmVersion="OCSVM-2",
        )

        response = predict_persisted_features(request)

        self.assertTrue(response.modelResults["OnlineOneClassSVM"]["anomaly"])
        self.assertFalse(response.modelResults["OnlineOneClassSVM"]["affectsProductionDecision"])
        self.assertEqual(
            "OCSVM-2",
            response.featureSummary["onlineOneClassSvmShadow"]["modelVersion"],
        )

    @patch("app.main.load_models")
    def test_v2_rejects_offline_only_models(self, load_models):
        load_models.return_value = SimpleNamespace(
            available_models={"IsolationForest": object(), "LOF": object()}
        )
        request = PersistedFeaturePredictRequest(
            transactionId="T-1",
            accountId="A-1",
            featureVersion="AML_FEATURES_V2",
            modelFeatureSchema="LEGACY_MODEL_INPUT_V1",
            features={"amount": 1.0},
            modelsDir="batch",
            modelNames=["LOF"],
        )

        with self.assertRaises(HTTPException) as error:
            predict_persisted_features(request)

        self.assertEqual(409, error.exception.status_code)
        self.assertIn("offline-comparison only", error.exception.detail)


if __name__ == "__main__":
    unittest.main()
