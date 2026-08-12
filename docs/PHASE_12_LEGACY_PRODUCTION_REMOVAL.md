# Phase 12: Legacy Production-Code Removal

## Purpose

Phase 12 completes the production cutover to persisted, versioned features. Production no longer calls the Python v1 raw-transaction endpoint and no longer permits LOF, kernel One-Class SVM, Elliptic Envelope, or PCA Reconstruction to influence alerts or cases.

The production decision hierarchy is now:

1. Promoted Half-Space Trees champion for the exact segment.
2. Promoted GLOBAL Half-Space Trees champion.
3. Isolation Forest batch fallback.
4. Auditable `NORMAL / ALLOW_AND_LOG` operational fallback if the v2 ML service is unavailable.

## Database migration

Run these scripts in SSMS:

1. `database/phase_12_legacy_production_removal.sql`
2. `database/verify_phase_12_legacy_production_removal.sql`

The migration changes every active anomaly configuration to a one-model Isolation Forest fallback with a threshold of one. It also records the legacy API migration deadline in `app_config`.

## Removed production code

- Spring `FraudDetectionClient`, `RealFraudDetectionClient`, and `PlaceholderFraudDetectionClient`.
- The Spring raw-transaction prediction method and automatic fallback to `/api/v1/fraud/predict`.
- LOF and One-Class SVM from production anomaly configuration.
- Python production-v2 access to any batch model except Isolation Forest.
- Python duplicate feature engineering from the production module path.

The exact persisted Spring feature vector is the only input accepted by `/api/v2/fraud/predict`.

## Temporary migration APIs

The following Python APIs remain temporarily for offline model comparison, the existing training UI, and migration clients:

- `POST /api/v1/fraud/predict`
- `POST /api/v1/fraud/compare`
- `POST /api/v1/models/train`
- `POST /api/v1/models/score-percentiles`
- `GET /api/v1/models/hyperparams`
- `GET /api/v1/models/artifacts-info`

They are marked deprecated in OpenAPI and return `Deprecation`, standards-formatted `Sunset`, and successor `Link` headers. The default sunset is `2026-12-31T23:59:59Z`.

Python runtime settings:

```text
LEGACY_API_ENABLED=true
LEGACY_API_SUNSET_AT=2026-12-31T23:59:59Z
```

After the deadline, these routes return HTTP 410. Their feature builder now lives only in `app/legacy/feature_engineering.py`; it is not imported by the v2 prediction path. The environment values should match the governance values stored in `app_config`.

## Offline research remains available

LOF, kernel One-Class SVM, Elliptic Envelope, and PCA Reconstruction remain available only for partition training and model-comparison reports. This preserves the project's primary comparison capability without exposing algorithms that do not scale safely to millions of production transactions.

## Operational note

Deploy the Spring and Python changes together, run the migration, and restart both services. Existing active configurations containing retired production models must not be used without the Phase 12 migration.
