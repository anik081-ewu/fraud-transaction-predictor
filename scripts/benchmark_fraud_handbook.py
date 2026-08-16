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


def load_days(data_dir: Path, maximum_days: int, maximum_customers: int | None = None) -> pd.DataFrame:
    files = sorted(data_dir.glob("*.pkl"))[:maximum_days]
    if not files:
        raise ValueError(f"No pickle files found in {data_dir}")
    frames = []
    for path in files:
        frame = pd.read_pickle(path)
        if maximum_customers is not None:
            frame = frame.loc[frame["CUSTOMER_ID"].astype(int) < maximum_customers]
        frames.append(frame)
    frame = pd.concat(frames, ignore_index=True)
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
            n_jobs=-1,
            random_state=random_state,
        ),
        "ExtraTrees": ExtraTreesClassifier(
            n_estimators=250,
            max_depth=None,
            min_samples_leaf=2,
            max_features=0.8,
            class_weight="balanced_subsample",
            n_jobs=-1,
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
            n_jobs=-1,
            random_state=random_state,
        )
    except ImportError:
        pass
    return models


def specialist_candidates(random_state: int) -> dict[str, tuple[ExtraTreesClassifier, list[str]]]:
    shared = ["TX_AMOUNT", "TX_TIME_SECONDS", "TX_TIME_DAYS", "TX_DURING_WEEKEND", "TX_DURING_NIGHT"]
    return {
        "GlobalContextExpert": (
            ExtraTreesClassifier(
                n_estimators=400,
                max_depth=None,
                min_samples_leaf=2,
                max_features=0.8,
                class_weight="balanced_subsample",
                n_jobs=-1,
                random_state=random_state,
            ),
            [],
        ),
        "CustomerCompromiseExpert": (
            ExtraTreesClassifier(
                n_estimators=400,
                max_depth=None,
                min_samples_leaf=2,
                max_features=1.0,
                class_weight="balanced_subsample",
                n_jobs=-1,
                random_state=random_state + 1,
            ),
            shared + [
                "CUSTOMER_ID_NB_TX_1DAY_WINDOW", "CUSTOMER_ID_AVG_AMOUNT_1DAY_WINDOW",
                "CUSTOMER_ID_NB_TX_7DAY_WINDOW", "CUSTOMER_ID_AVG_AMOUNT_7DAY_WINDOW",
                "CUSTOMER_ID_NB_TX_30DAY_WINDOW", "CUSTOMER_ID_AVG_AMOUNT_30DAY_WINDOW",
            ],
        ),
        "TerminalCompromiseExpert": (
            ExtraTreesClassifier(
                n_estimators=400,
                max_depth=None,
                min_samples_leaf=2,
                max_features=1.0,
                class_weight="balanced_subsample",
                n_jobs=-1,
                random_state=random_state + 2,
            ),
            shared + [
                "TERMINAL_ID_NB_TX_1DAY_WINDOW", "TERMINAL_ID_RISK_1DAY_WINDOW",
                "TERMINAL_ID_NB_TX_7DAY_WINDOW", "TERMINAL_ID_RISK_7DAY_WINDOW",
                "TERMINAL_ID_NB_TX_30DAY_WINDOW", "TERMINAL_ID_RISK_30DAY_WINDOW",
            ],
        ),
    }


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


def metrics(
    labels: np.ndarray,
    probabilities: np.ndarray,
    threshold: float | None,
    scenarios: np.ndarray | None = None,
) -> dict[str, float | int | None | dict[str, float]]:
    if threshold is None:
        return {"threshold": None, "precision": None, "recall": None, "f1": None}
    predictions = (probabilities >= threshold).astype(int)
    true_negative, false_positive, false_negative, true_positive = confusion_matrix(
        labels, predictions, labels=[0, 1]
    ).ravel()
    precision = true_positive / max(true_positive + false_positive, 1)
    recall = true_positive / max(true_positive + false_negative, 1)
    result = {
        "threshold": threshold,
        "precision": precision,
        "recall": recall,
        "f1": 2 * precision * recall / max(precision + recall, 1e-12),
        "truePositive": int(true_positive),
        "falsePositive": int(false_positive),
        "falseNegative": int(false_negative),
        "trueNegative": int(true_negative),
    }
    if scenarios is not None:
        result["recallByScenario"] = {
            str(int(scenario)): float(predictions[scenarios == scenario].mean())
            for scenario in sorted(set(scenarios[labels == 1]))
            if scenario > 0 and np.any(scenarios == scenario)
        }
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", type=Path, required=True)
    parser.add_argument("--days", type=int, default=45)
    parser.add_argument("--customers", type=int)
    parser.add_argument("--target-precision", type=float, default=0.90)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    frame = load_days(args.data_dir, args.days, args.customers)
    labels = frame["TX_FRAUD"].astype(int).to_numpy()
    feature_columns = [column for column in frame.columns if column not in NON_FEATURE_COLUMNS]
    features = frame[feature_columns].replace([np.inf, -np.inf], np.nan).fillna(0.0)

    fit_end = int(len(frame) * 0.70)
    calibration_end = int(len(frame) * 0.80)
    x_train, y_train = features.iloc[:fit_end], labels[:fit_end]
    x_calibration, y_calibration = features.iloc[fit_end:calibration_end], labels[fit_end:calibration_end]
    x_test, y_test = features.iloc[calibration_end:], labels[calibration_end:]
    test_scenarios = frame["TX_FRAUD_SCENARIO"].astype(int).to_numpy()[calibration_end:]
    positive_weight = max(float((y_train == 0).sum() / max((y_train == 1).sum(), 1)), 1.0)
    sample_weight = compute_sample_weight("balanced", y_train)

    results = []
    calibration_probabilities: dict[str, np.ndarray] = {}
    test_probabilities: dict[str, np.ndarray] = {}
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
        calibration_probabilities[name] = calibration_probability
        test_probabilities[name] = test_probability
        results.append({
            "model": name,
            "prAuc": float(average_precision_score(y_test, test_probability)),
            "bestF1": metrics(
                y_test, test_probability,
                best_f1_threshold(y_calibration, calibration_probability),
                test_scenarios,
            ),
            "targetPrecision": metrics(
                y_test,
                test_probability,
                target_precision_threshold(y_calibration, calibration_probability, args.target_precision),
                test_scenarios,
            ),
            "trainingSeconds": round(training_seconds, 3),
        })
        del model
        gc.collect()

    ensemble_results = evaluate_ensembles(
        y_calibration,
        y_test,
        calibration_probabilities,
        test_probabilities,
        args.target_precision,
        args.seed,
        test_scenarios,
    )
    specialist_results = evaluate_specialist_fusion(
        x_train,
        y_train,
        x_calibration,
        y_calibration,
        x_test,
        y_test,
        args.target_precision,
        args.seed,
        test_scenarios,
    )

    report = {
        "source": str(args.data_dir),
        "days": args.days,
        "customers": args.customers,
        "rows": len(frame),
        "fraudRows": int(labels.sum()),
        "fraudRate": float(labels.mean()),
        "features": feature_columns,
        "split": "chronological 70% train / 10% threshold calibration / 20% untouched test",
        "targetPrecision": args.target_precision,
        "results": results,
        "ensembles": ensemble_results,
        "specialistFusion": specialist_results,
    }
    rendered = json.dumps(report, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered)


def evaluate_ensembles(
    calibration_labels: np.ndarray,
    test_labels: np.ndarray,
    calibration_probabilities: dict[str, np.ndarray],
    test_probabilities: dict[str, np.ndarray],
    target_precision: float,
    random_state: int,
    test_scenarios: np.ndarray,
) -> list[dict[str, object]]:
    names = list(calibration_probabilities)
    midpoint = len(calibration_labels) // 2
    meta_labels = calibration_labels[:midpoint]
    threshold_labels = calibration_labels[midpoint:]
    calibration_matrix = np.column_stack([calibration_probabilities[name] for name in names])
    test_matrix = np.column_stack([test_probabilities[name] for name in names])

    meta_model = LogisticRegression(
        C=0.25,
        class_weight="balanced",
        max_iter=2000,
        solver="liblinear",
        random_state=random_state,
    )
    meta_model.fit(calibration_matrix[:midpoint], meta_labels)
    stacked_threshold_scores = meta_model.predict_proba(calibration_matrix[midpoint:])[:, 1]
    stacked_test_scores = meta_model.predict_proba(test_matrix)[:, 1]

    tuning_scores = np.asarray([
        average_precision_score(meta_labels, calibration_matrix[:midpoint, index])
        for index in range(len(names))
    ])
    weights = np.maximum(tuning_scores, 1e-6)
    weights = weights / weights.sum()
    weighted_threshold_scores = np.average(calibration_matrix[midpoint:], axis=1, weights=weights)
    weighted_test_scores = np.average(test_matrix, axis=1, weights=weights)

    strategies = [
        ("TemporalLogisticStack", stacked_threshold_scores, stacked_test_scores, {
            "members": names,
            "coefficients": {name: float(value) for name, value in zip(names, meta_model.coef_[0])},
        }),
        ("ValidationWeightedSoftVote", weighted_threshold_scores, weighted_test_scores, {
            "members": names,
            "weights": {name: float(value) for name, value in zip(names, weights)},
        }),
    ]
    output = []
    for name, threshold_scores, test_scores, details in strategies:
        output.append({
            "strategy": name,
            "prAuc": float(average_precision_score(test_labels, test_scores)),
            "bestF1": metrics(
                test_labels,
                test_scores,
                best_f1_threshold(threshold_labels, threshold_scores),
                test_scenarios,
            ),
            "targetPrecision": metrics(
                test_labels,
                test_scores,
                target_precision_threshold(threshold_labels, threshold_scores, target_precision),
                test_scenarios,
            ),
            **details,
        })
    return output


def evaluate_specialist_fusion(
    train_features: pd.DataFrame,
    train_labels: np.ndarray,
    calibration_features: pd.DataFrame,
    calibration_labels: np.ndarray,
    test_features: pd.DataFrame,
    test_labels: np.ndarray,
    target_precision: float,
    random_state: int,
    test_scenarios: np.ndarray,
) -> dict[str, object]:
    calibration_scores: dict[str, np.ndarray] = {}
    test_scores: dict[str, np.ndarray] = {}
    expert_metrics = []
    for name, (model, columns) in specialist_candidates(random_state).items():
        selected = columns or list(train_features.columns)
        model.fit(train_features[selected], train_labels)
        calibration_probability = model.predict_proba(calibration_features[selected])[:, 1]
        test_probability = model.predict_proba(test_features[selected])[:, 1]
        calibration_scores[name] = calibration_probability
        test_scores[name] = test_probability
        expert_metrics.append({
            "expert": name,
            "features": selected,
            "prAuc": float(average_precision_score(test_labels, test_probability)),
            "targetPrecision": metrics(
                test_labels,
                test_probability,
                target_precision_threshold(calibration_labels, calibration_probability, target_precision),
                test_scenarios,
            ),
        })
        del model
        gc.collect()

    names = list(calibration_scores)
    calibration_matrix = np.column_stack([calibration_scores[name] for name in names])
    test_matrix = np.column_stack([test_scores[name] for name in names])
    midpoint = len(calibration_labels) // 2
    meta_labels = calibration_labels[:midpoint]
    threshold_labels = calibration_labels[midpoint:]

    meta_model = LogisticRegression(
        C=0.1,
        class_weight="balanced",
        max_iter=2000,
        solver="liblinear",
        random_state=random_state,
    )
    meta_model.fit(calibration_matrix[:midpoint], meta_labels)
    stacked_calibration = meta_model.predict_proba(calibration_matrix[midpoint:])[:, 1]
    stacked_test = meta_model.predict_proba(test_matrix)[:, 1]

    candidate_weights = []
    for global_weight in np.arange(0.4, 1.01, 0.1):
        for customer_weight in np.arange(0.0, 1.01 - global_weight, 0.1):
            terminal_weight = 1.0 - global_weight - customer_weight
            candidate_weights.append(np.asarray([global_weight, customer_weight, terminal_weight]))
    best_weights = max(
        candidate_weights,
        key=lambda weights: average_precision_score(
            meta_labels,
            np.average(calibration_matrix[:midpoint], axis=1, weights=weights),
        ),
    )
    blended_calibration = np.average(calibration_matrix[midpoint:], axis=1, weights=best_weights)
    blended_test = np.average(test_matrix, axis=1, weights=best_weights)

    max_calibration = calibration_matrix[midpoint:].max(axis=1)
    max_test = test_matrix.max(axis=1)
    rescue_thresholds = optimize_specialist_rescue(
        calibration_labels,
        calibration_matrix,
        target_precision + 0.03,
    )
    rescue_calibration = specialist_rescue_score(calibration_matrix[midpoint:], rescue_thresholds)
    rescue_test = specialist_rescue_score(test_matrix, rescue_thresholds)
    strategies = [
        ("SpecialistTemporalStack", stacked_calibration, stacked_test, {
            "coefficients": {name: float(value) for name, value in zip(names, meta_model.coef_[0])},
        }),
        ("ValidationOptimizedSpecialistBlend", blended_calibration, blended_test, {
            "weights": {name: float(value) for name, value in zip(names, best_weights)},
        }),
        ("AnySpecialistMaximum", max_calibration, max_test, {}),
        ("HighConfidenceSpecialistRescue", rescue_calibration, rescue_test, {
            "thresholds": {name: float(value) for name, value in zip(names, rescue_thresholds)},
            "calibrationPrecisionFloor": min(target_precision + 0.03, 0.99),
        }),
    ]
    fused_metrics = []
    for name, threshold_scores, final_scores, details in strategies:
        fused_metrics.append({
            "strategy": name,
            "prAuc": float(average_precision_score(test_labels, final_scores)),
            "bestF1": metrics(
                test_labels,
                final_scores,
                best_f1_threshold(threshold_labels, threshold_scores),
                test_scenarios,
            ),
            "targetPrecision": metrics(
                test_labels,
                final_scores,
                target_precision_threshold(threshold_labels, threshold_scores, target_precision),
                test_scenarios,
            ),
            **details,
        })
    return {"experts": expert_metrics, "fusions": fused_metrics}


def optimize_specialist_rescue(
    labels: np.ndarray,
    score_matrix: np.ndarray,
    precision_floor: float,
) -> np.ndarray:
    quantiles = np.asarray([0.80, 0.85, 0.90, 0.93, 0.95, 0.97, 0.98, 0.99, 0.995, 0.999])
    candidates = [np.unique(np.quantile(score_matrix[:, index], quantiles)) for index in range(3)]
    best_thresholds = np.asarray([values[-1] for values in candidates])
    best_recall = -1.0
    best_precision = -1.0
    for global_threshold in candidates[0]:
        global_vote = score_matrix[:, 0] >= global_threshold
        for customer_threshold in candidates[1]:
            customer_vote = score_matrix[:, 1] >= customer_threshold
            for terminal_threshold in candidates[2]:
                predictions = global_vote | customer_vote | (score_matrix[:, 2] >= terminal_threshold)
                true_positive = int(np.sum(predictions & (labels == 1)))
                false_positive = int(np.sum(predictions & (labels == 0)))
                false_negative = int(np.sum(~predictions & (labels == 1)))
                precision = true_positive / max(true_positive + false_positive, 1)
                recall = true_positive / max(true_positive + false_negative, 1)
                if precision < min(precision_floor, 0.99):
                    continue
                if recall > best_recall or (recall == best_recall and precision > best_precision):
                    best_recall = recall
                    best_precision = precision
                    best_thresholds = np.asarray([
                        global_threshold,
                        customer_threshold,
                        terminal_threshold,
                    ])
    return best_thresholds


def specialist_rescue_score(score_matrix: np.ndarray, thresholds: np.ndarray) -> np.ndarray:
    margins = score_matrix / np.maximum(thresholds, 1e-12)
    return margins.max(axis=1)


if __name__ == "__main__":
    main()
