# Spring Framework Overview

## 1. Objectives

After this unit, learners can:

- Explain the problems the Spring ecosystem solves in Java applications.
- Identify major Spring Framework modules and their responsibilities.
- Describe the benefits and trade-offs of using Spring.
- Distinguish Spring Framework from Spring Boot.
- Explain starters, convention over configuration, and auto-configuration.
- Create and run a minimal Spring Boot application.

---

## 2. Why Spring Exists

A typical Java backend needs more than domain classes. It must also:

- Accept HTTP requests and produce responses.
- Create and connect application objects.
- Read configuration for different environments.
- Validate input and report errors.
- Access a database within transactions.
- Provide logging, health checks, and metrics.
- Support automated testing.

These concerns are necessary, but repeatedly implementing their infrastructure distracts from business requirements. Spring provides reusable abstractions and integrations for them.

Consider a service written without a container:

```java
public class BookService {
    private final BookRepository bookRepository;

    public BookService() {
        DataSource dataSource = createDataSource();
        this.bookRepository = new JdbcBookRepository(dataSource);
    }
}
```

`BookService` is responsible for creating its own dependency. This causes several problems:

- It is tightly coupled to `JdbcBookRepository`.
- Database configuration leaks into business code.
- Replacing the repository in a unit test is difficult.
- Object creation is repeated across the application.

Spring moves object creation and wiring into an **Inversion of Control container**:

```java
@Service
public class BookService {
    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }
}
```

The class declares what it needs. The container supplies it.

---

## 3. What Is the Spring Framework?

The **Spring Framework** is a modular Java framework for building enterprise applications. Its foundation is the Spring container, which creates objects and connects their dependencies.

Major areas include:

| Area | Typical module | Responsibility |
|---|---|---|
| Object management | Spring Core / Context | IoC, DI, beans, configuration |
| Web applications | Spring Web MVC | HTTP request handling and REST APIs |
| Data access | Spring JDBC / ORM | Consistent database and ORM integration |
| Transactions | Spring TX | Declarative transaction boundaries |
| Validation | Spring Context / Web | Bean Validation integration |
| Cross-cutting concerns | Spring AOP | Proxies, interception, reusable aspects |
| Testing | Spring Test | Context loading, MVC tests, test utilities |

Spring is **modular**. A project does not need every module. A REST API might use Core, Web MVC, Validation, and transaction support, while a command-line application may only use Core.

### The wider Spring ecosystem

Do not treat every project with “Spring” in its name as part of Spring Framework itself.

```text
Spring ecosystem
+-- Spring Framework        core container, MVC, transactions, testing
+-- Spring Boot             application setup, auto-configuration, operations
+-- Spring Data             repository abstractions for data stores
+-- Spring Security         authentication and authorization
+-- Spring Cloud            distributed-system patterns and integrations
+-- Other projects          Batch, Integration, Session, GraphQL, ...
```

This module focuses on Spring Framework, Spring Boot, and Spring Data JPA.

---

## 4. Benefits of Spring

### 4.1 Loose coupling

Dependency Injection allows classes to depend on abstractions instead of constructing concrete collaborators.

```java
public interface NotificationSender {
    void send(String recipient, String message);
}

@Service
public class OrderService {
    private final NotificationSender notificationSender;

    public OrderService(NotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }
}
```

The implementation can change without changing `OrderService`.

### 4.2 Testability

Constructor dependencies are easy to replace with fakes or mocks:

```java
NotificationSender sender = mock(NotificationSender.class);
OrderService service = new OrderService(sender);
```

The unit test does not need to start Spring.

### 4.3 Reduced infrastructure boilerplate

Spring provides consistent APIs for common work:

- Request mapping and JSON serialization.
- Declarative transactions with `@Transactional`.
- Repository implementations generated from interfaces.
- Validation using standard Jakarta annotations.
- Centralized exception handling.

### 4.4 Consistent programming model

The same ideas appear across the ecosystem:

- Components are managed as beans.
- Dependencies use constructor injection.
- Configuration comes from the environment.
- Cross-cutting behavior is applied declaratively.

### 4.5 Integration ecosystem

Spring integrates with servlet containers, JPA providers, databases, messaging systems, test libraries, monitoring systems, and cloud platforms.

### 4.6 Production support

Spring Boot adds executable packaging, externalized configuration, graceful startup/shutdown support, health endpoints, and metrics integration.

---

## 5. Costs and Trade-offs

Spring is powerful, but it is not magic.

- Auto-configuration can hide setup that learners still need to understand.
- Proxies can make runtime behavior different from a direct method call.
- Incorrect component scanning can create missing or duplicate beans.
- Loading a full application context makes tests slower.
- Framework upgrades may require configuration changes.
- A poorly designed layered application remains poorly designed even when it uses annotations.

Good Spring developers understand both the convenience and the mechanism behind it.

---

## 6. Spring Framework vs Spring Boot

Spring Boot is **built on top of Spring Framework**. It does not replace Spring.

| Concern | Spring Framework | Spring Boot |
|---|---|---|
| Core purpose | Application framework | Faster setup and production-ready application runtime |
| Bean container | Yes | Uses Spring's container |
| Spring MVC | Available, configure it yourself | Auto-configures MVC when dependencies are present |
| Dependency selection | Choose individual libraries | Curated starters and dependency management |
| Web server | Traditionally deploy to an external server or configure one | Embedded server by default |
| Configuration | Explicit framework setup | Sensible defaults plus external properties |
| Packaging | Flexible | Executable JAR is the common default |
| Operations | Separate setup | Actuator integration |

Without Boot, a Spring MVC application may need explicit servlet registration, component configuration, message converters, and server deployment.

With Boot, the common case begins with one class:

```java
@SpringBootApplication
public class LibraryApplication {
    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
    }
}
```

`@SpringBootApplication` combines three ideas:

```java
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan
public @interface SpringBootApplication {
}
```

- `@SpringBootConfiguration`: identifies the primary configuration class.
- `@EnableAutoConfiguration`: asks Boot to configure common infrastructure conditionally.
- `@ComponentScan`: discovers components from the application package downward.

Place the main class in a root package such as `com.example.library`, above controllers, services, repositories, and entities.

---

## 7. Starters and Dependency Management

A **starter** is a convenient dependency descriptor for a common capability.

Examples:

| Starter | Common capability |
|---|---|
| `spring-boot-starter-web` | Spring MVC, JSON support, embedded servlet server |
| `spring-boot-starter-data-jpa` | Spring Data JPA, Hibernate, transaction support |
| `spring-boot-starter-validation` | Jakarta Bean Validation implementation |
| `spring-boot-starter-test` | Spring test support, JUnit, AssertJ, Mockito |
| `spring-boot-starter-actuator` | Health, metrics, and management endpoints |

Maven example:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

The Spring Boot parent or BOM supplies compatible dependency versions. Application code should usually avoid overriding versions for libraries managed by Boot unless there is a documented reason.

Starters do not generate application code. They place a useful, compatible set of libraries on the classpath.

---

## 8. Auto-configuration Concept

Auto-configuration attempts to configure the application based on:

1. **Classpath**: which libraries are present?
2. **Existing beans**: has the developer already provided a configuration?
3. **Properties**: which features are enabled or configured?
4. **Application type**: servlet web, reactive web, or non-web?

Example reasoning:

```text
spring-boot-starter-web is present
        |
        v
Servlet and Spring MVC classes are present
        |
        v
No custom web-server factory bean exists
        |
        v
Boot configures Spring MVC and an embedded server
```

For database configuration:

```text
JDBC classes + database driver + connection properties
        |
        v
Boot can create a DataSource
        |
        v
JPA classes + Hibernate present
        |
        v
Boot can configure EntityManagerFactory and transactions
```

Auto-configuration is **conditional**, not unconditional. Common internal conditions include concepts such as:

- Configure this only if a class exists.
- Configure this only if a property has a specific value.
- Configure this only if the user has not defined a bean of this type.

### Back-off behavior

Boot defaults are designed to back off when application code provides an alternative.

```java
@Configuration
public class ClockConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

Where an auto-configuration is conditional on a missing `Clock`, this user bean wins.

### Inspecting auto-configuration

Run with the debug flag:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--debug
```

The conditions report explains which auto-configurations matched and which did not. Use it when behavior seems “magical.”

### Auto-configuration is not code generation

Boot does not generate controllers, entities, or business logic. It registers infrastructure beans at startup when conditions match.

---

## 9. Convention Over Configuration

Spring Boot chooses conventional defaults:

- Serve HTTP on port `8080`.
- Read `application.properties` or `application.yml`.
- Scan from the main application's package.
- Use an embedded server for a servlet web application.
- Use Jackson for JSON when it is available.

Defaults reduce setup, but remain configurable:

```properties
server.port=9090
spring.application.name=library-api
```

Convention over configuration means “start with a useful default,” not “configuration is impossible.”

---

## 10. Minimal Spring Boot REST Application

### Project structure

```text
library-api/
+-- pom.xml
+-- src/
    +-- main/
    |   +-- java/com/example/library/
    |   |   +-- LibraryApplication.java
    |   |   +-- book/BookController.java
    |   +-- resources/application.properties
    +-- test/
        +-- java/com/example/library/
```

### Main class

```java
package com.example.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LibraryApplication {
    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
    }
}
```

### Controller

```java
package com.example.library.book;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookController {
    @GetMapping("/api/books")
    public Map<String, Object> findAll() {
        return Map.of("items", java.util.List.of(), "total", 0);
    }
}
```

Run:

```bash
./mvnw spring-boot:run
```

Request:

```bash
curl http://localhost:8080/api/books
```

Response:

```json
{
  "items": [],
  "total": 0
}
```

The developer wrote no servlet registration and no JSON serialization code. Boot configured the common infrastructure, while Spring MVC invoked the controller.

---

## 11. Common Misconceptions

### “Spring and Spring Boot are competitors”

No. Spring Boot uses Spring Framework and adds opinionated setup and operational features.

### “Spring Boot removes configuration”

No. It provides defaults and conditional configuration. Real applications still configure databases, security, environment values, and domain-specific behavior.

### “Using Spring means every object is a bean”

No. Controllers, services, repositories, and infrastructure collaborators are commonly beans. Entities, DTOs, and request-specific values are normally ordinary objects.

### “Auto-configuration always overrides my beans”

Usually the opposite: many auto-configurations back off when an application bean exists.

### “Annotations contain the business logic”

Annotations provide metadata. Business rules still belong in readable Java code, typically in the service layer.

---

## 12. Design Checklist

Before adding a dependency or annotation, ask:

- Which problem does this module solve?
- Is this object application infrastructure or a normal domain object?
- Is Boot supplying a default, or did the application define it?
- Can the class be tested without loading the full Spring context?
- Is configuration externalized instead of hard-coded?
- Can I explain the runtime request and dependency flow?

---

## 13. Knowledge Check

1. What is the main responsibility of the Spring IoC container?
2. Why does constructor injection improve testability?
3. Name three concerns handled by Spring Framework.
4. What does Spring Boot add on top of Spring Framework?
5. What information does auto-configuration inspect?
6. What does “back off” mean in auto-configuration?
7. Why should the main application class normally be in a root package?
8. Is a starter a code generator? Explain.

---

## 14. Further Reading

- [Spring Framework Overview](https://docs.spring.io/spring-framework/reference/overview.html)
- [Spring IoC Container](https://docs.spring.io/spring-framework/reference/core/beans.html)
- [Spring Boot Auto-configuration](https://docs.spring.io/spring-boot/reference/using/auto-configuration.html)
- [Spring Boot Build Systems and Starters](https://docs.spring.io/spring-boot/reference/using/build-systems.html)

