# Spring Data JPA Basics

## 1. Objectives

After this unit, learners can:

- Distinguish JPA, Hibernate, Spring Data, and Spring Data JPA.
- Configure a basic database connection in Spring Boot.
- Define a Spring Data repository interface.
- Perform CRUD operations and write derived queries.
- Place transaction boundaries at the service layer.
- Implement pagination and sorting safely.
- Recognize common persistence mistakes in a web application.

---

## 2. From Hibernate to Spring Data JPA

The previous Hibernate module used JPA concepts such as:

- Entity mapping.
- `EntityManager` and persistence context.
- Entity lifecycle and dirty checking.
- Relationships and fetch strategies.
- JPQL and transactions.

Spring Data JPA does not replace these concepts. It builds a repository abstraction on top of JPA to remove repeated data-access code.

Manual JPA repository:

```java
@Repository
public class JpaBookRepository {
    @PersistenceContext
    private EntityManager entityManager;

    public Optional<Book> findById(long id) {
        return Optional.ofNullable(entityManager.find(Book.class, id));
    }

    public Book save(Book book) {
        if (book.getId() == null) {
            entityManager.persist(book);
            return book;
        }
        return entityManager.merge(book);
    }
}
```

Spring Data repository:

```java
public interface BookRepository extends JpaRepository<Book, Long> {
}
```

Spring Data supplies the common implementation at runtime. Domain-specific queries are still designed by the developer.

---

## 3. JPA vs Hibernate vs Spring Data JPA

| Technology | What it is | Main responsibility |
|---|---|---|
| JPA / Jakarta Persistence | Specification | Standard entity, persistence context, query, and mapping APIs |
| Hibernate ORM | JPA provider | Implements JPA and performs ORM work |
| Spring Data | Project family | Common repository programming model across data stores |
| Spring Data JPA | Spring Data module | Implements repository abstraction using JPA |
| Spring Boot | Application setup | Auto-configures provider, datasource, transactions, and repositories |

Relationship:

```text
Application service
        |
        v
Spring Data JPA repository proxy
        |
        v
JPA EntityManager API
        |
        v
Hibernate ORM provider
        |
        v
JDBC driver -> relational database
```

Precise statements:

- `@Entity` is a Jakarta Persistence annotation.
- `JpaRepository` is a Spring Data JPA interface.
- Hibernate commonly implements the JPA runtime in Spring Boot applications.
- A derived repository method is parsed by Spring Data, then executed through JPA/Hibernate.

---

## 4. Project Dependencies

Typical Maven dependencies:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

For PostgreSQL, replace or supplement the runtime driver:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

The JPA starter commonly brings Spring Data JPA, Hibernate, transaction support, and a connection-pool integration. A database driver is still required.

---

## 5. Database Configuration

Development `application.properties` example:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/library
spring.datasource.username=library_app
spring.datasource.password=${DB_PASSWORD}

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.format_sql=true
```

Key properties:

| Property | Purpose |
|---|---|
| `spring.datasource.url` | JDBC connection URL |
| `spring.datasource.username` | Database user |
| `spring.datasource.password` | Database password; use external configuration |
| `spring.jpa.hibernate.ddl-auto` | Schema action such as `none`, `validate`, `update`, `create`, `create-drop` |
| `spring.jpa.open-in-view` | Controls whether persistence context extends into web rendering |

Guidelines:

- Never commit production secrets.
- `create-drop` is useful for some tests, not production.
- `update` is convenient for experiments but is not a production migration strategy.
- Use Flyway or Liquibase for versioned production schema migrations.
- Consider disabling Open EntityManager in View and loading required data inside service transactions.

---

## 6. Entity Example

```java
package com.example.library.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "books")
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 13)
    private String isbn;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 120)
    private String author;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Book() {
        // Required by JPA.
    }

    public Book(String isbn, String title, String author, BigDecimal price) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.price = price;
    }

    // Getters and behavior methods omitted.
}
```

The database needs a real unique constraint for ISBN. An application-level `existsByIsbn` check improves error reporting but cannot prevent every race condition by itself.

---

## 7. Repository Abstraction

```java
package com.example.library.book;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {
}
```

No implementation class is written for the basic case. Spring Data creates a proxy bean for the interface and delegates operations to JPA.

Repository hierarchy concepts:

| Interface | Adds |
|---|---|
| `Repository<T, ID>` | Marker for repository abstraction |
| `CrudRepository<T, ID>` | Basic save, find, existence, and delete operations |
| `ListCrudRepository<T, ID>` | List-returning variants for multi-result CRUD |
| `PagingAndSortingRepository<T, ID>` | Paging and sorting operations |
| `JpaRepository<T, ID>` | JPA-focused repository capabilities plus list CRUD/paging |

Choose the narrowest abstraction that fits the design, but `JpaRepository` is a common application default.

### Repository generic types

```java
JpaRepository<Book, Long>
```

- `Book` is the managed entity type.
- `Long` is the entity ID type.

These must match the entity mapping.

---

## 8. CRUD Operations

### Create

```java
@Transactional
public BookResponse create(CreateBookRequest request) {
    Book book = new Book(
            request.isbn(),
            request.title(),
            request.author(),
            request.price());
    book.setCreatedAt(Instant.now(clock));

    Book saved = bookRepository.save(book);
    return mapper.toResponse(saved);
}
```

For a new entity, `save` normally results in persistence through JPA.

### Read one

```java
@Transactional(readOnly = true)
public BookResponse findById(long id) {
    return bookRepository.findById(id)
            .map(mapper::toResponse)
            .orElseThrow(() -> new BookNotFoundException(id));
}
```

`findById` returns `Optional<Book>`. Handle absence deliberately.

Avoid:

```java
Book book = bookRepository.findById(id).get();
```

`get()` throws a generic `NoSuchElementException` and loses domain meaning.

### Update

```java
@Transactional
public BookResponse update(long id, UpdateBookRequest request) {
    Book book = bookRepository.findById(id)
            .orElseThrow(() -> new BookNotFoundException(id));

    book.changeDetails(
            request.title(),
            request.author(),
            request.price());

    return mapper.toResponse(book);
}
```

The loaded entity is managed inside the transaction. JPA dirty checking detects changes at flush/commit. Calling `save(book)` is not required by JPA for this managed entity, although some teams keep it for consistency with repository style.

Do not replace a managed entity with an untrusted request body. Load the existing entity, enforce rules, and update permitted fields.

### Delete

```java
@Transactional
public void delete(long id) {
    Book book = bookRepository.findById(id)
            .orElseThrow(() -> new BookNotFoundException(id));
    bookRepository.delete(book);
}
```

Loading first provides deliberate `404` semantics and allows business rules before deletion.

`deleteById(id)` may be enough when the API deliberately treats deletion of an absent resource as successful. Define the contract.

---

## 9. Derived Query Methods

Spring Data can derive a query from a repository method name.

```java
public interface BookRepository extends JpaRepository<Book, Long> {
    Optional<Book> findByIsbn(String isbn);

    boolean existsByIsbn(String isbn);

    List<Book> findByAuthorIgnoreCaseOrderByTitleAsc(String author);

    Page<Book> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
```

The method name expresses:

- Operation: `find`, `exists`, `count`, `delete`.
- Predicate: `ByIsbn`, `ByTitleContaining`.
- Modifiers: `IgnoreCase`, `OrderByTitleAsc`.

Use derived queries when names remain readable. For complex conditions, use `@Query`, specifications, Query by Example, or a custom repository implementation.

Unreadable:

```java
findByTitleContainingIgnoreCaseAndAuthorContainingIgnoreCaseAndPriceBetweenOrderByCreatedAtDesc(...)
```

At that point, an explicit query or query object may communicate intent better.

---

## 10. Explicit JPQL Query

```java
public interface BookRepository extends JpaRepository<Book, Long> {
    @Query("""
            select b
            from Book b
            where lower(b.title) like lower(concat('%', :keyword, '%'))
               or lower(b.author) like lower(concat('%', :keyword, '%'))
            """)
    Page<Book> search(@Param("keyword") String keyword, Pageable pageable);
}
```

JPQL uses entity and Java property names, not table and column names.

For a modifying query:

```java
@Modifying
@Query("update Book b set b.price = b.price * :factor")
int adjustAllPrices(@Param("factor") BigDecimal factor);
```

Modifying queries require a transaction. They also bypass normal per-entity dirty checking and can leave already-managed entities stale. Use them deliberately.

---

## 11. Transaction Basics

A transaction groups database work into one unit:

```text
begin
  read book
  check rule
  update book
  save audit entry
commit
```

If an unchecked exception escapes before commit, Spring normally rolls the transaction back.

### Transaction boundary at the service

```java
@Service
@Transactional(readOnly = true)
public class LoanService {
    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;

    @Transactional
    public LoanResponse borrow(long memberId, long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        if (!book.isAvailable()) {
            throw new BookUnavailableException(bookId);
        }

        book.markBorrowed();
        Loan loan = Loan.start(memberId, book, clock.instant());
        loanRepository.save(loan);

        return mapper.toResponse(loan);
    }
}
```

One service operation coordinates two repositories in one transaction.

### `readOnly = true`

```java
@Transactional(readOnly = true)
public BookResponse findById(long id) { /* ... */ }
```

Read-only is a transaction hint and documentation of intent. It is not a universal security mechanism that guarantees no write can occur.

### Rollback rules

By default, Spring transaction interception rolls back for unchecked exceptions (`RuntimeException` and `Error`). Checked exceptions need an explicit rule when rollback is required:

```java
@Transactional(rollbackFor = ExternalImportException.class)
public void importBooks(...) throws ExternalImportException { /* ... */ }
```

Prefer meaningful exception design instead of adding `rollbackFor = Exception.class` everywhere.

### Proxy limitation and self-invocation

Declarative transactions are commonly applied through a Spring proxy:

```text
Controller -> transactional proxy -> Service method
```

A call from one method to another method on the same instance does not pass through that external proxy in the usual proxy mode.

```java
public void importAll() {
    saveBatch(); // May not apply a separate @Transactional boundary.
}

@Transactional
public void saveBatch() { /* ... */ }
```

Design public transactional use cases at clear service boundaries. Do not depend on self-invocation to change transaction behavior.

### Keep transactions focused

Avoid holding a database transaction open while making a slow remote HTTP call when the workflow can be redesigned. Long transactions consume connections and increase lock/concurrency risk.

---

## 12. Pagination and Sorting

### Repository method

```java
Page<Book> findByTitleContainingIgnoreCase(String title, Pageable pageable);
```

### Service method

```java
@Transactional(readOnly = true)
public PageResponse<BookSummaryResponse> search(
        String title,
        int page,
        int size) {

    int safeSize = Math.min(Math.max(size, 1), 100);
    int safePage = Math.max(page, 0);

    Pageable pageable = PageRequest.of(
            safePage,
            safeSize,
            Sort.by(Sort.Direction.ASC, "title")
                    .and(Sort.by(Sort.Direction.ASC, "id")));

    Page<Book> result = title == null || title.isBlank()
            ? bookRepository.findAll(pageable)
            : bookRepository.findByTitleContainingIgnoreCase(title.trim(), pageable);

    return PageResponse.from(result.map(mapper::toSummary));
}
```

`Page<T>` commonly contains:

- Current page content.
- Page number and size.
- Total elements.
- Total pages.
- First/last indicators.

### `Page` vs `Slice`

| Type | Use |
|---|---|
| `Page<T>` | Need total count and total pages |
| `Slice<T>` | Only need to know whether another slice exists |
| `List<T>` with `Pageable` | Need bounded results without page metadata |

`Page` usually requires an additional count query. For very large or complex datasets, `Slice` or cursor/keyset pagination may be more efficient.

### Stable sorting

Add a unique tie-breaker such as `id`:

```text
ORDER BY title ASC, id ASC
```

Without stable ordering, records with equal titles can move between pages.

### Never trust arbitrary sort properties

Do not pass any user-supplied string directly into sorting without an allow-list.

```java
private static final Set<String> ALLOWED_SORTS =
        Set.of("title", "author", "price", "createdAt");
```

Reject or replace unsupported properties.

---

## 13. Persistence Context in a Web Request

Inside a transaction:

```text
repository.findById
        |
        v
managed Book entity
        |
        v
business method changes fields
        |
        v
flush at commit -> SQL UPDATE
```

Outside the persistence context, a lazy relationship may not initialize.

Bad workaround:

- Return an entity from the controller.
- Keep the session open so JSON serialization can traverse relationships.

Better design:

- Decide which data the use case needs.
- Load it inside a service transaction.
- Map it to a DTO before leaving the boundary.

This makes query behavior and API shape deliberate.

---

## 14. Database Constraints and Application Checks

Use both where appropriate:

```java
if (bookRepository.existsByIsbn(request.isbn())) {
    throw new DuplicateIsbnException(request.isbn());
}
```

And in the schema:

```sql
alter table books
    add constraint uk_books_isbn unique (isbn);
```

Why both?

- Application check gives a friendly, early error.
- Database constraint is the final protection under concurrency.

Two requests can both pass `existsByIsbn` before either inserts. Handle the database constraint violation and convert it to a stable API error where necessary.

---

## 15. Common Mistakes

### Returning entities from controllers

This leaks persistence concerns and can trigger lazy loading. Map to response DTOs.

### `findAll()` on an unbounded table

It may load millions of rows. Use pagination.

### One transaction per repository call only

A use case involving multiple repositories may partially complete. Place the boundary around the service operation.

### Catching every exception inside a transactional method

If the method catches an exception and returns normally, Spring may commit. Only catch when the method can truly recover, or rethrow an appropriate exception.

### Assuming `save` immediately executes SQL

JPA may delay SQL until flush or commit. Do not infer transaction success merely from a returned entity.

### Setting `ddl-auto=update` in production

Automatic schema alteration is not a substitute for reviewed, versioned migrations.

### N+1 queries

Loading a page of entities and lazily accessing a relationship for every item can cause one initial query plus many additional queries. Use deliberate fetch joins, entity graphs, projections, or query-specific DTO mapping.

### Changing both sides of no relationship

For bidirectional JPA relationships, use helper methods that synchronize both sides in memory, as covered in the Hibernate module.

---

## 16. Practical Repository Design

- Create repositories around aggregate/entity persistence needs, not one repository for every table automatically.
- Keep query method names readable.
- Return `Optional<T>` for a result that may be absent, not for collections.
- Return an empty collection when a multi-result query finds nothing.
- Do not return web DTOs such as `ResponseEntity` from repositories.
- Use projections deliberately for read models, but do not mix them with entity update workflows.
- Put cross-repository transaction boundaries in services.
- Observe generated SQL during development and performance investigation.

---

## 17. Complete Flow

```text
POST /api/books
        |
        v
CreateBookRequest validated
        |
        v
BookService.create() starts transaction
        |
        +--> repository checks ISBN
        +--> mapper creates Book entity
        +--> repository.save(book)
        |
        v
transaction commits
        |
        v
BookResponse returned as 201 Created
```

Each part has one primary responsibility:

- MVC binds the request.
- Validation checks boundary constraints.
- Service runs the use case.
- Repository handles persistence.
- JPA/Hibernate maintains the persistence context and SQL mapping.
- Database constraints protect durable integrity.

---

## 18. Knowledge Check

1. Which technology is the specification: JPA, Hibernate, or Spring Data JPA?
2. What implementation does Spring Data create for a repository interface?
3. Why can a managed entity update succeed without calling `save` again?
4. Where should a transaction spanning two repositories begin?
5. What is the purpose of `readOnly = true`?
6. Why is `existsByIsbn` not enough to guarantee uniqueness?
7. When is `Slice` preferable to `Page`?
8. Why should pagination use stable sorting?
9. What problem does Open EntityManager in View sometimes hide?
10. When should a derived query be replaced with an explicit query?

---

## 19. Further Reading

- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/)
- [Repository Interfaces](https://docs.spring.io/spring-data/commons/reference/repositories/core-concepts.html)
- [Query Methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html)
- [Transactionality](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html)
- [Spring Boot SQL Databases](https://docs.spring.io/spring-boot/reference/data/sql.html)

