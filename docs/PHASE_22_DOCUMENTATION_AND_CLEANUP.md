# Phase 22: Documentation and Cleanup

## Completed work

- Updated the runtime architecture to the layered weighted scoring design.
- Documented the exact distinction between production components and offline research models.
- Updated bank-scale guidance for HST, Online OCSVM, behavioural scorers, deterministic rules, weighted policy, canary routing, and Isolation Forest rollback.
- Added a full end-to-end manual acceptance checklist.
- Renamed the Angular training workspace to **Research Comparison**.
- Removed unreachable Angular scenario-library, comparison-run, and simulator API clients and their unused interfaces.
- Updated the generated user guide and system report source for controlled layered promotion.

## Deliberately retained compatibility code

Legacy anomaly-vote persistence and compatibility scoring remain because controlled rollout is segment-specific. Unpromoted segments and historical prediction/case records still require those contracts.

Deleting them now would violate the Phase 21 fallback guarantee. Their final removal requires a dedicated migration after all required segments have layered pointers and successful rollback drills.

## Database impact

No database migration is required for Phase 22.

## Acceptance result

The implementation satisfies the layered refactoring acceptance criteria:

- customer and peer components are named behavioural scorers;
- HST and Online OCSVM are the production ML models;
- velocity and AML rules remain deterministic;
- component outputs are normalized and reasoned;
- weighted policies are configurable and versioned;
- hard-rule overrides exist;
- prediction evidence retains component results;
- offline research models are separated from layered production;
- shadow validation and reversible controlled promotion are implemented;
- automated tests cover scoring, rules, aggregation, training, validation, routing, and rollback.

## Next implementation phase

Build scheduled weekly fresh reconstruction and historical replay, then add drift and rollout monitoring with alert thresholds and operator dashboards.
