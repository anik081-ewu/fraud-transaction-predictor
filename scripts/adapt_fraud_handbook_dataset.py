from __future__ import annotations

import argparse
import csv
import hashlib
import json
from datetime import datetime
from pathlib import Path

import pandas as pd


OUTPUT_COLUMNS = [
    "TransactionID",
    "AccountID",
    "TransactionAmount",
    "TransactionDate",
    "TransactionType",
    "Location",
    "Channel",
    "CustomerAge",
    "CustomerOccupation",
    "LoginAttempts",
    "AccountBalance",
    "PreviousTransactionDate",
    "FraudLabel",
]
OCCUPATIONS = (
    "Engineer",
    "Doctor",
    "Teacher",
    "Student",
    "Retired",
    "Business Owner",
    "Banker",
    "Government Employee",
)


def customer_profile(customer_id: int) -> tuple[int, str, float]:
    occupation = OCCUPATIONS[customer_id % len(OCCUPATIONS)]
    if occupation == "Student":
        age = 18 + customer_id % 11
    elif occupation == "Retired":
        age = 58 + customer_id % 22
    else:
        age = 24 + customer_id % 44
    opening_balance = 20_000.0 + (customer_id * 7919) % 180_000
    return age, occupation, opening_balance


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Adapt the Fraud Detection Handbook data without changing its fraud labels or chronology."
    )
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--customers", type=int, default=750)
    parser.add_argument("--days", type=int, default=183)
    args = parser.parse_args()
    if args.customers < 1 or args.days < 1:
        raise ValueError("customers and days must be positive")

    source_files = sorted(args.source_dir.glob("*.pkl"))[: args.days]
    if not source_files:
        raise ValueError(f"No daily pickle files found in {args.source_dir}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    previous_by_account: dict[str, datetime] = {}
    balance_by_account: dict[str, float] = {}
    total_rows = 0
    fraud_rows = 0
    minimum_date: datetime | None = None
    maximum_date: datetime | None = None

    with args.output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=OUTPUT_COLUMNS)
        writer.writeheader()
        for source_file in source_files:
            frame = pd.read_pickle(source_file)
            frame = frame.loc[frame["CUSTOMER_ID"].astype(int) < args.customers]
            frame = frame.sort_values(["TX_DATETIME", "TRANSACTION_ID"], kind="stable")
            for row in frame.itertuples(index=False):
                customer_number = int(row.CUSTOMER_ID)
                account_id = f"FDB-{customer_number:05d}"
                transaction_date = row.TX_DATETIME.to_pydatetime()
                age, occupation, opening_balance = customer_profile(customer_number)
                balance = balance_by_account.get(account_id, opening_balance)
                amount = float(row.TX_AMOUNT)
                balance = max(100.0, balance - amount)
                previous = previous_by_account.get(account_id)
                writer.writerow({
                    "TransactionID": f"FDB-{int(row.TRANSACTION_ID):09d}",
                    "AccountID": account_id,
                    "TransactionAmount": f"{amount:.2f}",
                    "TransactionDate": transaction_date.isoformat(timespec="seconds"),
                    "TransactionType": "CARD_PURCHASE",
                    "Location": f"TERMINAL-{int(row.TERMINAL_ID):05d}",
                    "Channel": "POS",
                    "CustomerAge": age,
                    "CustomerOccupation": occupation,
                    "LoginAttempts": 1,
                    "AccountBalance": f"{balance:.2f}",
                    "PreviousTransactionDate": "" if previous is None else previous.isoformat(timespec="seconds"),
                    "FraudLabel": int(row.TX_FRAUD),
                })
                previous_by_account[account_id] = transaction_date
                balance_by_account[account_id] = balance
                total_rows += 1
                fraud_rows += int(row.TX_FRAUD)
                minimum_date = transaction_date if minimum_date is None else min(minimum_date, transaction_date)
                maximum_date = transaction_date if maximum_date is None else max(maximum_date, transaction_date)

    summary = {
        "source": "Fraud Detection Handbook simulated-data-transformed",
        "adaptation": (
            "Selected complete customer histories; preserved transaction chronology, amounts, terminal identity, "
            "and original TX_FRAUD labels. TERMINAL_ID is represented as Location so the current application "
            "can derive location novelty without label leakage."
        ),
        "rows": total_rows,
        "fraudRows": fraud_rows,
        "fraudRate": fraud_rows / max(total_rows, 1),
        "customers": len(previous_by_account),
        "days": len(source_files),
        "minimumDate": None if minimum_date is None else minimum_date.isoformat(),
        "maximumDate": None if maximum_date is None else maximum_date.isoformat(),
        "sha256": sha256(args.output),
    }
    args.output.with_suffix(".summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
