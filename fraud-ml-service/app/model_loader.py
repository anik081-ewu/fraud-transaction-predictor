import os
import pickle
from dataclasses import dataclass, field

import joblib
import json


@dataclass(frozen=True)
class LoadedModels:
    iso_model: object | None
    lof_model: object | None
    svm_model: object | None
    elliptic_model: object | None
    pca_model: object | None
    autoencoder_model: object | None
    scaler: object
    feature_columns: list[str]
    hyperparams: dict
    available_models: dict[str, object]
    supervised_scaler: object | None = None
    supervised_feature_columns: list[str] = field(default_factory=list)
    supervised_thresholds: dict[str, float] = field(default_factory=dict)


def load_models(models_dir: str) -> LoadedModels:
    iso_path = os.path.join(models_dir, "iso_model.pkl")
    lof_path = os.path.join(models_dir, "lof_model.pkl")
    svm_path = os.path.join(models_dir, "svm_model.pkl")
    elliptic_path = os.path.join(models_dir, "elliptic_envelope.pkl")
    pca_path = os.path.join(models_dir, "pca_reconstruction.pkl")
    autoencoder_path = os.path.join(models_dir, "autoencoder.pkl")
    supervised_paths = {
        "XGBoost": "xgboost_classifier.pkl",
        "RandomForestClassifier": "random_forest_classifier.pkl",
        "LogisticRegression": "logistic_regression.pkl",
    }

    iso_model = joblib.load(iso_path) if os.path.exists(iso_path) else None
    lof_model = joblib.load(lof_path) if os.path.exists(lof_path) else None
    svm_model = joblib.load(svm_path) if os.path.exists(svm_path) else None
    elliptic_model = joblib.load(elliptic_path) if os.path.exists(elliptic_path) else None
    pca_model = joblib.load(pca_path) if os.path.exists(pca_path) else None
    autoencoder_model = joblib.load(autoencoder_path) if os.path.exists(autoencoder_path) else None
    supervised_models = {
        name: joblib.load(os.path.join(models_dir, file_name))
        for name, file_name in supervised_paths.items()
        if os.path.exists(os.path.join(models_dir, file_name))
    }
    scaler_path = os.path.join(models_dir, "scaler.pkl")
    columns_path = os.path.join(models_dir, "feature_columns.pkl")
    scaler = joblib.load(scaler_path) if os.path.exists(scaler_path) else None
    feature_columns = []
    if os.path.exists(columns_path):
        with open(columns_path, "rb") as f:
            feature_columns = pickle.load(f)
    hp_path = os.path.join(models_dir, "training_hyperparams.json")
    hyperparams = {}
    if os.path.exists(hp_path):
        with open(hp_path, "r", encoding="utf-8") as f:
            hyperparams = json.load(f) or {}
    supervised_scaler_path = os.path.join(models_dir, "supervised_scaler.pkl")
    supervised_columns_path = os.path.join(models_dir, "supervised_feature_columns.pkl")
    supervised_scaler = joblib.load(supervised_scaler_path) if os.path.exists(supervised_scaler_path) else None
    supervised_feature_columns = []
    if os.path.exists(supervised_columns_path):
        with open(supervised_columns_path, "rb") as handle:
            supervised_feature_columns = pickle.load(handle)
    supervised_thresholds_path = os.path.join(models_dir, "supervised_thresholds.json")
    supervised_thresholds = {}
    if os.path.exists(supervised_thresholds_path):
        with open(supervised_thresholds_path, "r", encoding="utf-8") as handle:
            supervised_thresholds = {
                str(name): float(value) for name, value in (json.load(handle) or {}).items()
            }
    return LoadedModels(
        iso_model=iso_model,
        lof_model=lof_model,
        svm_model=svm_model,
        elliptic_model=elliptic_model,
        pca_model=pca_model,
        autoencoder_model=autoencoder_model,
        scaler=scaler,
        feature_columns=feature_columns,
        hyperparams=hyperparams,
        available_models={**{
            name: model
            for name, model in {
                "IsolationForest": iso_model,
                "LOF": lof_model,
                "OneClassSVM": svm_model,
                "EllipticEnvelope": elliptic_model,
                "PCAReconstruction": pca_model,
                "Autoencoder": autoencoder_model,
            }.items()
            if model is not None
        }, **supervised_models},
        supervised_scaler=supervised_scaler,
        supervised_feature_columns=supervised_feature_columns,
        supervised_thresholds=supervised_thresholds,
    )
