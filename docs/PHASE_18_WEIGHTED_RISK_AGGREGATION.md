# Phase 18: Weighted Risk Aggregation

## Purpose

This phase replaces the conceptual five-way equal vote with a dedicated, versioned weighted-risk component. It combines normalized evidence from components that remain logically distinct:

- Customer Behaviour Scorer;
- Peer Behaviour Scorer;
- Half-Space Trees;
- Online One-Class SVM;
- deterministic velocity and AML rules.

The engine is available as a Spring service but is not yet called by the production orchestrator. Legacy HST/Isolation Forest outcomes therefore continue to control alerts and cases until shadow deployment.

## Active V2 formula

All input values are normalized to `0.0-1.0` before aggregation:

```text
final weighted score =
    0.20 * customer behaviour
  + 0.15 * peer behaviour
  + 0.25 * Half-Space Trees
  + 0.15 * Online One-Class SVM
  + 0.25 * deterministic rules
```

The values are initial operational settings, not proven optimal weights. They are loaded from `app_config`; the aggregation class contains no default business weights.

## Versioned policy

`AppConfigRiskPolicyRepository` requires every policy key and constructs `AML_RISK_POLICY_V2`. Missing, non-numeric, out-of-range, misordered, or non-summing values fail explicitly. Hidden code defaults are not substituted.

The V2 thresholds are:

```text
0.00-0.39 = NORMAL
0.40-0.64 = LOW
0.65-0.79 = MEDIUM
0.80-1.00 = HIGH
```

`MEDIUM` and `HIGH` are suspicious outcomes. `NORMAL` and `LOW` are not suspicious.

## Missing components

Weights are never silently renormalized. If HST or Online OCSVM is unavailable, its component value is `0.0`, and the result contains one of:

- `HALF_SPACE_TREES_SCORE_UNAVAILABLE`
- `ONLINE_ONE_CLASS_SVM_SCORE_UNAVAILABLE`

This keeps scores comparable under one policy version and makes degraded evidence visible. Operational availability handling and promotion gates will be evaluated during shadow deployment.

## Hard-rule override

When the rule engine returns `hardAlert=true`:

- `hardRuleOverride=true`;
- `suspicious=true`;
- final risk level is `HIGH`;
- final risk score is raised to at least the configured HIGH threshold;
- `HARD_RULE_OVERRIDE` is included in reasons.

The original rule code, such as `SANCTIONS_MATCH`, remains present. A hard override does not claim model consensus.

## Explanation contract

The result persists the policy version in its contract and returns:

- normalized final score;
- final risk band and suspicious flag;
- hard-rule override flag;
- all five normalized component scores;
- deduplicated, deterministically sorted reason codes.

Stable reason ordering makes historical replay and shadow-result comparison reproducible.

## Persistence preparation

The migration adds nullable `risk_policy_version` when absent and preserves the existing nullable `final_risk_score`. `FraudPredictionLog` maps both fields.

These values are intentionally not written by the current production flow yet because the layered result does not control production. Phase 7 will run both architectures, then persist the policy version, layered result, component details, and legacy comparison together.

## Configuration migration

Run `database/phase_18_weighted_risk_policy.sql` in SSMS after the earlier AML migrations. It:

- inserts the V2 policy version, five weights, and three thresholds into `app_config`;
- enables layered shadow and legacy comparison flags;
- adds only the missing nullable prediction-log audit columns.

## Compatibility boundary

- The legacy production decision remains unchanged.
- Isolation Forest remains a marked batch fallback during migration.
- No equal-vote code is removed.
- No alert or case threshold is changed.
- The layered engine will first be connected in Phase 7 shadow mode.

## Tests

Tests verify:

- exact weighted arithmetic;
- NORMAL, LOW, MEDIUM, and HIGH boundaries;
- suspicious classification;
- hard-rule override behavior;
- unavailable-model treatment;
- deterministic reason deduplication and replay;
- complete app-config policy loading;
- rejection of incomplete configuration.

## Next phase

Implemented in Phase 19: the layered architecture now runs in shadow mode and persists legacy-versus-layered comparisons without changing production alert creation.
