# Code organization by GitHub issue

Repository: https://github.com/LED-FOR-CAR-DECORATION/backend

Branch names follow the repository's existing convention: `<issue-number>-iam-service-authentication-<feature>`. The branch stack starts at `develop`. Each diff against the review base below contains the issue's changes and required integration updates.

| Order | Issue | Branch | Review / PR base | Scope | Passing tests |
| --- | --- | --- | --- | --- | --- |
| 1 | [#3 Register](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/3) | `3-iam-service-authentication-register-account` | `develop` | Registration factories, canonical email, Redis OTP confirmation, email delivery and Swagger | 23 tests |
| 2 | [#2 Login follow-up](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/2) | `2-iam-service-authentication-login-with-email` | `3-iam-service-authentication-register-account` | Shared email login, direct REFRESH_TOKEN records, lockout/reset/audit, no Flyway | 27 tests |
| 3 | [#5 Refresh](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/5) | `5-iam-service-authentication-refresh-token` | `2-iam-service-authentication-login-with-email` | Refresh endpoint, atomic rotation, replay prevention and request validation | 29 tests |
| 4 | [#4 Google](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/4) | `4-iam-service-authentication-google-oauth-login` | `5-iam-service-authentication-refresh-token` | Verified email-based Google login, management Google checks, approved schema | 37 tests |
| 5 | [#6 Logout](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/6) | `6-iam-service-authentication-logout` | `4-iam-service-authentication-google-oauth-login` | Revoking all sessions, Redis blacklist, cache/database checks and consolidated documentation | 40 tests |

## Review and merge

Review against the listed base to avoid reviewing prerequisite changes again. Merge in order: #3 -> #2 follow-up -> #5 -> #4 -> #6. After the prerequisite branch merges into `develop`, retarget the next PR to `develop`. Squash or rebase merges require restacking dependent branches before proceeding; do not force-push outside the team's review process.

Issue #2 was closed before the split. Its branch contains login follow-up changes; creating the branch did not reopen the issue. This work does not change issue statuses or acceptance checklists.

Issues [#7 Forgot Password](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/7) and [#8 Reset Password](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/8) have no backend implementation in this delivery. No empty branches were created and those issues were not marked complete.

The English documentation update is propagated through the stack using merge commits, preserving published history without force-pushes.

## Running tests

Run `./mvnw clean test` on every branch. Tests use isolated H2 databases with Hibernate create-drop and mocked provider/Redis transports. Counts in the table are cumulative at each stage of the stack.

## Review follow-up (2026-10-28)

All branches include the latest `develop` permission update and its missing-comma build fix. Review changes remove fullName from signup, add entity factories and OTP email confirmation, use a single password login route, remove Flyway, and map refresh records to the current Drive ERD. See [schema-reference.md](schema-reference.md), [architecture-exceptions.md](architecture-exceptions.md), and [auth-api.md](auth-api.md).

The existing PRs #10-#14 are updated in place. Issue statuses and reviewer discussions are not marked resolved automatically. The pre-review code remains on local branch `backup/auth-before-review-20261028`.
