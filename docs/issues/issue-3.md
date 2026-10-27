# Issue #3

Issue: https://github.com/LED-FOR-CAR-DECORATION/backend/issues/3

Branch: `3-iam-service-authentication-register-account`

Review/PR base: `develop`

This branch is stacked on its prerequisite; review its diff against the base above. Merge in order #3 → #2 (follow-up) → #5 → #4 → #6. After the prerequisite merges, retarget the dependent PR to `develop`. Issue #2 was already closed; its branch contains follow-up work and does not reopen the issue. #7/#8 are not included because forgot/reset-password have not been implemented.

Validation: ./mvnw test -Dtest=RegistrationTest — 14 tests passed. Includes rollback, duplicate email and Swagger schema checks.
