# Supervised Fraud Performance Evidence

## Evaluation contract

- Dataset: 264,500 chronologically ordered transactions from the Fraud Detection Handbook simulator subset used by this project.
- Fraud rows: 2,340 (0.8847%).
- Split: oldest 70% training, next 10% threshold calibration, newest 20% untouched testing.
- Fraud-scenario identifiers were used only to diagnose recall after scoring. They were never model inputs.
- The operating threshold was selected on calibration data to maximize recall while calibration precision remained at or above 70%.

## Reproducible result

The strongest standalone candidate was Extra Trees:

| Metric | Untouched newest 20% |
|---|---:|
| Precision | 74.8% |
| Recall | 71.8% |
| F1 | 73.2% |
| PR-AUC | 73.3% |
| True positives | 323 |
| False positives | 109 |
| False negatives | 127 |
| True negatives | 52,341 |

The evidence is stored in `outputs/fraud_handbook_750customers_p70_ensemble_benchmark.json`.

## What did not work

- Generic stacking across redundant classifiers did not beat Extra Trees on the untouched test period.
- A specialist rescue cascade raised precision but lost too much recall.
- These candidates remain research comparisons and are not presented as improvements.

## Production change

- Logistic Regression was replaced as a production base classifier by Extra Trees.
- The three supervised base models are now XGBoost, Class-Balanced Random Forest, and Extra Trees.
- Logistic Regression remains only as the temporal stack's meta-classifier.
- Customer behaviour, peer behaviour, and AML rules remain separate risk-policy layers.
- A calibrated fraud decision now consumes the deciding model's configured share of the ML layer. This prevents a valid model decision from being diluted below the case threshold merely because its probability is less than 1.0.

## Honest system-level claim

The current evidence proves that the revised standalone model exceeds 70% precision and 70% recall on an untouched chronological period. It does not yet prove that the complete four-layer policy beats Extra Trees. That comparison must be rerun after migration and retraining; the Supervised Comparison page reports both results on the same held-out rows and should be used to make that claim only when its measured deltas are positive.
