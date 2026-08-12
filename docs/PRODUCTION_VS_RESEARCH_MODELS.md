# Production and Research Model Boundary

## Production components

| Component | Classification | Production role |
|---|---|---|
| Velocity and AML rules | Deterministic control | Detect configured transaction patterns and apply hard overrides. |
| Customer behavioural scorer | Transparent statistical scorer | Measure deviation from trusted customer history. |
| Peer-group behavioural scorer | Transparent statistical scorer | Measure deviation from comparable customer behaviour. |
| Half-Space Trees | Production ML model | Incremental tree-based anomaly evidence with bounded state. |
| Online One-Class SVM | Production ML model | Incremental normal-boundary evidence. |
| Weighted risk policy | Versioned decision policy | Combine normalized component scores, confidence, and overrides. |
| Isolation Forest | Production rollback fallback | Preserve a known batch decision when layered scoring is disabled or unsafe. |

Only HST and Online OCSVM are described as production ML models. Behaviour scorers and rules must not be relabeled as ML models merely because their normalized outputs enter the same aggregator.

## Offline research models

The following models exist to compare anomaly behaviour as data grows:

- Isolation Forest;
- Local Outlier Factor;
- kernel One-Class SVM;
- Elliptic Envelope;
- PCA Reconstruction.

They are evaluated on nested chronological partitions and a common future holdout. The comparison reports proxy quality, stability, rate control, fit score, anomaly rate, latency, artifact size, and growth trend.

LOF, kernel One-Class SVM, Elliptic Envelope, and PCA Reconstruction cannot affect layered production alerts or cases. A research leaderboard is not a production deployment recommendation.

## Terminology rules

Use these phrases:

- **layered production risk architecture** for the weighted production path;
- **offline five-model research comparison** for the chronological experiment;
- **component score** for normalized rule, behavioural, or ML evidence;
- **final risk score** for the weighted policy output;
- **legacy compatibility path** for an unpromoted segment during migration;
- **Isolation Forest rollback fallback** for an explicit fallback deployment pointer.

Avoid these phrases:

- five production models;
- five equal-voting models;
- production voting ensemble;
- clustering models when referring to anomaly detectors;
- fraud accuracy when representative confirmed labels are unavailable.

## Migration boundary

The compatibility path remains necessary while selected segments are promoted gradually. Removing its voting configuration or stored `anomaly_votes` fields before all required segments are migrated would break unpromoted routing and historical audit records.

It can be retired only after:

1. every required segment has a verified layered deployment pointer;
2. a fallback drill succeeds for every segment;
3. monitoring confirms acceptable layered operation;
4. historical APIs no longer require legacy response reconstruction;
5. a dedicated database migration preserves historical audit semantics.
