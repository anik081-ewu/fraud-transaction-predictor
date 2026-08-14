from __future__ import annotations

import argparse
import gc
import json
import time
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.ensemble import ExtraTreesClassifier, HistGradientBoostingClassifier, RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, confusion_matrix, precision_recall_curve
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import RobustScaler
from sklearn.utils.class_weight import compute_sample_weight


NON_FEATURE_COLUMNS = {
    "TRANSACTION_ID",
    "TX_DATETIME",
    "CUSTOMER_ID",
    "TERMINAL_ID",
    "TX_FRAUD",
    "TX_FRAUD_SCENARIO",
}


def load_days(data_dir: Path, maximum_days: int) -> pd.DataFrame:
    files = sorted(data_dir.glob("*.pkl"))[:maximum_days]
    if not files:
        raise ValueError(f"No pickle files found in {data_dir}")
    frame = pd.concat((pd.read_pickle(path) for path in files), ignore_index=True)
    return frame.sort_values(["TX_DATETIME", "TRANSACTION_ID"], kind="stable").reset_index(drop=True)


def model_candidates(random_state: int, positive_weight: float) -> dict[str, object]:
    models: dict[str, object] = {
        "LogisticRegression": Pipeline([
            ("imputer", SimpleImputer(strategy="median")),
            ("scaler", RobustScaler()),
            ("classifier", LogisticRegression(
                C=0.5,
                class_weight="balanced",
                max_iter=2000,
                random_state=random_state,
            )),
        ]),
        "RandomForest": RandomForestClassifier(
            n_estimators=250,
            max_depth=18,
            min_samples_leaf=2,
            max_features="sqrt",
            class_weight="balanced_subsample",
            n_jobs=1,
            random_state=random_state,
        ),
        "ExtraTrees": ExtraTreesClassifier(
            n_estimators=250,
            max_depth=None,
            min_samples_leaf=2,
            max_features=0.8,
            class_weight="balanced_subsample",
            n_jobs=1,
            random_state=random_state,
        ),
        "HistGradientBoosting": HistGradientBoostingClassifier(
            learning_rate=0.08,
            max_iter=300,
            max_leaf_nodes=31,
            min_samples_leaf=30,
            l2_regularization=1.0,
            random_state=random_state,
        ),
    }
    try:
        from xgboost import XGBClassifier

        models["XGBoost"] = XGBClassifier(
            n_estimators=400,
            max_depth=5,
            learning_rate=0.04,
            subsample=0.85,
            colsample_bytree=0.85,
            min_child_weight=3,
            reg_alpha=0.05,
            reg_lambda=2.0,
            scale_pos_weight=positive_weight,
            eval_metric="logloss",
            n_jobs=1,
            random_state=random_state,
        )
    except ImportError:
        pass
    return models


def best_f1_threshold(labels: np.ndarray, probabilities: np.ndarray) -> float:
    precision, recall, thresholds = precision_recall_curve(labels, probabilities)
    scores = 2 * precision[:-1] * recall[:-1] / np.maximum(precision[:-1] + recall[:-1], 1e-12)
    return float(thresholds[int(np.nanargmax(scores))]) if len(thresholds) else 0.5


def target_precision_threshold(
    labels: np.ndarray,
    probabilities: np.ndarray,
    target_precision: float,
) -> float | None:
    precision, recall, thresholds = precision_recall_curve(labels, probabilities)
    eligible = np.flatnonzero(precision[:-1] >= target_precision)
    if not len(eligible):
        return None
    best_recall = np.nanmax(recall[:-1][eligible])
    best = eligible[np.flatnonzero(recall[:-1][eligible] == best_recall)[-1]]
    return float(thresholds[int(best)])


def metrics(labels: np.ndarray, probabilities: np.ndarray, threshold: float | None) -> dict[str, float | int | None]:
    if threshold is None:
        return {"threshold": None, "precision": None, "recall": None, "f1": None}
    predictions = (probabilities >= threshold).astype(int)
    true_negative, false_positive, false_negative, true_positive = confusion_matrix(
        labels, predictions, labels=[0, 1]
    ).ravel()
    precision = true_positive / max(true_positive + false_positive, 1)
    recall = true_positive / max(true_positive + false_negative, 1)
    return {
        "threshold": threshold,
        "precision": precision,
        "recall": recall,
        "f1": 2 * precision * recall / max(precision + recall, 1e-12),
        "truePositive": int(true_positive),
        "falsePositive": int(false_positive),
        "falseNegative": int(false_negative),
        "trueNegative": int(true_negative),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", type=Path, required=True)
    parser.add_argument("--days", type=int, default=45)
    parser.add_argument("--target-precision", type=float, default=0.90)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    frame = load_days(args.data_dir, args.days)
    labels = frame["TX_FRAUD"].astype(int).to_numpy()
    feature_columns = [column for column in frame.columns if column not in NON_FEATURE_COLUMNS]
    features = frame[feature_columns].replace([np.inf, -np.inf], np.nan).fillna(0.0)

    fit_end = int(len(frame) * 0.70)
    calibration_end = int(len(frame) * 0.80)
    x_train, y_train = features.iloc[:fit_end], labels[:fit_end]
    x_calibration, y_calibration = features.iloc[fit_end:calibration_end], labels[fit_end:calibration_end]
    x_test, y_test = features.iloc[calibration_end:], labels[calibration_end:]
    positive_weight = max(float((y_train == 0).sum() / max((y_train == 1).sum(), 1)), 1.0)
    sample_weight = compute_sample_weight("balanced", y_train)

    results = []
    for name, model in model_candidates(args.seed, positive_weight).items():
        started = time.perf_counter()
        if name == "LogisticRegression":
            model.fit(x_train, y_train, classifier__sample_weight=sample_weight)
        elif name == "HistGradientBoosting":
            model.fit(x_train, y_train, sample_weight=sample_weight)
        else:
            model.fit(x_train, y_train)
        training_seconds = time.perf_counter() - started
        calibration_probability = model.predict_proba(x_calibration)[:, 1]
        test_probability = model.predict_proba(x_test)[:, 1]
        results.append({
            "model": name,
            "prAuc": float(average_precision_score(y_test, test_probability)),
            "bestF1": metrics(y_test, test_probability, best_f1_threshold(y_calibration, calibration_probability)),
            "targetPrecision": metrics(
                y_test,
                test_probability,
                target_precision_threshold(y_calibration, calibration_probability, args.target_precision),
            ),
            "trainingSeconds": round(training_seconds, 3),
        })
        del model
        gc.collect()

    report = {
        "source": str(args.data_dir),
        "days": args.days,
        "rows": len(frame),
        "fraudRows": int(labels.sum()),
        "fraudRate": float(labels.mean()),
        "features": feature_columns,
        "split": "chronological 70% train / 10% threshold calibration / 20% untouched test",
        "targetPrecision": args.target_precision,
        "results": results,
    }
    rendered = json.dumps(report, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
