# Spring Core Concepts

## 1. Objectives

After this unit, learners can:

- Explain Inversion of Control and Dependency Injection.
- Use constructor injection to express required dependencies.
- Define Spring beans with stereotype annotations and Java configuration.
- Describe the role of `ApplicationContext`.
- Explain singleton scope and the conceptual bean lifecycle.
- Resolve common dependency injection problems.

---

## 2. Inversion of Control

**Inversion of Control (IoC)** is a design principle in which control over object creation and assembly moves from application classes to a container or framework.

Without IoC:

```java
public class CheckoutService {
    private final PaymentGateway gateway;

    public CheckoutService() {
        this.gateway = new StripePaymentGateway("secret-key");
    }
}
```

The class decides:

- Which implementation to create.
- How to configure it.
- When to create it.
- How long it should live.

With IoC:

```java
public class CheckoutService {
    private final PaymentGateway gateway;

    public CheckoutService(PaymentGateway gateway) {
        this.gateway = gateway;
    }
}
```

The class only states its requirement. External configuration decides which `PaymentGateway` implementation is supplied.

```text
Traditional control
CheckoutService -> creates -> StripePaymentGateway

Inverted control
Spring container -> creates both -> injects gateway into service
```

IoC is the broad principle. Dependency Injection is the main technique Spring uses to implement it.

---

## 3. Dependency Injection

**Dependency Injection (DI)** means an object receives its collaborators from outside instead of finding or constructing them itself.

### Constructor injection

```java
@Service
public class BookService {
    private final BookRepository bookRepository;
    private final Clock clock;

    public BookService(BookRepository bookRepository, Clock clock) {
        this.bookRepository = bookRepository;
        this.clock = clock;
    }
}
```

Constructor injection is the preferred default because:

- Required dependencies are explicit.
- Fields can be `final`.
- The object cannot be created in an invalid state.
- Plain unit tests can call the constructor directly.
- Circular dependencies are revealed early.

If a Spring-managed class has one constructor, `@Autowired` is unnecessary.

### Setter injection

```java
@Component
public class ReportExporter {
    private Formatter formatter;

    @Autowired
    public void setFormatter(Formatter formatter) {
        this.formatter = formatter;
    }
}
```

Setter injection can represent an optional or reconfigurable dependency, but it permits an incomplete object unless carefully designed.

### Field injection

```java
@Service
public class BookService {
    @Autowired
    private BookRepository bookRepository;
}
```

Field injection is concise, but avoid it in application code because:

- Dependencies are hidden from the constructor.
- Fields cannot normally be `final`.
- Plain unit tests need reflection or a Spring context.
- Classes can accumulate too many dependencies without an obvious signal.

### DI does not require Spring

This is dependency injection too:

```java
BookRepository repository = new InMemoryBookRepository();
BookService service = new BookService(repository, Clock.systemUTC());
```

Spring automates the object graph; the design principle remains useful without the framework.

---

## 4. What Is a Spring Bean?

A **bean** is an object created, configured, and managed by the Spring IoC container.

Common beans include:

- Controllers.
- Services.
- Repositories.
- Configuration objects.
- HTTP clients.
- Mappers.
- Clocks and other infrastructure collaborators.

Objects that are normally **not** beans:

- JPA entity instances loaded from the database.
- Request and response DTO instances.
- Value objects created during business operations.
- Every use of `String`, `List`, or another Java type.

A bean definition is a recipe containing information such as:

- The bean's type and name.
- How it is constructed.
- Its dependencies.
- Its scope.
- Initialization and destruction callbacks.

---

## 5. ApplicationContext

`ApplicationContext` represents Spring's IoC container. It is responsible for:

- Reading configuration metadata.
- Discovering bean definitions.
- Creating bean instances.
- Resolving and injecting dependencies.
- Applying bean post-processors and proxies.
- Publishing application events.
- Providing environment and resource access.

Conceptual startup flow:

```text
Configuration metadata
(@Component, @Configuration, auto-configuration)
        |
        v
Bean definitions registered
        |
        v
Dependencies resolved
        |
        v
Singleton beans created and initialized
        |
        v
Application ready
```

Spring Boot creates the context when this line runs:

```java
ConfigurableApplicationContext context =
        SpringApplication.run(LibraryApplication.class, args);
```

Application code should usually use injection instead of repeatedly calling `context.getBean(...)`. Frequent lookup turns the container into a service locator and hides dependencies.

---

## 6. Component Scanning and Stereotypes

Spring can find annotated classes during component scanning.

### `@Component`

Generic managed component:

```java
@Component
public class IsbnNormalizer {
    public String normalize(String isbn) {
        return isbn.replace("-", "").trim();
    }
}
```

### Specialized stereotypes

| Annotation | Intended role |
|---|---|
| `@Controller` | MVC controller, often returning views |
| `@RestController` | REST controller whose methods write response bodies |
| `@Service` | Business/application service |
| `@Repository` | Persistence component |
| `@Configuration` | Java-based bean definitions |

`@Service`, `@Repository`, and `@Controller` are specialized forms of `@Component`. Their names communicate architectural responsibility; some also participate in framework-specific behavior.

Example package layout:

```text
com.example.library             <- @SpringBootApplication
+-- book
|   +-- BookController          <- discovered
|   +-- BookService             <- discovered
|   +-- BookRepository          <- discovered
+-- shared
    +-- ClockConfig             <- discovered
```

A class in `org.other.feature` would not be found by the default scan rooted at `com.example.library`.

---

## 7. Java Configuration with `@Configuration` and `@Bean`

Component scanning works well for application classes that you own. Use Java configuration when:

- The class comes from a third-party library.
- Construction requires explicit parameters.
- You want a clear configuration boundary.
- You need multiple beans of the same general type.

```java
@Configuration
public class TimeConfig {
    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}
```

Spring manages the object returned by the `@Bean` method.

```java
@Configuration
public class ClientConfig {
    @Bean
    public CatalogClient catalogClient(
            CatalogProperties properties,
            RestClient.Builder builder) {
        return new CatalogClient(
                builder.baseUrl(properties.baseUrl()).build());
    }
}
```

Method parameters are injected from the container, just like constructor parameters.

### `@Component` vs `@Bean`

| Question | `@Component` | `@Bean` |
|---|---|---|
| Where is metadata placed? | On the managed class | On a factory method |
| Best for | Application classes you own | Third-party or explicitly constructed objects |
| Discovered by | Component scanning | Configuration class processing |
| Construction control | Constructor selected by container | Factory method body |

---

## 8. Dependency Resolution

Spring primarily resolves a dependency by type.

```java
public interface MessageSender {
    void send(String target, String body);
}

@Component
public class EmailMessageSender implements MessageSender { /* ... */ }
```

With exactly one `MessageSender` bean, injection is unambiguous.

### Multiple candidates

```java
@Component
public class EmailMessageSender implements MessageSender { /* ... */ }

@Component
public class SmsMessageSender implements MessageSender { /* ... */ }
```

Now Spring cannot choose automatically.

Use `@Qualifier` for an explicit choice:

```java
@Service
public class ReminderService {
    private final MessageSender sender;

    public ReminderService(
            @Qualifier("emailMessageSender") MessageSender sender) {
        this.sender = sender;
    }
}
```

Or declare a default with `@Primary`:

```java
@Primary
@Component
public class EmailMessageSender implements MessageSender { /* ... */ }
```

Use meaningful custom qualifier names when a project has many implementations.

### Optional dependencies

Optional dependencies should be genuinely optional. Options include `Optional<T>` or `ObjectProvider<T>`:

```java
public AuditService(Optional<ExternalAuditClient> client) {
    this.client = client;
}
```

Do not make required business collaborators optional merely to silence startup errors.

---

## 9. Bean Scope

Scope answers: **how many instances exist, and for how long?**

| Scope | Meaning |
|---|---|
| `singleton` | One instance per Spring container; default |
| `prototype` | A new instance for each container request |
| `request` | One instance per HTTP request |
| `session` | One instance per HTTP session |
| `application` | One instance per servlet application |
| `websocket` | One instance per WebSocket session |

Spring singleton is **per container**, not necessarily one object in the entire JVM.

### Singleton beans and mutable state

Controllers and services are usually singleton beans. Therefore, do not store request-specific mutable state in their fields.

Incorrect:

```java
@Service
public class SearchService {
    private String currentKeyword;

    public List<Book> search(String keyword) {
        this.currentKeyword = keyword;
        return doSearch();
    }
}
```

Concurrent requests can overwrite the shared field.

Prefer local variables and immutable dependencies:

```java
public List<Book> search(String keyword) {
    String normalized = keyword.trim().toLowerCase();
    return repository.search(normalized);
}
```

---

## 10. Bean Lifecycle Concept

A simplified lifecycle for a typical singleton bean:

```text
1. Bean definition is registered
2. Bean is instantiated
3. Dependencies are injected
4. Aware callbacks may run
5. BeanPostProcessor runs before initialization
6. Initialization callback runs
7. BeanPostProcessor runs after initialization
8. Bean is ready for use
9. Destruction callback runs when the context closes
```

The real process has more extension points, but this model is sufficient for application development.

### Initialization and destruction callbacks

```java
@Component
public class CacheWarmer {
    @PostConstruct
    void load() {
        // Called after dependencies have been injected.
    }

    @PreDestroy
    void clear() {
        // Called during graceful context shutdown.
    }
}
```

Imports use Jakarta annotations in modern Spring applications:

```java
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
```

Alternative factory method callbacks:

```java
@Bean(initMethod = "connect", destroyMethod = "disconnect")
ExternalClient externalClient() {
    return new ExternalClient();
}
```

Guidelines:

- Keep initialization fast and predictable.
- Do not perform large database migrations in `@PostConstruct`.
- Use destruction callbacks to release resources owned by the bean.
- Prototype bean destruction is not automatically managed in the same way as singleton destruction.

### Proxies after initialization

Some post-processors wrap a bean in a proxy for features such as transactions, method security, or caching.

```text
Caller -> Spring proxy -> transaction advice -> target service
```

This explains why annotations such as `@Transactional` are more than passive labels.

---

## 11. Circular Dependencies

A circular dependency occurs when beans require each other:

```text
OrderService -> PaymentService -> OrderService
```

With constructor injection, the container cannot construct either object first.

Do not immediately “fix” the cycle with lazy injection. A cycle often signals mixed responsibilities.

Possible redesigns:

- Move shared behavior into a third service.
- Publish an application event.
- Reverse one dependency through a smaller abstraction.
- Reconsider whether both classes should exist separately.

```text
Before:
OrderService <-> PaymentService

After:
OrderService -> PaymentProcessor
PaymentService -> PaymentProcessor
```

---

## 12. Configuration Example: Complete Object Graph

```java
public interface DiscountPolicy {
    BigDecimal discountFor(Book book);
}

@Component
public class StandardDiscountPolicy implements DiscountPolicy {
    @Override
    public BigDecimal discountFor(Book book) {
        return book.price().multiply(new BigDecimal("0.05"));
    }
}

@Service
public class PricingService {
    private final DiscountPolicy discountPolicy;
    private final Clock clock;

    public PricingService(DiscountPolicy discountPolicy, Clock clock) {
        this.discountPolicy = discountPolicy;
        this.clock = clock;
    }
}

@Configuration
public class PricingConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

The container discovers two component classes, calls the `clock()` factory method, and injects both dependencies into `PricingService`.

Unit test without Spring:

```java
@Test
void calculatesPriceWithFixedTime() {
    DiscountPolicy policy = book -> new BigDecimal("2.00");
    Clock clock = Clock.fixed(
            Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC);

    PricingService service = new PricingService(policy, clock);

    // Exercise service directly.
}
```

Well-designed DI makes this possible.

---

## 13. Common Problems

### `NoSuchBeanDefinitionException`

The required bean does not exist.

Check:

- Is the implementation annotated or declared with `@Bean`?
- Is its package included in component scanning?
- Did a profile or condition disable it?
- Is the requested type correct?

### `NoUniqueBeanDefinitionException`

Multiple beans match one dependency.

Use `@Qualifier`, `@Primary`, or redesign the abstraction.

### Bean not scanned

Move the main application class to a common root package or explicitly configure scanning. Do not solve package problems with a broad scan of unrelated packages.

### Too many constructor arguments

The container is not the problem. A service with many collaborators may have too many responsibilities.

### Calling `new` for a managed service

An object created directly with `new` is not automatically processed as a Spring bean. Its injected fields, transactions, caching, or security proxies will not be applied.

---

## 14. Practical Guidelines

- Prefer constructor injection for required dependencies.
- Keep singleton beans stateless.
- Use stereotype annotations to communicate layer roles.
- Use `@Bean` for third-party or explicitly configured objects.
- Depend on small abstractions where multiple implementations are realistic.
- Do not access `ApplicationContext` from ordinary business code.
- Treat circular dependencies as a design signal.
- Keep bean lifecycle callbacks short and infrastructure-focused.

---

## 15. Knowledge Check

1. What is inverted in Inversion of Control?
2. How is Dependency Injection related to IoC?
3. Why is constructor injection preferred over field injection?
4. What makes an object a Spring bean?
5. What does `ApplicationContext` do during startup?
6. When should `@Bean` be preferred over `@Component`?
7. How does Spring resolve two beans implementing the same interface?
8. Why should singleton services avoid request-specific fields?
9. At what lifecycle stage is `@PostConstruct` called?
10. What design problem may a circular dependency reveal?

---

## 16. Further Reading

- [Introduction to the Spring IoC Container](https://docs.spring.io/spring-framework/reference/core/beans/introduction.html)
- [Container Overview](https://docs.spring.io/spring-framework/reference/core/beans/basics.html)
- [Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html)
- [Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Bean Lifecycle Callbacks](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html)

