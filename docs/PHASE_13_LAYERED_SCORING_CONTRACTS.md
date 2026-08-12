# Phase 13: Layered Scoring Architecture Contracts

## Purpose

This phase introduces the type boundaries required to refactor production AML scoring into separate behaviour, machine-learning, deterministic-rule, and risk-aggregation layers. It deliberately preserves the current production result and API shape.

## Current production map

```text
TransactionCreateService
  -> point-in-time FeatureEngineeringService
  -> FeaturePersistenceService
  -> AmlPredictionOrchestrator
       -> ConfiguredAnomalyPredictionService (legacy-compatible implementation)
            -> persisted-feature FastAPI v2 prediction
            -> promoted HST champion, otherwise Isolation Forest fallback
  -> prediction log, alert/case policy, learning eligibility, trusted profile update
```

Customer, peer, novelty, profile-confidence, and velocity calculations currently produce persisted features. They do not yet produce independent component scores.

## Introduced contracts

- `NormalizedScore`: finite raw score, normalized `0.0-1.0` score, and normalization version.
- `BehaviourScorer` and `BehaviourScore`.
- `CustomerBehaviourScore` and `PeerBehaviourScore`.
- `MlModelScore` and `MlModelScores`.
- `AmlRuleEngine`, `RuleEngineResult`, and `TriggeredRule`.
- `RiskPolicy`, `RiskPolicyRepository`, `RiskAggregationEngine`, `ComponentScores`, and `FinalRiskResult`.
- `AmlPredictionOrchestrator` as the future layered coordination boundary.

## Compatibility

`AmlPredictionOrchestrator` currently delegates to `ConfiguredAnomalyPredictionService`. Therefore:

- transaction API responses are unchanged;
- `anomalyVotes` remains available;
- current alert and case creation behavior is unchanged;
- HST champion and Isolation Forest fallback behavior is unchanged;
- no Python API changes are required in this phase.

## Database migration

No database migration is required for Phase 13.

A later additive migration will introduce versioned risk policies and component-level prediction/shadow-result persistence before the weighted engine affects production.

## API compatibility risks for later phases

- Existing clients consume `FraudPredictionResponse.anomalyVotes` and legacy `modelResults` maps.
- Existing anomaly configuration stores vote thresholds.
- Existing alert columns contain Isolation Forest, LOF, and SVM flags.
- FastAPI v2 HST and Isolation Forest scores do not yet share a normalization contract.

Later phases must add layered fields without removing legacy fields until shadow validation and controlled segment promotion are complete.

## Next phase

Phase 14 implements `CustomerBehaviourScorer` using transparent statistics, confidence adjustment, normalized score output, reason codes, and deterministic historical replay tests. It does not change alert creation.
