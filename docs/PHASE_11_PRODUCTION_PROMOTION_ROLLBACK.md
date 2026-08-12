# Phase 11: Production Promotion and Rollback

## Purpose

Phase 11 makes deployment explicit, authorized, atomic, audited, idempotent, and reversible. A successful Phase 10 validation does not change production. Only an ADMIN or AML_ADMIN bearer token can promote or roll back a model.

## Database migration

Run these scripts in SSMS in order:

1. `database/phase_11_production_promotion_rollback.sql`
2. `database/verify_phase_11_production_promotion_rollback.sql`

The migration creates:

- `aml_active_models`: one active pointer for each model type and segment, including a previous-model rollback pointer and monotonic pointer version.
- `aml_model_deployments`: immutable PROMOTION and ROLLBACK audit events.

`actionId` is unique. Repeating the same request returns the existing event instead of applying the deployment twice.

## Promote a validated model

```http
POST /api/v1/aml/models/{modelVersion}/promote
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "actionId": "5beabf1c-3743-4cc4-bc75-e604c8f44689",
  "reason": "Phase 10 validation passed and AML governance approved deployment"
}
```

Promotion performs one database transaction:

1. Verify the caller has ADMIN or AML_ADMIN role.
2. Require the candidate to be VALIDATED.
3. Recalculate and verify the immutable artifact checksum.
4. Lock the active pointer for the model type and segment.
5. Move the previous CHAMPION to CHALLENGER.
6. Move the candidate to CHAMPION and set approval/deployment timestamps.
7. Update the active pointer and append the audit event.

## Roll back an active model

```http
POST /api/v1/aml/models/{activeModelVersion}/rollback
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "actionId": "4a225ef1-f82d-4ef7-a830-5dc9e8be8758",
  "reason": "Post-deployment anomaly volume exceeded the operational limit"
}
```

Rollback requires the URL model to be the active CHAMPION and the pointer to contain a compatible previous CHALLENGER. It verifies the previous artifact before atomically restoring it.

## Deployment history

```http
GET /api/v1/aml/models/deployments?modelType=HALF_SPACE_TREES&modelSegment=RETAIL_GENERAL
```

The Angular governance page also reads all active pointers from:

```http
GET /api/v1/aml/models/active
```

## Production scoring

For each transaction, Spring resolves an exact segment pointer and falls back to a GLOBAL pointer. The Python service verifies the active artifact's feature and model versions, then returns `HalfSpaceTrees` with `affectsProductionDecision=true`.

The selected Half-Space Trees champion controls the anomaly decision for that segment. Batch models remain in the response as diagnostics and remain the operational fallback if active-model scoring is unavailable. Non-promoted HST versions continue silent challenger scoring and cannot affect alerts or cases.

## Safety guarantees

- Validation alone never deploys a model.
- Artifact tampering blocks promotion and rollback.
- Database row locking and a single transaction prevent partial pointer/status updates.
- Every deployment requires an explicit reason and authenticated administrator.
- Every action is reversible while its previous champion remains registered.
