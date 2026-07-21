# Assignment - EventHub Campus Event Registration API

> **Code:** SBF_A_01
> **Duration:** 7 days
> **Topics:** Spring Boot | IoC & DI | Spring Web MVC | REST API design | DTOs | Spring Data JPA | transactions | validation | exception handling | Spring Security | database-backed authentication | JWT | role-based authorization | testing | executable JAR | Docker | Actuator

---

## 1. Context & Objective

A training center currently announces workshops and campus events through chat messages and tracks registrations in spreadsheets. This causes duplicate registrations, overbooked events, inconsistent attendee lists, and no reliable way to check whether the service is healthy.

Your task is to build **EventHub**, a backend REST API for managing events, participants, and event registrations. The application must use Spring Boot and a relational database. It must expose a clear HTTP contract, keep business logic outside controllers, protect registration workflows with transactions, validate client input, authenticate database-backed user accounts, issue and verify signed JWT access tokens, enforce role-based authorization, return consistent errors, include automated tests at appropriate scopes, and be packageable for deployment.

This is a backend assignment. A frontend is not required.

By the end of the assignment, you should be able to demonstrate and explain:

- How Spring Boot creates and wires controllers, services, repositories, configuration, and infrastructure beans.
- How an HTTP request flows through Spring MVC into a layered application.
- Why request/response DTOs are separate from JPA entities.
- How Spring Data JPA repositories and service-layer transactions support a use case.
- How validation failures and domain exceptions become consistent HTTP errors.
- How Spring Security authenticates a username/password through `AuthenticationManager`, `UserDetailsService`, and `PasswordEncoder`, then uses a signed JWT for stateless requests.
- How authentication differs from authorization and why `401 Unauthorized` differs from `403 Forbidden`.
- Why unit, web-slice, JPA-slice, and full-context tests cover different risks.
- How one executable artifact is configured, containerized, and observed in different environments.

### Suggested schedule

| Day | Suggested focus                                                        |
| --- | ---------------------------------------------------------------------- |
| 1   | Project setup, package structure, domain model, database configuration |
| 2   | Event and participant endpoints, DTO mapping, pagination               |
| 3   | Registration and cancellation transactions, business rules             |
| 4   | Validation, exception handling, error contract                         |
| 5   | Spring Security, database login, JWT authorization, security tests     |
| 6   | Remaining tests, executable JAR, Docker, Actuator                      |
| 7   | Refactoring, documentation, clean build, final review                  |

---

## 2. Prerequisites

- Java 17 or later.
- Maven and an IDE such as IntelliJ IDEA, Eclipse, or VS Code.
- Basic understanding of Java OOP, exceptions, Maven, HTTP, JSON, SQL, JPA, Hibernate, password hashing, bearer tokens, and JWT claims.
- Docker or a compatible OCI container runtime for the packaging task.
- A relational database for normal application runtime, such as PostgreSQL, MySQL, SQL Server, or H2 file mode.
- H2 or the selected production database may be used for automated tests.
- An HTTP client such as `curl`, Postman, or an IDE HTTP client.

Use a Spring Boot version compatible with your selected Java version. Document the exact Java, Spring Boot, and database versions in `README.md`.

---

## 3. Constraints

| Scope       | Tools                                                                                                                            |
| ----------- | -------------------------------------------------------------------------------------------------------------------------------- |
| Required    | Java 17+, Maven, Spring Boot                                                                                                     |
| Required    | Spring Web MVC, Spring Data JPA, Bean Validation, Spring Security, Spring Boot Test, Spring Boot Actuator                        |
| Required    | JUnit, Mockito, MockMvc, Spring Security Test, `@WebMvcTest`, `@DataJpaTest`, and `@SpringBootTest`                              |
| Required    | A relational database; non-test data must not exist only in a Java collection                                                    |
| Required    | Controller-Service-Repository separation and constructor injection                                                               |
| Required    | Dedicated request/response DTOs; JPA entities must not be the public API contract                                                |
| Required    | Environment-based configuration and executable JAR packaging                                                                     |
| Required    | Dockerfile or Spring Boot Cloud Native Buildpacks for the container image                                                        |
| Allowed     | Lombok or a mapping library if its use is documented and the generated behavior can be explained                                 |
| Allowed     | H2 in-memory database for tests; H2 file mode or another relational database for normal runtime                                  |
| Allowed     | A maintained JWT library such as JJWT or Nimbus JOSE + JWT; document the selected library and version                            |
| Allowed     | Internet access, official documentation, and AI assistance for reference/debugging                                               |
| Not allowed | External identity providers or a separate authentication service as a substitute for the required EventHub login implementation  |
| Not allowed | Plain-text, reversible, `{noop}`, or fast general-purpose password hashing; hard-coded users as the final account store          |
| Not allowed | Trusting a client-supplied role header, accepting an unsigned/unverified JWT, or hard-coding a bearer token/signing secret       |
| Not allowed | A frontend, messaging, microservices, or cloud deployment as required scope                                                      |
| Not allowed | In-memory repository implementations replacing Spring Data JPA                                                                   |
| Not allowed | Returning stack traces, SQL details, credentials, or JPA entities from API endpoints                                             |

AI-assisted work must be disclosed briefly in `README.md`, including which parts received assistance. You remain responsible for correctness and must be able to explain all submitted code. Code that cannot be explained is not acceptable evidence of learning.

Do not add technologies outside the assignment scope merely to make the project look larger. Complete, correct core requirements are more important than optional features.

---

### Domain Model and API Contract

EventHub must include at least the following persistent entities. You may add fields when they support a clear requirement.

### `Event`

Required fields:

- `id`
- `title`
- `description`
- `location`
- `startAt`
- `capacity`
- `availableSeats`
- `status`: `DRAFT`, `OPEN`, `CLOSED`, or `CANCELLED`
- `createdAt`

### `Participant`

Required fields:

- `id`
- `fullName`
- `email`
- `createdAt`

Participant email must be unique.

### `Registration`

Required fields:

- `id`
- `event`
- `participant`
- `registeredAt`
- `cancelledAt`, nullable
- `status`: `ACTIVE` or `CANCELLED`

An event/participant pair must not have more than one active registration. Use application logic and an appropriate database integrity strategy. Document the exact strategy if cancelled registrations may be recreated.

### Required resource operations

Your URI design may vary slightly if it remains resource-oriented and is documented. The API must support at least:

```text
POST   /api/events
GET    /api/events/{eventId}
GET    /api/events
PUT    /api/events/{eventId}
POST   /api/events/{eventId}/cancellations

POST   /api/participants
GET    /api/participants/{participantId}
GET    /api/participants

POST   /api/events/{eventId}/registrations
GET    /api/events/{eventId}/registrations
DELETE /api/events/{eventId}/registrations/{registrationId}
```

If you choose a different route for an event or registration state transition, explain why it expresses the resource and HTTP semantics clearly.

### Required authorization matrix

Use these minimum access rules. You may make access stricter if the README explains the resulting contract.

| Operation                                                      | Minimum access                                                                         |
| -------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| Register a participant account or log in                       | Public                                                                                 |
| Read public event list/detail                                  | Public                                                                                 |
| Create/update/cancel an event                                  | `EVENT_ADMIN` authority                                                                |
| Create/read/list participants                                  | `EVENT_ADMIN` authority                                                                |
| Register for or cancel a registration                          | `PARTICIPANT` for their own participant identity, or `EVENT_ADMIN` for any participant |
| List registrations for an event                                | `EVENT_ADMIN` authority                                                                |
| `/actuator/health`                                             | Public                                                                                 |
| `/actuator/info`, `/actuator/metrics`, and metric detail paths | `EVENT_ADMIN` authority                                                                |

The server owns account roles. A client must never choose or override its role through registration input, a request header, or an untrusted JWT claim.

---

## 4. Tasks

### Task 1 - Spring Boot Foundation & Layered Architecture (10%)

Create a Maven Spring Boot project with a standard layout and the required starters.

Requirements:

- Provide a main class annotated with `@SpringBootApplication` in a root package.
- Organize code into clear feature or layer packages.
- Use constructor injection for required dependencies.
- Use Spring stereotypes intentionally: `@RestController`, `@Service`, `@Repository` where a custom persistence component exists, `@Configuration`, and `@Component` where appropriate.
- Define at least one explicit infrastructure bean with `@Configuration` and `@Bean`, such as a UTC `Clock`, and inject it instead of calling system time directly in business logic.
- Use `application.yml` or `application.properties` for safe defaults.
- Use a separate test configuration/profile.
- Define the three required entities and their basic relationships using modern `jakarta.*` imports.
- Keep singleton controllers and services stateless.
- Ensure the project has one documented command that starts the application.

Your README must include a short object graph or package responsibility explanation showing how Spring wires the main application components.

### Task 2 - REST API, DTOs & HTTP Contract (20%)

Implement the required event, participant, and registration resource operations.

Requirements:

- Use `@RestController` and explicit request mappings.
- Use path variables for resource identity and query parameters for filtering, pagination, and sorting.
- Support paginated event search with optional filters for at least:
  - title keyword
  - event status
  - start date/time from a supplied value
- Support paginated participant listing and paginated registration listing.
- Bound page size to a documented maximum.
- Use deterministic sorting with a unique tie-breaker.
- Use separate DTOs for create requests, update requests, list responses, and detail responses where their fields differ.
- Do not serialize JPA entities directly.
- Return deliberate HTTP statuses and headers, including:
  - `201 Created` plus `Location` for successful creation
  - `200 OK` for successful reads/updates with a body
  - `204 No Content` for successful cancellation/deletion without a body
- Keep `ResponseEntity`, servlet request objects, and HTTP status decisions out of the service and repository layers.
- Provide example request/response JSON for every endpoint group in the README or a checked-in `.http` file.

### Task 3 - Spring Data JPA & Transactional Business Rules (20%)

Create Spring Data JPA repositories and implement the registration use cases in the service layer.

Required repository behavior:

- Find a participant by email and enforce unique email persistence.
- Search events with pagination and deterministic sorting.
- Find registrations for an event with pagination.
- Check whether a participant already has an active registration for an event.
- Load the records required by registration and cancellation without exposing repository details to the controller.

Registration rules:

- The event and participant must exist.
- The event status must be `OPEN`.
- The event start time must be in the future.
- `availableSeats` must be greater than zero.
- The participant must not already have an active registration for the event.
- Creating the registration and decreasing `availableSeats` must complete in one transaction.
- Any failed rule or persistence operation must leave both the registration and seat count unchanged.

Cancellation rules:

- The registration must belong to the event in the path.
- Only an `ACTIVE` registration can be cancelled.
- Cancellation sets the status and `cancelledAt`, then restores one available seat.
- Repeating the cancellation must follow your documented idempotency/error contract and must never restore more than one seat.
- Cancelling an event must prevent new registrations. Document what happens to existing active registrations; automatic bulk cancellation is optional.

Transaction requirements:

- Place transaction boundaries on service use cases.
- Use read-only transactions for read use cases where appropriate.
- Rely on JPA managed-entity behavior deliberately; do not call `save` mechanically without understanding why.
- Add database constraints for durable integrity where appropriate.
- Document the sequential-concurrency limitation of a naive seat check. Advanced pessimistic/optimistic locking is optional and receives no credit unless the required behavior is already complete.

### Task 4 - Validation & Consistent Exception Handling (15%)

Validate request data and convert expected failures into one consistent API error contract.

Required validation includes:

- Event title, description, and location are not blank and have sensible maximum lengths.
- Capacity is positive.
- `availableSeats` cannot be set directly by a create/update client.
- Event `startAt` is required and creation input uses an appropriate future-date rule.
- Participant name is not blank.
- Participant email is required and email-shaped.
- Nested request objects or collection elements use cascading `@Valid` where applicable.
- Pagination values are bounded.

Requirements:

- Trigger request body validation with `@Valid`.
- Create meaningful domain exceptions for missing resources, duplicate email/registration, invalid event state, full capacity, and invalid cancellation.
- Use `@RestControllerAdvice` and `@ExceptionHandler` to centralize mappings.
- Return one documented error response shape containing at least:
  - timestamp
  - HTTP status
  - stable machine-readable code
  - human-readable message
  - request path
  - field violations when applicable
- Map validation/type/JSON failures to suitable `400` responses.
- Map missing resources to `404` and state/uniqueness conflicts to `409`.
- Return a safe generic `500` response for unexpected failures and log the actual exception server-side.
- Never return a stack trace, SQL statement, implementation class name, or secret to the client.

You may use a custom error DTO or Spring `ProblemDetail`; use one approach consistently.

### Task 5 - JWT Authentication & Role-Based Authorization (10%)

Implement authentication inside EventHub using Spring Security, database-backed accounts, encoded passwords, and signed JWT access tokens.

#### Account model and registration

- Add a persistent `UserAccount` entity with at least: `id`, unique `email`, `passwordHash`, `role`, `enabled`, and `createdAt`.
- Use roles `EVENT_ADMIN` and `PARTICIPANT`.
- Link each `PARTICIPANT` account to exactly one `Participant` record. An administrator account does not need a participant record.
- Provide `POST /api/auth/register` for creating a participant account and participant profile in one transaction.
- Public registration must always create `PARTICIPANT`; ignore/reject any client attempt to select `EVENT_ADMIN`.
- Provide a documented, safe way to create the initial administrator, such as an idempotent development seed using externalized credentials. Do not commit a real administrator password.
- Store only a one-way adaptive password hash using Spring Security `PasswordEncoder`, preferably `DelegatingPasswordEncoder`/BCrypt. Never store or log the raw password.

#### Login and JWT

- Provide `POST /api/auth/login` accepting email and password in a request DTO.
- Authenticate credentials through Spring Security's `AuthenticationManager`, a database-backed `UserDetailsService`, and `PasswordEncoder`; do not compare password strings manually.
- On successful login, return a signed JWT access token plus its type and expiration information.
- The token must contain a stable subject identifying the account, the server-owned role/authority, issued-at time, and expiration time.
- Sign tokens with a supported algorithm. Keep symmetric signing secrets or asymmetric private keys outside source control and load them from environment-based configuration.
- Validate signature, expiration, token structure, and the expected signing algorithm on every authenticated request. Merely Base64-decoding JWT claims is not authentication.
- Implement a Spring Security filter, such as a well-scoped `OncePerRequestFilter`, that reads the `Authorization: Bearer <token>` header, validates the token, loads or verifies the account state, and sets an authenticated `SecurityContext` only after successful validation.
- Use stateless session management; do not use an HTTP session to preserve authentication.

#### Authorization and error handling

- Configure a component-based `SecurityFilterChain` implementing the required authorization matrix.
- Link a `PARTICIPANT` principal to its participant record using the authenticated account identity. A participant must not register or cancel on behalf of another participant; an `EVENT_ADMIN` may do so.
- Use a deny-by-default fallback for routes not explicitly covered.
- Return `401 Unauthorized` for missing, invalid, expired, or disabled-account authentication.
- Return `403 Forbidden` when a valid authenticated account lacks the required role or resource ownership.
- Use the same safe API error contract for security failures via an authentication entry point and access-denied handler.
- Document the CSRF decision for a stateless bearer-token API. Configure CORS only if a real client origin requires it; do not combine wildcard origins with credentials.
- Never return or log raw passwords, password hashes, bearer tokens, signing secrets, or sensitive JWT contents.

#### Security tests

Add focused unit and MockMvc/integration tests covering at least:

- participant registration stores an encoded password and cannot self-assign `EVENT_ADMIN`
- successful login returns a signed, non-expired token
- wrong password returns `401` and no token
- public endpoint without a token succeeds
- protected endpoint without a token returns `401`
- tampered or expired token returns `401`
- authenticated account with insufficient role returns `403`
- `EVENT_ADMIN` can perform an administrator operation
- `PARTICIPANT` can perform an allowed operation for their own identity
- `PARTICIPANT` cannot register or cancel on behalf of another participant
- disabled account behavior follows the documented contract

This is a classification task: secure password handling, correct token validation, authorization boundaries, and meaningful tests matter more than the number of security annotations.

### Task 6 - Automated Testing (15%)

Create a test suite that demonstrates why different Spring test scopes exist.

Minimum evidence:

#### Service unit tests

- Use JUnit and Mockito without loading the Spring context.
- Cover successful registration and cancellation.
- Cover full capacity, duplicate registration, invalid event status, and missing resources.
- Verify that failed rules do not save a registration or incorrectly restore/decrease seats.
- Use a fixed `Clock` for time-sensitive behavior.

#### Web-layer tests

- Use `@WebMvcTest` and `MockMvc` with the service mocked in the test context.
- Cover at least one successful create response with `201` and `Location`.
- Cover path/query binding and JSON response content.
- Cover invalid request validation and field-error JSON.
- Cover `404` and `409` exception mappings.
- Verify invalid requests do not call the service.

#### Persistence tests

- Use `@DataJpaTest` with a real test database.
- Cover at least one derived/custom search query with pagination.
- Cover participant email uniqueness.
- Cover the chosen event/participant registration integrity strategy.
- Flush when required to prove database constraints.

#### Full application integration test

- Use `@SpringBootTest` with `MockMvc` or a random-port HTTP client.
- Cover at least one complete HTTP-to-database workflow.
- Isolate or clean test data so tests do not depend on execution order.

Tests must assert meaningful response fields, database state, or business outcomes, not only that a method completed or the context loaded.

### Task 7 - Packaging, Observability & Delivery Quality (10%)

Prepare EventHub as a deployable and observable application.

Requirements:

- Configure the Spring Boot Maven plugin and build an executable JAR.
- Document the commands to build, test, and run the JAR.
- Package the application as an OCI image using either:
  - a Dockerfile, or
  - `spring-boot:build-image` with Cloud Native Buildpacks.
- Run the container process as a non-root user.
- Do not bake credentials or environment-specific configuration into the JAR or image.
- Demonstrate environment-variable configuration for server port and database connection.
- Add Spring Boot Actuator.
- Expose only `health`, `info`, and `metrics` over HTTP for this assignment.
- Add useful application/build information to `info` without secrets.
- Document health, info, and metrics request examples and expected purpose.
- If liveness/readiness probes are enabled, explain their different operational consequences.
- Provide a complete README, sample seed data or documented create requests, and a clean deliverable.

Cloud deployment, Kubernetes manifests, Prometheus, Grafana, and distributed tracing are optional and do not replace any required item.

---

## 5. Deliverables

Submit one compressed project folder named:

```text
SBF_A_01_<trainee-account>_EventHub.zip
```

The archive must contain the EventHub Maven project with:

- `pom.xml`.
- Maven wrapper files if used.
- `src/main/java/` application source.
- `src/main/resources/` configuration.
- `src/test/java/` automated tests.
- `src/test/resources/` test configuration and test data if used.
- Security configuration, database-backed account implementation, login/registration endpoints, and JWT configuration with environment placeholders only.
- `Dockerfile` and `.dockerignore`, or documented Buildpacks commands.
- `.gitignore`.
- `README.md` containing:
  - project overview and architecture
  - exact Java, Spring Boot, and database versions
  - prerequisites
  - database and environment-variable configuration
  - build, test, JAR run, and container run commands
  - endpoint table with example requests/responses
  - pagination and error-contract documentation
  - business-rule and transaction explanation
  - test-scope summary
  - account bootstrap, password policy, login flow, authorization matrix, JWT claim/expiration/signing configuration, and `401`/`403` examples
  - Actuator endpoint explanation
  - AI-assistance disclosure
  - requirement checklist for Task 1 through Task 7
- An optional `requests.http`, Postman collection, or `curl` section for quick review.

Do **not** include:

- `target/` or generated build output.
- IDE-only folders such as `.idea/` or `.vscode/` unless a small shared configuration is intentionally required.
- Local database files unless the instructor explicitly requests seed data in that format.
- `.env` files containing real values.
- Passwords, API keys, tokens, private certificates, or production connection strings.
- Container image archives, dependency caches, log files, or unrelated large files.
- Nested `.git/` history.

Before submission, extract the archive into a clean directory and verify that the documented build/test command works using only the submitted files and documented prerequisites.
