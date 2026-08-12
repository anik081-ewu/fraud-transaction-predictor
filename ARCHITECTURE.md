# Fraud Transaction Detector Architecture

## Runtime applications

The project runs as three applications:

1. `fraud-transaction-detector`: Spring Boot modular monolith on port `8080`.
2. `fraud-ml-service`: Python FastAPI machine-learning service on port `8000`.
3. `fraud-transaction-ui`: Angular web application on port `4200`.

Microsoft SQL Server is the operational system of record. Spring Boot is the only application that accesses it directly.

```text
Angular :4200
      |
      v
Spring Boot :8080 -----------------------> SQL Server
      |
      +---- persisted feature vector ---> FastAPI :8000
      |                                      |
      |                                      +-> versioned model artifacts
      |
      +---- Parquet training command ------> incremental trainers
```

No separately deployed API gateway, authentication service, or case service is required. Authentication, transaction processing, AML scoring orchestration, governance, and case management are internal Spring modules.

## Production decision architecture

Production uses weighted component evidence rather than model vote counting. The layered architecture combines normalized evidence from:

- deterministic velocity and AML rules;
- customer behavioural scoring;
- peer-group behavioural scoring;
- Half-Space Trees (`HALF_SPACE_TREES`);
- Online One-Class SVM (`ONLINE_ONE_CLASS_SVM`);
- a configurable, versioned weighted risk policy with hard-rule overrides.

Only Half-Space Trees and Online One-Class SVM are classified as production ML models. Customer and peer components are transparent behavioural scorers, while velocity and AML rules remain deterministic controls.

```text
Persisted feature vector
        |
        +--> Velocity + AML rules -----------+
        +--> Customer behaviour scorer ------+
        +--> Peer-group behaviour scorer ----+--> Weighted risk policy
        +--> Half-Space Trees ---------------+         |
        +--> Online One-Class SVM ------------+         v
                                                    risk + reasons
                                                         |
                                              alert/case threshold
```

Every component produces a normalized score and reason metadata. The weighted result, component evidence, policy version, and exact model versions are persisted for audit.

## Controlled production routing

The layered architecture is enabled per peer group through an authorized, reversible deployment pointer:

1. Layered scores run in shadow beside the legacy path.
2. Phase 20 validation measures volume, overlap, stability, synthetic recall, reviewed outcomes, latency, availability, and exact versions.
3. A recent passing report can be promoted by `ADMIN` or `AML_ADMIN`.
4. SHA-256 account bucketing routes a stable 1-100% canary cohort.
5. Runtime output is accepted only when policy, HST, and Online OCSVM versions match the pointer exactly.
6. Any scoring failure or mismatch uses the retained Isolation Forest production fallback.
7. Rollback sets the entire segment to `ISOLATION_FOREST_FALLBACK` immediately.

Unpromoted segments retain the compatibility decision path during migration. It must not be removed until every required segment has a verified layered pointer and rollback procedure.

## Research comparison boundary

The project retains its primary academic experiment: compare model behaviour at the oldest 10%, 25%, 50%, and 100% of one frozen chronological dataset.

The research models are:

- Isolation Forest;
- Local Outlier Factor;
- kernel One-Class SVM;
- Elliptic Envelope;
- PCA Reconstruction.

LOF, kernel One-Class SVM, Elliptic Envelope, and PCA Reconstruction cannot affect layered production alerts or cases. Their fit score, proxy quality, stability, anomaly rate, latency, and growth trends are research evidence, not confirmed-fraud accuracy.

## Training and governance

Spring creates closed-date training runs and exports eligible persisted features into checksummed, chunked Parquet datasets. Python trains HST and Online OCSVM candidates incrementally and writes immutable versioned bundles. Spring verifies checksums and records registry entries.

Training never deploys a model automatically. Candidate validation, architecture validation, model promotion, layered canary activation, expansion, and rollback are distinct authenticated actions with immutable audit events.

## Case management

The final production risk result controls alert and automatic case creation. Analysts can inspect component evidence, add notes, mark a case false positive, or generate draft STR XML. The system reports suspicious activity; it does not claim fraud confirmation.

## Future scale path

Kafka, a schema registry, durable object storage, and distributed state stores remain planned extensions. Kafka should be keyed by account/customer ID to preserve per-customer ordering. SQL Server remains authoritative, with an outbox preventing event dual-write loss.
