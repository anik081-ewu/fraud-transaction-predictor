from __future__ import annotations

import json
import os
import pickle
import tempfile
import time
from typing import Any, Dict, List, Optional, Tuple

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    brier_score_loss,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.preprocessing import StandardScaler


SUPPORTED_MODELS = ["XGBoost", "RandomForestClassifier", "LogisticRegression"]
MODEL_FILES = {
    "XGBoost": "xgboost_classifier.pkl",
    "RandomForestClassifier": "random_forest_classifier.pkl",
    "LogisticRegression": "logistic_regression.pkl",
}


def train_supervised_from_transactions_df(
    transactions_df: pd.DataFrame,
    models_dir: str,
    hyperparams: Optional[Dict[str, Any]] = None,
    model_names: Optional[List[str]] = None,
) -> Tuple[int, int, Dict[str, Dict[str, Any]]]:
    from app.legacy.feature_engineering import build_training_features

    if "fraud_label" not in transactions_df.columns:
        raise ValueError("Supervised training requires FraudLabel")
    labelled = transactions_df.loc[transactions_df["fraud_label"].notna()].copy()
    if len(labelled) < 100:
        raise ValueError("Supervised training requires at least 100 labelled rows")
    labels = labelled["fraud_label"].astype(int)
    if set(labels.unique()) != {0, 1}:
        raise ValueError("Supervised training requires both FraudLabel classes 0 and 1")

    labelled = labelled.sort_values(["transaction_date", "transaction_id"], kind="stable").reset_index(drop=True)
    labels = labelled["fraud_label"].astype(int).reset_index(drop=True)
    features = build_training_features(labelled).fillna(0.0)
    split = max(1, min(len(labelled) - 1, int(len(labelled) * 0.8)))
    x_train_frame, x_test_frame = features.iloc[:split], features.iloc[split:]
    y_train, y_test = labels.iloc[:split], labels.iloc[split:]
    if y_train.nunique() < 2 or y_test.nunique() < 2:
        raise ValueError("Chronological train and test partitions must each contain FraudLabel 0 and 1")

    selected = _normalize_models(model_names)
    scaler = StandardScaler()
    x_train = scaler.fit_transform(x_train_frame.to_numpy(dtype=float))
    x_test = scaler.transform(x_test_frame.to_numpy(dtype=float))
    metrics: Dict[str, Dict[str, Any]] = {}
    models: Dict[str, Any] = {}
    random_state = int((hyperparams or {}).get("ml.random_state", 42))

    for model_name in selected:
        model = _build_model(model_name, random_state, y_train, hyperparams)
        started = time.perf_counter()
        model.fit(x_train, y_train)
        training_ms = (time.perf_counter() - started) * 1000.0
        prediction_started = time.perf_counter()
        probabilities = model.predict_proba(x_test)[:, 1]
        predictions = (probabilities >= 0.5).astype(int)
        prediction_ms = (time.perf_counter() - prediction_started) * 1000.0
        matrix = confusion_matrix(y_test, predictions, labels=[0, 1])
        models[model_name] = model
        metrics[model_name] = {
            "evaluationRows": int(len(y_test)),
            "positiveRows": int(y_test.sum()),
            "positiveRate": float(y_test.mean()),
            "prAuc": float(average_precision_score(y_test, probabilities)),
            "rocAuc": float(roc_auc_score(y_test, probabilities)),
            "precision": float(precision_score(y_test, predictions, zero_division=0)),
            "recall": float(recall_score(y_test, predictions, zero_division=0)),
            "f1": float(f1_score(y_test, predictions, zero_division=0)),
            "brierScore": float(brier_score_loss(y_test, probabilities)),
            "trueNegative": int(matrix[0, 0]),
            "falsePositive": int(matrix[0, 1]),
            "falseNegative": int(matrix[1, 0]),
            "truePositive": int(matrix[1, 1]),
            "decisionThreshold": 0.5,
            "trainingDurationMs": round(training_ms, 2),
            "predictionDurationMs": round(prediction_ms, 2),
            "throughputRowsPerSecond": round(len(y_test) / max(prediction_ms / 1000.0, 0.000001), 2),
        }

    os.makedirs(models_dir, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="fraud-supervised-train-", dir=models_dir) as tmp:
        for model_name, model in models.items():
            joblib.dump(model, os.path.join(tmp, MODEL_FILES[model_name]))
        joblib.dump(scaler, os.path.join(tmp, "supervised_scaler.pkl"))
        with open(os.path.join(tmp, "supervised_feature_columns.pkl"), "wb") as handle:
            pickle.dump(list(features.columns), handle)
        with open(os.path.join(tmp, "learning_mode.json"), "w", encoding="utf-8") as handle:
            json.dump({"learningMode": "SUPERVISED", "models": selected}, handle, indent=2)
        for name in os.listdir(tmp):
            os.replace(os.path.join(tmp, name), os.path.join(models_dir, name))

    return int(len(labelled)), int(len(features.columns)), metrics


def _normalize_models(model_names: Optional[List[str]]) -> List[str]:
    selected = model_names or SUPPORTED_MODELS
    normalized = list(dict.fromkeys(str(name).strip() for name in selected if name))
    unsupported = [name for name in normalized if name not in SUPPORTED_MODELS]
    if unsupported:
        raise ValueError(f"Unsupported supervised model names: {unsupported}")
    return normalized or list(SUPPORTED_MODELS)


def _value(hyperparams: Optional[Dict[str, Any]], key: str, default: Any, converter) -> Any:
    try:
        return converter((hyperparams or {}).get(key, default))
    except (TypeError, ValueError):
        return converter(default)


def _build_model(
    model_name: str,
    random_state: int,
    labels: pd.Series,
    hyperparams: Optional[Dict[str, Any]] = None,
) -> Any:
    if model_name == "LogisticRegression":
        return LogisticRegression(
            C=_value(hyperparams, "ml.logistic_regression.c", 1.0, float),
            max_iter=_value(hyperparams, "ml.logistic_regression.max_iter", 1000, int),
            class_weight="balanced",
            random_state=random_state,
        )
    if model_name == "RandomForestClassifier":
        return RandomForestClassifier(
            n_estimators=_value(hyperparams, "ml.random_forest.n_estimators", 300, int),
            max_depth=_value(hyperparams, "ml.random_forest.max_depth", 12, int),
            min_samples_leaf=_value(hyperparams, "ml.random_forest.min_samples_leaf", 2, int),
            class_weight="balanced_subsample",
            n_jobs=-1,
            random_state=random_state,
        )
    if model_name == "XGBoost":
        try:
            from xgboost import XGBClassifier
        except ImportError as exception:
            raise ValueError("XGBoost is not installed; run pip install -r requirements.txt") from exception
        positives = max(1, int(labels.sum()))
        negatives = max(1, int(len(labels) - positives))
        return XGBClassifier(
            n_estimators=_value(hyperparams, "ml.xgboost.n_estimators", 300, int),
            max_depth=_value(hyperparams, "ml.xgboost.max_depth", 6, int),
            learning_rate=_value(hyperparams, "ml.xgboost.learning_rate", 0.05, float),
            subsample=_value(hyperparams, "ml.xgboost.subsample", 0.8, float),
            colsample_bytree=_value(hyperparams, "ml.xgboost.colsample_bytree", 0.8, float),
            scale_pos_weight=negatives / positives,
            eval_metric="logloss",
            n_jobs=-1,
            random_state=random_state,
        )
    raise ValueError(f"Unsupported supervised model name: {model_name}")
