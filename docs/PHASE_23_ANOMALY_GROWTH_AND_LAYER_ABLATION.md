# Phase 23 — Anomaly Growth and Layer Ablation

## Objective

This phase replaces the legacy five-model comparison with two separate research questions:

1. How do scalable anomaly detectors behave as the oldest available history grows from 10% to 25%, 50%, and 100%?
2. How much do rules, customer behaviour, peer behaviour, Half-Space Trees, and Online One-Class SVM change the final suspicious decision?

The two questions remain separate because detector quality and production-policy impact are not the same measurement.

## Detector Growth Track

The detector track compares:

- Isolation Forest as a bounded batch baseline
- Half-Space Trees as a streaming anomaly detector
- Online One-Class SVM as a streaming boundary detector
- Incremental PCA Reconstruction as a streaming-batch reconstruction detector

Each usable partition is ordered oldest first. Eighty percent of that partition is used for learning and the newest twenty percent is its chronological holdout. Holdout scoring is capped at 20,000 rows to keep report latency and memory bounded.

Isolation Forest is intentionally capped at 100,000 training rows. Its result is marked `boundedTrainingSample=true` whenever the available training history is larger. The three incremental detectors consume the complete training prefix with bounded-memory iteration.

Reported diagnostics include anomaly rate, score percentiles, score separation, anomaly-rate control, training duration, throughput, learned rows, and evaluation rows. They are unsupervised diagnostics, not accuracy.

## Layer Ablation Track

The layer track replays saved rows from `aml_shadow_predictions` oldest first. It evaluates:

- Full layered system
- Without deterministic rules
- Without customer behaviour
- Without peer behaviour
- Without Half-Space Trees
- Without Online One-Class SVM
- Rules only
- ML only
- Behaviour only

When a layer is removed, its weight is redistributed proportionally across included layers. A hard-rule override applies only to variants that include rules. For every variant the report shows suspicious rate, average score, decision changes versus the full system, average score delta, and hard-rule override count.

The ablation track requires at least 200 saved layered predictions. Before that point the API returns `INSUFFICIENT_DATA` without failing detector growth analysis.

## Scalability Controls

- Java sends only the training-run identifier and verified dataset location.
- Python validates the Parquet manifest and checksums before reading.
- Feature rows are streamed from Parquet instead of serialized through Angular or Spring.
- Streaming quantile estimators avoid retaining millions of calibration scores.
- Evaluation data is bounded to 20,000 chronological holdout rows.
- Isolation Forest is a bounded baseline; it is never allowed to consume unbounded bank history.
- Research endpoints do not register, promote, activate, or overwrite production model artifacts.

## How to Run

1. Open **Training Operations**.
2. Create a `BACKTEST` training run covering closed business dates.
3. Generate its persisted-feature dataset and wait for `DATASET_READY`.
4. Start Spring Boot on port `8080`.
5. Start FastAPI on port `8000`.
6. Open **Research Comparison** in Angular.
7. Select the ready snapshot and choose **Run Growth Analysis**.

Spring calls:

- `POST /api/v1/aml/growth-analysis/training-runs/{trainingRunId}`
- `POST /api/v1/aml/growth-analysis/layer-ablation`

Python receives detector work at:

- `POST /api/v1/aml/research/growth-analysis`

## Database Impact

No database migration is required for Phase 23. Detector reports are reproducible from immutable training snapshots, and layer reports are reproducible from existing `aml_shadow_predictions` rows. Persisted report history can be added later as a separate audited migration without coupling the first analysis implementation to a new table.

## Interpretation Limits

Do not present the research score, score separation, anomaly-rate control, Silhouette score, Davies-Bouldin index, or Calinski-Harabasz index as fraud accuracy. True precision, recall, F1, and false-positive rate require confirmed reviewer outcomes. Cluster indices should only be introduced later for genuine clustering experiments; the four detectors in this phase are anomaly detectors, not clustering algorithms.
