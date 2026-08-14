# Supervised Fraud Model Quality

## Why the previous result plateaued

`supervised_bank_transactions_25000.csv` is useful for integration testing, but its generator reads the ULB dataset's anonymized `V1`-`V28` features and then discards them. Fraud labels are attached to newly generated bank-shaped fields with only a weak aggregate influence. This creates legitimate-looking rows whose available features do not fully explain their labels, which limits attainable precision, recall, and PR-AUC regardless of model tuning.

## Replacement dataset

The project now includes an adapter for the official Fraud Detection Handbook transformed simulator dataset:

- Source: <https://github.com/Fraud-Detection-Handbook/simulated-data-transformed>
- Methodology: <https://fraud-detection-handbook.github.io/fraud-detection-handbook/Chapter_5_ModelValidationAndSelection/ValidationStrategies.html>
- Generator: `scripts/adapt_fraud_handbook_dataset.py`
- Generated local upload: `DataSet/fraud_handbook_bank_compatible.csv`
- Summary: `DataSet/fraud_handbook_bank_compatible.summary.json`

The generated file contains 264,500 transactions, 2,340 fraud labels, 749 complete customer histories, and six chronological months. It preserves source amounts, timestamps, customer identity, terminal identity, and labels. `TERMINAL_ID` is represented as `Location`, allowing the current feature engine to derive location novelty without using future labels.

## Threshold policy

Supervised training uses an unbiased F1 threshold by default. An optional precision-constrained policy is also available:

1. Keep `ml.supervised.minimum_precision` at `0` to maximize the configured F-beta score.
2. If an operating policy requires a precision floor, set a value such as `0.80`.
3. Among thresholds satisfying that optional constraint, choose the one with the highest recall.
4. If calibration cannot reach the target, fall back to the configured F-beta objective.
5. Report results on a newer untouched evaluation window.

Configure the optional target through `ml.supervised.minimum_precision` on the Model Tuning page. A calibration target does not guarantee the same result on future data; drift must be checked on the untouched evaluation window.

## Candidate benchmark

`scripts/benchmark_fraud_handbook.py` compares Logistic Regression, Random Forest, Extra Trees, Histogram Gradient Boosting, and XGBoost using chronological train/calibration/test windows.

On the first ten source days (95,815 rows, 274 frauds), Random Forest achieved 100% precision and 37.1% recall on the untouched test window at the precision-constrained threshold. Extra Trees did not improve recall, and Histogram Gradient Boosting underperformed. Therefore, the production catalog remains XGBoost, Random Forest, and Logistic Regression instead of adding models without evidence.

The production tuner evaluates joint model-complexity and class-imbalance candidates instead of changing one setting at a time. Candidates are selected using PR-AUC across both chronological halves of the tuning window with a penalty for unstable period-to-period performance. The comparison response includes the selected hyperparameters for auditability.

## Production expectations

High precision and high recall require all of the following:

- Labels confirmed by investigators rather than model decisions alone.
- Point-in-time customer, velocity, novelty, peer, counterparty, and device features.
- Chronological validation with label delay and no random train/test split.
- Separate calibration and final evaluation windows.
- Monitoring by fraud type, channel, customer group, and month.
- Periodic threshold recalibration when fraud prevalence or behavior changes.

The public dataset proves model capacity and supports system testing. It must not be presented as evidence of performance on a bank's real transaction population.
