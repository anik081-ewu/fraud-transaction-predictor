"""Deprecated raw-transaction feature compatibility for migration and offline research only."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Optional

import numpy as np
import pandas as pd

from app.feature_compaction import LOCATION_HASH_BUCKETS, location_bucket, location_bucket_column


def _safe_div(n: float, d: float) -> float:
    if d is None or d == 0:
        return 0.0
    return float(n) / float(d)


def build_training_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Builds training-time features from historical transactions DataFrame.
    Expected columns (lowercase): transaction_id, account_id, transaction_amount, transaction_type, transaction_date,
    location, channel, login_attempts, account_balance, optional customer_occupation.
    """
    data = df.copy()
    data["__feature_row_id"] = np.arange(len(data))
    data["transaction_date"] = pd.to_datetime(data["transaction_date"], errors="coerce")
    data = data.dropna(subset=["transaction_date"])
    data = data.sort_values(["account_id", "transaction_date"]).reset_index(drop=True)

    data["prev_transaction_date"] = data.groupby("account_id")["transaction_date"].shift(1)
    data["time_diff_hours"] = (
        (data["transaction_date"] - data["prev_transaction_date"]).dt.total_seconds() / 3600.0
    ).fillna(0.0)

    data["amount_balance_ratio"] = data.apply(
        lambda r: _safe_div(r["transaction_amount"], r["account_balance"]), axis=1
    )

    data["transaction_hour"] = data["transaction_date"].dt.hour.astype(int)
    data["is_night"] = ((data["transaction_hour"] >= 0) & (data["transaction_hour"] <= 5)).astype(int)

    data["high_login_attempts"] = (data["login_attempts"].fillna(0).astype(float) >= 3).astype(int)
    data["transaction_dayofweek"] = data["transaction_date"].dt.dayofweek.astype(int)
    data["is_weekend"] = (data["transaction_dayofweek"] >= 5).astype(int)
    data["login_attempt_risk"] = (data["login_attempts"].fillna(0).astype(float) / 10.0).clip(0, 1)

    grp = data.groupby("account_id")
    data["user_txn_count"] = grp.cumcount().astype(int)
    data["user_avg_amount"] = grp["transaction_amount"].expanding().mean().reset_index(level=0, drop=True).shift(1)
    data["user_max_amount"] = grp["transaction_amount"].expanding().max().reset_index(level=0, drop=True).shift(1)
    data["user_amount_std"] = grp["transaction_amount"].expanding().std(ddof=0).reset_index(level=0, drop=True).shift(1)

    data["user_avg_amount"] = data["user_avg_amount"].fillna(0.0)
    data["user_max_amount"] = data["user_max_amount"].fillna(0.0)
    data["user_amount_std"] = data["user_amount_std"].fillna(0.0)

    data["amount_vs_user_avg"] = data.apply(lambda r: _safe_div(r["transaction_amount"], r["user_avg_amount"]), axis=1)
    data["amount_vs_user_max"] = data.apply(lambda r: _safe_div(r["transaction_amount"], r["user_max_amount"]), axis=1)
    data["amount_z_score_user"] = data.apply(
        lambda r: 0.0 if r["user_amount_std"] == 0 else (float(r["transaction_amount"]) - float(r["user_avg_amount"])) / float(r["user_amount_std"]),
        axis=1,
    )

    # Rolling time-window features (by event time, excluding current row via closed="left")
    rolling_7d = (
        data.groupby("account_id")
        .rolling("7D", on="transaction_date", closed="left")["transaction_amount"]
        .mean()
        .reset_index(level=0, drop=True)
        .sort_index()
    )
    rolling_30d = (
        data.groupby("account_id")
        .rolling("30D", on="transaction_date", closed="left")["transaction_amount"]
        .mean()
        .reset_index(level=0, drop=True)
        .sort_index()
    )
    # Avoid index alignment issues by assigning by position
    data["rolling_7d_avg_amount"] = rolling_7d.to_numpy()
    data["rolling_30d_avg_amount"] = rolling_30d.to_numpy()
    data["rolling_7d_avg_amount"] = data["rolling_7d_avg_amount"].fillna(0.0)
    data["rolling_30d_avg_amount"] = data["rolling_30d_avg_amount"].fillna(0.0)

    data["amount_vs_rolling_7d_avg"] = data.apply(
        lambda r: _safe_div(r["transaction_amount"], r["rolling_7d_avg_amount"]), axis=1
    )
    data["amount_vs_rolling_30d_avg"] = data.apply(
        lambda r: _safe_div(r["transaction_amount"], r["rolling_30d_avg_amount"]), axis=1
    )

    data["prev_location"] = data.groupby("account_id")["location"].shift(1)
    data["location_changed"] = (data["prev_location"].fillna("") != data["location"].fillna("")).astype(int)

    if "customer_occupation" not in data.columns:
        data["customer_occupation"] = ""
    data["customer_occupation"] = data["customer_occupation"].fillna("").astype(str)

    base_features = data[
        [
            "time_diff_hours",
            "amount_balance_ratio",
            "transaction_hour",
            "is_night",
            "high_login_attempts",
            "user_avg_amount",
            "user_max_amount",
            "user_txn_count",
            "amount_vs_user_avg",
            "amount_vs_user_max",
            "user_amount_std",
            "amount_z_score_user",
            "transaction_dayofweek",
            "is_weekend",
            "login_attempt_risk",
            "rolling_7d_avg_amount",
            "rolling_30d_avg_amount",
            "amount_vs_rolling_7d_avg",
            "amount_vs_rolling_30d_avg",
            "location_changed",
        ]
    ].copy()

    cats = data[["transaction_type", "channel", "customer_occupation"]].copy()
    cats.columns = ["TransactionType", "Channel", "CustomerOccupation"]
    dummies = pd.get_dummies(cats, prefix=cats.columns, dtype=np.float32)
    location_buckets = pd.get_dummies(
        data["location"].map(location_bucket),
        prefix="LocationHashBucket",
        dtype=np.float32,
    )
    location_buckets.columns = [f"LocationHashBucket_{int(column.rsplit('_', 1)[1]):03d}" for column in location_buckets]

    features = pd.concat(
        [base_features.reset_index(drop=True), dummies.reset_index(drop=True), location_buckets.reset_index(drop=True)],
        axis=1,
    )
    features.index = data["__feature_row_id"].to_numpy()
    return features.astype(np.float32)


@dataclass(frozen=True)
class FeatureSummary:
    amountVsUserAvg: float
    amountVsRolling7dAvg: float
    amountVsRolling30dAvg: float
    locationChanged: bool
    highLoginAttempts: bool


def build_single_features(
    transaction: dict,
    customer: dict,
    account_profile: dict,
) -> tuple[pd.DataFrame, FeatureSummary, list[str]]:
    """
    Build a single-row DataFrame with the same feature logic used in training,
    using account_profile from Spring Boot for historical aggregates.
    """
    txn_amount = float(transaction.get("transactionAmount", 0.0) or 0.0)
    txn_balance = float(transaction.get("accountBalance", 0.0) or 0.0)
    login_attempts = int(transaction.get("loginAttempts", 0) or 0)
    txn_date: datetime = transaction["transactionDate"]

    prev_date = account_profile.get("previousTransactionDate")
    time_diff_hours = 0.0
    if prev_date:
        time_diff_hours = (txn_date - prev_date).total_seconds() / 3600.0

    amount_balance_ratio = _safe_div(txn_amount, txn_balance)
    transaction_hour = int(txn_date.hour)
    is_night = int(0 <= transaction_hour <= 5)
    high_login_attempts = int(login_attempts >= 3)
    day_of_week = int(txn_date.weekday())
    is_weekend = int(day_of_week >= 5)
    login_attempt_risk = float(min(max(login_attempts / 10.0, 0.0), 1.0))

    user_avg_amount = float(account_profile.get("userAvgAmount") or 0.0)
    user_max_amount = float(account_profile.get("userMaxAmount") or 0.0)
    user_txn_count = float(account_profile.get("userTxnCount") or 0.0)
    user_amount_std = float(account_profile.get("userAmountStd") or 0.0)

    rolling_7d_avg_amount = float(account_profile.get("rolling7dAvgAmount") or 0.0)
    rolling_30d_avg_amount = float(account_profile.get("rolling30dAvgAmount") or 0.0)

    amount_vs_user_avg = _safe_div(txn_amount, user_avg_amount)
    amount_vs_user_max = _safe_div(txn_amount, user_max_amount)
    amount_z_score_user = 0.0 if user_amount_std == 0 else (txn_amount - user_avg_amount) / user_amount_std
    amount_vs_rolling_7d_avg = _safe_div(txn_amount, rolling_7d_avg_amount)
    amount_vs_rolling_30d_avg = _safe_div(txn_amount, rolling_30d_avg_amount)

    prev_loc = (account_profile.get("previousLocation") or "") if account_profile else ""
    loc = transaction.get("location") or ""
    location_changed = int(str(prev_loc).strip().lower() != str(loc).strip().lower() and str(prev_loc).strip() != "")

    base = {
        "time_diff_hours": float(time_diff_hours),
        "amount_balance_ratio": float(amount_balance_ratio),
        "transaction_hour": float(transaction_hour),
        "is_night": float(is_night),
        "high_login_attempts": float(high_login_attempts),
        "user_avg_amount": float(user_avg_amount),
        "user_max_amount": float(user_max_amount),
        "user_txn_count": float(user_txn_count),
        "amount_vs_user_avg": float(amount_vs_user_avg),
        "amount_vs_user_max": float(amount_vs_user_max),
        "user_amount_std": float(user_amount_std),
        "amount_z_score_user": float(amount_z_score_user),
        "transaction_dayofweek": float(day_of_week),
        "is_weekend": float(is_weekend),
        "login_attempt_risk": float(login_attempt_risk),
        "rolling_7d_avg_amount": float(rolling_7d_avg_amount),
        "rolling_30d_avg_amount": float(rolling_30d_avg_amount),
        "amount_vs_rolling_7d_avg": float(amount_vs_rolling_7d_avg),
        "amount_vs_rolling_30d_avg": float(amount_vs_rolling_30d_avg),
        "location_changed": float(location_changed),
    }

    # One-hot inputs
    tx_type = str(transaction.get("transactionType") or "")
    channel = str(transaction.get("channel") or "")
    occupation = str(customer.get("customerOccupation") or "")

    cat_map = {
        f"TransactionType_{tx_type}": 1.0,
        location_bucket_column(loc): 1.0,
        f"Channel_{channel}": 1.0,
        f"CustomerOccupation_{occupation}": 1.0,
    }

    row = {**base, **cat_map}
    features = pd.DataFrame([row], dtype=np.float32)

    reasons: list[str] = []
    if amount_vs_user_avg >= 3:
        reasons.append("Transaction amount is much higher than user's average")
    if amount_vs_rolling_7d_avg >= 3:
        reasons.append("Transaction amount is much higher than recent 7-day average")
    if location_changed == 1:
        reasons.append("Transaction location changed from previous transaction")
    if high_login_attempts == 1:
        reasons.append("High login attempts detected")

    summary = FeatureSummary(
        amountVsUserAvg=float(amount_vs_user_avg),
        amountVsRolling7dAvg=float(amount_vs_rolling_7d_avg),
        amountVsRolling30dAvg=float(amount_vs_rolling_30d_avg),
        locationChanged=bool(location_changed),
        highLoginAttempts=bool(high_login_attempts),
    )

    # Extra details to help explain anomalies beyond the basic reasons list
    extra = {
        "timeDiffHours": float(time_diff_hours),
        "transactionHour": int(transaction_hour),
        "isNight": bool(is_night),
        "transactionDayOfWeek": int(day_of_week),
        "isWeekend": bool(is_weekend),
        "amountBalanceRatio": float(amount_balance_ratio),
        "amountVsUserMax": float(amount_vs_user_max),
        "userAvgAmount": float(user_avg_amount),
        "userMaxAmount": float(user_max_amount),
        "userAmountStd": float(user_amount_std),
        "amountZScoreUser": float(amount_z_score_user),
        "userTxnCount": float(user_txn_count),
        "rolling7dAvgAmount": float(rolling_7d_avg_amount),
        "rolling30dAvgAmount": float(rolling_30d_avg_amount),
        "transactionType": tx_type,
        "location": loc,
        "channel": channel,
        "customerOccupation": occupation,
    }
    return features, summary, reasons, extra
