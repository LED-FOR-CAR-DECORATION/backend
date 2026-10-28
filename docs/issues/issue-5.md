# Issue #5

Issue: https://github.com/LED-FOR-CAR-DECORATION/backend/issues/5

Branch: `5-iam-service-authentication-refresh-token`

Review/PR base: `2-iam-service-authentication-login-with-email`

This branch is stacked on its prerequisite; review its diff against the base above. Merge in order #3 → #2 (follow-up) → #5 → #4 → #6. After the prerequisite merges, retarget the dependent PR to `develop`. Issue #2 was already closed; its branch contains follow-up work and does not reopen the issue. #7/#8 are not included because forgot/reset-password have not been implemented.

Review follow-up validation (2026-10-28): `./mvnw clean test` - 29 tests passed. See [schema-reference.md](../schema-reference.md) and [registration-verification.md](../registration-verification.md) for the updated contract.
