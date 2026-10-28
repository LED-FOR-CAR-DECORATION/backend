# Authentication API contracts

## Current schema and configuration

The [approved Drive schema](schema-reference.md) defines `REFRESH_TOKEN` with a direct `account_id` foreign key. There are no `auth_sessions` or `google_identities` tables and no added refresh-token columns on `user_account`. Flyway is removed; development uses Hibernate `create` under the [documented temporary exception](architecture-exceptions.md). Production retains `validate`. Do not start the development profile against data that must be preserved.

## Endpoints

| Method | Endpoint | Request | Success |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | `email`, `password`, `confirmPassword` | 201: `data` contains `userId`, `email`, `status: PENDING_VERIFICATION` |
| POST | `/api/auth/verify-email` | `email`, six-digit `otp` | 200: account status becomes `ACTIVE` |
| POST | `/api/auth/resend-verification` | `email` | 202, no body |
| POST | `/api/auth/login` | `email`, `password` | 200: JWT pair; both Customer and Employee accounts use this endpoint |
| POST | `/api/auth/google` | Google `idToken`, optional linking `password` | 200: JWT pair |
| POST | `/api/management/auth/google` | Google `idToken`, optional linking `password` | 200: JWT pair; an active Employee account must already exist |
| POST | `/api/auth/refresh` | `refreshToken` | 200: rotated JWT pair |
| POST | `/api/auth/logout` | Bearer access token, no body | 204: all account refresh records revoked |

There is no `/api/management/auth/login` endpoint. See [registration-verification.md](registration-verification.md) for OTP delivery, cooldown, attempts and failure recovery. Full name is not accepted by the registration DTO; the profile can be completed later.

## Tokens and revocation

Login returns `data: { accessToken, refreshToken, tokenType: "Bearer", expiresIn: 900 }`. Access tokens last 15 minutes. Each login creates a refresh record with an absolute 30-day expiry. JWT `sid` contains that record's ID; `type` distinguishes access and refresh tokens and each token has a unique `jti`.

Only SHA-256 refresh hashes are stored. Issue, rotation and logout serialize on the account row. Rotation verifies signature/type/expiry, record ownership, revocation and hash, then replaces the stored hash in one transaction. At most one concurrent request using the same refresh token succeeds; the old token is invalid immediately. Rotation does not extend the original 30-day expiry. Clients must coalesce refresh requests and store the returned pair; do not automatically retry a timed-out rotation.

Protected APIs check that the referenced refresh record remains active. Logout sets `revoked_at` on every account refresh record before adding the current access token's `jti` to Redis with its remaining lifetime as TTL. Database checks enforce logout even if Redis is unavailable. A database outage is never authentication success. Old tokens pointing to removed session UUIDs require a new login.

Five failed password attempts lock the account for 15 minutes. The fifth failure returns 401; subsequent attempts during lockout return 423. A successful login resets counters. Failed attempts use a separate database transaction and pessimistic lock. Employee success/lockout events are written to audit logs by account ID and role.

## Google login

Configure `GOOGLE_CLIENT_ID`. The verifier checks RSA signature through Google's fixed JWKS endpoint, issuer, audience, expiry, subject and verified email. Key retrieval has three-second connection/read timeouts and at most one retry with 100 ms backoff for provider failures. Verification occurs before database transactions.

Account lookup uses the verified normalized email, matching the approved schema; no provider-identity table is introduced. Gmail/Workspace addresses can link to an existing active account or create a new Customer through entity factories. For email outside Gmail/Workspace, first register and verify it, then provide the existing account password on each Google login. Unknown non-authoritative emails are rejected with `GOOGLE_LINK_REQUIRED` rather than creating accounts whose provider identity cannot be safely persisted in the approved schema. Management Google login never creates internal accounts. Pending password registrations must complete OTP confirmation before Google or password login.

## Errors and Swagger

Every error uses `{ code, message, status, timestamp, traceId, details }`. Invalid fields/JSON/OTP return 400; credentials or invalid/expired/revoked/replayed tokens return 401; unverified/inactive accounts, denied management access or linking confirmation return 403; duplicates return 409; lockout returns 423; OTP cooldown returns 429; verification/provider/session outages return 503. Unexpected failures return a safe 500 response.

Swagger UI is `/swagger-ui/index.html`; OpenAPI JSON is `/v3/api-docs`. Protected operations use the `bearerAuth` scheme.

## Verification and scope

Run `./mvnw clean test`. The final branch passes 40 tests covering startup/OpenAPI, registration/factories/normalization/OTP activation, provider retries, Redis adapter failures, management Google restrictions, token lifetime/type, concurrent rotation, logout across devices, Redis fallback, lockout/audit and RSA signature verification. Tests use H2 and mocked Redis/provider transports. No real database, live Redis server or real email/Google account was used.

The frontend still needs the new OTP confirmation screen and calls to Google/refresh/server logout. Password recovery issues #7/#8 are not implemented by this review follow-up.
