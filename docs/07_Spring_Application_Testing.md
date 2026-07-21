# Spring Application Testing

## 1. Objectives

After this unit, learners can:

- Distinguish unit, slice, integration, and end-to-end tests.
- Test a service without loading Spring.
- Use Mockito to isolate dependencies.
- Use `MockMvc` to test Spring MVC endpoints.
- Load a full application context with `@SpringBootTest` when appropriate.
- Use `@WebMvcTest` and `@DataJpaTest` test slices.
- Choose a test scope that gives useful confidence at reasonable cost.

---

## 2. What Should a Test Prove?

A valuable test has a clear risk and boundary.

Examples:

- Does `BookService` reject a duplicate ISBN?
- Does `POST /api/books` validate an empty title?
- Does `BookRepository.search` generate the intended query result?
- Can the whole application start with its configuration?
- Does the API persist data and return the expected response?

Do not start by selecting an annotation. Start by asking which behavior and integration must be proven.

---

## 3. Test Scope

| Test type | Loads Spring? | Typical dependencies | Main purpose |
|---|---:|---|---|
| Unit test | No | Objects plus mocks/fakes | One class or small behavior |
| Web slice | Partial | MVC infrastructure, controller, mocked service | HTTP mapping, validation, JSON, advice |
| JPA slice | Partial | JPA, repository, test database | Mapping and repository queries |
| Integration test | Usually full | Application context and selected real infrastructure | Components work together |
| End-to-end test | Full deployed system | Real external boundary | User/client-visible workflow |

These categories describe scope, not quality. A small unit test can be high value; a large integration test can be vague and brittle.

### Unit vs integration

Unit test:

```text
BookService + mocked BookRepository
```

Integration test:

```text
Controller + MVC + Service + Repository + Database
```

Unit tests are usually faster and diagnose logic failures precisely. Integration tests catch wiring, configuration, serialization, transaction, and query problems that mocks cannot.

A healthy suite uses both.

---

## 4. Test Dependencies

Common Maven test starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

It provides or integrates common testing libraries such as:

- JUnit Jupiter.
- Spring Test and Spring Boot Test.
- AssertJ.
- Mockito.
- JSON testing support.

Application tests commonly live in:

```text
src/test/java/com/example/library/
```

Mirror production packages so package-private helpers can be tested where appropriate and navigation remains clear.

---

## 5. Unit Testing a Service

Production service:

```java
@Service
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

JUnit and Mockito test:

```java
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookMapper bookMapper;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-15T00:00:00Z"),
            ZoneOffset.UTC);

    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookService(bookRepository, bookMapper, clock);
    }
}
```

This test does not use `@SpringBootTest`. Constructor injection makes plain object construction easy.

---

## 6. Mockito Basics

Mockito creates controllable test doubles.

### Stub behavior

```java
when(bookRepository.existsByIsbn("9781617297571"))
        .thenReturn(false);
```

### Verify interaction

```java
verify(bookRepository).save(book);
```

### Verify no call

```java
verify(bookRepository, never()).save(any(Book.class));
```

### Capture an argument

```java
ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
verify(bookRepository).save(captor.capture());
assertThat(captor.getValue().getCreatedAt())
        .isEqualTo(Instant.parse("2026-07-15T00:00:00Z"));
```

Common APIs:

| API | Purpose |
|---|---|
| `mock(Type.class)` / `@Mock` | Create a mock |
| `when(...).thenReturn(...)` | Define behavior |
| `when(...).thenThrow(...)` | Simulate failure |
| `verify(...)` | Check interaction |
| `any()`, `eq(...)` | Match arguments |
| `ArgumentCaptor` | Inspect a passed argument |

---

## 7. Unit Test: Successful Creation

```java
@Test
void createsBookWhenIsbnIsAvailable() {
    CreateBookRequest request = new CreateBookRequest(
            "9781617297571",
            "Spring in Action",
            "Craig Walls",
            new BigDecimal("45.00"));

    Book unsaved = new Book(
            request.isbn(),
            request.title(),
            request.author(),
            request.price());

    Book saved = bookWithId(42L, unsaved);
    BookResponse expected = new BookResponse(
            42L,
            request.isbn(),
            request.title(),
            request.author(),
            request.price(),
            Instant.parse("2026-07-15T00:00:00Z"));

    when(bookRepository.existsByIsbn(request.isbn())).thenReturn(false);
    when(bookMapper.toEntity(request)).thenReturn(unsaved);
    when(bookRepository.save(unsaved)).thenReturn(saved);
    when(bookMapper.toResponse(saved)).thenReturn(expected);

    BookResponse actual = bookService.create(request);

    assertThat(actual).isEqualTo(expected);
    verify(bookRepository).save(unsaved);
}
```

The test checks observable result and one important collaboration.

Avoid verifying every getter or exact internal call order. Tests coupled to implementation details become expensive to refactor.

---

## 8. Unit Test: Business Failure

```java
@Test
void rejectsDuplicateIsbn() {
    CreateBookRequest request = new CreateBookRequest(
            "9781617297571",
            "Spring in Action",
            "Craig Walls",
            new BigDecimal("45.00"));

    when(bookRepository.existsByIsbn(request.isbn())).thenReturn(true);

    assertThatThrownBy(() -> bookService.create(request))
            .isInstanceOf(DuplicateIsbnException.class)
            .hasMessageContaining(request.isbn());

    verify(bookRepository, never()).save(any(Book.class));
    verifyNoInteractions(bookMapper);
}
```

This proves both the exception and the absence of an invalid write.

---

## 9. Good Mocking Practice

Mock boundaries and expensive/external collaborators:

- Repository in a service unit test.
- Remote API client.
- Message publisher.
- Clock for time-dependent behavior.

Usually do not mock:

- The class under test.
- Simple value objects.
- DTO getters.
- Every Java collection.
- A mapper whose real behavior is central to the test, unless the test specifically isolates service orchestration.

Risks of over-mocking:

- Tests pass with impossible mock behavior.
- Refactoring breaks many interaction assertions.
- Entity mapping and queries remain untested.
- The test verifies the implementation rather than the result.

Use a fake when stateful behavior is clearer than many stubs. Use a real object when it is fast and deterministic.

---

## 10. `MockMvc` Basics

`MockMvc` tests Spring MVC request handling without starting a real network server. It performs requests through MVC infrastructure and can verify:

- Route selection.
- Path and query parameter binding.
- JSON deserialization and serialization.
- Bean Validation.
- Exception advice.
- HTTP status and headers.

It does not prove that a real socket, proxy, TLS configuration, or deployed container works.

Conceptual flow:

```text
MockMvc request
    -> DispatcherServlet
    -> controller
    -> mocked service in a web slice
    -> JSON response
```

---

## 11. `@WebMvcTest`

`@WebMvcTest` loads a focused MVC test context rather than the full application.

```java
@WebMvcTest(BookController.class)
@Import(GlobalExceptionHandler.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookService bookService;
}
```

`@MockitoBean` replaces/adds the service dependency with a Mockito mock in the test context. Older Spring Boot code often uses `@MockBean`; use the annotation supported by the project's framework version.

The slice commonly includes:

- MVC controllers.
- MVC configuration.
- JSON message conversion.
- Validation.
- Controller advice and related web components.

It does not normally load:

- Full service implementation graph.
- JPA repositories.
- Entire database configuration.

If a controller dependency is not part of the slice, provide it as a test bean or mock.

---

## 12. `MockMvc` GET Test

```java
@Test
void returnsBookById() throws Exception {
    BookResponse response = new BookResponse(
            42L,
            "9781617297571",
            "Spring in Action",
            "Craig Walls",
            new BigDecimal("45.00"),
            Instant.parse("2026-07-15T00:00:00Z"));

    when(bookService.findById(42L)).thenReturn(response);

    mockMvc.perform(get("/api/books/{id}", 42)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.title").value("Spring in Action"))
            .andExpect(jsonPath("$.price").value(45.00));

    verify(bookService).findById(42L);
}
```

Static imports usually come from:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
```

---

## 13. `MockMvc` POST Test

```java
@Test
void createsBook() throws Exception {
    CreateBookRequest request = new CreateBookRequest(
            "9781617297571",
            "Spring in Action",
            "Craig Walls",
            new BigDecimal("45.00"));

    BookResponse created = new BookResponse(
            42L,
            request.isbn(),
            request.title(),
            request.author(),
            request.price(),
            Instant.parse("2026-07-15T00:00:00Z"));

    when(bookService.create(any(CreateBookRequest.class)))
            .thenReturn(created);

    mockMvc.perform(post("/api/books")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/books/42"))
            .andExpect(jsonPath("$.id").value(42));
}
```

Serializing the request with the configured `ObjectMapper` avoids fragile hand-written JSON in most success tests. Hand-written JSON remains useful when testing malformed input or missing properties.

---

## 14. Validation Test

```java
@Test
void rejectsInvalidCreateRequest() throws Exception {
    String json = """
            {
              "isbn": "123",
              "title": "   ",
              "author": "",
              "price": -1
            }
            """;

    mockMvc.perform(post("/api/books")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.violations").isArray())
            .andExpect(jsonPath("$.violations[?(@.field == 'title')]").exists())
            .andExpect(jsonPath("$.violations[?(@.field == 'price')]").exists());

    verifyNoInteractions(bookService);
}
```

This test proves:

- JSON reaches MVC.
- The DTO is validated.
- Global advice produces the API error shape.
- Invalid input does not call the service.

Do not rely on the ordering of field errors unless the public contract explicitly guarantees it.

---

## 15. Exception Mapping Test

```java
@Test
void returns404WhenBookDoesNotExist() throws Exception {
    when(bookService.findById(999L))
            .thenThrow(new BookNotFoundException(999L));

    mockMvc.perform(get("/api/books/{id}", 999))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
            .andExpect(jsonPath("$.path").value("/api/books/999"));
}
```

This isolates the web mapping. The service unit test separately proves when the exception is thrown.

---

## 16. Standalone `MockMvc`

`MockMvc` can also be created without a Spring test context:

```java
BookService service = mock(BookService.class);
BookController controller = new BookController(service);

MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
```

Advantages:

- Very fast.
- Explicit setup.

Trade-off:

- May differ from application MVC configuration.
- Custom converters, filters, argument resolvers, and validation need explicit setup.

Use standalone setup for focused controller behavior. Use `@WebMvcTest` to verify the Boot MVC slice configuration.

---

## 17. `@DataJpaTest`

`@DataJpaTest` loads a focused persistence context for JPA entities and repositories.

```java
@DataJpaTest
class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private TestEntityManager entityManager;
}
```

The slice commonly:

- Configures JPA repositories.
- Scans entities.
- Configures a test database when available.
- Runs each test transactionally and rolls it back afterward.

It does not load controllers or the complete service graph.

### Query test

```java
@Test
void findsBooksByTitleIgnoringCase() {
    entityManager.persist(book("9780000000001", "Spring in Action"));
    entityManager.persist(book("9780000000002", "Effective Java"));
    entityManager.flush();

    Page<Book> result = bookRepository
            .findByTitleContainingIgnoreCase(
                    "SPRING",
                    PageRequest.of(0, 10));

    assertThat(result.getContent())
            .extracting(Book::getTitle)
            .containsExactly("Spring in Action");
}
```

`flush()` is useful when a test must force SQL execution and reveal mapping or constraint problems before assertion.

### Unique constraint test

```java
@Test
void rejectsDuplicateIsbn() {
    entityManager.persistAndFlush(book("9780000000001", "First"));

    assertThatThrownBy(() ->
            entityManager.persistAndFlush(
                    book("9780000000001", "Second")))
            .isInstanceOf(PersistenceException.class);
}
```

The exact translated exception may depend on where the operation is executed and which Spring API boundary is involved. Assert at the abstraction level the application guarantees.

---

## 18. Embedded vs Production Database

An H2 test is fast, but it does not behave exactly like PostgreSQL, MySQL, or SQL Server.

Differences can include:

- SQL syntax and functions.
- Type mapping.
- Case sensitivity and collation.
- Locking and isolation.
- Constraint behavior.
- Query plans.

Use an embedded database for fast mapping/query feedback when portability is expected. Add tests against the real database engine for database-specific queries and production-critical behavior, often with containers.

To prevent automatic replacement with an embedded database:

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookRepositoryPostgresTest {
}
```

The test still needs a safe, isolated datasource configuration.

---

## 19. `@SpringBootTest`

`@SpringBootTest` asks Spring Boot to find the main configuration and load a full application context.

Basic context smoke test:

```java
@SpringBootTest
class LibraryApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

This catches startup and wiring errors, but an empty smoke test does not prove endpoint behavior.

### Web environment modes

| Mode | Behavior |
|---|---|
| `MOCK` | Web application context without a real server; default |
| `RANDOM_PORT` | Starts a real server on an available port |
| `DEFINED_PORT` | Starts server on configured port |
| `NONE` | Loads a non-web application context |

For full context with `MockMvc`:

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
}
```

This loads service and repository beans, unlike `@WebMvcTest`.

---

## 20. Full Integration Test Example

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void cleanDatabase() {
        bookRepository.deleteAll();
    }

    @Test
    void createsAndReadsBook() throws Exception {
        String request = """
                {
                  "isbn": "9781617297571",
                  "title": "Spring in Action",
                  "author": "Craig Walls",
                  "price": 45.00
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();

        String location = createResult.getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value("9781617297571"));
    }
}
```

This proves multiple layers work together. It is slower and needs careful test data isolation.

---

## 21. Transaction Rollback Caveat

Test-managed rollback works when test code and application database work participate in the same test transaction, as with many `@DataJpaTest` and mock-web-context cases.

With a real server:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
```

The HTTP request executes on a server thread with its own transaction. Adding `@Transactional` to the test method does not automatically roll back the server's committed transaction.

Use deliberate cleanup, isolated schemas/databases, or container recreation for real-server tests.

---

## 22. Test Profiles and Configuration

`src/test/resources/application-test.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:library-test;MODE=PostgreSQL
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.open-in-view=false
logging.level.org.hibernate.SQL=DEBUG
```

Activate it:

```java
@ActiveProfiles("test")
```

Test configuration should:

- Never point accidentally to a shared production database.
- Be deterministic.
- Avoid real third-party calls unless the test explicitly targets them.
- Use fixed time and controlled random values when relevant.

---

## 23. Selecting the Right Test

| Behavior to prove | Recommended starting point |
|---|---|
| Service business branch | Plain unit test |
| Controller route and JSON | `@WebMvcTest` + `MockMvc` |
| Request validation and advice | `@WebMvcTest` + `MockMvc` |
| Repository derived query | `@DataJpaTest` |
| Entity mapping/constraint | `@DataJpaTest` |
| Application wiring | `@SpringBootTest` smoke test |
| Full HTTP-to-database workflow | Integration test |
| Real server/network behavior | `RANDOM_PORT` test |

Use the smallest scope that can reveal the targeted failure.

---

## 24. Common Testing Mistakes

### Using `@SpringBootTest` for every test

The suite becomes slow and failures become harder to localize. Use plain unit tests and slices where appropriate.

### Mocking the repository in a repository test

That does not test mapping or generated queries. Use `@DataJpaTest` with a database.

### Mocking the service in an integration test unintentionally

The test may claim full coverage while skipping business logic. Name and scope tests honestly.

### Verifying only status code

`200` does not prove the response contract. Assert important JSON, headers, and state changes.

### Testing only happy paths

Include invalid input, missing resources, conflicts, transaction failures, and boundary values.

### Shared mutable test data

Tests influence each other and fail based on order. Reset or isolate state.

### Time-dependent assertions using the system clock

Tests fail near time boundaries or under slow execution. Inject `Clock` and use a fixed value.

### Ignoring generated SQL

Mock tests cannot catch invalid JPQL, missing columns, or N+1 query behavior. Add persistence tests and inspect SQL where risk justifies it.

---

## 25. Test Quality Checklist

- The test name describes behavior and expected outcome.
- Arrange, act, and assert sections are easy to identify.
- The smallest useful context is loaded.
- External dependencies are controlled.
- Important output and state are asserted.
- Failure paths are included.
- Tests do not depend on execution order.
- Time and randomness are deterministic.
- Web tests verify the public contract, not private methods.
- Repository tests execute real persistence behavior.

---

## 26. Knowledge Check

1. What is the main difference between a unit and an integration test?
2. Why should service unit tests usually avoid `@SpringBootTest`?
3. What does Mockito isolate in a unit test?
4. What parts of request handling can `MockMvc` test?
5. What does `@WebMvcTest` intentionally leave out?
6. Why should a repository query use `@DataJpaTest` instead of a mocked repository?
7. What does `@SpringBootTest` load?
8. When is `RANDOM_PORT` useful?
9. Why might H2 tests pass while PostgreSQL fails?
10. Why may a transactional real-server test still leave database rows behind?

---

## 27. Further Reading

- [Spring Boot Testing](https://docs.spring.io/spring-boot/reference/testing/)
- [Testing Spring Boot Applications](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)
- [Spring MVC Test Framework](https://docs.spring.io/spring-framework/reference/testing/mockmvc.html)
- [Spring TestContext Framework](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework.html)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html)
