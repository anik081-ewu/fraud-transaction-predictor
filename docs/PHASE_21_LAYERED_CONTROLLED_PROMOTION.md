# Phase 21: Layered Controlled Promotion

## Objective

Phase 21 moves the validated layered risk architecture into production for explicitly selected customer segments. The release is reversible, version-locked, auditable, and gradually exposed through deterministic canary routing.

The production architecture combines:

- velocity and AML rules;
- customer streaming statistical scoring;
- peer-group streaming scoring;
- Half-Space Trees;
- Online One-Class SVM;
- the versioned weighted risk policy.

Isolation Forest remains the production fallback. A promotion never deletes or overwrites that fallback.

## Safety boundary

A segment can be promoted only when all of the following conditions hold:

1. the caller has `ADMIN` or `AML_ADMIN` authority;
2. layered shadow scoring and legacy comparison are enabled;
3. a Phase 20 report for the exact segment has status `PASSED`;
4. the report is no older than `aml.layered_deployment.max_validation_age_days`;
5. exactly one HST version and one Online OCSVM version occur in the validation window;
6. both model artifacts exist, their checksums match the registry, and their feature versions are compatible;
7. the validated risk policy and model versions are persisted as one immutable release bundle.

Promotion fails closed when any check cannot be completed.

## Database state

`aml_layered_deployment_pointers` stores one current routing pointer per peer group. It records:

- `LAYERED_ACTIVE` or `ISOLATION_FOREST_FALLBACK` mode;
- exact risk-policy, HST, and Online OCSVM versions;
- the supporting validation report;
- canary percentage;
- optimistic pointer version;
- actor and activation time.

`aml_layered_deployment_events` is the immutable audit history. Every promotion, canary expansion, and rollback has a unique action ID so retries are idempotent.

## Deterministic canary routing

The service hashes the account ID with SHA-256 and maps it to bucket 1–100. An account enters layered production when its bucket is at or below the configured canary percentage.

This gives three important properties:

- the same account remains on the same route across requests and restarts;
- increasing 10% to 25% keeps the original cohort and adds a stable 15%;
- no database lookup or random state is required for each routing choice.

The percentage applies only to the selected peer group. Other segments remain unchanged.

## Runtime fallback behavior

The orchestrator always obtains the legacy/fallback result. It returns layered production output only when:

- the account is inside the segment canary;
- layered scoring completes successfully;
- the runtime risk-policy, HST, and Online OCSVM versions exactly match the active pointer.

Any exception, unavailable score, or version mismatch returns the Isolation Forest fallback result. A pointer in `ISOLATION_FOREST_FALLBACK` mode explicitly disables layered production for the entire segment while preserving shadow diagnostics.

Layered alerts and cases are created from the weighted final risk result. `anomalyVotes` remains zero because layered scoring does not use equal model voting.

## API operations

All write operations require the normal bearer token.

### Validate current shadow evidence

```http
POST /api/v1/aml/layered-shadow/validate
Content-Type: application/json

{
  "peerGroupCode": "RETAIL_SALARIED",
  "validatedBy": "aml-reviewer"
}
```

### Activate a 10% canary

Use the `validationId` returned by a passing report and generate a new UUID for `actionId`.

```http
POST /api/v1/aml/layered-deployments/promote
Authorization: Bearer <token>
Content-Type: application/json

{
  "actionId": "11111111-1111-4111-8111-111111111111",
  "validationId": "22222222-2222-4222-8222-222222222222",
  "peerGroupCode": "RETAIL_SALARIED",
  "canaryPercentage": 10,
  "reason": "Phase 20 evidence passed and the 10 percent release was approved."
}
```

### Expand an existing canary

Call the same `/promote` endpoint with the same validation ID, a new action ID, and a higher percentage. Canary percentages cannot move backward through promotion. Use rollback when production exposure must stop.

### Roll back immediately

```http
POST /api/v1/aml/layered-deployments/rollback
Authorization: Bearer <token>
Content-Type: application/json

{
  "actionId": "33333333-3333-4333-8333-333333333333",
  "peerGroupCode": "RETAIL_SALARIED",
  "reason": "Operational monitoring exceeded the approved release threshold."
}
```

Rollback sets the segment pointer to `ISOLATION_FOREST_FALLBACK` with zero layered traffic. It does not delete the validated bundle or audit history.

### Read current state and history

```http
GET /api/v1/aml/layered-deployments/active
GET /api/v1/aml/layered-deployments/history?peerGroupCode=RETAIL_SALARIED
GET /api/v1/aml/layered-shadow/validations
```

## Angular workflow

Open **Model Governance** and use **Layered architecture rollout**:

1. enter and apply the customer segment;
2. run shadow validation;
3. review agreement, alert overlap, alert rate, synthetic recall, latency, and exact versions;
4. enter the approved canary percentage and an audit reason;
5. activate or expand the canary;
6. monitor the mode, production percentage, pointer version, and audit history;
7. select **Rollback to Isolation Forest** if operational evidence requires immediate fallback.

The backend remains the authority. Disabled UI controls are convenience safeguards, not security controls.

## Recommended rollout sequence

Use a conservative progression such as 5%, 10%, 25%, 50%, and 100%. At each stage compare:

- alert and case volume;
- reviewed false-positive outcomes;
- score availability and version mismatches;
- p95 prediction latency and service errors;
- customer-segment concentration;
- synthetic scenario coverage;
- fallback frequency.

Do not expand solely because elapsed time has passed. Require reviewed operational evidence and a documented approver decision.

## Database rollout

Run in SSMS after all scripts through Phase 20:

1. `database/phase_21_layered_controlled_promotion.sql`
2. `database/verify_phase_21_layered_controlled_promotion.sql`

The migration is additive and does not activate any segment. Production remains on the current path until an authorized promotion request succeeds.
