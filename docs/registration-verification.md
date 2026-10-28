# Registration and email verification

## Contract

`POST /api/auth/register` accepts only `email`, `password` and `confirmPassword`. A successful request creates a Customer account with status `PENDING_VERIFICATION`, creates its empty profile using entity factory methods, stores an OTP digest in Redis, and dispatches a verification email. The response is HTTP 201:

```json
{"data":{"userId":123,"email":"customer@example.com","status":"PENDING_VERIFICATION"}}
```

`POST /api/auth/verify-email` accepts `email` and a six-digit `otp`. On success it atomically consumes the Redis code and updates the account status to `ACTIVE`; the response is HTTP 200 with the same envelope and `status: "ACTIVE"`. Login rejects pending accounts with HTTP 403 `EMAIL_NOT_VERIFIED`.

`POST /api/auth/resend-verification` accepts `email` and returns HTTP 202 without a body after dispatching a new code for a pending account. A 60-second resend cooldown applies. Invalid/expired codes return HTTP 400 `OTP_INVALID`; cooldown violations return 429 `OTP_RATE_LIMITED`; Redis or email delivery failures return 503 `VERIFICATION_UNAVAILABLE`.

## Storage and failures

Codes are generated with `SecureRandom`, expire after five minutes and allow five incorrect attempts. Redis stores an HMAC-SHA256 digest, not a plaintext code. Keys hash the normalized email and share a Redis Cluster hash tag; Lua scripts make issue, attempt counting and one-time consumption atomic.

The account/profile database transaction commits before provider calls. If delivery fails, the account stays pending, the failed challenge is invalidated when possible, and the caller can use resend verification. No email is sent if account/profile creation rolls back. If activation fails after a code is consumed, the pending account can request another code; an outage never activates an account implicitly.

Resend calls use three-second connection/read timeouts, at most one retry with 100 ms backoff for transient failures, and a stable idempotency key across that retry. No real email is sent by automated tests. See [Resend idempotency documentation](https://resend.com/changelog/idempotency-keys).

The latest source schema is recorded in [schema-reference.md](schema-reference.md). The frontend must collect the verification code and call verify-email before offering login; the previous immediate-login registration message no longer describes this API flow.

## Review follow-up

PR #10: removed `fullName`, added entity factories, normalized email at both entity and DTO boundaries, added Redis/email OTP confirmation, and corrected the new upstream permission list's missing comma. Existing tests cover case-insensitive registration/login, rollback, pending-account rejection, OTP activation/replay rejection and delivery failures. Redis/provider adapter tests cover cooldown/outage handling and idempotent retries. A live Redis service and real Resend account were not used in these tests.
