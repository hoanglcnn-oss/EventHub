# Deployment & Observability

## 1. Objectives

After this unit, learners can:

- Package a Spring Boot application as an executable JAR.
- Run and configure the same artifact in different environments.
- Build a Docker image using a Dockerfile or Cloud Native Buildpacks.
- Apply basic container packaging practices.
- Add Spring Boot Actuator and configure endpoint exposure.
- Explain health, metrics, and info endpoints.
- Distinguish liveness from readiness.
- Apply a basic production deployment checklist.

---

## 2. From Source Code to a Running Service

A common delivery flow is:

```text
Source code
    |
    v
Compile + automated tests
    |
    v
Executable JAR
    |
    +--> run directly with Java
    |
    +--> package into container image
            |
            v
        deploy with environment configuration
            |
            v
        observe health, metrics, logs, and traces
```

Build once and configure at deployment time. Do not rebuild different application code for development, staging, and production merely to change a database URL.

---

## 3. Executable JAR

A normal Java JAR may contain application classes but not a directly runnable dependency layout. Spring Boot's build plugin can create an executable archive containing:

- Application classes and resources.
- Dependency libraries.
- Boot loader classes.
- Metadata that identifies the main application class.

Typical Maven plugin:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

When using the Spring Boot parent, plugin version management is normally provided.

Build:

```bash
./mvnw clean package
```

Typical output:

```text
target/library-api-1.0.0.jar
```

Run:

```bash
java -jar target/library-api-1.0.0.jar
```

The embedded server starts with the application. An external Tomcat installation is not required for the common executable-JAR deployment model.

---

## 4. Build Lifecycle and Verification

`mvn package` should run automated tests before producing the deliverable.

Common CI outline:

```bash
./mvnw --batch-mode clean verify
```

The `verify` lifecycle phase can include additional checks configured by the project.

Do not routinely deploy artifacts built with:

```bash
-DskipTests
```

Skipping tests may be acceptable during a local experiment, but it removes a delivery gate.

Record useful build identity:

- Artifact name and version.
- Git commit.
- Build timestamp.
- Java and Spring Boot versions.

This identity helps operators determine which code is running.

---

## 5. Running with External Configuration

The same JAR can run with different properties.

Command-line arguments:

```bash
java -jar target/library-api-1.0.0.jar \
  --server.port=9090 \
  --spring.profiles.active=staging
```

Environment variables:

```bash
export SERVER_PORT=9090
export SPRING_PROFILES_ACTIVE=staging
export SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/library
export SPRING_DATASOURCE_USERNAME=library_app
export SPRING_DATASOURCE_PASSWORD=change-me

java -jar target/library-api-1.0.0.jar
```

Spring's relaxed binding maps conventional environment names:

```text
spring.datasource.url       -> SPRING_DATASOURCE_URL
management.server.port      -> MANAGEMENT_SERVER_PORT
library.loan.max-active     -> LIBRARY_LOAN_MAX_ACTIVE
```

Do not place `export` commands containing real production secrets in committed scripts.

---

## 6. Configuration Sources

Spring Boot can read configuration from multiple sources, including:

- Packaged `application.properties` or `application.yml`.
- Profile-specific files.
- External configuration files.
- Environment variables.
- Java system properties.
- Command-line arguments.
- Test-specific property sources.

Later/higher-precedence sources can override earlier defaults according to Boot's documented property-source order.

Example packaged defaults:

```yaml
spring:
  application:
    name: library-api
  jpa:
    open-in-view: false

server:
  port: 8080

library:
  loan:
    max-active: 5
```

Environment-specific values should be injected outside the artifact:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

---

## 7. Profiles

Profiles conditionally activate configuration.

Files:

```text
application.yml
application-local.yml
application-test.yml
application-prod.yml
```

Activate:

```bash
SPRING_PROFILES_ACTIVE=prod java -jar library-api.jar
```

Profiles are useful for environment-specific wiring or grouped configuration, but avoid turning them into unrelated application variants.

Good uses:

- Local mail adapter vs production mail adapter.
- Development logging defaults.
- Test-only database setup.

Risky uses:

- Hiding major business logic differences between environments.
- Creating many overlapping profile combinations.
- Storing secrets in `application-prod.yml`.

---

## 8. Type-safe Configuration

Avoid scattering string lookups across the application.

```java
@ConfigurationProperties(prefix = "library.loan")
@Validated
public record LoanProperties(
        @Min(1) @Max(20) int maxActive,
        @NotNull Duration defaultPeriod
) {
}
```

Configuration:

```yaml
library:
  loan:
    max-active: 5
    default-period: 14d
```

Enable scanning:

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class LibraryApplication {
}
```

Inject the typed object:

```java
@Service
public class LoanPolicy {
    private final LoanProperties properties;

    public LoanPolicy(LoanProperties properties) {
        this.properties = properties;
    }
}
```

Benefits:

- Type conversion.
- Startup validation.
- IDE metadata support.
- One documented configuration group.
- Easier unit testing.

---

## 9. Secret Management

Secrets include:

- Database passwords.
- API keys.
- Signing keys.
- Private certificates.
- Client credentials.

Rules:

- Do not commit real secrets to Git.
- Do not bake secrets into a JAR or image.
- Inject secrets at runtime using the platform's secret mechanism.
- Limit access by service identity and environment.
- Rotate secrets and audit access.
- Prevent secrets from appearing in logs or Actuator output.

Environment variables are a transport mechanism, not a complete secret-management strategy. Production platforms may mount secret files or integrate with a managed vault.

---

## 10. Why Containers?

A container image packages the application and runtime dependencies into a portable unit.

Benefits:

- Consistent runtime across environments.
- Immutable, versioned deployment artifact.
- Explicit Java runtime.
- Standard process and networking model.
- Integration with container orchestration.

Containers do not automatically provide:

- Secure configuration.
- Correct health checks.
- Database migrations.
- Horizontal scalability.
- Good monitoring.
- Efficient resource limits.

Those remain deployment design responsibilities.

---

## 11. Dockerfile Packaging

A simple runtime Dockerfile after the JAR has been built:

```dockerfile
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --uid 10001 spring

COPY target/library-api-1.0.0.jar app.jar

USER 10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Build:

```bash
./mvnw clean package
docker build -t library-api:1.0.0 .
```

Run:

```bash
docker run --rm \
  --name library-api \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/library \
  -e SPRING_DATASOURCE_USERNAME=library_app \
  -e SPRING_DATASOURCE_PASSWORD=change-me \
  library-api:1.0.0
```

Use `host.docker.internal` only where supported for local development. In a container network or production platform, use the database service's network name.

---

## 12. Multi-stage Docker Build

A multi-stage build compiles in one stage and copies only the artifact into the runtime stage.

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 spring
COPY --from=build /workspace/target/*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Benefits:

- Builder tools do not remain in the runtime image.
- Build steps are repeatable in Docker.
- Dependency layers can be cached.

Trade-offs:

- Docker build now performs Maven work and needs dependency access.
- Cache behavior depends on Dockerfile ordering.
- `COPY target/*.jar` or `/workspace/target/*.jar` must match one intended executable artifact.

Use a `.dockerignore`:

```text
.git
.idea
.vscode
target
*.log
.env
```

When using the multi-stage Dockerfile above, excluding host `target` is correct because the container builds its own artifact.

---

## 13. Buildpacks

Spring Boot's Maven plugin can build an OCI image using Cloud Native Buildpacks:

```bash
./mvnw spring-boot:build-image \
  -Dspring-boot.build-image.imageName=library-api:1.0.0
```

Buildpacks can:

- Detect the application type.
- Select a compatible runtime.
- Create optimized image layers.
- Configure a non-root process.
- add image metadata.

Dockerfile vs Buildpacks:

| Concern | Dockerfile | Buildpacks |
|---|---|---|
| Control | Explicit, highly customizable | Convention-driven |
| Maintenance | Team maintains instructions/base image | Builder maintains much runtime logic |
| Reproducibility | Depends on pinned inputs and build | Depends on selected builder and inputs |
| Spring Boot layering | Must configure/use intentionally | Integrated support |

Choose based on platform standards and required control.

---

## 14. Container Image Practices

- Use a supported runtime matching the project's Java version.
- Run as a non-root user.
- Keep the runtime image focused and small enough for operations.
- Do not include source, build caches, or secrets unnecessarily.
- Use immutable version tags such as a release number or commit digest.
- Avoid relying only on `latest` for production traceability.
- Scan images for known vulnerabilities.
- Rebuild when the base image receives security fixes.
- Set CPU and memory requests/limits in the deployment platform.
- Stop gracefully so in-flight work can finish within the platform deadline.

Image size matters, but operability, patching, and compatibility matter too. Do not choose an extremely minimal image if it makes diagnostics and runtime support unreliable.

---

## 15. JVM in Containers

Modern JVMs understand container memory and CPU constraints, but the application still needs resource planning.

Container memory includes more than Java heap:

```text
container memory
+-- Java heap
+-- metaspace
+-- thread stacks
+-- direct/native buffers
+-- JIT/code cache
+-- native libraries and JVM overhead
```

Setting heap equal to the container limit can cause the operating system to terminate the process due to non-heap usage.

Observe real workloads, set headroom, and test under the same limits used in deployment.

---

## 16. Spring Boot Actuator

Actuator adds production-oriented endpoints and integrations.

Dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Common endpoint IDs:

| Endpoint | Purpose |
|---|---|
| `health` | Application and dependency health |
| `info` | Build, Git, or application information |
| `metrics` | Names and values of recorded metrics |
| `prometheus` | Prometheus scrape format when registry is added |
| `loggers` | Inspect or change logging levels |
| `mappings` | Registered web mappings |
| `beans` | Registered Spring beans |
| `conditions` | Auto-configuration condition report |
| `env` | Environment properties, subject to sanitization |

The base path is commonly:

```text
/actuator/{endpoint-id}
```

Example:

```bash
curl http://localhost:8080/actuator/health
```

---

## 17. Endpoint Availability and Exposure

An endpoint must be enabled/available and exposed through the selected technology to be remotely accessible.

Conservative HTTP exposure:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

Do not use this in production without access controls:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

Some endpoints reveal configuration, mappings, logging controls, or environment details. Exposure is a security decision.

Options include:

- Expose only necessary endpoints.
- Protect sensitive endpoints with authentication/authorization.
- Bind management endpoints to a separate port or network.
- Restrict access at the firewall, service mesh, or ingress.
- Keep response details minimal for unauthenticated callers.

Separate management port:

```yaml
management:
  server:
    port: 9090
```

A separate port helps network policy, but it must still be secured.

---

## 18. Health Endpoint

Request:

```bash
curl http://localhost:8080/actuator/health
```

Minimal response:

```json
{
  "status": "UP"
}
```

Actuator can aggregate health contributors for:

- Database connectivity.
- Disk space.
- Messaging systems.
- Custom application dependencies.

Do not show sensitive details publicly.

```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
```

### Custom health indicator

```java
@Component
public class CatalogHealthIndicator implements HealthIndicator {
    private final CatalogClient client;

    public CatalogHealthIndicator(CatalogClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        return client.isReachable()
                ? Health.up().build()
                : Health.down()
                        .withDetail("reason", "catalog unavailable")
                        .build();
    }
}
```

Health checks should be fast, bounded by timeouts, and safe under frequent polling. A health endpoint that waits indefinitely can make operations worse.

---

## 19. Liveness vs Readiness

These checks answer different questions.

### Liveness

“Is the process internally alive, or is it stuck in a state that requires restart?”

If liveness fails, an orchestrator may restart the container.

Liveness should generally not fail merely because an external database or API is temporarily unavailable. Restarting every application instance during a shared dependency outage can create a cascading failure.

### Readiness

“Can this instance currently accept traffic?”

If readiness fails, an orchestrator can remove the instance from traffic without immediately restarting it.

Actuator probe paths commonly include:

```text
/actuator/health/liveness
/actuator/health/readiness
```

Enable probe support where needed:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
```

Conceptual orchestrator behavior:

```text
liveness DOWN  -> restart process
readiness DOWN -> stop routing new traffic
```

Design the included health indicators according to these consequences.

---

## 20. Metrics Endpoint

Spring Boot uses Micrometer as its metrics facade.

List metric names:

```bash
curl http://localhost:8080/actuator/metrics
```

Inspect one metric:

```bash
curl http://localhost:8080/actuator/metrics/http.server.requests
```

Common metrics can include:

- HTTP request count and duration.
- JVM memory and garbage collection.
- Process CPU usage.
- Thread counts.
- Database connection-pool usage.
- Log event counts.

Metrics usually need a monitoring backend for dashboards, retention, and alerts. The Actuator endpoint alone is not a complete monitoring system.

### Custom counter

```java
@Service
public class LoanService {
    private final Counter loansCreated;

    public LoanService(MeterRegistry registry) {
        this.loansCreated = Counter.builder("library.loans.created")
                .description("Number of created library loans")
                .register(registry);
    }

    public LoanResponse borrow(...) {
        // Complete business operation.
        loansCreated.increment();
        return result;
    }
}
```

Metric naming guidelines:

- Use a stable namespace.
- Measure meaningful business and technical events.
- Do not place unbounded values such as user ID, email, or request ID in metric tags.

High-cardinality tags can overwhelm a metrics backend.

---

## 21. Prometheus Export

Add registry dependency when Prometheus is the monitoring backend:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Expose the endpoint:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

Scrape path:

```text
/actuator/prometheus
```

Prometheus pulls metrics. Grafana commonly visualizes them. Alerting rules should reflect user impact and service objectives rather than arbitrary thresholds alone.

---

## 22. Info Endpoint

Configuration:

```yaml
management:
  info:
    env:
      enabled: true
  endpoints:
    web:
      exposure:
        include: health,info

info:
  app:
    name: Library API
    description: Manages books and loans
```

Request:

```bash
curl http://localhost:8080/actuator/info
```

Possible response:

```json
{
  "app": {
    "name": "Library API",
    "description": "Manages books and loans"
  }
}
```

The Maven plugin can generate build metadata:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>build-info</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Useful info:

- Application name.
- Artifact version.
- Build time.
- Git commit and branch where policy permits.

Never expose credentials, tokens, internal connection strings, or personal data.

---

## 23. Logs, Metrics, and Traces

Observability is broader than Actuator endpoints.

| Signal | Answers |
|---|---|
| Logs | What event happened and with what context? |
| Metrics | How much, how often, and how long? |
| Traces | Where did time/failure occur across a request path? |

Good container logging practice:

- Write application logs to standard output/error.
- Let the platform collect, retain, and route them.
- Use structured fields where supported.
- Include a correlation/trace ID.
- Never log passwords, tokens, or sensitive request bodies.
- Use appropriate levels; avoid logging every normal event as an error.

Example log context:

```text
timestamp=... level=INFO service=library-api traceId=abc123 event=loan.created loanId=91
```

Avoid high-cardinality details in metrics, but detailed identifiers can be appropriate in access-controlled logs.

---

## 24. Graceful Shutdown

During deployment, the platform sends a termination signal. A graceful application should:

1. Stop accepting new traffic or become unready.
2. Allow in-flight requests to finish within a deadline.
3. Close the Spring context.
4. Run bean destruction callbacks.
5. Close connection pools and other resources.

The platform's termination grace period must be longer than the application's intended shutdown time.

Do not use a long `@PreDestroy` task as a substitute for durable job coordination. The process can still be terminated forcibly.

---

## 25. Database Migrations at Deployment

Production schema changes should be versioned and repeatable.

Typical migration tools:

- Flyway.
- Liquibase.

Guidelines:

- Review migrations like application code.
- Test them against production-like data and engine versions.
- Make compatibility plans for rolling deployments.
- Back up where required.
- Do not rely on `spring.jpa.hibernate.ddl-auto=update` as a deployment strategy.

During a rolling deployment, old and new application instances may run simultaneously. Schema changes should support that overlap or deployment must be coordinated explicitly.

---

## 26. Deployment Failure Scenarios

### Application starts locally but fails in container

Check:

- Java runtime version.
- Bound address and exposed/mapped port.
- Environment variable names.
- Database host from inside the container network.
- File paths and case sensitivity.
- Container memory limit.

### Health is `DOWN`

Inspect health components with authorized detail. Common causes include database connectivity, invalid credentials, migrations, or exhausted disk/connection pool.

### Container restarts repeatedly

Check startup logs, liveness timing, memory termination, configuration validation, and dependency timeouts. A liveness probe that starts too early can kill a healthy but slow-starting application.

### Metrics endpoint returns `404`

Check that Actuator is included, the endpoint is available, and it is exposed over HTTP. For Prometheus, include the registry dependency and expose `prometheus`.

### Info endpoint is empty

No contributors or permitted `info.*` values may be configured. Enable only the contributors required by the deployment.

---

## 27. Production Checklist

### Build

- Tests and verification checks pass.
- Executable JAR starts using the supported Java version.
- Artifact has a unique, traceable version.
- Dependencies and image are scanned.

### Configuration

- Environment values are externalized.
- Secrets are not in Git, JAR, image, or logs.
- Required configuration is validated at startup.
- Production schema uses versioned migrations.

### Container

- Process runs as non-root.
- Image uses a supported runtime.
- CPU and memory limits have been tested.
- Port and shutdown behavior match the platform.

### Operations

- Readiness and liveness have correct semantics.
- Only required Actuator endpoints are exposed.
- Sensitive endpoints are protected.
- Logs are collected centrally.
- Metrics dashboards and actionable alerts exist.
- Deployment rollback procedure is known.

---

## 28. Knowledge Check

1. What makes a Spring Boot JAR executable?
2. Why should the same JAR be used across environments?
3. How does `SPRING_DATASOURCE_URL` map to a Spring property?
4. Why should secrets not be copied into a container image?
5. What are the trade-offs between a Dockerfile and Buildpacks?
6. Why should a container run as non-root?
7. What is the difference between endpoint availability and exposure?
8. How do liveness and readiness differ?
9. Why must metric tags avoid unbounded identifiers?
10. Why is `ddl-auto=update` unsuitable as a production migration plan?

---

## 29. Further Reading

- [Spring Boot Packaging Executable Archives](https://docs.spring.io/spring-boot/maven-plugin/packaging.html)
- [Spring Boot Container Images](https://docs.spring.io/spring-boot/reference/packaging/container-images/)
- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring Boot Actuator Endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Spring Boot Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Spring Boot Monitoring over HTTP](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html)
