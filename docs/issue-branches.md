# Code organization by GitHub issue

Repository: https://github.com/LED-FOR-CAR-DECORATION/backend

Branch names follow the repository's existing convention: `<issue-number>-iam-service-authentication-<feature>`. The branch stack starts at `develop`. Each diff against the review base below contains the issue's changes and required integration updates.

| Order | Issue | Branch | Review / PR base | Scope | Passing tests |
| --- | --- | --- | --- | --- | --- |
| 1 | [#3 Register](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/3) | `3-iam-service-authentication-register-account` | `develop` | Registration DTO/use case/API, validation, BCrypt, default Customer, errors and Swagger | 14 RegistrationTest tests |
| 2 | [#2 Login follow-up](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/2) | `2-iam-service-authentication-login-with-email` | `3-iam-service-authentication-register-account` | Management login, session/token types, role authorities, lockout/reset/audit, Flyway V1/V2, security filter | 20 tests |
| 3 | [#5 Refresh](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/5) | `5-iam-service-authentication-refresh-token` | `2-iam-service-authentication-login-with-email` | Refresh endpoint, atomic rotation, replay prevention and request validation | 22 tests |
| 4 | [#4 Google](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/4) | `4-iam-service-authentication-google-oauth-login` | `5-iam-service-authentication-refresh-token` | Google verification/linking/creation, management Google login, OAuth dependency, migration V3 | 30 tests |
| 5 | [#6 Logout](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/6) | `6-iam-service-authentication-logout` | `4-iam-service-authentication-google-oauth-login` | Revoking all sessions, Redis blacklist, cache/database checks and consolidated documentation | 33 tests |

## Review and merge

Review against the listed base to avoid reviewing prerequisite changes again. Merge in order: #3 -> #2 follow-up -> #5 -> #4 -> #6. After the prerequisite branch merges into `develop`, retarget the next PR to `develop`. Squash or rebase merges require restacking dependent branches before proceeding; do not force-push outside the team's review process.

Issue #2 was closed before the split. Its branch contains login follow-up changes; creating the branch did not reopen the issue. This work does not change issue statuses or acceptance checklists.

Issues [#7 Forgot Password](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/7) and [#8 Reset Password](https://github.com/LED-FOR-CAR-DECORATION/backend/issues/8) have no backend implementation in this delivery. No empty branches were created and those issues were not marked complete.

The English documentation update is propagated through the stack using merge commits, preserving published history without force-pushes.

## Running tests

On branch #3, run `./mvnw test -Dtest=RegistrationTest`. From branch #2 onwards, run `./mvnw clean test`. Tests use an isolated H2 database, real migrations and application startup test configuration. Counts in the table are cumulative at each stage of the stack.

## Code preservation and migrations

The pre-split snapshot remains on local branch `backup/auth-before-issue-split-20261027` at commit `142455861df573b7b8e76b8e17149bc17deaf815`. At the start of the split, `develop` pointed to `17463c5f9bda469a56630fad707b78b62b8f3a62`. Frontend changes are not included in these backend branches.

The final branch preserves the tested implementation, with subsequent text-only English updates. The combined migration was split into V2 (`auth_sessions`, issue #2) and V3 (`google_identities`, issue #4) before it was applied to a real database in this session. If another environment has already applied the old combined migration, do not replace a migration whose checksum is recorded; inspect its history before deployment. The English update leaves migration filenames and SQL contents unchanged.

See [auth-api.md](auth-api.md) for API contracts and configuration, and [issues/](issues/) for individual issue notes.
