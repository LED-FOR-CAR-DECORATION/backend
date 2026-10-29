---
name: feature-completion
description: >-
  Automates the post-implementation workflow after coding a backend feature, endpoint, or task.
  Use this skill when the user asks to run the feature-completion workflow, finalize an endpoint/feature,
  or automate post-coding steps: (1) adding Swagger/OpenAPI annotations and schemas, (2) writing and running
  comprehensive unit tests covering the entire feature scope (Controller, UseCase, Domain Services, Event Listeners,
  and Provider Clients) until 100% green, (3) generating a multi-function SEP490 Unit Test Excel report (.xlsx)
  in /antigravity-output, and (4) creating Mermaid sequence and class diagrams in /antigravity-output.
---

# Feature-Completion Workflow (`feature-completion`)

Execute all four phases sequentially whenever a feature, endpoint, or task is ready for post-implementation completion.

> [!IMPORTANT]
> **Full Feature Scope Principle**:
> A feature is not just an endpoint method or use case. It encompasses **all code created or modified for that feature**, including:
> 1. **Transport Layer**: Controller (`api/controller`), Request/Response DTOs (`api/request`, `api/response`), Custom Validators.
> 2. **Application Layer**: Use Cases (`application`).
> 3. **Domain & Core Services**: Domain Models, Enums, Domain Services (`domain/service`, e.g., `OtpService` / `HybridOtpService`).
> 4. **Event Listeners / Side Effects**: Handlers reacting to domain events (`application/listener`, e.g., `NotificationEventListener`).
> 5. **External Providers & Integrations**: Provider clients (`infrastructure/integration`, e.g., `ResendEmailClient`).
>
> All of these components must have dedicated unit tests and appear as distinct functions in the SEP490 Unit Test Report!

---

## Phase 1: Swagger / OpenAPI Documentation

Inspect all new or modified Controllers (`api/controller`) and Request/Response DTOs (`api/request`, `api/response`) for the target feature:

1. **Controller Class (`@Tag`)**:
   - Ensure the controller has `@Tag(name = "...", description = "...")` from `io.swagger.v3.oas.annotations.tags.Tag`.
2. **Endpoint Method (`@Operation` & `@ApiResponses`)**:
   - Annotate the endpoint method with `@Operation(summary = "...", description = "...")`.
   - Annotate with `@ApiResponses` documenting every applicable HTTP status:
     - **Success (`200` or `201`, or `204`)**: Clear description of the returned `ApiResponse<T>` envelope.
     - **Client / Domain Errors (`400`, `401`, `403`, `404`, `409`, `423`, etc.)**: Set `content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(...))` with realistic JSON matching the project's `{ code, message, status, timestamp, traceId, details }` error contract.
     - **Server Error (`500`)**: Document `INTERNAL_SERVER_ERROR` using `ErrorResponse.class` and an `@ExampleObject`.
3. **Request & Response DTOs (`@Schema`)**:
   - Add class/record-level `@Schema(description = "...")`.
   - Add field/component-level `@Schema(description = "...", example = "...", requiredMode = ...)` on every field of the Request and Response DTOs.

---

## Phase 2: Comprehensive Unit Testing & Verification (Must Be 100% Passing)

Write unit tests for **every class involved in the feature** under `src/test/java/com/lumintra/backend/` using **JUnit 5**, **Mockito** (`@ExtendWith(MockitoExtension.class)`), and **AssertJ**. Mock external services and SDKs (e.g., Redis, Resend) so tests remain fast, deterministic, and isolated:

1. **Controller Unit Tests (`*ControllerTest.java`)**:
   - Verify HTTP status code (`200`/`201`), standard `ApiResponse<T>` envelope, parameter validation, and propagation of typed domain exceptions.
2. **Use Case Unit Tests (`*UseCaseTest.java`)**:
   - Cover Normal (`N`), Boundary (`B`), and Abnormal (`A`) scenarios:
     - **Normal (`N`)**: Happy path execution, input normalization (trimming, lowercase), database saves, and event publishing (`verify(eventPublisher).publishEvent(...)`).
     - **Boundary (`B`)**: Field length limits (e.g. min 8 / max 72 chars), threshold limits.
     - **Abnormal (`A`)**: Duplicate conflicts (`409`), missing resources (`404`), authentication failures (`401`/`423`), and bean validation constraint violations.
3. **Domain & Core Service Unit Tests (`*ServiceTest.java`)**:
   - Test business services and logic algorithms (e.g. `HybridOtpServiceTest` testing OTP generation, Redis caching, and cache-unavailable fallback to database repository).
4. **Event Listener / Side Effect Unit Tests (`*ListenerTest.java`)**:
   - Test event listeners in isolation (e.g. `NotificationEventListenerTest` verifying that when a `CustomerRegisteredEvent` arrives, the listener extracts event data and calls `EmailProvider.sendOtpEmail(...)`).
5. **Integration Client Unit Tests (`*ClientTest.java`)**:
   - Test external provider wrappers (e.g. `ResendEmailClientTest` verifying correct email options payload, and verifying that provider SDK exceptions are caught and translated into project-typed exceptions like `ExternalProviderUnavailableException`).
6. **Swagger Contract Tests (`*SwaggerTest.java`)**:
   - Verify reflection assertions that `@Tag`, `@Operation`, `@ApiResponses`, and `@Schema` annotations are present on controller and DTO classes.
7. **Run & Verify Green Build**:
   - Run the tests via Maven wrapper in `d:\Khanh Stuffs\Codes\SEP490\backend`:
     ```powershell
     .\mvnw.cmd test -Dtest="<TestClassNames>"
     ```
   - **Mandatory Gate**: Every single test **MUST PASS** (`Failures: 0, Errors: 0`). Fix any failing test or code bug before proceeding to Phase 3.

---

## Phase 3: SEP490 Excel Test Report Generation (`.xlsx` in `/antigravity-output`)

Generate a new `.xlsx` copy of the SEP490 Unit Test Report in `d:\Khanh Stuffs\Codes\SEP490\antigravity-output` while preserving 100% of the original formatting, merged cells, borders, and formulas from `d:\Khanh Stuffs\Codes\SEP490\antigravity-input\Unit Test.xls`.

1. **Package ALL Feature Components into the JSON Payload**:
   - Review [sample_payload.json](./references/sample_payload.json) for the exact schema.
   - The payload's `functions` array must include **every tested class/function of the feature** (Controller, UseCase, Services, Event Listeners, Provider Clients).
   - Each entry in `functions` contains:
     - `requirementName`, `className`, `functionName`, `functionCode` (e.g., `FN_01`, `FN_02`, ...), `sheetName` (unique, max 31 chars), `description`, `preConditionSummary`, `loc`, `testRequirement`.
     - `preconditions`: Array of distinct precondition strings.
     - `inputs`: Array of `{ "name": "<paramField>", "values": ["<val1>", "<val2>", ...] }`.
     - `returns`: Array of expected return descriptions.
     - `exceptions`: Array of expected exception class names.
     - `logs`: Array of expected log message snippets.
     - `testCases`: Array of `UTCID01..N` objects matching the executed unit tests. Every test case must specify matching values from `preconditions`, `inputs`, `returns`, `exceptions`, `logs`, `type` (`"N"`, `"A"`, or `"B"`), and `status: "P"` (Passed).
2. **Execute the Generator Script**:
   - Run [update_excel_report.py](./scripts/update_excel_report.py):
     ```powershell
     python "d:\Khanh Stuffs\Codes\SEP490\backend\.agents\skills\feature-completion\scripts\update_excel_report.py" --input "<path-to-payload.json>"
     ```
   - The script creates:
     - Updated `Functions` list containing all feature classes.
     - Dedicated matrix sheets for each class/function.
     - Updated `Statistics` sheet summarizing total test cases, pass/fail rates, and coverage across all feature components.
     - Outputs to `d:\Khanh Stuffs\Codes\SEP490\antigravity-output\Unit_Test_<featureSlug>_<timestamp>.xlsx`.

---

## Phase 4: Sequence & Class Diagrams in `/antigravity-output`

Create or update a Markdown documentation file at:
`d:\Khanh Stuffs\Codes\SEP490\antigravity-output\<feature-slug>-diagrams.md`

Include:
1. **Endpoint & Feature Summary**: HTTP method, path, request/response contract, and complete component breakdown (Controller, Use Case, Service, Listener, Client).
2. **Sequence Diagram (`sequenceDiagram`)**:
   - Trace the complete end-to-end interaction across all layers:
     `Client` $\rightarrow$ `Controller` $\rightarrow$ `UseCase` $\rightarrow$ `DomainService` / `Repository` $\rightarrow$ `ApplicationEventPublisher` $\rightarrow$ `EventListener` $\rightarrow$ `ExternalProviderClient` (including `alt` blocks for normal path, validation errors, domain conflicts, cache fallbacks, and provider failures).
3. **Class Diagram (`classDiagram`)**:
   - Document all classes involved in the feature across all packages:
     - API: `Controller`, `Request DTO`, `Response DTO`, `Validators`.
     - Application: `UseCase`, `EventListeners`.
     - Domain: `Domain Services`, `Domain Events`, `Enums`.
     - Infrastructure: `Entities`, `Repositories`, `Provider Clients`.
   - Include fields, key methods, and explicit relationships (`-->`, `..>`, `--|>`).
   - Quote any Mermaid labels containing special characters (parentheses, brackets, colons) to avoid syntax errors.
