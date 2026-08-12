# Bank-Scale ML Architecture Decisions

## Goal

Support millions of daily transactions without losing the primary academic feature: measuring how anomaly models change as chronological training data grows.

## Two deliberately separate tracks

### Production AML scoring

Production uses a layered risk architecture:

1. velocity and AML rules;
2. customer behavioural scorer;
3. peer-group behavioural scorer;
4. Half-Space Trees;
5. Online One-Class SVM;
6. versioned weighted aggregation and hard-rule overrides.

HST and Online OCSVM are the only production ML models. Behaviour scorers are transparent statistical components, and rules are deterministic components. Isolation Forest is retained as the rollback fallback.

Layered production decisions use weighted component scores, confidence handling, and hard-rule overrides rather than model vote counting.

### Offline research comparison

The research page evaluates Isolation Forest, LOF, kernel One-Class SVM, Elliptic Envelope, and PCA Reconstruction at the oldest 10%, 25%, 50%, and 100% of one frozen dataset.

The expensive classical models must remain capped/offline at bank scale. Comparison scores support academic interpretation and tuning; they do not automatically select or deploy production models.

## Why millions of rows remain manageable

- Persisted feature vectors prevent training and prediction feature drift.
- Keyset pagination exports eligible rows without loading the lifetime dataset into Java memory.
- Checksummed Parquet parts replace giant JSON requests.
- Python reads record batches and updates bounded-state incremental models.
- Training is asynchronous and separate from transaction ingestion.
- Model bundles are immutable, versioned, and checksum verified.
- Segment-specific candidates score silently before authorization.
- Deterministic canaries expose stable customer cohorts gradually.
- Runtime mismatches and failures fall back rather than producing unverified layered output.

## Controlled rollout

For each peer group:

1. collect shadow comparisons against the compatibility production path;
2. validate volume, overlap, score stability, synthetic scenarios, reviewed outcomes, latency, and availability;
3. lock the exact risk policy, HST, and Online OCSVM versions;
4. activate a small deterministic account canary;
5. expand only after operational review;
6. roll back the full segment to Isolation Forest when required.

## Kafka roadmap

Kafka provides durable transport, account-keyed ordering, replay, back-pressure isolation, and independently scalable consumers. Kafka does not train models or replace SQL governance.

Suggested topics:

- `transactions.raw`
- `transactions.features`
- `anomaly.predictions`
- `model.training.commands`
- `model.training.results`
- `model.lifecycle.events`
- `case.events`

Introduce Kafka through a SQL outbox. Partition transaction and feature topics by account ID so one customer's state changes remain ordered.

## Operational monitoring

Monitor by segment and version:

- throughput and ingestion lag;
- p50/p95/p99 prediction latency;
- component score availability;
- version mismatch and fallback frequency;
- alert and case rates;
- reviewed false-positive and STR rates;
- feature and score-distribution drift;
- model freshness and incremental update duration;
- canary cohort outcomes.

Retrain on a controlled schedule or validated drift trigger, not after every transaction.

## Clustering research roadmap

Scalable clustering candidates remain MiniBatchKMeans and BIRCH, with Bisecting KMeans and capped-sample HDBSCAN as optional challengers. Use Silhouette, Davies-Bouldin, Calinski-Harabasz, ARI/NMI stability, cluster balance, noise rate, runtime, and memory.

PCA is dimensional reduction and reconstruction modelling, not a clustering algorithm. Browser visualizations must use reproducible samples, centroids, density aggregation, or hexbin views instead of millions of points.
