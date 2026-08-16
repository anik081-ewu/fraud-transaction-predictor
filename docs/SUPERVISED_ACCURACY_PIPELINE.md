# Supervised Accuracy Pipeline

## What changed

Supervised model training, comparison, and production prediction now use the same immutable
persisted-feature snapshot. The former production path rebuilt a deprecated feature set from raw
transactions, while comparison used `model_features_json`. That training-serving skew could make a
model look good in comparison but behave differently in production.

The supervised path is intentionally separate from unsupervised anomaly detection:

- Unsupervised models continue to learn normality without `FraudLabel`.
- Supervised models consume only labelled rows from an exported chronological snapshot.
- XGBoost and Random Forest receive median-imputed numeric features without unnecessary scaling.
- Logistic Regression receives median imputation and robust scaling.
- Each classifier learns its own decision threshold on a dedicated calibration window.
- The untouched final 20% of time-ordered rows produces accuracy, balanced accuracy, PR-AUC,
  PR-AUC lift, ROC-AUC, precision, recall, F1, Brier score, and confusion-matrix counts.

## Chronological protocol

Rows remain ordered from oldest to newest:

1. Oldest 70% trains the model.
2. Next 10% calibrates its fraud threshold using F-beta.
3. Newest 20% evaluates the frozen model and threshold.

No evaluation row is used for model fitting or threshold selection. This better represents how a
bank deploys a model trained on past transactions to future transactions.

## Feature contract

`AML_FEATURES_V4` retains the V3 amount, velocity, novelty, customer profile, and peer signals and
adds point-in-time terminal volume, amount, and delayed confirmed-fraud-rate features to
`model_features_json`. Missing peer or profile values retain explicit availability indicators.
Existing categorical legacy features remain available, so the new contract is richer without
discarding prior signals.

## Operating steps

1. Run `database/phase_28_supervised_accuracy_pipeline.sql` once.
2. Run `database/phase_29_versioned_transaction_features.sql` once so V2 and V3 feature rows can coexist.
3. Restart Spring Boot, FastAPI, and Angular.
4. Keep **System Type** set to **Supervised Learning**.
5. Create a new training run using feature version `AML_FEATURES_V4`.
6. Generate the snapshot, then train the selected supervised models.
7. Run Supervised Comparison against that same snapshot.

Old V2/V3 snapshots and old model artifacts are retained for audit but should not be used to judge
the new supervised pipeline.
