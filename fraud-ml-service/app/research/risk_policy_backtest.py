from __future__ import annotations

from typing import Any, Optional

import numpy as np
import pandas as pd
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    balanced_accuracy_score,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)


PROTOCOL = "FULL_RISK_POLICY_BACKTEST_V2"

MODEL_KEY_BY_RESEARCH_NAME = {
    "XGBoost": "XGBOOST_CLASSIFIER",
    "RandomForestClassifier": "RANDOM_FOREST_CLASSIFIER",
    "ExtraTreesClassifier": "EXTRA_TREES_CLASSIFIER",
    "StackedEnsemble": "STACKED_ENSEMBLE",
}


def evaluate_risk_policy(
    *,
    percentage: int,
    partition_rows: int,
    evaluation_labels: np.ndarray,
    evaluation_features: pd.DataFrame,
    evaluation_probabilities: dict[str, np.ndarray],
    evaluation_predictions: dict[str, np.ndarray],
    tuning_weights: dict[str, float],
    hyperparams: Optional[dict[str, Any]],
    ml_baseline: dict[str, Any],
) -> dict[str, Any]:
    policy = _policy(hyperparams)
    selected_weights, allocation_source = _selected_model_weights(
        hyperparams, evaluation_probabilities, tuning_weights
    )
    model_names = list(selected_weights)
    probability = np.average(
        np.vstack([evaluation_probabilities[name] for name in model_names]),
        axis=0,
        weights=[selected_weights[name] for name in model_names],
    )
    selected_predictions = np.vstack([evaluation_predictions[name] for name in model_names])
    selected_probabilities = np.vstack([evaluation_probabilities[name] for name in model_names])
    decision_aware_scores = np.where(selected_predictions == 1, 1.0, selected_probabilities)
    ml_score = np.average(
        decision_aware_scores,
        axis=0,
        weights=[selected_weights[name] for name in model_names],
    )

    customer_score = _customer_scores(evaluation_features, policy)
    peer_score = _peer_scores(evaluation_features, policy)
    rule_score, hard_override = _rule_scores(evaluation_features, policy)
    final_score = np.clip(
        customer_score * policy["customerBehaviourWeight"]
        + peer_score * policy["peerBehaviourWeight"]
        + ml_score * policy["mlEnsembleWeight"]
        + rule_score * policy["rulesWeight"],
        0.0,
        1.0,
    )
    final_score = np.where(hard_override, np.maximum(final_score, policy["highThreshold"]), final_score)
    case_prediction = np.logical_or(final_score >= policy["mediumThreshold"], hard_override).astype(int)
    str_prediction = np.logical_or(final_score >= policy["highThreshold"], hard_override).astype(int)
    case_metrics = _decision_metrics(evaluation_labels, final_score, case_prediction)
    str_metrics = _decision_metrics(evaluation_labels, final_score, str_prediction)

    return {
        "protocol": PROTOCOL,
        "partitionPercentage": percentage,
        "partitionRows": partition_rows,
        "evaluationRows": int(len(evaluation_labels)),
        "policyVersion": policy["version"],
        "mlStrategy": "CONFIGURED_DECISION_AWARE_WEIGHTED_MODELS",
        "modelAllocationSource": allocation_source,
        "selectedModels": model_names,
        "modelWeights": selected_weights,
        "componentWeights": {
            "customerBehaviour": policy["customerBehaviourWeight"],
            "peerBehaviour": policy["peerBehaviourWeight"],
            "mlEnsemble": policy["mlEnsembleWeight"],
            "rules": policy["rulesWeight"],
        },
        "thresholds": {
            "low": policy["lowThreshold"],
            "mediumCase": policy["mediumThreshold"],
            "highStr": policy["highThreshold"],
        },
        "continuousPrAuc": float(average_precision_score(evaluation_labels, final_score)),
        "continuousRocAuc": float(roc_auc_score(evaluation_labels, final_score)),
        "averageFinalScore": float(np.mean(final_score)),
        "componentAverages": {
            "customerBehaviour": float(np.mean(customer_score)),
            "peerBehaviour": float(np.mean(peer_score)),
            "mlEnsemble": float(np.mean(ml_score)),
            "rules": float(np.mean(rule_score)),
        },
        "limitations": [
            "Historical snapshots do not persist sanctions-screening outcomes, so the sanctions hard override is not replayed."
        ],
        "caseDecision": case_metrics,
        "strDecision": str_metrics,
        "versusMlEnsemble": {
            "baselineStrategy": ml_baseline.get("strategy"),
            "precisionDelta": case_metrics["precision"] - float(ml_baseline.get("precision", 0.0)),
            "recallDelta": case_metrics["recall"] - float(ml_baseline.get("recall", 0.0)),
            "f1Delta": case_metrics["f1"] - float(ml_baseline.get("f1", 0.0)),
            "balancedAccuracyDelta": case_metrics["balancedAccuracy"]
            - float(ml_baseline.get("balancedAccuracy", 0.0)),
        },
    }


def _policy(hyperparams: Optional[dict[str, Any]]) -> dict[str, Any]:
    values = {
        "version": _raw(hyperparams, "aml.risk.policy.version", "UNVERSIONED_POLICY"),
        "customerBehaviourWeight": _number(hyperparams, "aml.risk.weight.customer_behaviour", 0.24),
        "peerBehaviourWeight": _number(hyperparams, "aml.risk.weight.peer_behaviour", 0.09),
        "mlEnsembleWeight": _number(hyperparams, "aml.risk.weight.ml_ensemble", 0.65),
        "rulesWeight": _number(hyperparams, "aml.risk.weight.rules", 0.02),
        "lowThreshold": _number(hyperparams, "aml.risk.threshold.low", 0.40),
        "mediumThreshold": _number(hyperparams, "aml.risk.threshold.medium", 0.65),
        "highThreshold": _number(hyperparams, "aml.risk.threshold.high", 0.80),
        "customerAmountWeight": _number(hyperparams, "aml.risk.weight.customer_behaviour.amount", 0.55),
        "customerFrequencyWeight": _number(hyperparams, "aml.risk.weight.customer_behaviour.frequency", 0.12),
        "customerTimeGapWeight": _number(hyperparams, "aml.risk.weight.customer_behaviour.time_gap", 0.08),
        "customerNoveltyWeight": _number(hyperparams, "aml.risk.weight.customer_behaviour.novelty", 0.20),
        "customerUnusualHourWeight": _number(hyperparams, "aml.risk.weight.customer_behaviour.unusual_hour", 0.05),
        "peerAmountWeight": _number(hyperparams, "aml.risk.weight.peer_behaviour.amount", 0.60),
        "peerFrequencyWeight": _number(hyperparams, "aml.risk.weight.peer_behaviour.frequency", 0.25),
        "peerTurnoverWeight": _number(hyperparams, "aml.risk.weight.peer_behaviour.expected_turnover", 0.15),
        "reportingThreshold": _number(
            hyperparams,
            "aml.structuring.reporting_threshold",
            _number(hyperparams, "aml.risk.rules.reporting_threshold", 10_000.0),
        ),
        "structuringCount": _integer(hyperparams, "aml.risk.rules.structuring_count_24h", 3),
        "rapidCount10m": _integer(hyperparams, "aml.risk.rules.rapid_tx_count_10m", 5),
        "highCount1h": _integer(hyperparams, "aml.risk.rules.high_tx_count_1h", 10),
        "multipleBeneficiaries1h": _integer(hyperparams, "aml.risk.rules.multi_beneficiary_count_1h", 4),
        "repeatedAmountCount24h": _integer(hyperparams, "aml.risk.rules.repeated_amount_count_24h", 4),
        "highCustomerAmountRatio": _number(hyperparams, "aml.risk.rules.high_customer_amount_ratio", 4.0),
        "extremeCustomerAmountRatio": _number(hyperparams, "aml.risk.rules.extreme_customer_amount_ratio", 8.0),
        "highBalanceRatio": _number(hyperparams, "aml.risk.rules.high_balance_ratio", 0.80),
        "highTurnoverRatio": _number(hyperparams, "aml.risk.rules.high_expected_turnover_ratio", 0.50),
    }
    total = sum(values[key] for key in (
        "customerBehaviourWeight", "peerBehaviourWeight", "mlEnsembleWeight", "rulesWeight"
    ))
    if abs(total - 1.0) > 0.000001:
        raise ValueError("Frozen risk policy component weights must sum to 1.0")
    customer_total = sum(values[key] for key in (
        "customerAmountWeight", "customerFrequencyWeight", "customerTimeGapWeight",
        "customerNoveltyWeight", "customerUnusualHourWeight"
    ))
    peer_total = sum(values[key] for key in (
        "peerAmountWeight", "peerFrequencyWeight", "peerTurnoverWeight"
    ))
    if abs(customer_total - 1.0) > 0.000001 or abs(peer_total - 1.0) > 0.000001:
        raise ValueError("Frozen customer and peer sub-weights must each sum to 1.0")
    if not 0.0 <= values["lowThreshold"] < values["mediumThreshold"] < values["highThreshold"] <= 1.0:
        raise ValueError("Frozen risk thresholds must be ordered within 0.0 and 1.0")
    return values


def _selected_model_weights(
    hyperparams: Optional[dict[str, Any]],
    probabilities: dict[str, np.ndarray],
    tuning_weights: dict[str, float],
) -> tuple[dict[str, float], str]:
    configured = _raw(hyperparams, "aml.risk.ml_model_allocations", {})
    reverse_names = {model_key: research_name for research_name, model_key in MODEL_KEY_BY_RESEARCH_NAME.items()}
    selected: dict[str, float] = {}
    if isinstance(configured, dict):
        for model_key, weight in configured.items():
            research_name = reverse_names.get(str(model_key).strip().upper())
            if research_name in probabilities:
                parsed = _finite_number(weight)
                if parsed is not None and parsed > 0.0:
                    selected[research_name] = parsed
    if selected:
        if "StackedEnsemble" in selected and any(
            name in selected for name in ("XGBoost", "RandomForestClassifier", "ExtraTreesClassifier")
        ):
            raise ValueError(
                "Temporal Stacked Ensemble already combines the three base classifiers. "
                "Select it alone, or choose weighted base classifiers."
            )
        return _normalize(selected), "FROZEN_PRODUCTION_CONFIG"
    fallback = {name: tuning_weights.get(name, 0.0) for name in probabilities}
    return _normalize(fallback), "TUNING_WEIGHTED_RESEARCH_FALLBACK"


def _customer_scores(frame: pd.DataFrame, policy: dict[str, Any]) -> np.ndarray:
    amount = np.maximum.reduce([
        _ratio_deviation(_optional(frame, "amount_vs_last_30_avg")),
        _ratio_deviation(_optional(frame, "amount_vs_last_30_median")),
        _z_deviation(_optional(frame, "amount_z_score_last_30")),
    ])
    frequency = np.maximum.reduce([
        np.clip((_series(frame, "transaction_count_10m") - 1.0) / 5.0, 0.0, 1.0),
        np.clip((_series(frame, "transaction_count_1h") - 1.0) / 10.0, 0.0, 1.0),
        np.clip((_series(frame, "transaction_count_24h") - 1.0) / 40.0, 0.0, 1.0),
    ])
    expected_gap = _optional(frame, "last_30_avg_time_gap_minutes")
    current_gap = _optional(frame, "time_since_previous_transaction_minutes")
    time_gap = np.where(
        np.isfinite(expected_gap) & np.isfinite(current_gap) & (expected_gap > 0.0) & (current_gap < expected_gap),
        np.clip(1.0 - current_gap / np.maximum(expected_gap, 0.000001), 0.0, 1.0),
        0.0,
    )
    novelty = np.clip(
        0.40 * _series(frame, "new_beneficiary")
        + 0.20 * _series(frame, "new_location")
        + 0.20 * _series(frame, "new_channel")
        + 0.20 * _series(frame, "new_device"),
        0.0,
        1.0,
    )
    unusual_hour = np.clip(_series(frame, "unusual_transaction_hour"), 0.0, 1.0)
    unadjusted = np.clip(
        amount * policy["customerAmountWeight"]
        + frequency * policy["customerFrequencyWeight"]
        + time_gap * policy["customerTimeGapWeight"]
        + novelty * policy["customerNoveltyWeight"]
        + unusual_hour * policy["customerUnusualHourWeight"],
        0.0,
        1.0,
    )
    confidence = np.clip(_series(frame, "profile_confidence"), 0.0, 1.0)
    return np.clip(unadjusted * (0.35 + 0.65 * confidence), 0.0, 1.0)


def _peer_scores(frame: pd.DataFrame, policy: dict[str, Any]) -> np.ndarray:
    amount_ratio = _optional(frame, "amount_vs_peer_avg")
    amount_z = _optional(frame, "peer_amount_z_score")
    frequency_percentile = _optional(frame, "peer_frequency_percentile")
    turnover_ratio = _optional(frame, "amount_vs_expected_turnover")
    amount = np.maximum(_ratio_deviation(amount_ratio), _z_deviation(amount_z))
    frequency = np.where(
        np.isfinite(frequency_percentile),
        np.clip((np.clip(frequency_percentile, 0.0, 1.0) - 0.50) / 0.50, 0.0, 1.0),
        0.0,
    )
    turnover = np.where(
        np.isfinite(turnover_ratio) & (turnover_ratio > 0.05),
        np.clip((turnover_ratio - 0.05) / 0.45, 0.0, 1.0),
        0.0,
    )
    completeness = (
        0.60 * (np.isfinite(amount_ratio) | np.isfinite(amount_z))
        + 0.25 * np.isfinite(frequency_percentile)
        + 0.15 * np.isfinite(turnover_ratio)
    )
    baseline_confidence = _peer_baseline_confidence(frame)
    confidence = np.clip(completeness * baseline_confidence, 0.0, 1.0)
    unadjusted = np.clip(
        amount * policy["peerAmountWeight"]
        + frequency * policy["peerFrequencyWeight"]
        + turnover * policy["peerTurnoverWeight"],
        0.0,
        1.0,
    )
    return np.clip(unadjusted * confidence, 0.0, 1.0)


def _peer_baseline_confidence(frame: pd.DataFrame) -> np.ndarray:
    confidence = np.full(len(frame), 0.70, dtype=float)
    peer_columns = [column for column in frame.columns if column.startswith("peer_group_")]
    for column in peer_columns:
        active = _series(frame, column) > 0.5
        group = column.removeprefix("peer_group_").upper()
        if group != "GLOBAL" and group != "UNKNOWN":
            confidence[active] = 1.00 if "_AGE_" in f"_{group}_" else 0.85
    return confidence


def _rule_scores(frame: pd.DataFrame, policy: dict[str, Any]) -> tuple[np.ndarray, np.ndarray]:
    rule_strengths: list[np.ndarray] = []

    def add(condition: np.ndarray, strength: float) -> None:
        rule_strengths.append(np.where(condition, strength, 0.0))

    add(
        (_series(frame, "below_threshold_count_24h") >= policy["structuringCount"])
        & (_series(frame, "below_threshold_amount_sum_24h") >= policy["reportingThreshold"]),
        0.90,
    )
    add(_series(frame, "transaction_count_10m") >= policy["rapidCount10m"], 0.75)
    add(_series(frame, "transaction_count_1h") >= policy["highCount1h"], 0.80)
    add(_series(frame, "unique_beneficiaries_1h") >= policy["multipleBeneficiaries1h"], 0.65)
    add(_series(frame, "repeated_amount_count_24h") >= policy["repeatedAmountCount24h"], 0.60)
    customer_ratio = _optional(frame, "amount_vs_last_30_avg")
    add(np.isfinite(customer_ratio) & (customer_ratio >= policy["extremeCustomerAmountRatio"]), 0.90)
    add(
        np.isfinite(customer_ratio)
        & (customer_ratio >= policy["highCustomerAmountRatio"])
        & (customer_ratio < policy["extremeCustomerAmountRatio"]),
        0.75,
    )
    balance_ratio = _optional(frame, "amount_balance_ratio_v2", "amount_balance_ratio")
    add(np.isfinite(balance_ratio) & (balance_ratio >= policy["highBalanceRatio"]), 0.85)
    new_beneficiary = _series(frame, "new_beneficiary") > 0.5
    new_location = _series(frame, "new_location") > 0.5
    new_device = _series(frame, "new_device") > 0.5
    unusual_hour = _series(frame, "unusual_transaction_hour") > 0.5
    add(new_beneficiary & new_device & unusual_hour, 0.85)
    add(~(new_beneficiary & new_device & unusual_hour) & new_location & new_device, 0.65)
    add(~(new_beneficiary & new_device & unusual_hour) & ~(new_location & new_device) & unusual_hour, 0.25)
    turnover_ratio = _optional(frame, "amount_vs_expected_turnover")
    add(np.isfinite(turnover_ratio) & (turnover_ratio >= policy["highTurnoverRatio"]), 0.80)
    remaining = np.ones(len(frame), dtype=float)
    for strength in rule_strengths:
        remaining *= 1.0 - strength
    return np.clip(1.0 - remaining, 0.0, 1.0), np.zeros(len(frame), dtype=bool)


def _decision_metrics(labels: np.ndarray, score: np.ndarray, prediction: np.ndarray) -> dict[str, Any]:
    matrix = confusion_matrix(labels, prediction, labels=[0, 1])
    predicted_count = int(np.sum(prediction))
    return {
        "accuracy": float(accuracy_score(labels, prediction)),
        "balancedAccuracy": float(balanced_accuracy_score(labels, prediction)),
        "precision": float(precision_score(labels, prediction, zero_division=0)),
        "recall": float(recall_score(labels, prediction, zero_division=0)),
        "f1": float(f1_score(labels, prediction, zero_division=0)),
        "trueNegative": int(matrix[0, 0]),
        "falsePositive": int(matrix[0, 1]),
        "falseNegative": int(matrix[1, 0]),
        "truePositive": int(matrix[1, 1]),
        "decisionCount": predicted_count,
        "decisionsPer1000": float(predicted_count * 1000.0 / max(1, len(labels))),
        "fraudCaptured": int(matrix[1, 1]),
        "fraudMissed": int(matrix[1, 0]),
    }


def _ratio_deviation(values: np.ndarray) -> np.ndarray:
    return np.where(
        np.isfinite(values) & (values > 1.0),
        np.clip(1.0 - np.exp(-(values - 1.0) / 2.5), 0.0, 1.0),
        0.0,
    )


def _z_deviation(values: np.ndarray) -> np.ndarray:
    return np.where(
        np.isfinite(values) & (values > 0.0),
        np.clip(1.0 - np.exp(-values / 3.0), 0.0, 1.0),
        0.0,
    )


def _optional(frame: pd.DataFrame, *names: str) -> np.ndarray:
    for name in names:
        if name not in frame:
            continue
        values = pd.to_numeric(frame[name], errors="coerce").to_numpy(dtype=float)
        missing_name = f"{name}_missing"
        if missing_name in frame:
            values = np.where(_series(frame, missing_name) > 0.5, np.nan, values)
        return values
    return np.full(len(frame), np.nan, dtype=float)


def _series(frame: pd.DataFrame, name: str) -> np.ndarray:
    if name not in frame:
        return np.zeros(len(frame), dtype=float)
    return pd.to_numeric(frame[name], errors="coerce").fillna(0.0).to_numpy(dtype=float)


def _normalize(weights: dict[str, float]) -> dict[str, float]:
    total = sum(value for value in weights.values() if np.isfinite(value) and value > 0.0)
    if total <= 0.0:
        equal = 1.0 / max(1, len(weights))
        return {name: equal for name in weights}
    return {name: float(value / total) for name, value in weights.items() if value > 0.0}


def _raw(hyperparams: Optional[dict[str, Any]], key: str, default: Any) -> Any:
    return default if not hyperparams or key not in hyperparams else hyperparams[key]


def _number(hyperparams: Optional[dict[str, Any]], key: str, default: float) -> float:
    parsed = _finite_number(_raw(hyperparams, key, default))
    return default if parsed is None else parsed


def _integer(hyperparams: Optional[dict[str, Any]], key: str, default: int) -> int:
    try:
        return int(_raw(hyperparams, key, default))
    except (TypeError, ValueError):
        return default


def _finite_number(value: Any) -> Optional[float]:
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        return None
    return parsed if np.isfinite(parsed) else None
