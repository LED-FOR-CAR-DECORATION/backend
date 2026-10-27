# Issue #4

Issue: https://github.com/LED-FOR-CAR-DECORATION/backend/issues/4

Branch: `4-iam-service-authentication-google-oauth-login`

Review/PR base: `5-iam-service-authentication-refresh-token`

This branch is stacked on its prerequisite; review its diff against the base above. Merge in order #3 → #2 (follow-up) → #5 → #4 → #6. After the prerequisite merges, retarget the dependent PR to `develop`. Issue #2 was already closed; its branch contains follow-up work and does not reopen the issue. #7/#8 are not included because forgot/reset-password have not been implemented.

Validation: ./mvnw clean test — 30 tests passed. Includes RSA signature/claims, identity linking, management restrictions, migrations and startup.
