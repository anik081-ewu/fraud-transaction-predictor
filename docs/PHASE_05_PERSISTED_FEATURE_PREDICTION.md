# Phase 05 — Prediction from Persisted Features

## Database deployment

Run these scripts in SSMS before restarting Spring Boot:

1. `database/phase_05_persisted_model_input.sql`
2. `database/verify_phase_05_persisted_model_input.sql`

Existing feature rows are marked `LEGACY_UNAVAILABLE` because their exact historical model-input map cannot be reconstructed safely. New rows store the precise versioned model input in `model_features_json`.

## Runtime flow

1. Spring calculates the point-in-time AML feature vector.
2. Spring creates the current artifact-compatible model-input map.
3. Both are inserted into the immutable feature row.
4. Spring calls `POST /api/v2/fraud/predict` with that saved map.
5. Python aligns the supplied values to the artifact's stored feature columns and scores the selected models.
6. Python does not recalculate transaction or history features in the v2 path.
7. Spring applies the configured voting threshold.
8. Phase 12 removed the legacy v1 fallback. If v2 cannot score, Spring returns an auditable `NORMAL / ALLOW_AND_LOG` operational result.

## Compatibility

- `POST /api/v1/fraud/predict` remains deprecated only until the Phase 12 migration sunset and is never called by production Spring code.
- `POST /api/v1/fraud/compare` remains available for existing comparison screens.
- Current artifacts use `LEGACY_MODEL_INPUT_V1`.
- Future persisted-feature-native artifacts can introduce a new model feature schema without changing the immutable feature version.

## Operational check

After both services restart, create one API transaction and confirm:

- `aml_transaction_features.model_feature_schema = 'LEGACY_MODEL_INPUT_V1'`;
- `model_features_json` is valid JSON and is not empty;
- the API response `featureSummary.scoringContract` equals `PERSISTED_FEATURES_V2`.

If `scoringContract` is absent, inspect the Spring logs for the v2 fallback warning and verify that the Python service exposes `/api/v2/fraud/predict`.
