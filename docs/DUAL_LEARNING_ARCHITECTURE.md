# Dual Learning Architecture

## Objective

The platform supports two explicit operating modes. A deployment must choose one mode for its ML ensemble so that metrics, thresholds, explanations, and governance remain scientifically valid.

- `UNSUPERVISED`: use when confirmed transaction labels are unavailable.
- `SUPERVISED`: use only when a reviewed binary label is available for enough historical transactions.

Customer behaviour, peer behaviour, and deterministic AML rules remain active in both modes. Only the ML-ensemble implementation and its evaluation protocol change.

## Constrained Model Catalog

### Unsupervised

1. **Isolation Forest** — batch detector for broad, nonlinear anomaly isolation over historical snapshots.
2. **Half-Space Trees** — incremental detector for high-volume streams and changing transaction behaviour.
3. **Autoencoder** — batch reconstruction detector for complex interactions that simpler isolation boundaries miss.

The older batch and online One-Class SVM paths remain readable for existing artifacts but are not recommended for new production policies. They are slower at scale and have been less stable in the current growth studies.

### Supervised

1. **Histogram Gradient Boosting** — primary nonlinear classifier; efficient on large tabular datasets and captures feature interactions.
2. **Random Forest** — robust nonlinear benchmark with understandable feature importance and class weighting.
3. **Logistic Regression** — calibrated, interpretable baseline that exposes whether added nonlinear complexity provides real value.

These choices use the existing scikit-learn runtime and do not introduce a second native ML dependency.

## Label Contract

A supervised label must be explicit and auditable:

- `1`: confirmed suspicious/fraud outcome according to the bank's approved review process.
- `0`: confirmed legitimate/false-positive outcome.
- `NULL`: unresolved and excluded from supervised training.

An alert, anomaly vote, risk score, generated case, or draft STR is not automatically a positive supervised label. Doing so would teach the new model to copy the old system and create target leakage.

Every label must retain its source, reviewer, and timestamp. Imported labels must identify the source dataset and label column.

## Training Protocol

1. Validate feature schema and label availability.
2. Sort by transaction date and use chronological train, validation, and test windows.
3. Fit preprocessing on the training window only.
4. Tune on validation data; evaluate once on the untouched test window.
5. Handle imbalance with class weights and threshold tuning, not random duplication before splitting.
6. Persist model, preprocessing, feature schema, label definition, threshold, metrics, and data-window checksum as one versioned bundle.
7. Require governance approval before promotion.

## Comparison Metrics

### Unsupervised reports

- score separation and excess-mass quality
- anomaly-rate control and growth stability
- agreement and disagreement between detectors
- throughput, training time, and prediction latency
- clustering projections and label-free diagnostics where scientifically applicable

Silhouette, Davies-Bouldin, and Calinski-Harabasz describe geometric grouping. They do not prove fraud accuracy and must be presented as exploratory structure diagnostics.

### Supervised reports

- precision-recall AUC as the primary imbalanced-data ranking metric
- precision, recall, and F1 at the proposed production threshold
- recall at fixed false-positive-rate operating points
- ROC AUC as a secondary ranking metric
- confusion matrix and per-period stability
- Brier score and calibration curve for probability quality
- throughput, training time, and prediction latency

## Production Scoring

The four top-level layers continue to total exactly `1.0`:

`customer behaviour + peer behaviour + ML ensemble + AML rules = 1.0`

Within the selected learning mode, enabled ML models also total exactly `1.0`. Model output is normalized to a `0..1` ML risk contribution before the top-level ML weight is applied.

- Unsupervised models contribute calibrated anomaly severity, not raw model-specific scores.
- Supervised models contribute calibrated suspicious probability.
- A hard AML rule may still override the weighted score according to the approved rule policy.

## Scalability Boundary

- Real-time scoring reads precomputed customer, peer, and velocity state; it does not scan full transaction history.
- Kafka carries immutable transaction events to asynchronous feature-state and training-data consumers.
- Incremental HST updates consume bounded stream state.
- Batch models train from date-bounded Parquet snapshots using capped or stratified samples where required.
- Supervised retraining uses only reviewed labels available before the training cutoff.

## Delivery Sequence

1. Publish the mode-aware model catalog.
2. Add auditable transaction labels and label-quality reporting.
3. Add supervised dataset export and chronological evaluation.
4. Implement the three supervised models in Python.
5. Redesign training operations around mode and selected models.
6. Split comparison results into supervised and unsupervised methodologies.
7. Make risk-policy model selection mode-aware and enforce normalized weights.
8. Promote models only through validation and governance.
