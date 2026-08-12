# Phase 04 — Observed and Trusted Profiles

## Database deployment

Run these scripts in SSMS in order:

1. `database/phase_04_profile_separation_backfill.sql`
2. `database/verify_phase_04_profile_separation.sql`

The backfill is idempotent. It creates missing profile state from legacy transactions and excludes unresolved or suspicious alerted transactions from the trusted baseline. Reviewed false positives remain eligible.

## Runtime behaviour

- Every scored API transaction updates the observed profile.
- Only non-suspicious `NORMAL` and `LOW` transactions update the trusted profile.
- `MEDIUM`, `HIGH`, and suspicious transactions remain visible in recent state with `trusted_flag = 0`.
- `FROZEN` and `UNDER_REVIEW` trusted profiles are not updated.
- Feature generation reads the trusted profile and recent-state table before scoring the current transaction.
- Raw transaction history remains a compatibility fallback when recent state has not been backfilled.

## Current limitations

- The API still maps `customer_id` to `account_id` because no separate customer identifier is supplied.
- Beneficiary and device fields are unavailable in the current transaction request.
- Dominant channel and location retain their initial values until frequency counters are introduced.
- Review-driven release of delayed transactions belongs to the learning-eligibility phase.
