# Authentication APIs - IAM-01 through IAM-05

Try the APIs through `/swagger-ui/index.html`; the OpenAPI JSON is available at `/v3/api-docs`. Successful response bodies use `{ "data": ... }`. Errors use `{ code, message, status, timestamp, traceId, details }`.

## Endpoints

| Method | Endpoint | JSON request | Result |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | `fullName`, `email`, `password`, `confirmPassword` | 201, creates a Customer and profile |
| POST | `/api/auth/login` | `email`, `password` | 200, JWT token pair |
| POST | `/api/management/auth/login` | `email`, `password` | 200, JWT token pair; active Employees only |
| POST | `/api/auth/google` | `idToken`, optional linking `password` | 200, JWT token pair; links an account or creates a Customer |
| POST | `/api/management/auth/google` | `idToken`, optional linking `password` | 200, JWT token pair; the Employee account must already exist |
| POST | `/api/auth/refresh` | `refreshToken` | 200, new JWT token pair; invalidates the previous refresh token |
| POST | `/api/auth/logout` | No body; `Authorization: Bearer <accessToken>` | 204, revokes all sessions on all devices |

Public email login also accepts Employees for compatibility with the current client. Management routes always check the account type on the backend and never create internal accounts automatically.

## Tokens and sessions

```json
{
  "data": {
    "accessToken": "<access-token>",
    "refreshToken": "<refresh-token>",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

Access tokens last 15 minutes. Refresh sessions have an absolute lifetime of 30 days from initial login. Rotation does not extend that deadline. Tokens contain a unique `jti`, a session `sid`, and a `type` of `access` or `refresh`. Refresh tokens cannot be used as Bearer access tokens, and access tokens cannot be used for refresh. Inactive or locked accounts cannot receive new tokens.

`auth_sessions` stores only the SHA-256 hash of each refresh token. Issue, refresh and logout operations lock the same account row. Refresh validates the session, hash and expiry, then replaces the hash in one transaction. At most one of two concurrent requests using the same refresh token can succeed. Clients must store the new pair and coalesce concurrent refresh requests. Do not automatically retry a timed-out refresh: the previous token may already have been consumed.

Protected APIs check database session state on every request. Logout first revokes every account session in the database, then writes `auth:blacklist:<jti>` to Redis with the access token's remaining lifetime as its TTL. Database checks enforce revocation during Redis outages. A database failure never counts as successful authentication. Signing in again creates a new session.

## Lockout and audit

Five failed password attempts lock the account for 15 minutes. The fifth failure returns 401; subsequent requests during lockout return 423. Successful login resets the attempt counter and lockout timestamp. After lockout expires, a new failure starts the counter at one. Attempts are stored in the database using a separate transaction and a pessimistic lock, so they survive a failed login rollback and Redis recovery.

Successful Employee login publishes `StaffLoginSuccessEvent`; reaching the lockout threshold publishes `StaffAccountLockedEvent`. The listener writes audit logs containing user ID and role, without passwords, tokens or email addresses. Audit records currently live in application logs; there is no separate audit table.

## Google ID tokens

Set `GOOGLE_CLIENT_ID` to the application's OAuth client ID. Without it, Google login returns 503 `GOOGLE_UNAVAILABLE`; email/password APIs remain available.

The client sends the Google **ID token** returned by Google Identity Services, not a Google access token:

```json
{ "idToken": "<google-id-token>" }
```

The Nimbus JWT decoder verifies the RSA signature against Google's fixed JWKS endpoint and checks issuer, audience, expiry, subject and `email_verified`. Public-key requests have three-second connection/read timeouts and at most one provider-failure retry with a 100 ms backoff. Rejected tokens are not retried. External verification finishes before a database transaction begins.

Account links use the stable Google `sub` in `google_identities`. An existing email is linked to its account. When Google is not authoritative for that email (outside Gmail/Workspace), the existing account password is required before linking:

```json
{ "idToken": "<google-id-token>", "password": "<existing-account-password>" }
```

If the email is unknown, only the public API may create a Customer. Management Google login rejects emails without an existing Employee account and never creates internal accounts. Later logins use the stored `sub` link.

Reference: [Google: verify ID tokens on the server](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).

## Error codes

| HTTP | Code | Condition |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED`, `INVALID_REQUEST` | Invalid fields or JSON payload |
| 401 | `INVALID_CREDENTIALS` | Incorrect credentials |
| 401 | `INVALID_TOKEN`, `UNAUTHENTICATED` | Wrong token type, expiry, revocation, refresh replay or missing authentication |
| 401 | `INVALID_GOOGLE_TOKEN` | Invalid Google token or unverified email |
| 403 | `MANAGEMENT_ACCESS_DENIED`, `ACCOUNT_DISABLED`, `ACCESS_DENIED` | Insufficient access or an inactive account |
| 403 | `GOOGLE_LINK_REQUIRED` | Password confirmation required to link a non-Gmail/Workspace email |
| 409 | `EMAIL_ALREADY_REGISTERED`, `DATA_CONFLICT` | Duplicate email/identity or a concurrent linking conflict |
| 423 | `ACCOUNT_LOCKED` | Temporary account lockout |
| 503 | `GOOGLE_UNAVAILABLE`, `AUTH_UNAVAILABLE` | Provider failure or session verification unavailable |

## Database migrations

Flyway uses `V1__iam_baseline.sql`, `V2__auth_sessions.sql` and `V3__google_identities.sql`. Hibernate uses `validate` instead of the former development setting `create`, preserving data across application restarts. Migration filenames and SQL contents remain unchanged by the English documentation update.

- New database: keep `FLYWAY_BASELINE_ON_MIGRATE=false` (the default); run V1, V2 and V3.
- Existing IAM database without Flyway history: verify that the schema matches V1, back up the database, and set `FLYWAY_BASELINE_ON_MIGRATE=true` for the initial transition. Flyway records a V1 baseline and applies V2 and V3. Remove the variable or set it back to `false` afterwards.
- Database with migration history: keep `false`; only pending migrations are applied.

No migrations were run against development or deployed databases during this implementation. Tokens without `type`/`sid` require a new login after deployment. Application rollback does not automatically delete tables; assess session and token compatibility before rolling back. Expired session records are currently retained; a cleanup job can follow the project's retention policy.

## Tests and client integration

Run `./mvnw test`. Tests use H2 in PostgreSQL mode with actual migrations and do not connect to the development database. Coverage includes registration/rollback, management login, token lifetime/type, concurrent rotation, logout across devices, Redis outages, lockout/reset/audit, Google account linking/creation and real RSA signature verification. The Spring Boot startup test checks the OpenAPI endpoints.

Google tests use test RSA keys and mock the provider for account workflows; no real Google account login was performed. Migrations were tested on H2 and have not been applied to deployed PostgreSQL.

The frontend currently integrates login and registration. Google, refresh and server logout APIs are available for client integration; the current frontend does not call those new endpoints yet.
