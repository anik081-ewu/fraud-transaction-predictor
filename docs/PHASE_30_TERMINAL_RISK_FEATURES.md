# Phase 30 — Point-in-Time Terminal Risk Features

## Problem

The supervised benchmark preserves Fraud Detection Handbook terminal identity in `Location`. The earlier feature vector included location identity and customer location novelty, but it did not describe how all customers were behaving at the same terminal. A terminal-level compromise pattern was therefore difficult to learn reliably.

The simulator-specific `amount > 220` shortcut was removed before this phase. The benchmark must measure learned patterns rather than encode a generator threshold directly.

## Feature contract

`AML_FEATURES_V4` and `AML_MODEL_INPUT_V3` add terminal transaction count, average amount, confirmed fraud count, and Bayesian-smoothed confirmed fraud rate over 1, 7, and 30 days. `terminal_risk_available` becomes true after the configured minimum 30-day terminal volume.

Operational count and amount features use `[t - window, t)`. Label-derived counts and rates use `[t - delay - window, t - delay)`. This preserves useful immediate activity while representing the delay between a transaction and completion of case review.

Rows labelled `AUTO_NO_CASE` are weak negatives. They contribute to transaction volume but never enter the confirmed-label numerator or denominator.

## Smoothing

For each terminal window:

`rate = (confirmed_fraud_count + m × global_confirmed_fraud_rate) / (confirmed_label_count + m)`

The default prior strength `m` is 20. This prevents a terminal with one reviewed fraud from receiving a fraud rate of 100%.

## Performance design

Live scoring uses one SQL aggregate protected by `IX_transactions_location_transaction_date`. Historical materialization loads a compact chronological terminal index once and resolves every window through binary search and prefix sums. This avoids per-row SQL calls during large backfills.

## Deployment

1. Run `database/phase_30_terminal_risk_features.sql` in SSMS.
2. Run `database/verify_phase_30_terminal_risk_features.sql`.
3. Restart Spring Boot and FastAPI.
4. Create a new training run using `AML_FEATURES_V4`.
5. Materialize and export the dataset again; V3 and V4 rows must not be mixed.
6. Train the supervised models and rerun the chronological comparison.

## Required evaluation

Record the clean V3 baseline and the V4 terminal-feature result separately. Report PR-AUC, precision, recall, F1, balanced accuracy, confusion matrix, and training time. An unexpectedly extreme improvement must trigger a point-in-time and reporting-delay leakage audit.
