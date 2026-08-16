from __future__ import annotations

import hashlib


LOCATION_HASH_BUCKETS = 128
LEGACY_LOCATION_PREFIX = "Location_"
LOCATION_HASH_PREFIX = "LocationHashBucket_"


def location_bucket(value: object) -> int:
    normalized = str(value or "").strip().casefold().encode("utf-8")
    digest = hashlib.sha256(normalized).digest()
    return int.from_bytes(digest[:8], byteorder="big", signed=False) % LOCATION_HASH_BUCKETS


def location_bucket_column(value: object) -> str:
    return f"{LOCATION_HASH_PREFIX}{location_bucket(value):03d}"


def compact_feature_name(name: str) -> str:
    if name.startswith(LEGACY_LOCATION_PREFIX):
        return location_bucket_column(name.removeprefix(LEGACY_LOCATION_PREFIX))
    return name


def compact_feature_values(features: dict[str, float]) -> dict[str, float]:
    compacted: dict[str, float] = {}
    for raw_name, raw_value in features.items():
        name = compact_feature_name(str(raw_name))
        value = float(raw_value)
        compacted[name] = max(compacted.get(name, 0.0), value) if name.startswith(LOCATION_HASH_PREFIX) else value
    return compacted
