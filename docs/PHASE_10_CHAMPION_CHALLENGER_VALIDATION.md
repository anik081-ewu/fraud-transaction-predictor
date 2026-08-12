# Phase 10: Champion-Challenger Validation

## Purpose

This phase compares a silent Half-Space Trees candidate with the current production ensemble. It does not claim accuracy, precision, recall, or F1 across the full transaction population because confirmed labels are not yet available for every transaction.

## Database Migration

Run these scripts in SQL Server Management Studio after Phase 09:

1. `database/phase_10_champion_challenger_validation.sql`
2. `database/verify_phase_10_champion_challenger_validation.sql`

The migration creates `aml_model_validations` and adds configurable validation gates through `app_config`.

## Required Observation Period

After an HST candidate reaches `CANDIDATE_READY`, allow real transactions to accumulate silent scores. Every prediction stores:

- candidate model version;
- HST anomaly score;
- production suspicious decision;
- Isolation Forest decision score when available;
- analyst review outcome when a production alert is reviewed.

The candidate remains excluded from production votes and case creation.

## Run Validation

```http
POST /api/v1/aml/models/{modelVersion}/validate
Content-Type: application/json

{
  "windowStartedAt": "2026-08-05T00:00:00Z",
  "windowEndedAt": "2026-08-12T00:00:00Z",
  "validatedBy": "model-risk-reviewer"
}
```

Both timestamps are optional. The default window starts when the candidate was registered and ends at the current time.

Retrieve historical reports:

```http
GET /api/v1/aml/models/{modelVersion}/validations
```

## Metrics

- `candidateAnomalyRate`: HST threshold exceedances divided by silent predictions.
- `productionAlertRate`: production suspicious decisions divided by the same predictions.
- `overlapCount`: transactions flagged by both systems.
- `candidateOnlyCount`: HST anomalies not alerted by production.
- `productionOnlyCount`: production alerts not flagged by HST.
- `agreementRate`: normal-normal and anomaly-alert agreement across the observation window.
- `alertJaccard`: overlap divided by the union of candidate anomalies and production alerts.
- `scoreP50`, `scoreP95`, `scoreP99`: HST score distribution and upper-tail separation.
- `dailyAnomalyRateStandardDeviation`: day-to-day candidate alert-volume stability.
- `reviewedPrecision`: STR-generated overlaps divided by all reviewed overlapping alerts. This is selection-biased evidence, not population-wide precision.

## Validation Gates

The default policy requires:

- at least 1,000 silent predictions;
- candidate anomaly rate between 0.1% and 10%;
- daily anomaly-rate standard deviation no greater than 5 percentage points;
- measurable score separation between the median and 99th percentile;
- at least 20 reviewed overlaps before the reviewed-precision gate is enforced;
- reviewed precision of at least 20% when that review sample is large enough.

Possible report statuses:

- `INSUFFICIENT_DATA`: collect more silent predictions; registry status remains `CANDIDATE`.
- `FAILED`: one or more configured gates failed; registry status remains `CANDIDATE` for investigation or an explicit later rejection.
- `PASSED`: the registry model becomes `VALIDATED`, but it is still not approved or deployed.

## Next Phase

Phase 11 introduces explicit approval, segment-level promotion, active-model pointers, and rollback. Validation alone must never deploy a model.
