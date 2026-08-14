from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from datetime import datetime
from pathlib import Path


REQUIRED_COLUMNS = {
    "TransactionID", "AccountID", "TransactionAmount", "TransactionDate",
    "TransactionType", "Location", "Channel", "CustomerAge",
    "CustomerOccupation", "LoginAttempts", "AccountBalance",
    "PreviousTransactionDate", "FraudLabel",
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("dataset", type=Path)
    args = parser.parse_args()
    with args.dataset.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        missing = REQUIRED_COLUMNS.difference(reader.fieldnames or [])
        if missing:
            raise ValueError(f"Missing columns: {sorted(missing)}")
        rows = list(reader)

    ids = [row["TransactionID"] for row in rows]
    if len(ids) != len(set(ids)):
        raise ValueError("Transaction IDs are not unique")
    dates = [datetime.fromisoformat(row["TransactionDate"]) for row in rows]
    if dates != sorted(dates):
        raise ValueError("Rows are not sorted oldest-first")
    labels = [int(row["FraudLabel"]) for row in rows]
    if set(labels) != {0, 1}:
        raise ValueError("FraudLabel must contain both 0 and 1")

    previous_by_account: dict[str, str] = {}
    for row in rows:
        expected = previous_by_account.get(row["AccountID"], "")
        if row["PreviousTransactionDate"] != expected:
            raise ValueError(f"PreviousTransactionDate mismatch for {row['TransactionID']}")
        previous_by_account[row["AccountID"]] = row["TransactionDate"]

    partitions = {}
    for percentage in (10, 25, 50, 100):
        size = int(len(rows) * percentage / 100)
        split = int(size * 0.8)
        training = Counter(labels[:split])
        evaluation = Counter(labels[split:size])
        if set(training) != {0, 1} or set(evaluation) != {0, 1}:
            raise ValueError(f"Partition {percentage}% does not contain both classes in train/evaluation")
        partitions[str(percentage)] = {"rows": size, "trainingLabels": training, "evaluationLabels": evaluation}

    print(json.dumps({
        "rows": len(rows),
        "accounts": len(previous_by_account),
        "labels": Counter(labels),
        "minimumDate": dates[0].isoformat(),
        "maximumDate": dates[-1].isoformat(),
        "partitions": partitions,
        "validation": "PASSED",
    }, indent=2))


if __name__ == "__main__":
    main()
