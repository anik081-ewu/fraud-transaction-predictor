# Phase 20: Layered Shadow Validation

## Purpose

This phase turns Phase 19 shadow comparisons into a strict, versioned validation report. It does not promote the layered architecture. A report can only establish that the configured evidence gates passed for a specific risk-policy version, optional peer group, and time window.

## Validation API

Run a validation over the default trailing 30-day window:

```http
POST /api/v1/aml/layered-shadow/validate
Content-Type: application/json

{}
```

Run an explicit policy, segment, and observation window:

```json
{
  "riskPolicyVersion": "AML_RISK_POLICY_V2",
  "peerGroupCode": "RETAIL_SALARIED",
  "windowStartedAt": "2026-08-01T00:00:00Z",
  "windowEndedAt": "2026-08-31T23:59:59Z",
  "validatedBy": "model-risk-reviewer"
}
```

Retrieve the latest 100 immutable reports:

```http
GET /api/v1/aml/layered-shadow/validations
```

## Synthetic scenario evidence

Synthetic AML transactions must first pass through the normal transaction API so a shadow result exists. Label the expected outcome afterward:

```http
POST /api/v1/aml/layered-shadow/scenario-labels
Content-Type: application/json

{
  "transactionId": "SYN-STRUCTURING-001",
  "scenarioCode": "STRUCTURING",
  "expectedSuspicious": true,
  "labeledBy": "scenario-runner"
}
```

Labels are upserted by transaction and scenario. A label is rejected when no shadow prediction exists, preventing disconnected test evidence.

## Metrics

The report calculates the latest evaluation per transaction and policy, preventing replayed shadow rows from inflating sample volume.

It includes:

- legacy and layered alert counts and rates;
- overlap, layered-only, legacy-only, agreement, and Jaccard values;
- relative alert-volume change;
- layered score mean, standard deviation, and P50/P95/P99;
- legacy coverage among the top one percent of layered scores;
- daily layered-alert-rate standard deviation;
- sample volume, alert rate, and daily stability by peer group;
- maximum segment instability;
- overall and scenario-level synthetic recall;
- reviewed precision and false-positive rate where labels exist;
- average and p95 shadow prediction latency;
- HST and Online OCSVM score availability;
- exact HST and Online OCSVM versions and distinct-version counts;
- completed incremental update count, average duration, and maximum duration.

The query uses immutable policy versions and optional peer-group filters. Historical evaluations with missing peer metadata are reported as `UNCLASSIFIED`.

## Default minimum evidence

A report is `INSUFFICIENT_DATA` unless it contains at least:

- 1,000 unique shadow predictions;
- seven distinct observation days;
- 20 legacy alerts for overlap analysis;
- 20 labeled expected-positive synthetic scenarios;
- 20 reviewed layered alerts;
- one completed HST or Online OCSVM incremental update.

These values are stored under `aml.layered_validation.*` in `app_config`.

## Default quality gates

After minimum evidence is satisfied, the report fails when any configured gate is breached:

- layered alert rate above 10%;
- relative alert-volume increase above 50%;
- top-risk overlap below 50%;
- daily alert-rate standard deviation above 5 percentage points;
- any segment daily standard deviation above 8 percentage points;
- synthetic expected-positive recall below 80%;
- reviewed false-positive rate above 80%;
- prediction p95 latency above 250 ms;
- HST or Online OCSVM score availability below 99%;
- more than one HST or Online OCSVM version in the validation window;
- average incremental update time above one hour;
- no measurable P50-to-P99 upper-tail score separation.

Configuration values are validated before use. Invalid probabilities, durations, or minimum evidence values fail explicitly instead of producing a misleading pass.

## Interpretation limits

The report deliberately includes warnings:

- reviewed outcomes are selection-biased because layered-only alerts do not yet create production review work;
- synthetic recall is not population recall and does not establish real-world accuracy;
- precision, recall, F1, or false-positive rate over the full population still require representative confirmed labels.

A `PASSED` report therefore means all configured operational and available evidence gates passed. It does not mean fraud accuracy has been scientifically proven.

## Database rollout

Run after Phase 19:

1. `database/phase_20_layered_shadow_validation.sql`
2. `database/verify_phase_20_layered_shadow_validation.sql`

The migration adds peer-group persistence support, synthetic labels, immutable validation reports, analysis indexes, integrity constraints, and all default gates. Existing configuration values are never overwritten.

## Production boundary

- Legacy scoring still controls production alerts and cases.
- Validation does not switch any segment to layered scoring.
- No model or risk-policy pointer is changed.
- Phase 21 controlled promotion requires a recent passing report and remains reversible.

## Next phase

Implemented in Phase 21 with explicit authorization, exact version and artifact checks, deterministic canary rollout, runtime fail-safe routing, and immediate Isolation Forest rollback.
