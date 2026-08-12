# Phase 14: Customer Behaviour Scorer

## Purpose

This phase implements a transparent statistical score describing how unusual a transaction is relative to the customer's own trusted historical behaviour.

The scorer is not a machine-learning model and does not train one model per customer.

## Inputs

The scorer consumes the persisted point-in-time `TransactionFeatureVector`, including:

- amount versus the recent average and median;
- recent amount z-score;
- transaction counts over 10 minutes, 1 hour, and 24 hours;
- actual time gap versus the customer's recent average time gap;
- new beneficiary, location, channel, and device flags;
- unusual transaction hour;
- trusted-profile confidence.

Because these features are produced before prediction from prior transactions only, the scorer does not query future information.

## Transparent V1 formula

The unadjusted score is a weighted combination:

```text
0.55 * amount deviation
+ 0.12 * frequency deviation
+ 0.08 * time-gap deviation
+ 0.20 * categorical novelty
+ 0.05 * unusual-hour indicator
```

Every sub-score is bounded to `0.0-1.0`.

Amount ratios and positive z-scores use documented saturating exponential transforms so very large values approach, but never exceed, `1.0`. Frequency is measured as bounded excess above the current-transaction baseline. Time-gap deviation increases as the current gap becomes shorter than the customer's recent average.

## Confidence adjustment

The trusted-profile confidence adjusts the customer-specific evidence:

```text
confidence multiplier = 0.35 + (0.65 * profile confidence)
normalized score = unadjusted score * confidence multiplier
```

The non-zero floor preserves a limited amount of transparent transaction evidence during cold start while preventing an immature profile from producing the same customer-specific score as an established profile.

The score output contains:

- display/raw score on `0-100`;
- normalized score on `0.0-1.0`;
- normalization version `CUSTOMER_BEHAVIOUR_TRANSPARENT_V1`;
- risk band;
- profile confidence;
- reason codes.

## Risk bands

```text
0.00-0.39 = NORMAL
0.40-0.59 = LOW
0.60-0.74 = MEDIUM
0.75-1.00 = HIGH
```

These are component-level interpretation bands. They do not create alerts and are separate from the future versioned final-risk policy.

## Reason codes

Implemented reason codes include:

- `AMOUNT_ABOVE_2X_RECENT_AVERAGE`
- `AMOUNT_ABOVE_4X_RECENT_AVERAGE`
- `AMOUNT_ABOVE_8X_RECENT_AVERAGE`
- `AMOUNT_ZSCORE_ABOVE_3`
- `CUSTOMER_FREQUENCY_BURST_10M`
- `CUSTOMER_FREQUENCY_BURST_1H`
- `RAPID_TRANSACTION_VS_CUSTOMER_BASELINE`
- `NEW_BENEFICIARY`
- `NEW_LOCATION`
- `NEW_CHANNEL`
- `NEW_DEVICE`
- `UNUSUAL_TRANSACTION_HOUR`
- `LOW_CUSTOMER_PROFILE_CONFIDENCE`

## Compatibility and production boundary

The scorer is not yet called by the production orchestrator. Existing HST/Isolation Forest decisions, API responses, alert creation, and case creation remain unchanged.

The scorer will be connected as a stored shadow component after peer and rules scoring contracts are available. It must not affect alerts before the layered architecture reaches the shadow-deployment phase.

## Database migration

No database migration is required for this phase.

Component-score persistence will be introduced through a later additive migration before shadow deployment.

## Tests

Automated tests cover:

- established normal behaviour;
- high amount plus categorical novelty;
- profile-confidence reduction;
- deterministic historical replay;
- monotonic score growth as replayed amounts depart further from the same trusted baseline.

## Next phase

Implemented in `PHASE_15_PEER_BEHAVIOUR_SCORER.md` with specific-group, parent-segment, and global fallback semantics.
