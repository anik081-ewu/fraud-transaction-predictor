# Phase 16: Deterministic AML Rules Scoring

## Purpose

This phase implements an independently explainable AML rules component. Rules consume the same persisted point-in-time feature vector used by the behavioural and model layers. They do not claim that fraud occurred; they identify deterministic conditions requiring risk aggregation or review.

The engine is not connected to production decisions in this phase.

## Rule catalogue

| Rule | Trigger | Severity | Score | Hard override |
|---|---|---:|---:|---:|
| `SANCTIONS_MATCH` | External screening result in evaluation context | CRITICAL | 1.00 | Yes |
| `POTENTIAL_STRUCTURING_24H` | At least 3 below-threshold transactions whose 24-hour sum reaches the configured reporting threshold | HIGH | 0.90 | No |
| `RAPID_TRANSACTION_VELOCITY_10M` | At least 5 transactions in 10 minutes | HIGH | 0.75 | No |
| `HIGH_TRANSACTION_VELOCITY_1H` | At least 10 transactions in one hour | HIGH | 0.80 | No |
| `MULTIPLE_BENEFICIARIES_1H` | At least 4 unique beneficiaries in one hour | MEDIUM | 0.65 | No |
| `REPEATED_AMOUNT_PATTERN_24H` | At least 4 equal-amount transactions in 24 hours | MEDIUM | 0.60 | No |
| `HIGH_AMOUNT_VS_CUSTOMER_AVERAGE` | Amount is at least 4x the recent customer average | HIGH | 0.75 | No |
| `EXTREME_AMOUNT_VS_CUSTOMER_AVERAGE` | Amount is at least 8x the recent customer average | HIGH | 0.90 | No |
| `HIGH_TRANSACTION_TO_BALANCE_RATIO` | Transaction is at least 80% of current balance | HIGH | 0.85 | No |
| `NEW_BENEFICIARY_DEVICE_AT_UNUSUAL_HOUR` | New beneficiary and device during an unusual hour | HIGH | 0.85 | No |
| `NEW_LOCATION_AND_DEVICE` | New location and device | MEDIUM | 0.65 | No |
| `UNUSUAL_TRANSACTION_HOUR` | Unusual transaction hour without a stronger novelty combination | LOW | 0.25 | No |
| `HIGH_TRANSACTION_TO_EXPECTED_TURNOVER` | Transaction reaches 50% of expected monthly turnover | HIGH | 0.80 | No |

Thresholds belong to the versioned `DETERMINISTIC_AML_RULES_V1` policy. The reporting threshold defaults to `10000` but can be supplied through `RuleEvaluationContext` from jurisdiction-specific application configuration.

## Score aggregation

Triggered rule scores use a bounded noisy-OR calculation:

```text
rules score = 1 - product(1 - individual rule score)
```

This has three useful properties:

- the result always remains between `0.0` and `1.0`;
- multiple independent warning signals increase risk;
- duplicate arithmetic addition cannot push the score above `1.0`.

The output also includes a `0-100` display score, normalization version, highest severity, hard-alert flag, ordered reason codes, and immutable evidence for every triggered rule.

## Hard overrides

Only `SANCTIONS_MATCH` is a hard override in V1. A match must originate from an external screening component through the rule context. Velocity, anomaly, amount, novelty, and structuring conditions remain risk evidence and do not independently assert confirmed fraud.

## Determinism and auditability

Rules depend only on the point-in-time feature vector, versioned policy, and explicit evaluation context. Triggered rules are sorted by rule code, making replay output stable. Each rule records the values responsible for the trigger.

## Compatibility and production boundary

`TriggeredRule` now includes an immutable evidence map. Its original four-argument constructor remains available, preserving existing callers.

The engine is not a Spring bean and is not called by the production orchestrator. Existing HST/Isolation Forest decisions, alerts, and automatic cases remain unchanged.

No database migration is required. Rule-result persistence will be added before shadow deployment.

## Tests

Tests cover normal traffic, velocity combinations, configurable structuring detection, novelty combinations, sanctions hard override, deterministic replay, and stable rule ordering.

## Next phase

Implemented in `PHASE_17_ONLINE_ONE_CLASS_SVM.md` as a shadow-only online model while retaining current fallback behavior.
