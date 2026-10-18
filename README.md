# Contributing Guide

This guide defines mandatory engineering rules for the single backend application. The backend serves the web applications, owns the database and cache, and integrates with approved external providers.

## 1. Understand the Architecture

The target architecture is:

```text
Web application / device
        |
        v
Single backend application
   |          |          |
Database    Cache    External providers
```

Rules:

- The backend is one deployable application.
- The backend owns authentication, authorization, business rules, persistence, caching, and provider integrations.
- Keep provider-specific code behind integration interfaces so business logic does not depend on vendor SDKs.

## 2. Use the Required Backend Structure
### 2.1 Project Structure
Use this structure example inside the backend application:

```text
src/main/java/com/lumintra/backend/
  billing/
    api/
      controller/
        BillingController.java
      request/
        BillingRequest.java
      response/
        BillingResponse.java
    application/
      CreateInvoiceUseCase.java
    domain/
      model/
        Invoice.java
      service/
        BillingService.java
    infrastructure/
      persistence/
        entity/ 
          InvoiceEntity.java
        repository/
          InvoiceRepository.java
        mapper/
          InvoiceMapper.java

  inventory/
    api/
      controller/
        InventoryController.java
      request/
      response/
    application/
      UpdateStockUseCase.java
    domain/
      model/
      service/
        StockManagementService.java
    infrastructure/
      persistence/
        entity/
          StockEntity.java
        repository/
          StockRepository.java

  shared/
    config/
    security/
    exception/
    logging/
```
#### Layer Responsibilities

| Layer / Directory | Primary Responsibility | What Goes Here | What is FORBIDDEN Here |
| :--- | :--- | :--- | :--- |
| **`api/`** | HTTP transport and validation. Receives requests and returns the standard JSON envelope. | Controllers, Request DTOs, Response DTOs. | Business logic, database calls, or returning raw database Entities. |
| **`application/`** | Orchestrating workflows. Fetches data, calls the domain to apply rules, and saves the result. | Use Cases (e.g., `CreateOrderUseCase`), background workers, scheduled jobs. | HTTP annotations (`@PostMapping`), `@Entity` mappings, or raw SQL queries. |
| **`domain/`** | Pure business rules and state protection. The core of the application. | Core Models (`Order`, `Commission`), Domain Services, custom Domain Exceptions. | Framework annotations (`@Entity`, `@Table`), HTTP logic, or external SDKs. |
| **`infrastructure/`** | External I/O. Handles the database, caching, and 3rd-party provider integrations. | JPA Entities, Repositories, Mappers, External Clients (`AhaMoveClient`). | Core business logic or state validation that belongs in the Domain layer. |
| **`shared/`** | Cross-cutting concerns that apply globally across all features. | Security filters, `GlobalExceptionHandler`, logging configs, authentication. | Feature-specific logic, or a generic `util/` package used as a dumping ground. |



## 3. Keep Boundaries Clear
### 3.1 Inter-Feature Boundaries
1. **Never inject a Repository from another feature.** If `commerce` needs inventory data, it must not use `StockEntity` or `StockRepository`.
2. **Rich Domain Model** Rules that define the absolute truth of a business entity should be embedded directly inside the Entity or Domain Model
3. **Use Domain Services for Synchronous answers.** If Feature A needs data from Feature B to proceed, inject Feature B's Use Case or Domain Service. Feature B acts as a black box.
4. **Use Spring Events (`ApplicationEventPublisher`) for side effects.** If Feature A completes a task and Feature B needs to react (e.g., sending an email after an order is created), Feature A should publish a domain event (`OrderCreatedEvent`). Feature A must not directly call the Email Service.
5. **Keep 3rd-party SDKs in their specific feature.** Put external provider clients inside their respective feature's infrastructure/integration folder (e.g., VNPay goes in `billing`, AhaMove goes in `commerce`).

### 3.2 Transport and Layer Boundaries
1. **A controller may:** Validate requests, call one use case, return the standard response.

2. **A controller must not:** Call repositories directly, build persistence entities, perform business logic, read/write cache, or catch broad exceptions.

3. **Use constructor injection.** Do not use @Autowired on fields.

4. **Never return database entities from an HTTP endpoint.** Use explicit request and response DTOs.

5. **Map entities at the boundaries.** Database schema changes must not silently change the public API.


## 4. Use One API Response Contract

Every successful HTTP response must use this envelope:

```json
{
  "data": {
    "id": 42,
    "name": "John Code"
  }
}
```

Rules:

- Every successful response with a body must contain a top-level `data` field.
- `data` contains the resource, list, or documented pagination object.
- a `meta` metadata field may be added only when the endpoint needs it, such as pagination, export information, or an asynchronous job status.
- Optional metadata must use a documented, consistent structure.
- `204 No Content` responses must not contain a response body.
- Do not return raw entities, plain strings, unstructured maps, or framework-specific `Page` objects.
- Use resource-oriented, plural, lowercase paths: `/api/orders`, `/api/users`.
- Endpoints must not end with a trailing slash (use `/api/orders`, not `/api/orders/`)
- Use `201 Created` for successful creation and `200 OK` for reads and updates.

### API Response Example

#### DO THIS

```java
@PostMapping("api/patients")
public ResponseEntity<ApiResponse<PatientResponse>> createPatient(
  @Valid @RequestBody CreatePatientRequest request) {

    PatientResponse patient = createPatientUseCase.execute(request);

    return ResponseEntity.status(HttpStatus.CREATED)
      .body(ApiResponse.success(patient));
}
```

```json
{
  "data": {
    "id": 42,
    "fullName": "John Code"
  }
}
```

#### DON'T DO THIS

```java
@PostMapping("/")
public ResponseEntity<String> createPatient(@RequestBody CreatePatientRequest request) {
    patientRepository.save(request.toEntity());
    return ResponseEntity.ok("Patient created successfully");
}
```

Why this is forbidden:

- It bypasses the application layer.
- It exposes persistence behavior in the controller.
- It returns a plain string instead of the standard contract.
- It uses `200 OK` for resource creation.

## 5. Use One Error Contract

Every failed HTTP response must use this structure:

```json
{
  "code": "PATIENT_NOT_FOUND",
  "message": "Patient was not found.",
  "status": 404,
  "timestamp": "2026-10-05T12:00:00Z",
  "traceId": "trace-123",
  "details": []
}
```

Rules:

- `code` is a stable machine-readable identifier.
- `message` is safe for the client and must not expose SQL, stack traces, tokens, passwords, or provider details.
- `status` must match the actual HTTP status.
- `timestamp` must use ISO-8601 UTC.
- `traceId` is required for support and log correlation.
- `details` contains zero or more structured problems related to the response. It is not a list of exceptions thrown during execution.
- All errors must be produced by one global exception handler for the backend application.
- Do not create endpoint-specific variations of the error schema.
- Do not return a `400` HTTP status with a body claiming `401`.

### Error Handling Example

#### DO THIS

```java
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final TraceIdProvider traceIdProvider;

    @ExceptionHandler(PatientNotFoundException.class)
    ResponseEntity<ErrorResponse> handlePatientNotFound(
            PatientNotFoundException exception) {

        ErrorResponse response = ErrorResponse.builder()
                .code("PATIENT_NOT_FOUND")
                .message("Patient was not found.")
                .status(HttpStatus.NOT_FOUND.value())
                .timestamp(Instant.now())
                .traceId(traceIdProvider.currentId())
                .details(List.of())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
}
```

```json
{
  "code": "VALIDATION_FAILED",
  "message": "The request contains invalid fields.",
  "status": 400,
  "timestamp": "2026-10-05T12:00:00Z",
  "traceId": "trace-123",
  "details": [
    {
      "field": "email",
      "message": "Must be a valid email address"
    }
  ]
}
```

#### DON'T DO THIS

```java
try {
    patientRepository.save(patient);
} catch (Exception exception) {
    return ResponseEntity.badRequest().body(
            Map.of(
                    "message", exception.getMessage(),
                    "status", 401,
                    "timestamp", LocalDateTime.now()
            ));
}
```

Why this is forbidden:

- It catches every exception and hides its category.
- It returns the wrong status for the actual failure.
- It leaks internal exception text.
- It creates a second, incompatible error format.
- It puts persistence and HTTP error handling in the wrong layer.

## 6. Handle Exceptions Deliberately
These rules define **where errors should be handled and what information must be preserved**.

- Catch an exception only when the current layer can recover, translate, retry, or add useful context.
- Never use `catch (Exception)` to return `false`, `null`, or an empty collection.
- Preserve the original cause when translating an exception.
- Use typed domain exceptions for expected business failures.
- Let unexpected exceptions reach the global handler.

### 6.1 Catch an exception only when the current layer can recover, translate, retry, or add useful context

Do not catch an exception just to stop it from propagating. Catch it only when the current layer has a meaningful action to take.

Valid actions include:

- Recovering with a safe fallback.
- Translating a provider-specific exception into a typed application exception.
- Retrying a temporary operation according to an explicit retry policy.
- Adding useful context and rethrowing the exception.

```java
try {
    return googleClient.getUserProfile(accessToken);
} catch (GoogleTimeoutException exception) {
    throw new ExternalProviderUnavailableException(
            "Google authentication is temporarily unavailable",
            exception);
}
```

The integration layer understands the Google-specific failure. The rest of the application receives a stable application-level exception.

### 6.2 Never use `catch (Exception)` to return `false`, `null`, or an empty collection

An infrastructure failure is not the same as a valid business result. A database outage, timeout, or programming error must not be reported as if no data exists.

```java
// Forbidden: a provider outage becomes "Order does not exist".
try {
  return Order.exists(orderId);
} catch (Exception exception) {
    return false;
}
```

Let the exception be translated or handled by the global exception handler instead.

### 6.3 Preserve the original cause when translating an exception

When converting a low-level exception into an application exception, pass the original exception as the cause. This gives the client a safe message while keeping the original stack trace available for logs.

```java
throw new ExternalProviderUnavailableException(
        "Payment provider is temporarily unavailable",
        exception);
```

Do not discard the original exception and replace it with a message-only exception.

### 6.4 Use typed domain exceptions for expected business failures

Expected business failures should have names that describe what happened:

```java
throw new InsufficientStockException(productId);
```

Prefer `PatientNotFoundException`, `InsufficientStockException`, or `EmailAlreadyRegisteredException` over a generic `RuntimeException("Something went wrong")`.

Typed exceptions allow the global exception handler to choose the correct error code, HTTP status, and safe client message.

### 6.5 Let unexpected exceptions reach the global handler

Controllers and services must not build ad-hoc error responses for unexpected failures. Let unexpected exceptions propagate to the one global exception handler for the backend.

```text
Expected business error
  -> typed exception
  -> global handler
  -> safe 4xx response

Unexpected technical error
  -> preserve and log stack trace
  -> global handler
  -> safe 5xx response
```

The global handler must not expose stack traces, SQL, provider credentials, or internal infrastructure details to the client.

## 7. Database and Transaction Rules

- Every schema change must use a versioned database migration.
- Do not modify production tables manually.
- Define transaction boundaries in the application/use-case layer.
- Keep transactions short and do not hold a database transaction open while waiting for an external provider.
- Enforce important uniqueness and referential-integrity rules in the database as well as in application code.
- Do not expose SQL exceptions or database vendor details through the API.
- Test rollback behavior for operations that update multiple records.

## 8. Cache Rules

- Use the cache to improve performance, never as the authoritative source of business data unless explicitly approved.
- Define a TTL for every cache entry.
- Define how writes invalidate or refresh affected cache entries.
- Use stable, namespaced cache keys, such as `patient:{id}`.
- A cache outage must degrade to the database or return a typed service error according to the use case; it must not produce incorrect business results.
- Never cache passwords, access tokens, or sensitive personal data without an approved protection and retention policy.
- Test stale data, cache misses, invalidation, and cache unavailability.

## 9. External Provider Integrations

- Keep provider SDKs and HTTP clients inside `infrastructure/integration`.
- Every external call must have a timeout and typed failure mapping.
- Retries must be limited, use backoff, and apply only to retryable failures.
- Use idempotency keys for operations that may create payments, notifications, files, or other external side effects.
- Do not convert a provider outage into a valid business result such as `false` or `null`.
- Do not log provider credentials, authorization headers, full payloads, or sensitive customer data.
- Test provider success, timeout, rejection, rate limiting, and partial failure behavior.


## 10. Logging, Security, and Observability

These rules define how to log **consistent, searchable, useful for debugging, and safe** info.

* Use one logging facade and add relevant resource ID in log messages.
* Every request should have a trace ID, generated by the backend.
* Log unexpected failures with their stack traces, but do not expose those details to clients.
* Protect sensitive identity, payment, and credential data in logs, cache entries, error messages, and analytics.
* Use appropriate log levels. Validation and expected business errors should not be logged as system failures.
* Log only what is necessary. If possible, do not log every layer
* Keep secrets in environment variables, never in source code.
* Apply authentication and authorization at the backend boundary and enforce resource ownership in the application layer.
* Add health checks and metrics for the database, cache, and critical external providers.

### 10.1. Use one logging facade and add relevant resource ID in log messages.

Use the project’s logging abstraction, normally SLF4J:

```java
private static final Logger log =
        LoggerFactory.getLogger(CreateOrderUseCase.class);
```

Do not use:

```java
System.out.println("Order failed");
```

**Log Messages: Static String + Structured Variables**
Log messages should start with a short, static human-readable description with NO variables, followed immediately by relevant structured `[key=value]` context metadata at the end of the string. The context metadata can be anything that provide more context to the log message, including but not restricted to: affected resource ID, request duration, exception stack trace, etc...

```java
log.info("Order creation started [orderId={}]", orderId);
```


Because the logging framework automatically injects the timestamp, log level, trace ID, and class name, log output should automatically format to look like this in the file:

```text
2026-10-07 15:10:00 [INFO] [traceId=abc123] CreateOrderUseCase - Order creation started [orderId=42]
```



**DON'T DO THIS (Variables mixed into the prose - Unsearchable):**

```java
log.warn("Customer {} tried to use code {} but it expired on {}", customerId, code, expirationDate);
```

**DON'T DO THIS (Pure machine logs - Hard for humans to skim):**

```java
log.warn("[customerId={}] [discountCode={}] [expirationDate={}] [reason=expired]", customerId, code, expirationDate);
```

**DO THIS (The Standard: Static Prefix + Key/Value Metadata):**

```java
log.warn("Promo code rejected [customerId={}] [discountCode={}] [expirationDate={}] [reason=expired]", 
    customerId, 
    code, 
    expirationDate
);

```

### 10.2. Every request should have a trace ID, generated by the backend.

At the backend request boundary, usually a filter or interceptor:

```text
Request enters backend
  -> Generate trace ID
  -> Put it into the logging context
  -> Process request
  -> Remove it after completion

```

You should not manually pass `traceId` through every controller, DTO, use case, and service method. The logging framework should add it automatically through MDC or an observability library.

### 10.3. Log failures with the exception object

Good:

```java
log.error(
    "Database unavailable while loading order [orderId={}]",
    orderId,
    exception
);

```

The final `exception` argument preserves the stack trace.

Avoid:

```java
log.error("Database error: {}", exception.getMessage());

```

That logs only the message and loses the execution path needed for debugging.

### 10.4. Protect sensitive identity, payment, and credential data in logs, cache entries, error messages, and analytics.

Logs often go to shared systems and may be retained for a long time. Never log:

* Passwords
* JWTs or refresh tokens
* Payment credentials
* Full request bodies
* Personal records
* Authorization headers

Safe:

```text
Failed to create order [orderId=42]

```

Unsafe:

```text
Login request received [password=secret123] [token=eyJhbGci...]

```

### 10.5. Use appropriate log levels. Validation and expected business errors should not be logged as system failures.

Typical levels:

```text
DEBUG  Development details, usually disabled in production
INFO   Important normal operations
WARN   Expected or recoverable abnormal conditions
ERROR  Unexpected technical failures

```

Examples:

```java
log.info("Order created successfully [orderId={}]", orderId);

log.warn("Order not found [orderId={}]", orderId);

log.error("Database unavailable while loading order [orderId={}]", orderId, exception);

```

Invalid input, a missing resource, or insufficient stock is an expected outcome of normal application behavior, so it should not automatically be logged as an `ERROR`. Return the appropriate typed error response without logging it as an infrastructure outage. For example:

```java
log.warn("Order not found [orderId={}]", orderId);

```

Use error-level logging with a stack trace for actual technical failures:

```java
log.error("Database unavailable while loading order [orderId={}]", orderId, exception);

```

### 10.6. Log only what is necessary. If possible, do not log every layer

The guide does not mean every controller, use case, service, and repository must log the same event.

A practical distribution is:

```text
Request filter:
  request start, status, duration

Controller:
  only API-specific context when useful

Use case:
  important business milestones

Domain:
  unusual business decisions only

Repository:
  failures, not every query

External integration:
  provider call, result, duration, safe failure details

```

The goal is one useful record per important event, not a log statement on every line of code. For example, this **success** "Create Order" request omits logging for the controller and repository layers entirely:

```text
2026-10-07 15:10:00 [INFO] [traceId=abc123] RequestFilter - HTTP request started [method=POST] [path=/api/orders]
2026-10-07 15:10:00 [INFO] [traceId=abc123] CreateOrderUseCase - Starting order creation [customerId=99]
2026-10-07 15:10:00 [INFO] [traceId=abc123] AhaMoveClient - Estimating shipping fee
2026-10-07 15:10:00 [INFO] [traceId=abc123] AhaMoveClient - Shipping fee estimated successfully [duration=120ms]
2026-10-07 15:10:01 [INFO] [traceId=abc123] Order - Applied discount [orderId=42] [reason=First-time buyer]
2026-10-07 15:10:01 [INFO] [traceId=abc123] CreateOrderUseCase - Order created successfully [orderId=42]
2026-10-07 15:10:01 [INFO] [traceId=abc123] RequestFilter - HTTP request completed [status=201] [duration=350ms]
```


### 10.7 Keep secrets in environment variables, never in source code

Secrets include:

- Database passwords
- JWT signing keys
- Google OAuth client secrets
- Payment provider keys
- SMTP passwords
- Redis passwords

Good:

```java
String googleClientSecret = environment.getProperty("GOOGLE_CLIENT_SECRET");
```

Good: 
```yaml
google:
  client-secret: ${GOOGLE_CLIENT_SECRET}
```

Avoid:

```java
String googleClientSecret = "abc123-secret";
```





The actual value belongs in `.env` locally and deployment configuration in production.

Never commit `.env` files containing real secrets. Environment variables prevent credentials from being stored in Git and allow different values for development, staging, and production.

### 10.8 Apply authentication and authorization at the backend boundary and enforce resource ownership in the application layer

These are two separate checks:

### 10.8.1 Authentication: “Who are you?”

The backend verifies the user’s identity:

```text
Request
  -> Check session or JWT
  -> Identify user
  -> Continue if authenticated
```

This normally happens in a security filter or middleware before the controller executes.

#### 10.8.2 Authorization: “Are you allowed to do this?”

The backend checks permissions:

```text
User has ADMIN role
User can create inventory
User can view reports
```

This can be enforced at the backend endpoint boundary:

```java
@PreAuthorize("hasAuthority('INVENTORY_READ')")
@GetMapping("/inventory")
```

#### 10.8.3 Resource ownership: “Can this user access this specific record?”

A user may be authenticated and have general permission, but still not be allowed to access every resource.

```java
public OrderResponse execute(Long orderId, UserId currentUserId) {
    Order order = orderRepository.findById(orderId)
            .orElseThrow(OrderNotFoundException::new);

    if (!order.isOwnedBy(currentUserId)) {
        throw new AccessDeniedException("You cannot access this order");
    }

    return mapper.toResponse(order);
}
```

Do not rely only on frontend checks. A user can bypass frontend code and call the backend directly. The backend must enforce the final security decision.

### 10.9 Add health checks and metrics for the database, cache, and critical external providers

A health check answers:

> Is this dependency currently available?

Examples:

```text
Database: reachable
Redis: reachable
Google SSO: configuration valid
Payment provider: reachable
```

Health checks are useful for deployment platforms and operations:

```text
Application is alive
Application is ready to receive traffic
Database is unavailable
```

Metrics answer:

> How is this dependency behaving over time?

Useful metrics include:

```text
database_connection_failures_total
redis_cache_hits_total
redis_cache_misses_total
google_sso_request_duration
payment_provider_errors_total
```

System must monitor:

- Response duration
- Error count
- Timeout count
- Connection failures
- Cache hit/miss ratio
- External provider availability

Important distinction:

- A **health check** gives a current status.
- A **metric** records behavior over time.

For example:

```text
Health check:
  Redis is currently UP

Metrics:
  Redis cache hit ratio is 82%
  Redis had 12 timeouts in the last hour
```

Do not expose sensitive dependency details publicly. Health endpoints should be protected or split into:

- **Liveness:** the application process is running.
- **Readiness:** required dependencies are available.
- **Detailed internal health:** diagnostic information for administrators and monitoring systems.

## 11. Testing and Pull Request Checklist

Before opening a pull request, verify:

- The code follows the standard backend directory structure.
- The controller calls an application use case and no repository or provider client.
- The endpoint returns the standard success envelope and correct HTTP status.
- All failures use the standard error envelope.
- No broad exception is swallowed or converted into `false` or `null`.
- Database transactions and migrations are covered where relevant.
- Cache hit, miss, invalidation, stale data, and outage behavior are covered where relevant.
- External provider timeout and failure behavior is covered where relevant.
- Logs contain a trace ID and no sensitive data.
- Tests cover success, validation failure, business failure, and infrastructure failure.

Changes that violate these rules require a documented architecture exception approved by the project owner.
