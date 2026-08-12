# Phase 09: Half-Space Trees Incremental Challenger

## Purpose

This phase introduces River Half-Space Trees as a bounded-state incremental anomaly-model candidate. The model remains silent: its score is logged for later comparison, but it does not add an anomaly vote, create an alert, or open a case.

## Database Migration

Run these scripts in SQL Server Management Studio after Phase 08:

1. `database/phase_09_hst_incremental_challenger.sql`
2. `database/verify_phase_09_hst_incremental_challenger.sql`

The migration adds `app_config` settings for tree count, height, window size, threshold quantile, Parquet batch size, random seed, and silent scoring.

## Dependencies

Install the Python dependencies after updating the source:

```powershell
cd fraud-ml-service
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

The implementation uses River `0.25.x` and PyArrow `25.x`.

## Training Workflow

1. Close the business date.
2. Create an AML training run with `modelType` set to `HALF_SPACE_TREES`.
3. Generate its Parquet dataset and wait for `DATASET_READY`.
4. Start asynchronous incremental training:

```http
POST /api/v1/aml/training-runs/{trainingRunId}/incremental-training
Content-Type: application/json

{
  "baseModelVersion": null,
  "requestedBy": "nightly-eod-job"
}
```

For later daily updates, provide the compatible earlier HST model version as `baseModelVersion`.

Spring changes the run to `TRAINING`, calls Python, verifies the returned artifact manifest and checksum, registers the model as `CANDIDATE`, and completes the run as `CANDIDATE_READY`. Failures produce `TRAINING_FAILED` without replacing any existing model.

## Scale Characteristics

- Parquet is decoded in configurable record batches.
- Rows are exported and learned chronologically.
- The entire dataset is never loaded into memory.
- HST state is bounded by tree count and height, not transaction count.
- The manifest carries the stable persisted-feature schema, avoiding an additional full dataset scan.
- Model scoring and model learning are separate operations.

## Silent Scoring

Spring selects the newest compatible HST candidate for the transaction feature version and segment. Python returns the result under:

```json
{
  "featureSummary": {
    "silentChallenger": {
      "modelVersion": "HST-RETAIL-GENERAL-20260805-12345678",
      "score": 0.87,
      "threshold": 0.81,
      "anomaly": true,
      "affectsProductionDecision": false
    }
  }
}
```

Spring stores the model version and score in `fraud_prediction_logs.model_version` and `fraud_prediction_logs.incremental_model_score`. Production `modelResults`, votes, risk level, alerts, and cases remain unchanged.

## Next Phase

Phase 10 compares the HST challenger with the current champion using anomaly rate, alert overlap, score stability, known scenarios, and analyst outcomes.
