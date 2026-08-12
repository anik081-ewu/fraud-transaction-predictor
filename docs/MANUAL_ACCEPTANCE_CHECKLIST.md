# End-to-End Manual Acceptance Checklist

## Test record

- Tester:
- Date and time:
- Application version:
- Database/migration level:
- Feature version:
- Risk-policy version:
- Browser:
- Evidence location:

Record screenshots, API responses, relevant database rows, and generated files for every failed or safety-critical check.

## 1. Startup and authentication

- [ ] Start SQL Server and confirm the target database is reachable.
- [ ] Start FastAPI and open `http://localhost:8000/docs`.
- [ ] Start Spring Boot and verify `GET http://localhost:8080/health` returns `UP`.
- [ ] Start Angular and open `http://localhost:4200`.
- [ ] Register a user and confirm self-registration creates `REVIEWER`, not an administrator.
- [ ] Log in and confirm the JWT is attached to protected requests.
- [ ] Confirm a reviewer cannot open administrator routes.
- [ ] Confirm `ADMIN` or `AML_ADMIN` can open training and governance actions.
- [ ] Sign out and confirm protected pages redirect to login.

## 2. Transaction ingestion

- [ ] Upload a valid CSV or Excel batch and verify total, success, and failure counts.
- [ ] Upload a file with invalid rows and verify valid rows remain traceable while failures report row-level reasons.
- [ ] Create a transaction through the API and verify it is stored with API source metadata.
- [ ] Verify the feature vector uses only history before the transaction timestamp.
- [ ] Retry the same transaction identity and verify duplicate handling is safe.

## 3. Offline research comparison

- [ ] Open **Research Comparison** and confirm it is labeled as offline research.
- [ ] Confirm fewer than 200 eligible rows blocks comparison clearly.
- [ ] Run a database snapshot with sufficient rows.
- [ ] Verify partitions represent oldest 10%, 25%, 50%, and 100% data.
- [ ] Verify all five research models run sequentially.
- [ ] Confirm reports show fit score, proxy quality, stability, anomaly rate, growth, latency, and artifact size.
- [ ] Confirm the UI states that proxy metrics are not confirmed-fraud accuracy.
- [ ] Confirm no research result automatically changes a layered production pointer.

## 4. Governed incremental training

- [ ] Close every business date included in the intended window.
- [ ] Create a training run with feature version, model type, segment, cutoff, and date range.
- [ ] Generate the dataset and verify checksummed Parquet parts and manifest.
- [ ] Train an HST candidate and verify immutable registry metadata.
- [ ] Train an Online OCSVM candidate and verify immutable registry metadata.
- [ ] Restart the ML service and verify registered model state can be loaded.
- [ ] Confirm training never activates a candidate automatically.
- [ ] Attempt an incompatible base model or feature version and verify safe rejection.

## 5. Model and architecture validation

- [ ] Generate silent predictions without changing production alerts or cases.
- [ ] Validate a candidate and verify minimum evidence and configured gates.
- [ ] Confirm `INSUFFICIENT_DATA` and `FAILED` reports cannot be promoted.
- [ ] Run layered shadow validation for a specific peer group.
- [ ] Review alert volume, agreement, Jaccard overlap, daily stability, synthetic recall, reviewed outcomes, latency, availability, and version counts.
- [ ] Confirm a passing layered report contains exactly one HST and one Online OCSVM version.
- [ ] Confirm an expired validation report cannot authorize promotion.

## 6. Controlled promotion and rollback

- [ ] Attempt promotion as a reviewer and verify authorization failure.
- [ ] Activate a small layered canary with a unique action ID and audit reason.
- [ ] Verify the pointer locks the exact policy, HST, Online OCSVM, and validation versions.
- [ ] Submit the same action ID again and verify idempotent replay rather than duplicate change.
- [ ] Verify the same account remains in the same deterministic canary bucket.
- [ ] Expand the canary and verify the original cohort remains selected.
- [ ] Attempt to reduce canary exposure through promotion and verify rejection.
- [ ] Introduce a model-version mismatch and verify the compatibility result is used.
- [ ] Simulate layered scoring failure and verify fail-safe fallback.
- [ ] Roll back and verify mode becomes `ISOLATION_FOREST_FALLBACK` with 0% layered traffic.
- [ ] Verify promotion, expansion, and rollback events appear in immutable history.

## 7. Production transaction and case flow

- [ ] Submit a normal transaction and verify no automatic case is generated.
- [ ] Submit a transaction meeting the weighted suspicious policy and verify alert and case creation.
- [ ] Verify the prediction log contains final risk score, policy version, component evidence, reason codes, model versions, and learning decision.
- [ ] Confirm layered production does not present equal model votes as its decision method.
- [ ] Verify cold-start handling for an account below minimum trusted history.
- [ ] Confirm suspicious transactions do not contaminate trusted profile learning.

## 8. Case management and STR

- [ ] Open the generated case and verify transaction and prediction evidence.
- [ ] Add an attributed, timestamped analyst note.
- [ ] Mark one case false positive and verify the linked alert outcome.
- [ ] Confirm a false-positive case cannot generate STR XML.
- [ ] Generate draft STR XML for another case and verify status and download.
- [ ] Confirm an STR-generated case cannot later be marked false positive.
- [ ] Search a transaction and create a manual case.
- [ ] Repeat manual creation and verify the existing case is returned instead of duplicated.

## 9. Failure and recovery

- [ ] Stop FastAPI and verify Spring returns the documented auditable fallback behavior.
- [ ] Restart FastAPI and verify scoring recovers without corrupting transaction state.
- [ ] Test expired JWT, CORS rejection, invalid request, and unavailable database handling.
- [ ] Verify failed export/training jobs preserve failure reasons and do not alter active models.
- [ ] Verify artifact checksum tampering blocks validation or promotion.
- [ ] Restart Spring and confirm active pointers and audit history remain intact.

## 10. Exit criteria

- [ ] Java automated tests pass.
- [ ] Python automated tests pass.
- [ ] Angular production build passes.
- [ ] No unresolved severity-high defect remains.
- [ ] Every safety-critical failure has captured evidence.
- [ ] Rollback was demonstrated, not merely inferred.
- [ ] The operator and AML reviewer approve the result.

## Final result

- Overall status: PASS / FAIL / PASS WITH CONDITIONS
- Blocking defects:
- Accepted limitations:
- Reviewer approval:
- Technical approval:
