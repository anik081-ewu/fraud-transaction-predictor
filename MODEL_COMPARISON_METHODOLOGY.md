# Anomaly Model Comparison Methodology

## Purpose and boundary

The comparison report studies how five offline research models change as chronological data grows. It does not configure the layered production architecture and does not claim supervised fraud accuracy when confirmed labels are unavailable.

Production uses weighted rules, customer and peer behavioural scorers, Half-Space Trees, and Online One-Class SVM. Isolation Forest is retained as the production rollback fallback. LOF, kernel One-Class SVM, Elliptic Envelope, and PCA Reconstruction are never production decision components.

## Partition Protocol

- Sort transactions by `transaction_date` ascending.
- Reserve the newest 20% of the frozen dataset as one shared future holdout.
- Build 10%, 25%, 50%, and 100% training partitions from the remaining oldest training pool.
- Evaluate every trained partition against the same shared future holdout.
- Exclude transactions inserted after snapshot creation.

## Model-Specific Optimization

Each model searches a bounded candidate space appropriate to its algorithm:

- Isolation Forest: tree count and sampling strategy.
- Local Outlier Factor: neighborhood size.
- One-Class SVM: `nu` and RBF kernel scale.
- Elliptic Envelope: robust covariance support fraction.
- PCA Reconstruction: retained components and reconstruction-error percentile.

Candidate selection uses the shared future holdout. Generated perturbations provide a repeatable anomaly challenge, and three resampled fits measure prediction stability. Search data is capped by configuration to keep runtime predictable.

## Evidence Scores

Proxy quality combines:

- 50% proxy average precision against generated anomalies.
- 35% proxy ROC AUC against generated anomalies.
- 15% validation anomaly-rate control.

The model Fit Score uses non-overlapping components:

- 50% average proxy quality.
- 35% average bootstrap stability.
- 15% average anomaly-rate control.

The report also exposes anomaly rate, training duration, prediction latency per row, artifact size, feature-schema hash, and quality change from 10% to 100%. These operational metrics support deployment decisions but do not change the Fit Score.

## Research artifact governance

- Every training run creates immutable candidate artifacts.
- Training never activates a candidate automatically.
- A reviewed 100% training-pool bundle can be retained for reproducible research or compatibility fallback use.
- Research lifecycle records do not authorize a layered production deployment.
- Layered production promotion requires a passing shadow architecture validation and exact policy/HST/Online OCSVM version locks.
- Dataset snapshot, feature schema, scaler, hyperparameters, thresholds, artifact paths, and model code version remain traceable through the training and model-version records.

## Governance Limitation

Generated anomalies are a model-selection proxy, not real fraud cases. Proxy ROC AUC, average precision, and the composite quality score must never be presented as production fraud accuracy.

When reviewer outcomes become available, the selection process should add temporal out-of-sample precision, recall, false-positive rate, PR AUC, alert volume, and cost-weighted business utility. Reviewer labels must remain isolated from the training period used by each model version.
