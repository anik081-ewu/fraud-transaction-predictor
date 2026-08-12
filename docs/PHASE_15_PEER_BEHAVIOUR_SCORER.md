# Phase 15: Peer Behaviour Scorer

## Purpose

This phase implements a transparent statistical score describing how unusual a transaction is relative to a suitable peer baseline. It complements the customer behaviour score: customer scoring asks whether the transaction is unusual for this customer, while peer scoring asks whether it is unusual among comparable customers.

The scorer is deterministic and does not train a model per peer group.

## Baseline hierarchy

The scorer records the most specific available baseline:

1. the `peerGroupCode` already selected during point-in-time feature engineering;
2. the parent customer segment supplied by `BehaviourScoringContext`;
3. `GLOBAL` when neither is available.

Specific, parent, and global baselines receive confidence factors of `1.00`, `0.85`, and `0.70`. Missing statistics further reduce confidence. The current feature loader does not yet populate production peer profiles; that data-access work remains separate so this phase cannot alter production behaviour.

## Transparent V1 formula

```text
0.60 * peer amount deviation
+ 0.25 * peer frequency deviation
+ 0.15 * expected-turnover deviation
```

Each component is bounded to `0.0-1.0`:

- amount deviation uses the maximum of amount-versus-peer-average and positive peer z-score transforms;
- frequency deviation grows from the 50th to the 100th peer percentile;
- turnover deviation grows when one transaction exceeds 5% of expected monthly turnover and saturates at 50%.

The evidence-completeness weights match the scoring weights. Final component confidence is:

```text
baseline-level confidence * available-evidence completeness
```

The normalized score is the weighted score multiplied by this confidence. Missing peer data therefore produces zero peer evidence instead of inventing zero-valued statistics.

## Output

The scorer returns:

- selected peer-group identifier;
- display/raw score on `0-100`;
- normalized score on `0.0-1.0`;
- normalization version `PEER_BEHAVIOUR_TRANSPARENT_V1`;
- risk band and confidence;
- deterministic reason codes.

Component risk bands remain `NORMAL < 0.40`, `LOW < 0.60`, `MEDIUM < 0.75`, and `HIGH >= 0.75`.

## Reason codes

Reason codes cover:

- amount above `2x`, `4x`, or `8x` peer average;
- peer amount z-score above `3`;
- frequency above the 95th or 99th percentile;
- transaction above 25% or 50% of expected monthly turnover;
- parent or global fallback selection;
- low-confidence or unavailable peer evidence.

## Compatibility and production boundary

The peer scorer is not a Spring bean and is not called by the production orchestrator. Existing HST/Isolation Forest decisions, API responses, alert creation, and case creation remain unchanged.

No database migration is required for this phase. Peer-profile storage/loading and component-score persistence will be introduced additively before shadow deployment.

## Tests

Tests cover normal and extreme specific-group comparisons, parent fallback, unavailable global evidence, deterministic replay, and monotonic score growth.

## Next phase

Implemented in `PHASE_16_DETERMINISTIC_AML_RULES.md` as an independently explainable score component.
