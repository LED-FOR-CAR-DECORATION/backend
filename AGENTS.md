# Backend engineering rules

Scope: this backend repository. Read `README.md` (Contributing Guide) before changing application code. It contains the detailed project conventions; `../contributing.pdf` is the supplied reference. Preserve the more specific project guidance in README when adding rules from the PDF.

## Architecture and boundaries

- Keep one deployable backend, organized by business feature: `api/{controller,request,response}`, `application`, `domain/{model,service}`, and `infrastructure/{persistence,integration}`.
- Controllers validate and deserialize requests, call one application use case, map its response, and return the correct HTTP status. No repositories, persistence entities, business decisions, cache access, provider calls, or broad exception handling in controllers.
- Keep domain business rules independent of HTTP, persistence annotations, and vendor SDKs. Keep entities, repositories, and persistence mappers in the feature's infrastructure.
- Use constructor injection, never field `@Autowired`. Prefer focused use cases for workflows; domain services contain reusable business rules.
- Features communicate through application/domain interfaces, not another feature's repositories or persistence entities. Follow README's Spring Events convention for side effects.
- Reserve `shared` for cross-cutting configuration, security, exceptions, and logging; do not place feature-specific logic there.

## HTTP contracts and failures

- Every successful response with a body has top-level `data`; pagination uses a documented DTO inside `data`. Optional metadata must be documented. `204` has no body.
- Use plural lowercase resource paths without trailing slashes. Use `201` for creation and `200` for reads/updates. Return explicit DTOs, not entities, plain strings, maps, or framework `Page` objects.
- Every error uses `{ code, message, status, timestamp, traceId, details }`. `details` is an array, `timestamp` is ISO-8601 UTC, and `status` matches the HTTP status.
- Use one global exception handler. Error codes are stable; messages must not expose SQL, stack traces, tokens, credentials, or provider internals.
- Catch only to recover, translate, retry, or add useful context. Preserve the original cause. Use typed business exceptions; never disguise infrastructure failures as `false`, `null`, or empty collections.

## Persistence, cache, and providers

- Version every schema change with a migration. Define short transactions in the application/use-case layer; do not keep a DB transaction open while waiting for a provider.
- Enforce uniqueness and referential integrity in both database and application logic. Test rollback for multi-record operations.
- Cache is not authoritative business storage unless explicitly approved. Specify TTL, namespaced keys, invalidation/refresh, and outage behavior. An outage must not produce incorrect business results.
- Keep provider clients/SDKs in the owning feature's `infrastructure/integration`, behind integration interfaces. Require timeouts, typed failure mapping, bounded backoff retries for retryable failures, and idempotency keys for external side effects.

## Security, logging, and verification

- Enforce authentication/authorization at the backend boundary and resource ownership in use cases. Frontend guards are not an authorization boundary.
- Keep secrets in environment configuration or a secrets manager. Do not log credentials, authorization headers, personal records, payment data, full requests, or provider payloads; protect sensitive cache/analytics data too.
- Use the project logging facade and trace context. Follow README's static message prefix followed by structured `[key=value]` metadata; keep exception objects for stack traces. Do not log expected validation/business errors as system failures or repeat the same event at every layer.
- Generate a correlation/trace ID at the backend boundary when needed; use logging context rather than passing it through every method. Add appropriate dependency health checks and metrics without exposing sensitive diagnostics publicly.
- Before a PR, verify contracts, layer boundaries, migrations/rollback, cache behavior, provider timeout/rejection/rate-limit/partial-failure behavior, safe logs, and tests for success, validation, business, and infrastructure failures.
- A deviation requires a documented architecture exception approved by the project owner. First check whether existing user authorization already covers the concrete exception; do not invent approval requirements beyond this rule.
