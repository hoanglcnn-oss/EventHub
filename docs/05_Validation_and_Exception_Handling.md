# Validation & Exception Handling

## 1. Objectives

After this unit, learners can:

- Apply Jakarta Bean Validation constraints to request models.
- Trigger object graph validation with `@Valid`.
- Distinguish boundary validation from business rules and database constraints.
- Handle local exceptions with `@ExceptionHandler`.
- Centralize API exception mapping with `@ControllerAdvice`.
- Design consistent, safe, and useful error responses.
- Test both successful and invalid request paths.

---

## 2. Why Validation Is a Boundary Concern

External input cannot be trusted. A client may send:

- Missing required fields.
- Blank text.
- Negative prices.
- Invalid email addresses.
- Oversized content.
- Incorrect JSON types.
- Syntactically valid data that violates a business rule.

Validation should reject invalid data early and return a stable explanation.

```text
HTTP request
    |
    v
JSON parsing
    |
    v
Bean Validation constraints
    |
    v
Service business rules
    |
    v
Database constraints
```

Each level protects a different concern. One level does not eliminate the others.

---

## 3. Bean Validation Basics

Modern Spring applications use the Jakarta Validation API:

```java
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
```

Spring Boot project dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

The API defines annotations and validation contracts. A provider, commonly Hibernate Validator, performs validation at runtime. Hibernate Validator is separate from Hibernate ORM, although both are Hibernate projects.

---

## 4. Common Constraints

| Constraint | Typical meaning |
|---|---|
| `@NotNull` | Value must not be `null` |
| `@NotBlank` | Text must contain at least one non-whitespace character |
| `@NotEmpty` | String, collection, map, or array must not be null or empty |
| `@Size(min, max)` | Size must be within bounds |
| `@Min`, `@Max` | Integer-style numeric lower/upper bound |
| `@Positive`, `@PositiveOrZero` | Numeric sign constraint |
| `@DecimalMin`, `@DecimalMax` | Decimal boundary represented precisely as text |
| `@Email` | Email-shaped text |
| `@Pattern` | Text must match a regular expression |
| `@Past`, `@PastOrPresent` | Date/time must be in the past |
| `@Future`, `@FutureOrPresent` | Date/time must be in the future |
| `@Digits` | Limits integer and fractional digits |

Choose the constraint that communicates intent.

Prefer:

```java
@NotBlank
private String title;
```

Over a harder-to-read equivalent:

```java
@NotNull
@Size(min = 1)
@Pattern(regexp = ".*\\S.*")
private String title;
```

---

## 5. Request DTO Validation

```java
public record CreateBookRequest(
        @NotBlank(message = "ISBN is required")
        @Pattern(
                regexp = "^(?:\\d{10}|\\d{13})$",
                message = "ISBN must contain 10 or 13 digits")
        String isbn,

        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @NotBlank(message = "Author is required")
        @Size(max = 120, message = "Author must not exceed 120 characters")
        String author,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
        @Digits(integer = 10, fraction = 2,
                message = "Price must have at most 10 integer digits and 2 decimals")
        BigDecimal price
) {
}
```

Controller:

```java
@PostMapping
public ResponseEntity<BookResponse> create(
        @Valid @RequestBody CreateBookRequest request) {
    BookResponse created = bookService.create(request);
    return ResponseEntity
            .created(URI.create("/api/books/" + created.id()))
            .body(created);
}
```

`@Valid` asks Spring MVC to validate the deserialized object before calling the controller method. If validation fails, normal controller logic does not execute.

---

## 6. Constraint Semantics and Null

Many constraints accept `null` as valid because nullability is a separate concern.

For example, this does not necessarily reject `null`:

```java
@Size(max = 200)
String title
```

Use `@NotNull`, `@NotBlank`, or `@NotEmpty` when absence is forbidden.

### `@NotNull` vs `@NotEmpty` vs `@NotBlank`

| Value | `@NotNull` | `@NotEmpty` | `@NotBlank` |
|---|---:|---:|---:|
| `null` | invalid | invalid | invalid |
| `""` | valid | invalid | invalid |
| `"   "` | valid | valid | invalid |
| `"Spring"` | valid | valid | valid |

Use `@NotBlank` for required human-entered text.

---

## 7. Nested Object Validation

Constraint annotations on a nested object are not automatically traversed without cascading validation.

```java
public record AddressRequest(
        @NotBlank String street,
        @NotBlank String city,
        @Pattern(regexp = "^[0-9]{5,6}$") String postalCode
) {
}
```

```java
public record CreateMemberRequest(
        @NotBlank String fullName,
        @Email @NotBlank String email,
        @NotNull @Valid AddressRequest address
) {
}
```

`@Valid` on `address` tells the validator to inspect its constraints.

Collections also need element cascading:

```java
public record CreateOrderRequest(
        @NotEmpty(message = "At least one item is required")
        List<@Valid OrderItemRequest> items
) {
}
```

Container element constraints can validate values directly:

```java
List<@NotBlank String> tags
```

---

## 8. Path and Query Parameter Validation

Request body validation and method parameter validation have different failure paths.

Example:

```java
@GetMapping
public PageResponse<BookSummaryResponse> search(
        @RequestParam(defaultValue = "0")
        @PositiveOrZero int page,

        @RequestParam(defaultValue = "20")
        @Min(1) @Max(100) int size) {
    return bookService.search(page, size);
}
```

Modern Spring MVC supports validation of controller method parameters when Bean Validation is present. Depending on the method signature and framework version, failures can be represented by method-validation exceptions rather than request-body validation exceptions. A global handler should account for both categories used by the project's Spring version.

Even with annotation validation, enforce a maximum page size in the service or application policy so non-web callers cannot bypass it.

---

## 9. Validation vs Business Rules

Bean Validation is best for local, structural rules:

- Required field.
- Text length.
- Number range.
- Date direction.
- Format.

Business rules often require current application state:

- ISBN must be unique.
- Member must have fewer than five active loans.
- Book must be available before borrowing.
- Return date cannot precede the loan's actual start date.
- A price change above 30% requires approval.

These belong in a service or domain model:

```java
@Transactional
public BookResponse create(CreateBookRequest request) {
    if (bookRepository.existsByIsbn(request.isbn())) {
        throw new DuplicateIsbnException(request.isbn());
    }

    Book book = mapper.toEntity(request);
    return mapper.toResponse(bookRepository.save(book));
}
```

Database integrity also remains necessary:

```sql
unique (isbn)
```

```text
Bean Validation       -> Is ISBN present and correctly shaped?
Service rule          -> Does a book with this ISBN already exist?
Database constraint   -> Can concurrent writes violate uniqueness?
```

---

## 10. Exceptions as Failure Signals

Define exceptions with domain meaning:

```java
public class BookNotFoundException extends RuntimeException {
    private final long bookId;

    public BookNotFoundException(long bookId) {
        super("Book %d was not found".formatted(bookId));
        this.bookId = bookId;
    }

    public long getBookId() {
        return bookId;
    }
}
```

```java
public class DuplicateIsbnException extends RuntimeException {
    private final String isbn;

    public DuplicateIsbnException(String isbn) {
        super("A book with ISBN %s already exists".formatted(isbn));
        this.isbn = isbn;
    }
}
```

Services throw meaningful exceptions. The web layer maps them to HTTP responses.

Avoid throwing `ResponseStatusException` from the domain/service layer as a default design; it couples application logic to HTTP. It can be convenient for small web-only applications, but use it deliberately.

---

## 11. `@ExceptionHandler`

An `@ExceptionHandler` method handles exceptions raised by controller execution.

Local handler:

```java
@RestController
@RequestMapping("/api/books")
public class BookController {
    // Endpoints omitted.

    @ExceptionHandler(BookNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(
            BookNotFoundException exception,
            HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "BOOK_NOT_FOUND",
                exception.getMessage(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}
```

Local handling is useful when behavior is truly controller-specific. Repeating the same handlers across controllers is a signal to centralize them.

Handler methods can return:

- `ResponseEntity<T>`.
- A response DTO with `@ResponseStatus`.
- `ProblemDetail`.
- Other MVC-supported return types.

---

## 12. `@ControllerAdvice` and `@RestControllerAdvice`

`@ControllerAdvice` applies cross-cutting controller logic across multiple controllers. `@RestControllerAdvice` combines it with `@ResponseBody` behavior.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<ApiError> handleBookNotFound(
            BookNotFoundException exception,
            HttpServletRequest request) {
        return build(
                HttpStatus.NOT_FOUND,
                "BOOK_NOT_FOUND",
                exception.getMessage(),
                request.getRequestURI(),
                List.of());
    }

    @ExceptionHandler(DuplicateIsbnException.class)
    public ResponseEntity<ApiError> handleDuplicateIsbn(
            DuplicateIsbnException exception,
            HttpServletRequest request) {
        return build(
                HttpStatus.CONFLICT,
                "DUPLICATE_ISBN",
                exception.getMessage(),
                request.getRequestURI(),
                List.of());
    }
}
```

Benefits:

- Consistent status mapping.
- One error response shape.
- Controllers stay focused on normal request flow.
- Internal exception types are translated at the web boundary.

Advice can be scoped by package, annotation, or assignable controller type when an application has multiple API styles.

---

## 13. Error Response Design

A useful error contract is:

- Stable enough for clients to parse.
- Clear enough for developers to diagnose.
- Safe for public exposure.
- Consistent across endpoints.

Example:

```java
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldViolation> violations
) {
    public static ApiError of(
            HttpStatus status,
            String code,
            String message,
            String path) {
        return new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                List.of());
    }
}
```

```java
public record FieldViolation(
        String field,
        String code,
        String message
) {
}
```

Example JSON:

```json
{
  "timestamp": "2026-07-15T05:10:22Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/books",
  "violations": [
    {
      "field": "title",
      "code": "NotBlank",
      "message": "Title is required"
    },
    {
      "field": "price",
      "code": "DecimalMin",
      "message": "Price must be at least 0.01"
    }
  ]
}
```

### Stable machine code vs human message

- `code`: stable identifier used by client logic, such as `BOOK_NOT_FOUND`.
- `message`: human-readable explanation, which may be localized or revised.

Clients should not parse English sentences to decide what happened.

### Correlation ID

Production APIs often add a request/correlation identifier:

```json
{
  "traceId": "7e3fa6a4d91c4b52"
}
```

The public error can stay safe while logs contain detailed diagnostics linked by that identifier.

---

## 14. Handling Request Body Validation Errors

Invalid `@Valid @RequestBody` input commonly raises `MethodArgumentNotValidException`.

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiError> handleValidation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request) {

    List<FieldViolation> violations = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new FieldViolation(
                    error.getField(),
                    error.getCode(),
                    error.getDefaultMessage()))
            .toList();

    return build(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_FAILED",
            "Request validation failed",
            request.getRequestURI(),
            violations);
}
```

Helper:

```java
private ResponseEntity<ApiError> build(
        HttpStatus status,
        String code,
        String message,
        String path,
        List<FieldViolation> violations) {

    ApiError body = new ApiError(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            code,
            message,
            path,
            violations);

    return ResponseEntity.status(status).body(body);
}
```

Avoid including the rejected value for sensitive fields such as passwords or tokens.

---

## 15. JSON Parsing and Type Errors

These two requests fail differently.

Constraint violation:

```json
{
  "title": "",
  "price": -2.00
}
```

The JSON can be parsed, then validation reports invalid values.

Malformed or incompatible JSON:

```json
{
  "title": "Spring",
  "price": "not-a-number"
}
```

Deserialization fails before a valid DTO exists, commonly producing `HttpMessageNotReadableException`.

```java
@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<ApiError> handleUnreadableBody(
        HttpMessageNotReadableException exception,
        HttpServletRequest request) {
    return build(
            HttpStatus.BAD_REQUEST,
            "MALFORMED_JSON",
            "Request body is missing or contains invalid JSON",
            request.getRequestURI(),
            List.of());
}
```

Do not expose the full parser exception to clients; it can reveal implementation types and internal details.

---

## 16. Type Mismatch and Missing Parameters

Request:

```http
GET /api/books/not-a-number
```

Controller expects:

```java
@PathVariable long id
```

Spring cannot convert the text to `long`. Map the failure to a clear `400` response such as:

```json
{
  "code": "INVALID_PARAMETER",
  "message": "Parameter 'id' has an invalid value"
}
```

Other web input errors include:

- Missing required query parameter.
- Unsupported HTTP method.
- Unsupported media type.
- Unacceptable response representation.

Decide whether the API will customize each response or use Spring's standard error representation consistently.

---

## 17. RFC 9457 Problem Details

Spring supports `ProblemDetail`, a standard-oriented HTTP error representation.

```java
@ExceptionHandler(BookNotFoundException.class)
public ProblemDetail handleNotFound(BookNotFoundException exception) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            exception.getMessage());
    problem.setTitle("Book not found");
    problem.setProperty("code", "BOOK_NOT_FOUND");
    return problem;
}
```

Conceptual JSON:

```json
{
  "type": "about:blank",
  "title": "Book not found",
  "status": 404,
  "detail": "Book 42 was not found",
  "instance": "/api/books/42",
  "code": "BOOK_NOT_FOUND"
}
```

Choose either a custom error contract or `ProblemDetail` based on organizational needs. Do not return several unrelated shapes from different controllers.

---

## 18. Mapping Exceptions to Status Codes

| Failure | Possible exception | Status |
|---|---|---:|
| Invalid request fields | `MethodArgumentNotValidException` | 400 |
| Invalid JSON/type | `HttpMessageNotReadableException` | 400 |
| Unknown book | `BookNotFoundException` | 404 |
| Duplicate ISBN | `DuplicateIsbnException` | 409 |
| Invalid state transition | `BookUnavailableException` | 409 |
| Not authenticated | Security exception/entry point | 401 |
| Not permitted | Security exception/handler | 403 |
| Unexpected defect | unhandled exception | 500 |

The same Java exception is not always the same HTTP status in every system. Define mapping based on the API contract, not exception class names alone.

---

## 19. Unexpected Exceptions

A final handler can provide a safe response:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiError> handleUnexpected(
        Exception exception,
        HttpServletRequest request) {
    log.error("Unhandled request failure for {}", request.getRequestURI(), exception);

    return build(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            request.getRequestURI(),
            List.of());
}
```

Rules:

- Log the real exception server-side.
- Return a generic safe message.
- Do not include a stack trace in the API response.
- Do not turn every exception into `400`; server defects must remain visible as server errors.
- Avoid a broad catch that hides framework exceptions that already have correct mappings unless the handler preserves their semantics.

---

## 20. Custom Class-level Constraint

Use a class-level constraint when validity depends on multiple fields.

```java
@ValidLoanPeriod
public record CreateLoanRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate dueDate
) {
}
```

Conceptual validator:

```java
public class LoanPeriodValidator
        implements ConstraintValidator<ValidLoanPeriod, CreateLoanRequest> {

    @Override
    public boolean isValid(
            CreateLoanRequest value,
            ConstraintValidatorContext context) {
        if (value == null
                || value.startDate() == null
                || value.dueDate() == null) {
            return true; // Null checks are handled by @NotNull.
        }
        return !value.dueDate().isBefore(value.startDate());
    }
}
```

Do not query the database from a Bean Validation constraint by default. Cross-record business rules are usually clearer and more transactionally correct in the service layer.

---

## 21. Validation Messages

Messages may be inline:

```java
@NotBlank(message = "Title is required")
```

Or referenced by message key:

```java
@NotBlank(message = "{book.title.required}")
```

`ValidationMessages.properties`:

```properties
book.title.required=Title is required
book.price.minimum=Price must be at least {value}
```

Message bundles support consistency and localization. Keep the stable machine error code separate from localized text.

---

## 22. Common Mistakes

### Missing `@Valid`

Constraints exist on the DTO, but the controller never triggers validation.

### Using only DTO validation for uniqueness

Uniqueness depends on stored data and concurrency. Enforce it with a service check and database constraint.

### Returning every error as `500`

Expected client mistakes should map to stable 4xx responses.

### Returning every error as `200`

This breaks HTTP semantics and client/infrastructure behavior.

### Exposing exception messages directly

Some exception messages contain SQL, class names, file paths, or secret values. Map internal failures to safe messages.

### Catching exceptions in every controller

This duplicates code. Use centralized advice for shared mappings.

### Different error shapes per endpoint

Clients must implement special parsing for every controller. Define one contract.

### Validating entities as the API contract

Entity constraints protect persistence assumptions, but create and update operations may have different input rules. Use dedicated request DTOs.

---

## 23. Error Handling Checklist

- Request DTOs express structural constraints.
- `@Valid` is placed on nested structures and controller request bodies where needed.
- Business rules are enforced in a service/domain boundary.
- Database constraints protect durable integrity.
- Exceptions have meaningful domain names.
- Global advice maps expected exceptions to correct 4xx statuses.
- Unexpected exceptions are logged and return a safe 500 response.
- Error JSON has a stable code and consistent shape.
- Sensitive values and stack traces are excluded.
- Tests cover malformed JSON, field violations, missing resources, and conflicts.

---

## 24. Knowledge Check

1. What is the difference between `@NotNull`, `@NotEmpty`, and `@NotBlank`?
2. What does `@Valid` do on a request body?
3. Why is `@Valid` needed on a nested object field?
4. Where should ISBN uniqueness be enforced?
5. What is the purpose of `@ExceptionHandler`?
6. When is `@RestControllerAdvice` useful?
7. Why should error responses contain a stable machine code?
8. How does invalid JSON differ from a Bean Validation failure?
9. What should a client receive for an unexpected exception?
10. Why should an API not expose stack traces?

---

## 25. Further Reading

- [Spring MVC Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
- [Spring MVC Controller Advice](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html)
- [Spring MVC Exceptions](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html)
- [Jakarta Bean Validation Specification](https://jakarta.ee/specifications/bean-validation/)
- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)

