# Phase 17: Online One-Class SVM

## Purpose

This phase adds Online One-Class SVM as the second production-capable streaming anomaly model beside Half-Space Trees. It is introduced as a shadow component: its score is returned and auditable, but it does not vote, create alerts, or generate cases yet.

The implementation is different from the existing scikit-learn kernel `OneClassSVM`. That batch model remains available only for offline research comparisons. The new model uses River's stochastic online One-Class SVM and updates one eligible feature row at a time.

## Streaming architecture

```text
eligible persisted features
  -> bounded Parquet batches
  -> online StandardScaler
  -> seeded RBF random-feature projection
  -> stochastic Online One-Class SVM
  -> empirical quantile calibration
  -> versioned candidate artifact
```

Only rows where `eligible_for_batch_training = 1` enter exported training datasets. Data remains chronologically ordered by transaction date and database ID. Python validates the dataset checksum, row count, feature version, and model type before learning.

## Why this scales

- Parquet rows are decoded in bounded batches rather than loaded into one dataframe.
- The model updates with `learn_one`, so training memory does not grow with transaction count.
- The RBF approximation has a fixed configurable component count; V1 defaults to `64`.
- Model state can continue from a compatible prior artifact for daily incremental updates.
- Calibration uses streaming quantile estimators and repeated bounded scans, not retained score arrays.

The training cost grows approximately linearly with rows. Memory is governed mainly by feature count and RBF component count, not by millions of historical transactions.

## Versioned artifact contract

Each candidate bundle contains:

- `online_ocsvm_model.pkl` with scaler, RBF projection, model state, schema, and calibration;
- `artifact-manifest.json` with model type, version, segment, feature version, dataset checksum, base version, parameters, metrics, and file checksums.

Incremental continuation rejects feature-version, segment, schema, or structural-parameter drift.

## Score normalization

River returns a signed, model-specific raw anomaly score. V1 calibrates it against the candidate dataset:

```text
normalization floor   = median raw score
normalization ceiling = configured upper raw-score quantile
normalized score      = clamp((raw - floor) / (ceiling - floor), 0, 1)
display score         = normalized score * 100
```

The default anomaly threshold is the 99th percentile. API results include raw score, normalized score, `0-100` score, raw threshold, model version, and normalization version `ONLINE_OCSVM_EMPIRICAL_V1`.

This score indicates relative model evidence, not confirmed fraud probability.

## Shadow prediction

Spring selects the newest compatible `ONLINE_ONE_CLASS_SVM` candidate for the transaction's feature version and peer segment. FastAPI returns it under `OnlineOneClassSVM` and `onlineOneClassSvmShadow`, both marked:

```json
"affectsProductionDecision": false
```

Current HST champion or Isolation Forest fallback logic remains the only ML decision path. Online OCSVM cannot change risk, alert, or case outcomes in this phase.

## Training operations

The Angular Training Operations page now offers:

- `Half-Space Trees` -> `HALF_SPACE_TREES`
- `Online One-Class SVM` -> `ONLINE_ONE_CLASS_SVM`

It also defaults to feature version `AML_FEATURES_V2` instead of the obsolete shorthand values.

Operational sequence:

1. close the business day;
2. create a `DAILY_INCREMENTAL` run with model type `ONLINE_ONE_CLASS_SVM`;
3. generate the eligible Parquet dataset;
4. start candidate training;
5. optionally provide a compatible earlier OCSVM version for continuation.

## Configuration migration

Run `database/phase_17_online_one_class_svm.sql` in SSMS. It adds only `app_config` entries and does not alter tables.

Important defaults are `nu=0.05`, learning rate `0.01`, RBF gamma `0.5`, `64` RBF components, calibration quantile `0.99`, minimum calibration rows `200`, batch size `65536`, and seed `42`.

## Tests

Automated coverage includes:

- bounded streaming training from verified Parquet;
- artifact serialization and reload;
- normalized-score bounds and replay stability;
- continuation from a compatible base artifact;
- rejection of mismatched dataset model types;
- shadow API output that cannot affect the production decision;
- Spring model routing and parameter selection.

## Remaining boundary

Online OCSVM candidates cannot be promoted through the HST-specific validation workflow yet. Controlled validation and promotion are intentionally deferred until the layered weighted-risk engine has run in shadow mode.

## Next phase

Implemented in `PHASE_18_WEIGHTED_RISK_AGGREGATION.md` over customer behaviour, peer behaviour, HST, Online OCSVM, and deterministic rules.
