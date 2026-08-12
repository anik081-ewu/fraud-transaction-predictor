# Phase 19: Layered Shadow Deployment

## Purpose

This phase connects the layered AML scoring architecture to transaction processing in shadow mode. Every eligible transaction can now receive two independent evaluations:

- the existing legacy production decision, which continues to control alerts and cases;
- the layered weighted-risk decision, which is stored for comparison only.

The production response object is returned unchanged. Layered scoring cannot create, suppress, or modify a production alert during this phase.

## Runtime flow

For a normal prediction, `AmlPredictionOrchestrator`:

1. calls the existing `ConfiguredAnomalyPredictionService`;
2. retains its response as the authoritative production result;
3. checks `aml.risk.layered_shadow_enabled` and `aml.risk.legacy_comparison_enabled`;
4. calculates customer, peer, model, rule, and weighted scores;
5. persists the legacy-versus-layered comparison;
6. returns the original legacy response instance.

Cold-start decisions also enter the same shadow comparison path. This makes the layered architecture's behavior on limited-history accounts measurable without changing the configured production cold-start policy.

## Layered evidence

The shadow service evaluates:

- Customer Behaviour Scorer;
- Peer Behaviour Scorer;
- deterministic velocity and AML rules;
- active Half-Space Trees normalized score;
- Online One-Class SVM normalized score;
- the active versioned weighted-risk policy.

Only model results containing a finite raw score, finite normalized score, and model version are accepted. Missing or incomplete model evidence remains `0.0` under the Phase 18 aggregation policy and produces an explicit unavailable reason. Weights are not renormalized.

## Model normalization

Half-Space Trees now exposes a bounded `0.0-1.0` empirical score. Its training mean is the normalization floor and its anomaly threshold maps to `1.0`. Scores are clamped to the valid range and tagged `HST_EMPIRICAL_THRESHOLD_V1`.

Online One-Class SVM continues to expose its bounded calibrated score tagged `ONLINE_OCSVM_EMPIRICAL_V1`.

Both raw and normalized values remain available in the Python prediction contract. The layered Java service consumes the normalized values and stores the corresponding model versions.

## Persisted comparison

`aml_shadow_predictions` stores one immutable comparison row per evaluation, including:

- transaction, account, feature, model, and risk-policy versions;
- legacy risk, suspicious flag, and anomaly votes;
- layered score, risk, suspicious flag, and hard-rule override;
- all five normalized component scores and behaviour confidence values;
- triggered rules, reason codes, component details, and full layered result JSON;
- suspicious-decision change, risk-band change, and alert-overlap flags;
- evaluation time and shadow duration.

Repeated evaluations of one transaction are allowed so policy or model versions can be replayed and compared historically. The table uses indexes for transaction history, time/risk analysis, and changed-decision analysis.

## Failure isolation

Layered scoring and persistence run after the legacy result is available. Any shadow exception is caught at the orchestrator boundary, logged with the transaction ID, and ignored by the production decision path.

This means:

- Python shadow-model unavailability cannot change the legacy decision;
- missing layered configuration cannot replace the production result;
- shadow-table failure cannot block the legacy response;
- alerts and automatic cases still use only legacy risk during this phase.

The failure remains operationally visible in application logs and must be monitored during rollout.

## Configuration

Two independent flags control execution:

```text
aml.risk.layered_shadow_enabled=true
aml.risk.legacy_comparison_enabled=true
```

The Phase 19 migration inserts missing flags but never overwrites existing values. An operator can therefore disable shadow execution without a later migration silently re-enabling it.

## Database rollout

Run these scripts in SSMS after Phase 18:

1. `database/phase_19_layered_shadow_predictions.sql`
2. `database/verify_phase_19_layered_shadow_predictions.sql`

The migration creates the table, score and JSON integrity constraints, analysis indexes, and missing configuration. The verification script checks required objects and returns overall comparison statistics plus the latest 100 evaluations.

## Operational verification

1. Start SQL Server, the Python ML service, and Spring Boot.
2. Confirm an active HST artifact and shadow Online OCSVM artifact are available.
3. Submit a normal transaction through the existing transaction API.
4. Confirm the API response and generated alert behavior still match the legacy decision.
5. Query `aml_shadow_predictions` for the transaction ID.
6. Confirm component values are bounded, versions are populated when models are available, and the legacy values equal the production response.
7. Stop the Python service or temporarily make the shadow table unavailable in a non-production test environment.
8. Confirm the legacy response still succeeds and the shadow failure is logged.

## Tests

Automated tests verify:

- the exact legacy response instance remains authoritative;
- enabled shadow mode invokes layered scoring;
- disabled shadow mode performs no layered work;
- shadow exceptions cannot change the production response;
- HST and Online OCSVM normalized scores and versions are extracted;
- weighted arithmetic is persisted with comparison flags;
- incomplete model output produces explicit unavailable reasons;
- HST normalized scores remain bounded and replayable.

## Next phase

Implemented in Phase 20: statistical shadow validation now blocks promotion until minimum volume, stability, alert-overlap, synthetic recall, reviewed outcome, latency, model-availability, and incremental-update gates pass.
