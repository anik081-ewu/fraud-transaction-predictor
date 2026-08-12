# Phase 01 — AML Schema Foundation

## Scope

This phase adds database foundations only. It does not change current Spring, Python, Angular, prediction, training, or alert behaviour.

## Existing tables retained

- `transactions` remains the authoritative raw transaction table.
- `fraud_prediction_logs` remains the active prediction log.
- Existing `training_runs` and `model_versions` remain untouched for compatibility with the current comparison pipeline.

## Additive changes

- Adds customer, business-date, and processing-status columns to `transactions`.
- Adds future AML audit fields to `fraud_prediction_logs` as nullable columns.
- Creates immutable transaction features.
- Creates observed and trusted customer profiles.
- Creates recent customer transaction state.
- Creates feature-learning eligibility state.
- Creates separate AML training-run and model-registry tables.

## Compatibility decisions

- Existing rows use `account_id` as the initial `customer_id` fallback.
- Existing transaction dates initialize `business_date`.
- New `customer_id` and `business_date` columns remain nullable during PR-01 so current Java inserts cannot fail before Phase 2 mappings exist.
- New prediction-log columns remain nullable until persisted-feature prediction is implemented.
- Transaction identifiers use `VARCHAR(50)` to match the existing authoritative table and permit SQL Server foreign keys.

## Execution

Run in SQL Server Management Studio:

1. `database/phase_01_aml_schema_foundation.sql`
2. `database/verify_phase_01_aml_schema.sql`

Take a database backup before applying the migration in a shared environment.

## Next phase

Phase 2 will introduce Java domain mappings and independently tested point-in-time feature calculators. It must not switch production prediction until persisted features match the existing feature pipeline.
