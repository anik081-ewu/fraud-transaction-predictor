# Phase 07: End-of-Day Parquet Export

## Purpose

This phase creates immutable, checksummed Parquet datasets from learning-eligible transaction features. A training run can be exported only after every business date in its requested range is closed.

## Database Migration

Run these scripts in SQL Server Management Studio against `fraud-transaction-detector`:

1. `database/phase_07_eod_parquet_export.sql`
2. `database/verify_phase_07_eod_parquet_export.sql`

The migration creates `aml_business_days` and adds these `app_config` values:

- `aml.export.base_path`
- `aml.export.chunk_size`
- `aml.export.rows_per_file`

## API Flow

### 1. Close the business day

```http
POST /api/v1/aml/business-days/2026-08-04/close
Content-Type: application/json

{
  "closedBy": "admin"
}
```

### 2. Create a training run

```http
POST /api/v1/aml/training-runs
Content-Type: application/json

{
  "trainingType": "DAILY_INCREMENTAL",
  "featureVersion": "AML_FEATURES_V2",
  "modelType": "HALF_SPACE_TREES",
  "modelSegment": "RETAIL_GENERAL",
  "fromBusinessDate": "2026-08-04",
  "toBusinessDate": "2026-08-04",
  "cutoffTimestamp": "2026-08-04T23:59:59"
}
```

### 3. Start dataset export

```http
POST /api/v1/aml/training-runs/{trainingRunId}/dataset
```

The endpoint returns `202 Accepted`. Export continues asynchronously.

### 4. Check status

```http
GET /api/v1/aml/training-runs/{trainingRunId}
```

The successful terminal status is `DATASET_READY`. The response then includes the exported row count, dataset path, and SHA-256 dataset checksum.

## Output Layout

```text
outputs/training-datasets/
  feature_version=AML_FEATURES_V2/
    model_segment=RETAIL_GENERAL/
      from=2026-08-04_to=2026-08-04/
        training_run_id=<uuid>/
          part-00001.parquet
          manifest.json
```

Each manifest records the schema columns, row counts, part checksums, and aggregate dataset checksum. Export uses keyset pagination and bounded chunks, so it does not load millions of rows into application memory at once.
