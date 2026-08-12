# Phase 08: Training-Run and Model-Registry Workflow

## Purpose

This phase adds the audited lifecycle surrounding model training. It does not introduce the Half-Space Trees algorithm yet and does not change production prediction. Phase 09 will use this workflow to train the incremental challenger.

## Database Migration

Run these scripts in SQL Server Management Studio after the Phase 07 migration:

1. `database/phase_08_training_model_registry.sql`
2. `database/verify_phase_08_training_model_registry.sql`

The migration extends `aml_model_registry` with reproducibility metadata and configures `aml.model.artifact_base_path`.

## Workflow

### 1. Start training

The training run must already have status `DATASET_READY`.

```http
POST /api/v1/aml/training-runs/{trainingRunId}/training/start
Content-Type: application/json

{
  "baseModelVersion": null
}
```

For incremental updates, `baseModelVersion` must identify a compatible `CHAMPION` or `CHALLENGER`. A first-time model may omit it.

### 2. Write an immutable candidate bundle

The trainer writes its model bundle under the configured `aml.model.artifact_base_path`. The bundle should contain the model state, feature schema, parameters, and its own manifest.

### 3. Register the candidate

```http
POST /api/v1/aml/training-runs/{trainingRunId}/candidate
Content-Type: application/json

{
  "modelVersion": "HST-RETAIL-20260804-01",
  "artifactPath": "HST-RETAIL-20260804-01",
  "artifactChecksum": "<64-character SHA-256>",
  "featureSchemaChecksum": "<64-character SHA-256>",
  "learnedRowCount": 925431,
  "anomalyRate": 0.018,
  "validationRowCount": 925431,
  "alertCount": 16658,
  "averageScore": 0.21,
  "scoreP95": 0.71,
  "scoreP99": 0.90,
  "parameters": {},
  "metrics": {},
  "registeredBy": "python-ml-service"
}
```

Spring independently recalculates the deterministic bundle checksum before inserting the `CANDIDATE` registry row. Candidate registration and training-run completion occur in one database transaction.

### 4. Query registry state

```http
GET /api/v1/aml/models/{modelVersion}
GET /api/v1/aml/models?status=CANDIDATE&modelType=HALF_SPACE_TREES&modelSegment=RETAIL_GENERAL
```

### 5. Record a training failure

```http
POST /api/v1/aml/training-runs/{trainingRunId}/training/fail
Content-Type: application/json

{
  "reason": "Dataset feature schema is incompatible with the trainer"
}
```

## Lifecycle Boundary

Phase 08 permits `DATASET_READY -> TRAINING -> CANDIDATE_READY` or `TRAINING_FAILED`. Validation, promotion, champion selection, and rollback remain separate controlled phases.
