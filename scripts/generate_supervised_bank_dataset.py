from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import random
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path


SEED = 20260812
OUTPUT_COLUMNS = [
    "TransactionID", "AccountID", "TransactionAmount", "TransactionDate",
    "TransactionType", "Location", "Channel", "CustomerAge",
    "CustomerOccupation", "LoginAttempts", "AccountBalance",
    "PreviousTransactionDate", "FraudLabel",
]
OCCUPATIONS = ["Engineer", "Doctor", "Teacher", "Student", "Retired", "Business Owner", "Banker", "Government Employee"]
LOCATIONS = ["Dhaka", "Chattogram", "Sylhet", "Rajshahi", "Khulna", "Barishal", "Rangpur", "Mymensingh", "Dubai", "London", "Singapore"]
CHANNELS = ["ONLINE", "MOBILE", "ATM", "BRANCH", "POS"]


@dataclass
class AccountProfile:
    account_id: str
    age: int
    occupation: str
    home_location: str
    preferred_channel: str
    typical_amount: float
    balance: float


def parse_label(value: str) -> int:
    return 1 if value.strip().lower() in {"1", "true", "yes"} else 0


def reservoir_append(reservoir: list[dict[str, str]], row: dict[str, str], seen: int, limit: int, rng: random.Random) -> None:
    if len(reservoir) < limit:
        reservoir.append(row)
        return
    replacement = rng.randrange(seen)
    if replacement < limit:
        reservoir[replacement] = row


def read_source(source: Path, normal_limit: int, rng: random.Random) -> tuple[list[dict[str, str]], list[dict[str, str]], int]:
    fraud: list[dict[str, str]] = []
    normal: list[dict[str, str]] = []
    normal_seen = 0
    with source.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            if parse_label(row["Class"]) == 1:
                fraud.append(row)
            else:
                normal_seen += 1
                reservoir_append(normal, row, normal_seen, normal_limit, rng)
    if not fraud or not normal:
        raise ValueError("Source must contain both legitimate and fraud records")
    return fraud, normal, normal_seen


def make_profiles(count: int, rng: random.Random) -> list[AccountProfile]:
    profiles = []
    for number in range(1, count + 1):
        occupation = rng.choice(OCCUPATIONS)
        age_range = (18, 28) if occupation == "Student" else (58, 79) if occupation == "Retired" else (24, 67)
        profiles.append(AccountProfile(
            account_id=f"AC{number:05d}",
            age=rng.randint(*age_range),
            occupation=occupation,
            home_location=rng.choice(LOCATIONS[:8]),
            preferred_channel=rng.choice(CHANNELS),
            typical_amount=round(math.exp(rng.uniform(math.log(80), math.log(1800))), 2),
            balance=round(rng.uniform(8_000, 180_000), 2),
        ))
    return profiles


def source_risk(row: dict[str, str]) -> float:
    columns = ("V1", "V2", "V3", "V4", "V10", "V12", "V14", "V17")
    return sum(abs(float(row[name])) for name in columns) / len(columns)


def synthetic_rows(
    fraud_source: list[dict[str, str]],
    normal_source: list[dict[str, str]],
    row_count: int,
    fraud_rate: float,
    account_count: int,
    rng: random.Random,
) -> list[dict[str, object]]:
    profiles = make_profiles(account_count, rng)
    fraud_count = round(row_count * fraud_rate)
    labels = [1] * fraud_count + [0] * (row_count - fraud_count)
    rng.shuffle(labels)
    start = datetime(2023, 1, 1, 0, 0)
    duration_seconds = int((datetime(2025, 12, 31, 23, 59) - start).total_seconds())
    generated: list[dict[str, object]] = []

    for index, label in enumerate(labels):
        source = rng.choice(fraud_source if label else normal_source)
        profile = rng.choice(profiles)
        source_amount = max(1.0, float(source["Amount"]))
        risk = source_risk(source)
        timestamp = start + timedelta(seconds=rng.randrange(duration_seconds))
        subtle_fraud = label == 1 and rng.random() < 0.15
        noisy_legitimate = label == 0 and rng.random() < 0.03
        risky = (label == 1 and not subtle_fraud) or noisy_legitimate

        baseline = 0.55 * profile.typical_amount + 0.45 * source_amount
        amount = baseline * math.exp(rng.normalvariate(0, 0.38))
        if risky and rng.random() < 0.55:
            amount *= rng.uniform(2.5, min(9.0, 3.5 + risk))
        amount = round(max(2.0, min(amount, 75_000.0)), 2)

        location = profile.home_location
        channel = profile.preferred_channel
        login_attempts = rng.choices([1, 2, 3], weights=[78, 19, 3])[0]
        transaction_type = rng.choices(["TRANSFER", "DEBIT", "CREDIT", "CASH_WITHDRAWAL"], weights=[38, 35, 17, 10])[0]
        if risky:
            if rng.random() < 0.62:
                location = rng.choice([place for place in LOCATIONS if place != profile.home_location])
            if rng.random() < 0.55:
                channel = rng.choice(["ONLINE", "ATM", "MOBILE"])
            if rng.random() < 0.42:
                login_attempts = rng.randint(3, 7)
            if rng.random() < 0.30:
                timestamp = timestamp.replace(hour=rng.choice([0, 1, 2, 3, 4, 23]), minute=rng.randrange(60))

        generated.append({
            "TransactionID": f"SUP-{index + 1:08d}",
            "AccountID": profile.account_id,
            "TransactionAmount": amount,
            "TransactionDate": timestamp,
            "TransactionType": transaction_type,
            "Location": location,
            "Channel": channel,
            "CustomerAge": profile.age,
            "CustomerOccupation": profile.occupation,
            "LoginAttempts": login_attempts,
            "AccountBalance": 0.0,
            "PreviousTransactionDate": None,
            "FraudLabel": label,
        })

    generated.sort(key=lambda row: (row["TransactionDate"], row["TransactionID"]))
    previous_by_account: dict[str, datetime] = {}
    balance_by_account = {profile.account_id: profile.balance for profile in profiles}
    for index, row in enumerate(generated, start=1):
        timestamp = row["TransactionDate"]
        account_id = str(row["AccountID"])
        previous = previous_by_account.get(account_id)
        amount = float(row["TransactionAmount"])
        if row["TransactionType"] == "CREDIT":
            balance_by_account[account_id] += amount
        else:
            balance_by_account[account_id] = max(100.0, balance_by_account[account_id] - amount)
        row["TransactionID"] = f"SUP-{index:08d}"
        row["PreviousTransactionDate"] = previous
        row["AccountBalance"] = round(balance_by_account[account_id], 2)
        previous_by_account[account_id] = timestamp  # type: ignore[assignment]
    return generated


def write_synthetic(path: Path, rows: list[dict[str, object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_COLUMNS)
        writer.writeheader()
        for row in rows:
            serialized = dict(row)
            for key in ("TransactionDate", "PreviousTransactionDate"):
                value = serialized[key]
                serialized[key] = "" if value is None else value.strftime("%Y-%m-%dT%H:%M:%S")  # type: ignore[union-attr]
            writer.writerow(serialized)


def write_reference(path: Path, fraud: list[dict[str, str]], normal: list[dict[str, str]], rng: random.Random) -> None:
    rows = fraud + normal
    rng.shuffle(rows)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--reference-output", type=Path, required=True)
    parser.add_argument("--rows", type=int, default=25_000)
    parser.add_argument("--fraud-rate", type=float, default=0.05)
    parser.add_argument("--accounts", type=int, default=600)
    args = parser.parse_args()
    rng = random.Random(SEED)
    fraud, normal, source_normal_count = read_source(args.source, 9_508, rng)
    write_reference(args.reference_output, fraud, normal, rng)
    rows = synthetic_rows(fraud, normal, args.rows, args.fraud_rate, args.accounts, rng)
    write_synthetic(args.output, rows)
    summary = {
        "seed": SEED,
        "sourceFraudRows": len(fraud),
        "sourceLegitimateRows": source_normal_count,
        "syntheticRows": len(rows),
        "syntheticFraudRows": sum(int(row["FraudLabel"]) for row in rows),
        "syntheticLegitimateRows": sum(1 - int(row["FraudLabel"]) for row in rows),
        "accounts": len({row["AccountID"] for row in rows}),
        "minimumDate": min(row["TransactionDate"] for row in rows).isoformat(),
        "maximumDate": max(row["TransactionDate"] for row in rows).isoformat(),
        "outputSha256": sha256(args.output),
    }
    args.output.with_suffix(".summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
