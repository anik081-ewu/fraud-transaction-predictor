from __future__ import annotations

import unittest
from datetime import datetime

import pandas as pd

from app.feature_compaction import LOCATION_HASH_BUCKETS, compact_feature_name, compact_feature_values
from app.legacy.feature_engineering import build_single_features, build_training_features


class LegacyFeatureEngineeringTest(unittest.TestCase):
    def test_high_cardinality_locations_use_bounded_hash_buckets(self) -> None:
        row_count = 1_000
        transactions = pd.DataFrame({
            "transaction_id": [f"TX-{index}" for index in range(row_count)],
            "account_id": [f"AC-{index % 20}" for index in range(row_count)],
            "transaction_amount": [100.0 + index for index in range(row_count)],
            "transaction_type": ["CARD_PURCHASE"] * row_count,
            "transaction_date": pd.date_range("2025-01-01", periods=row_count, freq="min"),
            "location": [f"TERMINAL-{index:05d}" for index in range(row_count)],
            "channel": ["POS"] * row_count,
            "login_attempts": [1] * row_count,
            "account_balance": [10_000.0] * row_count,
            "customer_occupation": ["Engineer"] * row_count,
        })

        features = build_training_features(transactions)
        location_columns = [column for column in features if column.startswith("Location")]

        self.assertLessEqual(len(location_columns), LOCATION_HASH_BUCKETS)
        self.assertLess(len(features.columns), 200)
        self.assertEqual("float32", str(features.dtypes.iloc[0]))

    def test_single_transaction_uses_same_location_hash_schema(self) -> None:
        features, _, _, _ = build_single_features(
            {
                "transactionAmount": 100.0,
                "accountBalance": 1_000.0,
                "loginAttempts": 1,
                "transactionDate": datetime(2025, 1, 2, 10, 30),
                "transactionType": "CARD_PURCHASE",
                "location": "TERMINAL-01234",
                "channel": "POS",
            },
            {"customerOccupation": "Engineer"},
            {},
        )

        location_columns = [column for column in features if column.startswith("LocationHashBucket_")]
        self.assertEqual(1, len(location_columns))
        self.assertEqual(1.0, float(features.iloc[0][location_columns[0]]))

    def test_existing_terminal_one_hot_features_are_compacted(self) -> None:
        first = compact_feature_name("Location_TERMINAL-00001")
        second = compact_feature_name("Location_TERMINAL-00002")
        compacted = compact_feature_values({
            "current_amount": 100.0,
            "Location_TERMINAL-00001": 1.0,
            "Location_TERMINAL-00002": 0.0,
        })

        self.assertTrue(first.startswith("LocationHashBucket_"))
        self.assertTrue(second.startswith("LocationHashBucket_"))
        self.assertEqual(100.0, compacted["current_amount"])
        self.assertLessEqual(len(compacted), 3)


if __name__ == "__main__":
    unittest.main()
