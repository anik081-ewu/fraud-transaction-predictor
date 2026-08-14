# Unified Unsupervised Model Architecture

## Decision

The production anomaly ensemble contains exactly three unsupervised models:

1. Isolation Forest
2. Autoencoder
3. Local Outlier Factor (LOF)

The earlier batch-versus-incremental model split is removed. Operators choose models for a run, and every selected model trains from the same immutable, chronologically ordered transaction snapshot.

## Training flow

1. An operator selects a closed business-date range.
2. Spring Boot exports one verified feature snapshot.
3. Spring Boot sends the selected model names and transactions to the Python training endpoint.
4. Python fits a shared scaler and the selected models.
5. Python writes all artifacts into one versioned model bundle.
6. Spring Boot registers one candidate record per trained model for comparison and governance.

There is no automatic daily or weekly cadence. Training starts only when an authorized operator starts it from Training Operations.

## Production scoring

The Risk Policy page controls which of the three trained models participate and their relative weights. All enabled model weights must total 100% inside the ML ensemble. The ML ensemble then contributes its configured share to the four-layer final risk score.

## Growth comparison

The Model Comparison Lab trains Isolation Forest, Autoencoder, and LOF on the oldest 10%, 25%, 50%, and 100% of the same snapshot. Chronological holdout rows measure anomaly-rate behaviour, score separation, EM-AUC, stability, and throughput as history grows.

These are label-free diagnostics. They do not claim fraud accuracy. Precision, recall, F1, and PR-AUC become available only after reviewed case outcomes provide trustworthy labels.

## Compatibility

Historical database columns and old audit records that mention incremental or batch scores remain readable. They no longer control model training, model selection, or live scoring. No database migration is required for this change.
