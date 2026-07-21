# Spring Web & REST API

## 1. Objectives

After this unit, learners can:

- Describe the Spring MVC request flow and the role of `DispatcherServlet`.
- Create REST controllers with clear request mappings.
- Choose correctly between path variables, query parameters, headers, and request bodies.
- Design separate request and response DTOs.
- Separate controller, service, and repository responsibilities.
- Apply resource-oriented REST principles, HTTP methods, and status codes.
- Design consistent, maintainable API contracts.

---

## 2. Spring MVC in a Spring Boot Application

Spring MVC is the servlet-based web framework in Spring Framework. A typical Boot application enables it by adding:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

The starter commonly provides:

- Spring MVC.
- An embedded servlet container.
- JSON serialization and deserialization.
- Validation and HTTP integration points.
- Sensible default MVC configuration.

Application code then declares controllers and mappings instead of manually implementing the Servlet API.

---

## 3. Front Controller Pattern

Spring MVC follows the **front controller pattern**. A central servlet receives requests and coordinates specialized components.

That central servlet is `DispatcherServlet`.

```text
HTTP client
    |
    v
Servlet container
    |
    v
DispatcherServlet
    |
    +--> find matching handler
    +--> invoke controller method
    +--> resolve arguments and return value
    +--> handle exceptions
    +--> write HTTP response
```

The `DispatcherServlet` coordinates the flow; it should not contain application business rules.

### Why a front controller?

Central coordination provides consistent handling for:

- Request mapping.
- Parameter binding.
- Type conversion.
- Validation.
- Exception resolution.
- Response serialization.
- Locale, multipart, and other web concerns.

Individual controllers focus on application endpoints.

---

## 4. Detailed Request Flow

For `GET /api/books/42`, a simplified flow is:

```text
1. Client sends HTTP request
2. Servlet container forwards it to DispatcherServlet
3. HandlerMapping searches registered mappings
4. Matching controller method is selected
5. HandlerAdapter prepares and invokes that method
6. Argument resolvers build method arguments
7. Controller delegates to the service
8. Controller returns a response object
9. Return-value handlers process status and headers
10. HttpMessageConverter serializes the object to JSON
11. HTTP response is sent to the client
```

Example handler:

```java
@GetMapping("/{id}")
public BookResponse findById(@PathVariable long id) {
    return bookService.findById(id);
}
```

Important delegate components:

| Component | Role |
|---|---|
| `HandlerMapping` | Finds a handler matching method, path, and conditions |
| `HandlerAdapter` | Invokes the chosen handler using its programming model |
| Argument resolvers | Build values for `@PathVariable`, `@RequestParam`, `@RequestBody`, etc. |
| `HttpMessageConverter` | Reads/writes representations such as JSON |
| Exception resolvers | Convert exceptions into an MVC response |
| View resolvers | Resolve views for server-rendered MVC; usually not used by REST endpoints |

Spring Boot configures the common implementations. Developers normally extend behavior rather than constructing the `DispatcherServlet` manually.

---

## 5. Controllers and `@RestController`

### `@Controller`

`@Controller` marks a web component. A method can return a logical view name for server-rendered HTML.

```java
@Controller
public class HomeController {
    @GetMapping("/")
    public String home() {
        return "home";
    }
}
```

### `@RestController`

`@RestController` combines `@Controller` and `@ResponseBody`. Return values are written to the HTTP response body through message conversion.

```java
@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }
}
```

For a REST API, use `@RestController` unless the controller also needs a special view-rendering design.

---

## 6. Request Mappings

`@RequestMapping` can define a shared base path. HTTP-specific annotations define operations:

```java
@RestController
@RequestMapping("/api/books")
public class BookController {

    @GetMapping
    public List<BookResponse> findAll() { /* ... */ }

    @GetMapping("/{id}")
    public BookResponse findById(@PathVariable long id) { /* ... */ }

    @PostMapping
    public ResponseEntity<BookResponse> create(
            @RequestBody CreateBookRequest request) { /* ... */ }

    @PutMapping("/{id}")
    public BookResponse replace(
            @PathVariable long id,
            @RequestBody UpdateBookRequest request) { /* ... */ }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) { /* ... */ }
}
```

Mapping annotations:

| Annotation | HTTP method | Typical use |
|---|---|---|
| `@GetMapping` | GET | Read resources |
| `@PostMapping` | POST | Create a resource or start a non-idempotent operation |
| `@PutMapping` | PUT | Replace a resource at a known URI |
| `@PatchMapping` | PATCH | Partially update a resource |
| `@DeleteMapping` | DELETE | Remove a resource |

Avoid generic endpoints such as `/api/doAction` when a clear resource and HTTP method can express the operation.

---

## 7. Path Variables vs Query Parameters

### Path variable

A path variable identifies a resource or a position in a resource hierarchy.

```java
@GetMapping("/{id}")
public BookResponse findById(@PathVariable long id) {
    return bookService.findById(id);
}
```

Request:

```http
GET /api/books/42
```

Nested resource example:

```http
GET /api/authors/7/books/42
```

### Query parameter

A query parameter modifies or narrows a collection request.

```java
@GetMapping
public PageResponse<BookSummaryResponse> search(
        @RequestParam(required = false) String title,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    return bookService.search(title, page, size);
}
```

Request:

```http
GET /api/books?title=spring&page=0&size=20
```

Decision rule:

| Requirement | Use |
|---|---|
| Identifies one resource | Path variable |
| Represents hierarchy | Path segment |
| Filters a collection | Query parameter |
| Controls sorting or pagination | Query parameter |
| Sends structured creation/update data | Request body |
| Sends metadata such as locale or authorization | Header |

Bad design:

```http
GET /api/books?id=42
```

This can work, but `/api/books/42` communicates resource identity more clearly.

### Optional query parameters

Do not use optional values without defining semantics.

```java
@RequestParam(required = false) String author
```

Document whether missing, empty, and whitespace-only values mean the same thing. Normalize them before repository querying.

---

## 8. Request Headers and Bodies

### Header

```java
@GetMapping("/{id}")
public BookResponse findById(
        @PathVariable long id,
        @RequestHeader(name = "X-Correlation-Id", required = false)
        String correlationId) {
    return bookService.findById(id);
}
```

Headers carry request metadata, not the main resource representation.

### Request body

```java
@PostMapping
public ResponseEntity<BookResponse> create(
        @RequestBody CreateBookRequest request) {
    BookResponse created = bookService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
}
```

An HTTP request has one body. Group related fields into a request DTO instead of defining many unrelated parameters.

---

## 9. DTOs: Request and Response Models

**DTO** means Data Transfer Object. It defines data crossing an application boundary.

Request DTO:

```java
public record CreateBookRequest(
        String isbn,
        String title,
        String author,
        BigDecimal price
) {
}
```

Response DTO:

```java
public record BookResponse(
        long id,
        String isbn,
        String title,
        String author,
        BigDecimal price,
        Instant createdAt
) {
}
```

DTOs can be records or normal classes. Choose the style supported by the team's Java version and serialization conventions.

### Why not expose JPA entities directly?

Returning entities creates accidental coupling between the API and persistence model:

- Internal fields may leak to clients.
- Lazy relationships may cause serialization failures or extra queries.
- Bidirectional relationships may recurse infinitely.
- Changing a database mapping can break the public API.
- Clients may send fields they should not control.
- Entity annotations become mixed with API concerns.

Keep boundaries explicit:

```text
HTTP JSON <-> Request/Response DTO <-> Service <-> Entity <-> Database
```

### Request and response models are different contracts

Create request:

```json
{
  "isbn": "9781617297571",
  "title": "Spring in Action",
  "author": "Craig Walls",
  "price": 45.00
}
```

Response:

```json
{
  "id": 42,
  "isbn": "9781617297571",
  "title": "Spring in Action",
  "author": "Craig Walls",
  "price": 45.00,
  "createdAt": "2026-07-15T04:30:00Z"
}
```

The client does not choose server-generated `id` or `createdAt` values.

### Summary vs detail response

Collection endpoints often need less data than detail endpoints:

```java
public record BookSummaryResponse(
        long id,
        String title,
        String author
) {
}
```

Do not create different models without a real contract need. Too many nearly identical DTOs add maintenance cost.

---

## 10. Mapping Between Entities and DTOs

For a small application, an explicit mapper is clear:

```java
@Component
public class BookMapper {
    public Book toEntity(CreateBookRequest request) {
        return new Book(
                request.isbn(),
                request.title(),
                request.author(),
                request.price());
    }

    public BookResponse toResponse(Book book) {
        return new BookResponse(
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                book.getAuthor(),
                book.getPrice(),
                book.getCreatedAt());
    }
}
```

Mapping can also be a private method or use a mapping library. Regardless of tool, the application owns the contract and must test important transformations.

Avoid placing repository calls inside a mapper. Mapping should not silently perform I/O.

---

## 11. Layered Design

A common Spring application separates web, business, and persistence responsibilities.

```text
Client
  |
  v
Controller       HTTP contract and web concerns
  |
  v
Service          use case and business rules
  |
  v
Repository       persistence abstraction
  |
  v
Database
```

Dependencies point inward from delivery mechanism toward application logic. A repository does not call a controller.

### Controller responsibilities

A controller should:

- Declare routes and HTTP methods.
- Bind path, query, header, and body inputs.
- Trigger input validation.
- Delegate to a service.
- Choose HTTP status and response headers.
- Return response DTOs.

A controller should not:

- Implement pricing, eligibility, or workflow rules.
- Build database queries.
- Open transactions manually.
- Return JPA entities as an accidental API.

### Service responsibilities

A service should:

- Represent application use cases.
- Enforce business rules.
- Coordinate repositories and external services.
- Define transaction boundaries where appropriate.
- Work independently of HTTP details.
- Return application results or DTOs according to the chosen architecture.

A service should not:

- Read `HttpServletRequest` for ordinary business data.
- Return `ResponseEntity`.
- Decide JSON property names.
- Depend on a controller.

### Repository responsibilities

A repository should:

- Load and persist entities.
- Express database queries.
- Hide routine persistence operations.
- Return persistence-oriented results.

A repository should not:

- Select HTTP status codes.
- Contain workflow rules.
- Return API response wrappers.
- Call web endpoints.

---

## 12. Service Layer Example

```java
@Service
@Transactional(readOnly = true)
public class BookService {
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final Clock clock;

    public BookService(
            BookRepository bookRepository,
            BookMapper bookMapper,
            Clock clock) {
        this.bookRepository = bookRepository;
        this.bookMapper = bookMapper;
        this.clock = clock;
    }

    public BookResponse findById(long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
        return bookMapper.toResponse(book);
    }

    @Transactional
    public BookResponse create(CreateBookRequest request) {
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateIsbnException(request.isbn());
        }

        Book book = bookMapper.toEntity(request);
        book.setCreatedAt(Instant.now(clock));
        return bookMapper.toResponse(bookRepository.save(book));
    }
}
```

The uniqueness check and creation workflow are business/application concerns. The controller should not duplicate them.

### Independent of web concerns

The service accepts meaningful Java values and returns an application result. It does not know:

- The URL of the endpoint.
- Whether input came from JSON.
- Which HTTP status will be returned.
- Whether the use case was started by HTTP, a scheduled job, or messaging.

This independence improves reuse and testing.

---

## 13. Complete Controller Example

```java
@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/{id}")
    public BookResponse findById(@PathVariable long id) {
        return bookService.findById(id);
    }

    @GetMapping
    public PageResponse<BookSummaryResponse> search(
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return bookService.search(title, page, size);
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(
            @RequestBody CreateBookRequest request) {
        BookResponse created = bookService.create(request);
        URI location = URI.create("/api/books/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public BookResponse update(
            @PathVariable long id,
            @RequestBody UpdateBookRequest request) {
        return bookService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        bookService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

This controller is intentionally thin. Thin does not mean “no responsibility”; it owns the HTTP boundary while delegating rules.

---

## 14. REST Principles

REST is an architectural style. A practical REST API commonly applies these ideas.

### 14.1 Resource-oriented URIs

Model nouns, not implementation procedures.

Prefer:

```text
GET    /api/books
GET    /api/books/42
POST   /api/books
PUT    /api/books/42
DELETE /api/books/42
```

Avoid:

```text
POST /api/getAllBooks
POST /api/createBook
POST /api/deleteBook?id=42
```

Some domain actions are not simple CRUD. Model them clearly:

```text
POST /api/orders/91/cancellations
POST /api/books/42/reservations
```

### 14.2 Uniform HTTP semantics

Use methods consistently:

- `GET` is safe and should not change business state.
- `PUT` and `DELETE` are intended to be idempotent.
- `POST` is usually non-idempotent unless the API adds an idempotency mechanism.
- `PATCH` applies a partial change whose exact semantics must be documented.

**Idempotent** means that repeating the same request has the same intended effect as sending it once. It does not require identical log entries or response timestamps.

### 14.3 Stateless requests

Each request should carry the information needed to process it. The server may store resource state and authentication/session state, but controllers should not rely on hidden per-client conversational state for ordinary REST workflows.

### 14.4 Representations

A resource is a conceptual thing; JSON is one representation of it. HTTP metadata communicates how to interpret the representation.

```http
Content-Type: application/json
Accept: application/json
```

### 14.5 Cache semantics

HTTP supports cache headers, validators, and conditional requests. Even if a beginner API does not implement them, avoid unsafe behavior in `GET` endpoints because clients and infrastructure assume standard HTTP semantics.

---

## 15. HTTP Status Codes

Use status codes to communicate the outcome at the protocol level.

### Success

| Code | Meaning | Typical API use |
|---|---|---|
| `200 OK` | Successful request | Read or update with a response body |
| `201 Created` | Resource created | Successful `POST`; often include `Location` |
| `204 No Content` | Successful with no body | Delete or update without a response body |

### Client errors

| Code | Meaning | Typical API use |
|---|---|---|
| `400 Bad Request` | Malformed or invalid request | Invalid JSON, type mismatch, validation failure |
| `401 Unauthorized` | Authentication required/invalid | Missing or invalid credentials |
| `403 Forbidden` | Authenticated but not permitted | Insufficient authority |
| `404 Not Found` | Resource does not exist | Unknown book ID |
| `409 Conflict` | Conflict with current state | Duplicate ISBN, invalid state transition |
| `422 Unprocessable Content` | Well-formed but semantically invalid | Used by some APIs for domain validation |

### Server errors

| Code | Meaning | Typical API use |
|---|---|---|
| `500 Internal Server Error` | Unexpected server failure | Unhandled defect or infrastructure error |
| `503 Service Unavailable` | Temporarily unavailable | Dependency outage or overload |

Do not return `200 OK` with `{ "success": false }` for every failure. HTTP already has a status model.

### `401` vs `403`

- `401`: the client must authenticate successfully.
- `403`: the server recognizes the identity but refuses the operation.

---

## 16. `ResponseEntity`

Use `ResponseEntity<T>` when the controller needs explicit status or headers.

```java
return ResponseEntity
        .created(URI.create("/api/books/" + result.id()))
        .body(result);
```

```java
return ResponseEntity.noContent().build();
```

Returning a DTO directly is fine when the default `200 OK` is correct:

```java
@GetMapping("/{id}")
public BookResponse findById(@PathVariable long id) {
    return bookService.findById(id);
}
```

Do not return `ResponseEntity` from the service layer. It is an HTTP concern.

---

## 17. Pagination Contract

Collection endpoints should not return an unlimited table.

Request:

```http
GET /api/books?page=0&size=20&sort=title,asc
```

One explicit response model:

```java
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
```

Example JSON:

```json
{
  "content": [
    { "id": 42, "title": "Spring in Action", "author": "Craig Walls" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Define and document:

- Whether page numbering starts at `0` or `1`.
- Maximum allowed page size.
- Default sort order.
- Syntax for multiple sort fields.
- Behavior for an out-of-range page.

Do not expose framework pagination JSON accidentally and assume its shape will remain the public contract.

---

## 18. API Design Best Practices

### Consistent naming

Choose one JSON naming style and use it consistently:

```json
{
  "createdAt": "2026-07-15T04:30:00Z"
}
```

Do not mix `createdAt`, `created_at`, and `CreateDate` without a contract reason.

### Stable identifiers

Use stable identifiers in URIs. Do not use a list index or mutable display name as the resource identity.

### Clear time and number formats

- Use an unambiguous timestamp with offset or UTC, commonly ISO 8601.
- Represent money with a deliberate decimal strategy, not binary floating-point business arithmetic.
- Document units for durations, weights, and sizes.

### Validate at the boundary, enforce rules in the service

- Structural rule: title must not be blank — request validation.
- Business rule: ISBN must be unique — service plus database constraint.
- Authorization rule: only an owner may update — security/service policy.

### Do not leak internals

Avoid returning stack traces, SQL, package names, database column names, or secrets.

### Evolve deliberately

API changes can break clients. Additive changes are usually safer than renaming or changing the type of an existing field. Have an explicit compatibility and versioning policy.

### Document examples and edge cases

An endpoint contract should answer:

- Required and optional inputs.
- Validation limits.
- Successful status and body.
- Error statuses and error shape.
- Pagination and sorting rules.
- Authorization requirements.

---

## 19. Common Anti-patterns

### Fat controller

```java
@PostMapping
public BookResponse create(@RequestBody CreateBookRequest request) {
    if (repository.existsByIsbn(request.isbn())) { /* ... */ }
    Book book = new Book(/* ... */);
    calculateDiscount(book);
    repository.save(book);
    sendNotification(book);
    return /* ... */;
}
```

The controller owns business workflow and is difficult to reuse or unit test. Move the use case into a service.

### Pass-through service

```java
public List<Book> findAll() {
    return repository.findAll();
}
```

A simple pass-through may be acceptable temporarily, but do not create layers mechanically. The service should represent a meaningful application boundary and be ready to own policies such as transactions, mapping, authorization, and orchestration.

### Generic response envelope for everything

```json
{
  "status": 200,
  "message": "success",
  "data": { }
}
```

An envelope can be a valid organizational standard, but it often duplicates HTTP and adds noise. Use it only when the API has a clear, consistently applied need.

### One DTO for create, update, persistence, and response

Different operations have different permissions and required fields. A single model often exposes fields unintentionally.

### Returning `null` for missing resources

An absent resource should result in a deliberate `404` response, not an empty `200` or an accidental `NullPointerException`.

---

## 20. Endpoint Design Exercise

Design endpoints for a library lending system with books, members, and loans.

Possible resource model:

```text
GET    /api/books?available=true&page=0&size=20
GET    /api/books/{bookId}
POST   /api/books
PUT    /api/books/{bookId}
DELETE /api/books/{bookId}

GET    /api/members/{memberId}/loans?status=active
POST   /api/members/{memberId}/loans
POST   /api/loans/{loanId}/returns
```

Discuss:

- Is “return book” better represented as `PATCH /loans/{id}` or `POST /loans/{id}/returns`?
- Which fields belong in `CreateLoanRequest`?
- Which values are generated by the server?
- What happens when the book is already on loan?
- Which response status represents each outcome?

There may be multiple reasonable designs. Consistency and documented semantics matter more than copying one URI pattern mechanically.

---

## 21. Review Checklist

For each endpoint, verify:

- The URI represents a resource or clear domain operation.
- The HTTP method matches the operation semantics.
- Path variables identify resources; query parameters refine collections.
- Input uses a dedicated request model where appropriate.
- Output does not expose persistence entities accidentally.
- The controller contains HTTP concerns, not business rules.
- The service is independent of Spring MVC types.
- Success and error statuses are deliberate.
- List endpoints have bounded pagination.
- Naming, time formats, and error shapes are consistent.

---

## 22. Knowledge Check

1. What pattern does `DispatcherServlet` implement?
2. Which component finds a matching controller method?
3. What is the difference between `@Controller` and `@RestController`?
4. When should a value be a path variable instead of a query parameter?
5. Why should an API avoid exposing JPA entities directly?
6. Which layer should enforce a duplicate ISBN rule?
7. Why should a service avoid returning `ResponseEntity`?
8. Which HTTP status is suitable after creating a resource?
9. What does idempotent mean?
10. Why should collection endpoints be paginated?

---

## 23. Further Reading

- [Spring MVC `DispatcherServlet`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html)
- [Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)
- [Mapping Requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html)
- [Spring Boot Servlet Web Applications](https://docs.spring.io/spring-boot/reference/web/servlet.html)
- [HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110)

