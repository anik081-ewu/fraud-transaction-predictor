# Phase 06 — Learning Eligibility Workflow

## Database deployment

Run these scripts in SSMS before restarting Spring Boot:

1. `database/phase_06_learning_eligibility_backfill.sql`
2. `database/verify_phase_06_learning_eligibility.sql`

The migration is idempotent and inserts decisions only for feature rows that do not already have one.

## Automatic decisions

| Condition | Decision | Trusted profile | Incremental training | Batch training |
|---|---|---:|---:|---:|
| Scored, non-suspicious `NORMAL` or `LOW` | `LEARN_IMMEDIATELY` | Yes | Yes | Yes |
| Suspicious, `MEDIUM`, or `HIGH` | `WAIT_FOR_REVIEW` | No | No | No |
| Prediction unavailable or empty | `DELAYED_LEARNING` | No | No | No |

Cold-start transactions remain eligible because their explicit `skipped` model result is an intentional policy decision rather than an ML outage.

## Analyst decisions

- Marking an alert false positive atomically changes `WAIT_FOR_REVIEW` to `LEARN_IMMEDIATELY`.
- The released transaction updates the trusted profile and recent-state trusted flag exactly once.
- Generating an STR changes the decision to `DO_NOT_LEARN`.
- Repeated false-positive requests cannot update the trusted profile twice.
- A false-positive alert cannot later generate an STR, and an STR-generated alert cannot later become false positive.

## Audit data

Every prediction log now stores:

- feature version;
- suspicious flag;
- learning decision;
- learning-decision reason;
- reason codes.

The authoritative training filter remains `aml_feature_learning_status`, not the prediction log.

## Current limitation

`DELAYED_LEARNING` retry/finalization will be added with asynchronous prediction retry and end-of-day orchestration. Delayed rows remain excluded until explicitly resolved.
