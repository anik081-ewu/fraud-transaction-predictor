import os
import math
from functools import lru_cache
from datetime import datetime, timezone
from email.utils import format_datetime
from typing import Any, Dict

import numpy as np
import pandas as pd
from fastapi import FastAPI, Request
from fastapi import HTTPException
from fastapi.responses import JSONResponse

from app.model_loader import load_models
from app.schemas import (
    ComparisonPredictRequest,
    ComparisonPredictResponse,
    FraudPredictRequest,
    FraudPredictResponse,
    PersistedFeaturePredictRequest,
    ScorePercentilesRequest,
    ScorePercentilesResponse,
    TrainModelRequest,
    TrainModelResponse,
    GrowthAnalysisRequest,
    GrowthAnalysisResponse,
    ModelAgreementRequest,
)
from app.training import train_from_transactions_df
from app.research.growth_analysis import GrowthAnalysisOptions, analyze_detector_growth


app = FastAPI(title="Fraud ML Service", version="0.1.0")

MODELS_DIR = os.getenv("MODELS_DIR", os.path.join(os.path.dirname(__file__), "..", "models"))
MODELS = None

DEFAULT_TRAINING_MODELS = ["IsolationForest", "Autoencoder", "LOF"]
PRODUCTION_MODELS = {
    "IsolationForest",
    "LOF",
    "Autoencoder",
}


@lru_cache(maxsize=32)
def _cached_models(models_dir: str):
    return load_models(models_dir)


def _models(models_dir: str):
    return _cached_models(models_dir) if os.path.isdir(models_dir) else load_models(models_dir)


def _clear_artifact_caches() -> None:
    _cached_models.cache_clear()
LEGACY_API_ENABLED = os.getenv("LEGACY_API_ENABLED", "true").strip().lower() in {"1", "true", "yes", "on"}
LEGACY_API_SUNSET_AT = os.getenv("LEGACY_API_SUNSET_AT", "2026-12-31T23:59:59Z")
LEGACY_API_PREFIXES = (
    "/api/v1/fraud/predict",
    "/api/v1/fraud/compare",
    "/api/v1/models/train",
    "/api/v1/models/score-percentiles",
    "/api/v1/models/hyperparams",
    "/api/v1/models/artifacts-info",
)


def _sunset_timestamp() -> datetime:
    return datetime.fromisoformat(LEGACY_API_SUNSET_AT.replace("Z", "+00:00")).astimezone(timezone.utc)


def _sunset_header() -> str:
    return format_datetime(_sunset_timestamp(), usegmt=True)


@app.middleware("http")
async def legacy_api_migration_window(request: Request, call_next):
    is_legacy = request.url.path in LEGACY_API_PREFIXES
    if is_legacy and (not LEGACY_API_ENABLED or datetime.now(timezone.utc) >= _sunset_timestamp()):
        return JSONResponse(
            status_code=410,
            content={"detail": "Deprecated raw-transaction ML API migration window has ended"},
            headers={"Sunset": _sunset_header(), "Deprecation": "true"},
        )
    response = await call_next(request)
    if is_legacy:
        response.headers["Deprecation"] = "true"
        response.headers["Sunset"] = _sunset_header()
        response.headers["Link"] = '</api/v2/fraud/predict>; rel="successor-version"'
    return response


@app.on_event("startup")
def _startup() -> None:
    global MODELS
    MODELS = load_models(os.path.abspath(MODELS_DIR))


def _vote(anomaly: bool) -> int:
    return 1 if anomaly else 0


def _risk_from_votes(votes: int) -> tuple[str, str]:
    if votes >= 3:
        return "HIGH", "HOLD_FOR_REVIEW"
    if votes == 2:
        return "MEDIUM", "ALLOW_AND_ALERT"
    if votes == 1:
        return "LOW", "ALLOW_AND_LOG"
    return "NORMAL", "ALLOW"

def _boolish(v: object, default: bool = False) -> bool:
    if v is None:
        return default
    if isinstance(v, bool):
        return v
    s = str(v).strip().lower()
    if s in ("true", "1", "yes", "y", "on"):
        return True
    if s in ("false", "0", "no", "n", "off"):
        return False
    return default


def _floatish(v: object, default: float) -> float:
    if v is None:
        return default
    if isinstance(v, (int, float)):
        return float(v)
    try:
        return float(str(v).strip())
    except Exception:
        return default


def _evaluate_models(loaded_models, x, selected_models: list[str]) -> Dict[str, Dict[str, Any]]:
    results: Dict[str, Dict[str, Any]] = {}
    for model_name in selected_models:
        started = __import__("time").perf_counter()
        if model_name == "IsolationForest" and loaded_models.iso_model is not None:
            score_samples = None
            decision = None
            try:
                score_samples = float(loaded_models.iso_model.score_samples(x)[0])
                decision = score_samples - float(loaded_models.iso_model.offset_)
            except Exception:
                pass
            raw_prediction = (-1 if decision < 0.0 else 1) if decision is not None \
                else int(loaded_models.iso_model.predict(x)[0])
            results[model_name] = {
                "anomaly": raw_prediction == -1,
                "rawPrediction": raw_prediction,
                "scoreSamples": score_samples,
                "decisionFunction": decision,
                "predictionDurationMs": round((__import__("time").perf_counter() - started) * 1000),
            }
        elif model_name == "LOF" and loaded_models.lof_model is not None:
            raw_prediction = int(loaded_models.lof_model.predict(x)[0])
            decision = None
            try:
                decision = float(loaded_models.lof_model.decision_function(x)[0])
            except Exception:
                pass
            results[model_name] = {
                "anomaly": raw_prediction == -1,
                "rawPrediction": raw_prediction,
                "decisionFunction": decision,
                "predictionDurationMs": round((__import__("time").perf_counter() - started) * 1000),
            }
        elif model_name == "OneClassSVM" and loaded_models.svm_model is not None:
            raw_prediction = int(loaded_models.svm_model.predict(x)[0])
            decision = None
            try:
                decision = float(loaded_models.svm_model.decision_function(x)[0])
            except Exception:
                pass
            results[model_name] = {
                "anomaly": raw_prediction == -1,
                "rawPrediction": raw_prediction,
                "decisionFunction": decision,
                "predictionDurationMs": round((__import__("time").perf_counter() - started) * 1000),
            }
        elif model_name == "EllipticEnvelope" and loaded_models.elliptic_model is not None:
            raw_prediction = int(loaded_models.elliptic_model.predict(x)[0])
            decision = None
            try:
                decision = float(loaded_models.elliptic_model.decision_function(x)[0])
            except Exception:
                pass
            results[model_name] = {
                "anomaly": raw_prediction == -1,
                "rawPrediction": raw_prediction,
                "decisionFunction": decision,
                "predictionDurationMs": round((__import__("time").perf_counter() - started) * 1000),
            }
        elif model_name == "PCAReconstruction" and loaded_models.pca_model is not None:
            pca_bundle = loaded_models.pca_model
            pca = pca_bundle["pca"]
            threshold = float(pca_bundle["threshold"])
            transformed = pca.transform(x)
            reconstructed = pca.inverse_transform(transformed)
            reconstruction_error = float(np.mean(np.square(x - reconstructed), axis=1)[0])
            raw_prediction = -1 if reconstruction_error > threshold else 1
            results[model_name] = {
                "anomaly": raw_prediction == -1,
                "rawPrediction": raw_prediction,
                "scoreSamples": reconstruction_error,
                "decisionFunction": threshold - reconstruction_error,
                "threshold": threshold,
                "predictionDurationMs": round((__import__("time").perf_counter() - started) * 1000),
            }
        elif model_name == "Autoencoder" and loaded_models.autoencoder_model is not None:
            autoencoder_bundle = loaded_models.autoencoder_model
            threshold = float(autoencoder_bundle["threshold"])
            reconstructed = autoencoder_bundle["autoencoder"].predict(x)
            reconstruction_error = float(np.mean(np.square(x - reconstructed), axis=1)[0])
            raw_prediction = -1 if reconstruction_error > threshold else 1
            results[model_name] = {
                "anomaly": raw_prediction == -1,
                "rawPrediction": raw_prediction,
                "scoreSamples": reconstruction_error,
                "decisionFunction": threshold - reconstruction_error,
                "threshold": threshold,
                "predictionDurationMs": round((__import__("time").perf_counter() - started) * 1000),
            }
    return results


def _persisted_feature_matrix(req: PersistedFeaturePredictRequest, loaded_models):
    if not req.featureVersion.strip():
        raise HTTPException(status_code=400, detail="featureVersion is required")
    if not req.modelFeatureSchema.strip():
        raise HTTPException(status_code=400, detail="modelFeatureSchema is required")
    if not req.features:
        raise HTTPException(status_code=400, detail="features must not be empty")
    invalid = [name for name, value in req.features.items() if not math.isfinite(float(value))]
    if invalid:
        raise HTTPException(
            status_code=400,
            detail=f"Non-finite persisted feature values: {', '.join(sorted(invalid))}",
        )
    supervised = req.learningMode.strip().upper() == "SUPERVISED"
    feature_columns = loaded_models.supervised_feature_columns if supervised else loaded_models.feature_columns
    scaler = loaded_models.supervised_scaler if supervised else loaded_models.scaler
    if not feature_columns or (not supervised and scaler is None):
        raise HTTPException(status_code=409, detail=f"{req.learningMode} feature contract is unavailable")
    aligned_values = [[float(req.features.get(column, 0.0)) for column in feature_columns]]
    aligned = pd.DataFrame(aligned_values, columns=feature_columns) if supervised else np.asarray(
        aligned_values, dtype=float
    )
    return aligned if supervised and scaler is None else scaler.transform(aligned)


def _evaluate_supervised_models(loaded_models, x, selected_models: list[str]):
    results = {}
    for model_name in selected_models:
        started = __import__("time").perf_counter()
        model = loaded_models.available_models[model_name]
        probability = float(model.predict_proba(x)[0, 1])
        threshold = float(loaded_models.supervised_thresholds.get(model_name, 0.5))
        fraud = probability >= threshold
        results[model_name] = {
            "fraud": fraud,
            "anomaly": fraud,
            "rawPrediction": 1 if fraud else 0,
            "fraudProbability": probability,
            "normalizedScore": probability,
            "rawScore": probability,
            "decisionThreshold": threshold,
            "normalizationVersion": "SUPERVISED_CALIBRATED_THRESHOLD_V2",
            "predictionDurationMs": round((__import__("time").perf_counter() - started) * 1000),
        }
    return results


@app.post("/api/v2/fraud/predict", response_model=ComparisonPredictResponse)
def predict_persisted_features(req: PersistedFeaturePredictRequest) -> ComparisonPredictResponse:
    loaded_models = MODELS if not req.modelsDir else _models(os.path.abspath(req.modelsDir))
    if loaded_models is None:
        raise HTTPException(status_code=503, detail="Models are not loaded")
    selected_models = req.modelNames if req.modelNames is not None else ["IsolationForest"]
    supervised_models = {"XGBoost", "RandomForestClassifier", "LogisticRegression"}
    allowed_models = supervised_models if req.learningMode.strip().upper() == "SUPERVISED" else set(PRODUCTION_MODELS)
    non_production_models = [name for name in selected_models if name not in allowed_models]
    if non_production_models:
        raise HTTPException(
            status_code=409,
            detail=f"Models are offline-comparison only: {', '.join(non_production_models)}",
        )
    unknown_models = [name for name in selected_models if name not in loaded_models.available_models]
    if unknown_models:
        raise HTTPException(
            status_code=409,
            detail=f"Requested model artifacts are unavailable: {', '.join(unknown_models)}",
        )
    x = _persisted_feature_matrix(req, loaded_models)
    model_results = (_evaluate_supervised_models(loaded_models, x, selected_models)
                     if req.learningMode.strip().upper() == "SUPERVISED"
                     else _evaluate_models(loaded_models, x, selected_models))
    summary = dict(req.featureSummary)
    summary.update({
        "featureVersion": req.featureVersion,
        "modelFeatureSchema": req.modelFeatureSchema,
        "persistedFeatureCount": len(req.features),
        "scoringContract": "PERSISTED_FEATURES_V2",
        "learningMode": req.learningMode.strip().upper(),
    })
    return ComparisonPredictResponse(
        transactionId=req.transactionId,
        accountId=req.accountId,
        modelResults=model_results,
        featureSummary=summary,
        reasons=list(req.reasons),
    )


@app.post("/api/v1/fraud/predict", response_model=FraudPredictResponse, deprecated=True)
def predict(req: FraudPredictRequest) -> FraudPredictResponse:
    from app.legacy.feature_engineering import build_single_features

    features_df, summary, reasons, extra = build_single_features(
        transaction=req.transaction.model_dump(),
        customer=req.customer.model_dump(),
        account_profile=req.accountProfile.model_dump(),
    )

    # Align columns
    aligned = features_df.reindex(columns=MODELS.feature_columns, fill_value=0.0)
    x = MODELS.scaler.transform(aligned.to_numpy(dtype=float))

    iso_pred = int(MODELS.iso_model.predict(x)[0])
    lof_pred = int(MODELS.lof_model.predict(x)[0])
    svm_pred = int(MODELS.svm_model.predict(x)[0])

    iso_anomaly = iso_pred == -1
    lof_anomaly = lof_pred == -1
    svm_anomaly = svm_pred == -1

    # Model scores for explainability (higher usually means "more normal"; negative often indicates anomaly)
    iso_score = None
    iso_decision = None
    try:
        iso_score = float(MODELS.iso_model.score_samples(x)[0])
    except Exception:
        pass
    try:
        iso_decision = float(MODELS.iso_model.decision_function(x)[0])
    except Exception:
        pass

    lof_decision = None
    try:
        lof_decision = float(MODELS.lof_model.decision_function(x)[0])
    except Exception:
        pass

    svm_decision = None
    try:
        svm_decision = float(MODELS.svm_model.decision_function(x)[0])
    except Exception:
        pass

    votes = _vote(iso_anomaly) + _vote(lof_anomaly) + _vote(svm_anomaly)
    risk_level, recommended_action = _risk_from_votes(votes)
    suspicious = risk_level in ("HIGH", "MEDIUM")

    model_results: Dict[str, Any] = {
        "isolationForest": {
            "anomaly": iso_anomaly,
            "rawPrediction": iso_pred,
            "scoreSamples": iso_score,
            "decisionFunction": iso_decision,
        },
        "localOutlierFactor": {
            "anomaly": lof_anomaly,
            "rawPrediction": lof_pred,
            "decisionFunction": lof_decision,
        },
        "oneClassSvm": {
            "anomaly": svm_anomaly,
            "rawPrediction": svm_pred,
            "decisionFunction": svm_decision,
        },
    }

    feature_summary: Dict[str, Any] = {
        "amountVsUserAvg": summary.amountVsUserAvg,
        "amountVsRolling7dAvg": summary.amountVsRolling7dAvg,
        "amountVsRolling30dAvg": summary.amountVsRolling30dAvg,
        "locationChanged": summary.locationChanged,
        "highLoginAttempts": summary.highLoginAttempts,
    }
    feature_summary.update(extra)

    # If rule-based reasons are empty but models voted anomaly, add model-based reasons.
    if (not reasons) and votes > 0:
        model_based: list[str] = []
        if iso_anomaly:
            extra = []
            if iso_decision is not None:
                extra.append(f"decision={iso_decision:.6f}")
            if iso_score is not None:
                extra.append(f"score={iso_score:.6f}")
            suffix = f" ({', '.join(extra)})" if extra else ""
            model_based.append(f"Isolation Forest flagged this transaction as an outlier{suffix}")
        if lof_anomaly:
            suffix = f" (decision={lof_decision:.6f})" if lof_decision is not None else ""
            model_based.append(f"Local Outlier Factor flagged this transaction as an outlier{suffix}")
        if svm_anomaly:
            suffix = f" (decision={svm_decision:.6f})" if svm_decision is not None else ""
            model_based.append(f"One-Class SVM flagged this transaction as an outlier{suffix}")
        reasons = model_based

    # Optional gating: downgrade MEDIUM when anomaly scores are not "strong" enough.
    hp = getattr(MODELS, "hyperparams", {}) or {}
    gating_enabled = _boolish(hp.get("ml.gating.enabled"), True)
    if gating_enabled and votes == 2 and risk_level == "MEDIUM":
        lof_thr = _floatish(hp.get("ml.gating.lof_decision_medium", -50.0), -50.0)
        svm_thr = _floatish(hp.get("ml.gating.svm_decision_medium", -15.0), -15.0)
        # If both models are only mildly negative (above thresholds), downgrade to LOW.
        if (lof_decision is not None and lof_decision > lof_thr) and (svm_decision is not None and svm_decision > svm_thr):
            risk_level = "LOW"
            recommended_action = "ALLOW_AND_LOG"
            suspicious = False
            reasons = reasons + [f"Downgraded to LOW by gating (lofThr={lof_thr}, svmThr={svm_thr})"]

    return FraudPredictResponse(
        transactionId=req.transaction.transactionId,
        accountId=req.transaction.accountId,
        suspicious=suspicious,
        riskLevel=risk_level,
        anomalyVotes=votes,
        modelResults=model_results,
        featureSummary=feature_summary,
        reasons=reasons,
        recommendedAction=recommended_action,
    )


@app.post("/api/v1/fraud/compare", response_model=ComparisonPredictResponse, deprecated=True)
def compare_predict(req: ComparisonPredictRequest) -> ComparisonPredictResponse:
    from app.legacy.feature_engineering import build_single_features

    comparison_models = load_models(os.path.abspath(req.modelsDir))
    features_df, summary, reasons, extra = build_single_features(
        transaction=req.transaction.model_dump(),
        customer=req.customer.model_dump(),
        account_profile=req.accountProfile.model_dump(),
    )
    aligned = features_df.reindex(columns=comparison_models.feature_columns, fill_value=0.0)
    x = comparison_models.scaler.transform(aligned.to_numpy(dtype=float))
    selected_models = req.modelNames or list(comparison_models.available_models.keys())
    model_results = _evaluate_models(comparison_models, x, selected_models)
    feature_summary: Dict[str, Any] = {
        "amountVsUserAvg": summary.amountVsUserAvg,
        "amountVsRolling7dAvg": summary.amountVsRolling7dAvg,
        "amountVsRolling30dAvg": summary.amountVsRolling30dAvg,
        "locationChanged": summary.locationChanged,
        "highLoginAttempts": summary.highLoginAttempts,
    }
    feature_summary.update(extra)
    return ComparisonPredictResponse(
        transactionId=req.transaction.transactionId,
        accountId=req.transaction.accountId,
        modelResults=model_results,
        featureSummary=feature_summary,
        reasons=reasons,
    )


@app.post("/api/v1/models/train", response_model=TrainModelResponse, deprecated=True)
def train_models_endpoint(req: TrainModelRequest) -> TrainModelResponse:
    try:
        df = None
        if req.transactions:
            rows = [t.model_dump() for t in req.transactions]
            df = _normalize_training_df(rows)
        evaluation_df = None
        if req.evaluationTransactions:
            evaluation_rows = [t.model_dump() for t in req.evaluationTransactions]
            evaluation_df = _normalize_training_df(evaluation_rows)
        target_models_dir = os.path.abspath(MODELS_DIR if not req.outputSubdir else os.path.join(MODELS_DIR, req.outputSubdir))
        learning_mode = req.learningMode.strip().upper()
        if learning_mode == "SUPERVISED":
            from app.supervised_training import (
                SUPPORTED_MODELS,
                train_supervised_from_persisted_dataset,
                train_supervised_from_transactions_df,
            )
            selected_models = req.modelNames or SUPPORTED_MODELS
            if req.datasetPath and req.datasetChecksum:
                trained_rows, feature_count, metrics = train_supervised_from_persisted_dataset(
                    req.datasetPath, req.datasetChecksum, target_models_dir,
                    hyperparams=req.hyperparams, model_names=selected_models,
                )
            elif df is not None:
                trained_rows, feature_count, metrics = train_supervised_from_transactions_df(
                    df, target_models_dir, hyperparams=req.hyperparams, model_names=selected_models
                )
            else:
                raise ValueError("Supervised training requires datasetPath and datasetChecksum")
        elif learning_mode == "UNSUPERVISED":
            if df is None:
                raise ValueError("Unsupervised legacy training requires transactions")
            selected_models = req.modelNames or DEFAULT_TRAINING_MODELS
            trained_rows, feature_count, metrics = train_from_transactions_df(
                df,
                target_models_dir,
                hyperparams=req.hyperparams,
                model_names=selected_models,
                evaluation_transactions_df=evaluation_df,
            )
        else:
            raise ValueError("learningMode must be SUPERVISED or UNSUPERVISED")
        _clear_artifact_caches()

        # Reload in-memory models so predict uses latest artifacts without restart
        global MODELS
        if os.path.abspath(target_models_dir) == os.path.abspath(MODELS_DIR):
            MODELS = load_models(os.path.abspath(MODELS_DIR))

        return TrainModelResponse(
            status="SUCCESS",
            message="Model training completed successfully",
            trainedRows=trained_rows,
            featureCount=feature_count,
            models=selected_models,
            artifacts=_build_training_artifacts(target_models_dir, selected_models),
            artifactBasePath=target_models_dir,
            metrics=metrics,
        )
    except HTTPException:
        raise
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex))
    except Exception as ex:
        raise HTTPException(status_code=500, detail=f"Training failed: {ex}")


@app.post("/api/v1/aml/research/growth-analysis", response_model=GrowthAnalysisResponse)
def growth_analysis_endpoint(req: GrowthAnalysisRequest) -> GrowthAnalysisResponse:
    try:
        if req.learningMode.strip().upper() == "SUPERVISED":
            from app.research.supervised_growth_analysis import analyze_supervised_growth
            return GrowthAnalysisResponse(**analyze_supervised_growth(
                req.datasetPath, req.datasetChecksum, req.percentages, req.minimumRows,
                req.maximumEvaluationRows, req.randomSeed, req.hyperparams
            ))
        result = analyze_detector_growth(
            req.datasetPath,
            req.datasetChecksum,
            GrowthAnalysisOptions(
                percentages=tuple(req.percentages),
                minimum_rows=req.minimumRows,
                holdout_fraction=req.holdoutFraction,
                maximum_evaluation_rows=req.maximumEvaluationRows,
                isolation_forest_max_training_rows=req.isolationForestMaximumTrainingRows,
                isolation_forest_estimators=req.isolationForestEstimators,
                autoencoder_max_training_rows=req.autoencoderMaxTrainingRows,
                lof_max_training_rows=req.localOutlierFactorMaxTrainingRows,
                lof_n_neighbors=req.localOutlierFactorNeighbors,
                lof_contamination=req.localOutlierFactorContamination,
                random_seed=req.randomSeed,
            ),
        )
        return GrowthAnalysisResponse(**result)
    except ValueError as exception:
        raise HTTPException(status_code=400, detail=str(exception))
    except Exception as exception:
        raise HTTPException(status_code=500, detail=f"Growth analysis failed: {exception}")


@app.post("/api/v1/aml/model-agreement")
def model_agreement_endpoint(req: ModelAgreementRequest) -> Dict[str, Any]:
    """Reports how much the trained models' flagged sets overlap on one snapshot."""
    from app.agreement import analyze_model_agreement

    try:
        return analyze_model_agreement(
            dataset_path=req.datasetPath,
            dataset_checksum=req.datasetChecksum,
            model_bundle_path=req.modelBundlePath,
        )
    except ValueError as exception:
        raise HTTPException(status_code=400, detail=str(exception))
    except Exception as exception:
        raise HTTPException(status_code=500, detail=f"Model agreement analysis failed: {exception}")


def _build_training_artifacts(models_dir: str, selected_models: list[str]) -> Dict[str, str]:
    file_map = {
        "IsolationForest": ("isolationForest", "iso_model.pkl"),
        "LOF": ("localOutlierFactor", "lof_model.pkl"),
        "OneClassSVM": ("oneClassSvm", "svm_model.pkl"),
        "EllipticEnvelope": ("ellipticEnvelope", "elliptic_envelope.pkl"),
        "PCAReconstruction": ("pcaReconstruction", "pca_reconstruction.pkl"),
        "Autoencoder": ("autoencoder", "autoencoder.pkl"),
        "XGBoost": ("xgboost", "xgboost_classifier.pkl"),
        "RandomForestClassifier": ("randomForestClassifier", "random_forest_classifier.pkl"),
        "LogisticRegression": ("logisticRegression", "logistic_regression.pkl"),
    }
    supervised = any(name in {"XGBoost", "RandomForestClassifier", "LogisticRegression"} for name in selected_models)
    artifacts: Dict[str, str] = ({
        "featureColumns": os.path.join(models_dir, "supervised_feature_columns.pkl"),
        "decisionThresholds": os.path.join(models_dir, "supervised_thresholds.json"),
        "learningMode": os.path.join(models_dir, "learning_mode.json"),
    } if supervised else {
        "scaler": os.path.join(models_dir, "scaler.pkl"),
        "featureColumns": os.path.join(models_dir, "feature_columns.pkl"),
        "trainingHyperparams": os.path.join(models_dir, "training_hyperparams.json"),
    })
    for model_name in selected_models:
        artifact_key, file_name = file_map[model_name]
        artifacts[artifact_key] = os.path.join(models_dir, file_name)
    return artifacts


def _normalize_training_df(rows: list[dict]) -> "pd.DataFrame":
    import pandas as pd

    df = pd.DataFrame(rows)
    rename_map = {
        "transactionId": "transaction_id",
        "accountId": "account_id",
        "transactionAmount": "transaction_amount",
        "transactionType": "transaction_type",
        "transactionDate": "transaction_date",
        "loginAttempts": "login_attempts",
        "accountBalance": "account_balance",
        "customerAge": "customer_age",
        "customerOccupation": "customer_occupation",
        "fraudLabel": "fraud_label",
    }
    df = df.rename(columns=rename_map)
    # Ensure required columns exist
    required = [
        "transaction_id",
        "account_id",
        "transaction_amount",
        "transaction_type",
        "transaction_date",
        "location",
        "channel",
        "login_attempts",
        "account_balance",
    ]
    missing = [c for c in required if c not in df.columns]
    if missing:
        raise ValueError(f"Missing required fields in transactions: {missing}")
    return df


@app.post("/api/v1/models/score-percentiles", response_model=ScorePercentilesResponse, deprecated=True)
def score_percentiles(req: ScorePercentilesRequest) -> ScorePercentilesResponse:
    if req.transactions is None or len(req.transactions) == 0:
        raise HTTPException(status_code=400, detail="transactions must not be empty")
    if MODELS is None:
        raise HTTPException(status_code=500, detail="Models not loaded. Train models first.")
    try:
        rows = [t.model_dump() for t in req.transactions]
        df = _normalize_training_df(rows)

        import pandas as pd
        from app.legacy.feature_engineering import build_training_features

        features = build_training_features(df)
        if features.isna().any().any():
            features = features.fillna(0.0)

        aligned = features.reindex(columns=MODELS.feature_columns, fill_value=0.0)
        x = MODELS.scaler.transform(aligned.to_numpy(dtype=float))

        # decision_function exists for novelty=True LOF and OneClassSVM
        lof_scores = MODELS.lof_model.decision_function(x)
        svm_scores = MODELS.svm_model.decision_function(x)

        pcts = [1, 5, 10, 25, 50, 75, 90, 95, 99]
        lof_p = np.percentile(lof_scores, pcts).tolist()
        svm_p = np.percentile(svm_scores, pcts).tolist()
        return ScorePercentilesResponse(
            percentiles=pcts,
            lofDecision=[float(v) for v in lof_p],
            svmDecision=[float(v) for v in svm_p],
        )
    except ValueError as ex:
        raise HTTPException(status_code=400, detail=str(ex))
    except Exception as ex:
        raise HTTPException(status_code=500, detail=f"Failed to compute percentiles: {ex}")


@app.get("/api/v1/models/hyperparams", deprecated=True)
def get_loaded_hyperparams() -> Dict[str, Any]:
    """
    Debug endpoint: returns hyperparameters currently loaded in-memory (from models/training_hyperparams.json).
    """
    if MODELS is None:
        raise HTTPException(status_code=500, detail="Models not loaded. Train models first.")
    return {"modelsDir": os.path.abspath(MODELS_DIR), "hyperparams": getattr(MODELS, "hyperparams", {}) or {}}


@app.get("/api/v1/models/artifacts-info", deprecated=True)
def get_artifacts_info() -> Dict[str, Any]:
    """
    Debug endpoint: returns artifact paths and last-modified timestamps so you can confirm retraining updated them.
    """
    models_dir = os.path.abspath(MODELS_DIR)
    files = [
        "iso_model.pkl",
        "lof_model.pkl",
        "svm_model.pkl",
        "scaler.pkl",
        "feature_columns.pkl",
        "training_hyperparams.json",
    ]
    info = {}
    for name in files:
        path = os.path.join(models_dir, name)
        if os.path.exists(path):
            stat = os.stat(path)
            info[name] = {
                "path": path,
                "size": stat.st_size,
                "mtimeEpoch": stat.st_mtime,
            }
        else:
            info[name] = {"path": path, "exists": False}
    return {"modelsDir": models_dir, "artifacts": info}
