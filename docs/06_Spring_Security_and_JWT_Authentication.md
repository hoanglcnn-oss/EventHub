# Spring Security & JWT Authentication

## 1. Objectives

After this unit, learners can:

- Distinguish authentication from authorization.
- Explain how Spring Security's servlet filter chain protects a Spring MVC application.
- Store database-backed accounts without storing raw passwords.
- Authenticate email/password credentials with `AuthenticationManager`, `UserDetailsService`, and `PasswordEncoder`.
- Issue and validate signed, expiring JWT access tokens.
- Configure stateless request authorization with `SecurityFilterChain`.
- Apply role and resource-ownership rules.
- Return correct `401 Unauthorized` and `403 Forbidden` responses.
- Explain CSRF, CORS, token storage, and common security failure modes.

---

## 2. Security Is a Boundary, Not a Feature Flag

A REST API must answer two different questions:

```text
Authentication: Who is making this request?
Authorization:  Is that identity allowed to perform this operation?
```

Examples:

- A valid participant account logs in: authentication succeeds.
- That participant reads the public event list: authorization succeeds.
- The participant tries to create an event: authentication succeeds, authorization fails.
- A request uses an expired token: authentication fails before authorization.

Spring Security applies these decisions before the controller method runs.

```text
HTTP request
    |
    v
Servlet container
    |
    v
Spring Security filter chain
    |
    +--> extract credentials/token
    +--> authenticate identity
    +--> authorize request
    |
    v
DispatcherServlet -> controller -> service -> repository
```

Security rules belong at the application boundary, but resource-specific rules may also require service/domain data.

---

## 3. Authentication vs Authorization

| Concern | Authentication | Authorization |
|---|---|---|
| Main question | Who are you? | What may you do? |
| Input | Email/password, token, certificate | Authenticated principal and authorities |
| Success result | An `Authentication` in the security context | Request/method is permitted |
| Failure response | Usually `401` | Usually `403` |

Do not mix these concepts:

```text
Wrong password              -> 401
Missing/expired/invalid JWT -> 401
Valid PARTICIPANT token on admin endpoint -> 403
```

Despite its historical name, `401 Unauthorized` means the request is not successfully authenticated. `403 Forbidden` means the server recognizes the authenticated identity but refuses the operation.

---

## 4. Spring Security Architecture

Spring Security's servlet support is filter-based. The servlet container invokes a `DelegatingFilterProxy`, which delegates to Spring's `FilterChainProxy`. That proxy selects a configured `SecurityFilterChain`.

Simplified flow:

```text
DelegatingFilterProxy
        |
        v
FilterChainProxy
        |
        v
SecurityFilterChain
        |
        +--> exploit protection filters
        +--> custom JWT authentication filter
        +--> anonymous/security-context filters
        +--> exception translation
        +--> authorization filter
        |
        v
DispatcherServlet
```

The order of security filters matters. Authentication must be established before authorization checks require it.

Application code normally defines a `SecurityFilterChain` bean:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Configure protection, authentication filter, and authorization.
        return http.build();
    }
}
```

Modern Spring Security uses component-based configuration. Do not extend the removed/deprecated `WebSecurityConfigurerAdapter` style in a new application.

### Project dependencies

Add Spring Security:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

Choose a maintained JWT library rather than implementing cryptography. A JJWT setup commonly separates API and runtime implementation modules:

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>${jjwt.version}</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>${jjwt.version}</version>
    <scope>runtime</scope>
</dependency>
```

Define `jjwt.version` once in Maven properties and select a current stable version compatible with the project. The examples later in this lecture use the modern JJWT API conceptually; confirm exact methods against the selected version.

Adding the security starter protects the application by default. A custom `SecurityFilterChain` must make public and protected routes explicit.

---

## 5. Core Security Types

### `SecurityContextHolder`

Holds the current request thread's `SecurityContext`.

### `SecurityContext`

Contains the current `Authentication`.

### `Authentication`

Represents an authentication request or authenticated principal.

Important data:

- `principal`: current identity.
- `credentials`: secret presented for authentication; should be cleared/not retained.
- `authorities`: roles or permissions.
- `authenticated`: whether authentication has succeeded.

### `GrantedAuthority`

A string-like permission associated with an authenticated identity, such as:

```text
EVENT_ADMIN
PARTICIPANT
```

Choose one convention and use it consistently.

- `hasAuthority("EVENT_ADMIN")` expects exactly `EVENT_ADMIN`.
- `hasRole("EVENT_ADMIN")` normally expects `ROLE_EVENT_ADMIN` internally.

This module uses `hasAuthority` to avoid an implicit prefix.

---

## 6. Account Model

A database-backed application needs a persistent account model separate from an API request DTO.

```java
@Entity
@Table(
        name = "user_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_accounts_email",
                columnNames = "email"))
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountRole role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "participant_id", unique = true)
    private Participant participant;

    protected UserAccount() {
    }

    // Domain constructor and behavior methods omitted.
}
```

```java
public enum AccountRole {
    EVENT_ADMIN,
    PARTICIPANT
}
```

Important decisions:

- Email has a database uniqueness constraint.
- Store `passwordHash`, never a raw `password` field.
- Enum values use `STRING`.
- `enabled` supports account disabling.
- A participant account links to exactly one participant identity.
- An administrator does not need a participant record.

Do not expose `UserAccount` directly from a controller. Response DTOs must never contain `passwordHash`.

---

## 7. Password Storage

Passwords must not be stored in plaintext or encrypted reversibly. Store a one-way adaptive hash.

Spring Security provides the `PasswordEncoder` abstraction:

```java
@Bean
PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
```

`DelegatingPasswordEncoder` commonly produces a value with an algorithm identifier:

```text
{bcrypt}$2a$10$...
```

Why adaptive hashing?

- It is intentionally expensive to slow password guessing.
- Each encoded password includes a salt.
- Work factor can evolve as hardware changes.
- The raw password cannot be recovered from the hash.

Correct registration logic:

```java
String passwordHash = passwordEncoder.encode(request.password());
UserAccount account = UserAccount.participant(
        normalizedEmail,
        passwordHash,
        participant,
        clock.instant());
```

Incorrect:

```java
account.setPasswordHash(request.password());
```

Also incorrect:

```java
if (request.password().equals(account.getPasswordHash())) {
    // authenticated
}
```

Credential comparison belongs to `PasswordEncoder.matches(...)`, normally through Spring Security's authentication provider.

---

## 8. Registration Flow

Public registration creates a participant account. The client must not choose its role.

Request DTO:

```java
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 120) String fullName
) {
}
```

Notice that the request has no `role` field.

Service:

```java
@Transactional
public AccountResponse register(RegisterRequest request) {
    String email = normalizeEmail(request.email());

    if (accountRepository.existsByEmailIgnoreCase(email)) {
        throw new DuplicateEmailException(email);
    }

    Participant participant = new Participant(
            request.fullName(), email, clock.instant());
    participantRepository.save(participant);

    UserAccount account = UserAccount.participant(
            email,
            passwordEncoder.encode(request.password()),
            participant,
            clock.instant());

    return mapper.toResponse(accountRepository.save(account));
}
```

One transaction protects participant/account consistency.

Role escalation vulnerability:

```json
{
  "email": "attacker@example.com",
  "password": "a-long-password",
  "role": "EVENT_ADMIN"
}
```

The API must ignore/reject such a field. The server assigns `PARTICIPANT` unconditionally.

### Initial administrator

Options include:

- A reviewed database migration containing only an encoded, temporary credential policy.
- An idempotent development bootstrap using credentials from environment configuration.
- A separate operational provisioning process.

Never commit a real administrator password. Never create a known default such as `admin/admin` in production.

---

## 9. `UserDetailsService`

`UserDetailsService` loads account data for username/password authentication.

```java
@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserAccountRepository accountRepository;

    public DatabaseUserDetailsService(UserAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        UserAccount account = accountRepository
                .findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        return User.withUsername(account.getEmail())
                .password(account.getPasswordHash())
                .authorities(account.getRole().name())
                .disabled(!account.isEnabled())
                .build();
    }
}
```

Do not reveal whether an email exists through different login messages. A generic “Invalid credentials” response reduces account enumeration risk.

For ownership checks, a custom principal can carry the account ID and participant ID. Do not put the entire mutable JPA entity into a long-lived security object.

---

## 10. `AuthenticationManager` and Provider

`DaoAuthenticationProvider` uses `UserDetailsService` and `PasswordEncoder`.

```text
Login request
    |
    v
AuthenticationManager
    |
    v
DaoAuthenticationProvider
    +--> UserDetailsService loads account
    +--> PasswordEncoder verifies password
    |
    v
authenticated Authentication
```

Explicit configuration:

```java
@Bean
AuthenticationManager authenticationManager(
        UserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder) {

    DaoAuthenticationProvider provider =
            new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
}
```

The exact Boot auto-configuration available depends on the project version. The important design is that authentication uses Spring Security components rather than manual password comparison.

---

## 11. Login Endpoint

Request:

```java
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
```

Response:

```java
public record TokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt
) {
}
```

Service/controller boundary:

```java
@PostMapping("/api/auth/login")
public TokenResponse login(@Valid @RequestBody LoginRequest request) {
    Authentication authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                    request.email(), request.password()));

    return tokenService.issue(authentication);
}
```

Successful login returns a signed access token. Failed login returns a generic `401` response and no token.

Do not log the request body or password.

---

## 12. JWT Structure

JWT means **JSON Web Token**. A compact signed JWT has three Base64URL-encoded parts:

```text
header.payload.signature
```

### Header

Describes token type and signing algorithm:

```json
{
  "typ": "JWT",
  "alg": "HS256"
}
```

### Payload

Contains claims:

```json
{
  "sub": "account-42",
  "role": "PARTICIPANT",
  "iat": 1784160000,
  "exp": 1784160900
}
```

### Signature

Protects the header and payload from undetected modification.

Important:

- JWT payload is encoded, not encrypted.
- Anyone holding the token can usually read its claims.
- Do not put passwords, password hashes, secrets, or unnecessary personal data in it.
- The signature provides integrity/authenticity only when it is verified correctly.

---

## 13. Token Claims

Useful registered claims:

| Claim | Meaning |
|---|---|
| `sub` | Stable subject/account identifier |
| `iat` | Issued-at time |
| `exp` | Expiration time |
| `jti` | Optional token identifier |

Application claim:

```text
role = EVENT_ADMIN or PARTICIPANT
```

Guidelines:

- Use a stable, non-sensitive subject.
- Keep access tokens short-lived.
- Use the server-owned role, never a role from login input.
- Define token time using an injected `Clock` where possible.
- Document how account disablement affects already-issued tokens.

For a simple training application, loading the account by `sub` on each request makes disablement and current role effective immediately. It costs one database lookup but avoids trusting stale account state solely from a token.

---

## 14. Signing Key Management

Two common approaches:

### Symmetric signing

The same secret signs and verifies tokens.

```text
HS256: one high-entropy secret shared by token creator and verifier
```

In a single application, this is simpler. The secret must be long enough for the selected algorithm and loaded from external configuration.

Example environment variable:

```text
APP_SECURITY_JWT_SECRET=<base64-encoded-random-secret>
```

### Asymmetric signing

A private key signs; a public key verifies.

```text
RS256/ES256: private key for signing, public key for verification
```

This separates signing authority from verification and scales better across services, but key management is more complex.

Rules:

- Never commit a production secret/private key.
- Never use a human-readable sentence as an HMAC secret.
- Never print keys at startup.
- Validate that configuration is present and strong enough.
- Plan for key rotation in real production systems.

---

## 15. Token Service Concept

Use a maintained JWT library. Do not implement signing primitives manually.

Conceptual API:

```java
public interface TokenService {
    TokenResponse issue(Authentication authentication);

    VerifiedToken verify(String compactToken);
}
```

JJWT-style issuance example:

```java
Instant issuedAt = clock.instant();
Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());

String token = Jwts.builder()
        .subject(principal.accountId().toString())
        .claim("role", principal.role().name())
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(expiresAt))
        .signWith(signingKey)
        .compact();
```

JJWT-style verification concept:

```java
Claims claims = Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
```

The library must verify:

- The signature.
- The expected algorithm/key.
- Expiration.
- Token structure.

The application must additionally validate required claims and load/check the referenced account.

Never use a method that merely decodes claims without verifying the signature.

---

## 16. JWT Authentication Filter

A custom `OncePerRequestFilter` can authenticate bearer tokens.

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokenService;
    private final AccountPrincipalService principalService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            TokenService tokenService,
            AccountPrincipalService principalService,
            AuthenticationEntryPoint authenticationEntryPoint) {
        this.tokenService = tokenService;
        this.principalService = principalService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String compactToken = header.substring(7);
            VerifiedToken token = tokenService.verify(compactToken);
            AccountPrincipal principal = principalService.loadEnabled(token.subject());

            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            principal,
                            null,
                            principal.getAuthorities());

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (AuthenticationException | JwtException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid access token", exception));
        }
    }
}
```

The example is conceptual; adapt exception types to the selected JWT library.

Key behaviors:

- A missing header remains anonymous; authorization decides whether the route is public.
- A present but invalid token produces `401`.
- The filter validates before setting the security context.
- A disabled account cannot remain authenticated merely because its old token is still signed.
- The filter is stateless and does not store the token in an HTTP session.

Do not log the bearer token in error messages.

---

## 17. Security Filter Chain Configuration

```java
@Bean
SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtFilter,
        AuthenticationEntryPoint authenticationEntryPoint,
        AccessDeniedHandler accessDeniedHandler) throws Exception {

    http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(errors -> errors
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(
                            "/api/auth/register",
                            "/api/auth/login",
                            "/actuator/health")
                        .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/*")
                        .permitAll()
                    .requestMatchers(
                            "/actuator/info",
                            "/actuator/metrics",
                            "/actuator/metrics/**")
                        .hasAuthority("EVENT_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/events/*/registrations")
                        .hasAnyAuthority("PARTICIPANT", "EVENT_ADMIN")
                    .requestMatchers(
                            HttpMethod.DELETE,
                            "/api/events/*/registrations/*")
                        .hasAnyAuthority("PARTICIPANT", "EVENT_ADMIN")
                    .requestMatchers("/api/events/**", "/api/participants/**")
                        .hasAuthority("EVENT_ADMIN")
                    .anyRequest()
                        .denyAll())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

This example communicates the model, but a real application must verify each matcher against its exact controller paths.

Authorization rules are evaluated in order. Place specific rules before broad patterns.

Prefer a deny-by-default fallback:

```java
.anyRequest().denyAll()
```

This prevents a newly added endpoint from becoming public accidentally.

---

## 18. Role Rules vs Ownership Rules

Route-level role check:

```text
PARTICIPANT may call registration endpoint
```

This is not enough. The participant must act only for their own identity.

Bad request design:

```json
{
  "participantId": 999
}
```

A participant can change the ID and act for another person.

Safer service design:

```java
@Transactional
public RegistrationResponse register(
        long eventId,
        AccountPrincipal principal) {

    Participant participant = participantRepository
            .findById(principal.participantId())
            .orElseThrow(/* ... */);

    // Continue use case for authenticated participant.
}
```

An administrator-only operation may accept a participant ID explicitly.

Ownership can be enforced:

- In a service using the authenticated principal and loaded domain data.
- With method security such as `@PreAuthorize` plus a policy bean.
- Through a dedicated authorization service.

Do not duplicate complex ownership logic across controllers.

---

## 19. Method Security

Enable method authorization when it adds defense in depth or protects use cases invoked outside one controller route:

```java
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
```

Example:

```java
@PreAuthorize("hasAuthority('EVENT_ADMIN')")
@Transactional
public EventResponse cancelEvent(long eventId) {
    // ...
}
```

Avoid placing every rule in a long SpEL expression. Domain ownership checks are often clearer in Java policy/service code.

Request rules and method rules can complement each other, but contradictory rules make debugging difficult. Document the intended authorization matrix.

---

## 20. Stateless Authentication

For this API, the client sends the token on every protected request:

```http
Authorization: Bearer eyJhbGciOi...
```

Configure:

```java
session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
```

Stateless means the server does not use an HTTP session to remember authentication between requests.

It does **not** mean the server has no state:

- Accounts remain in the database.
- Disabled status remains in the database.
- Roles and participant relationships remain in the database.
- Registration and event data remain in the database.

### Logout limitation

A signed access token normally remains valid until expiration. Deleting it on one client does not invalidate other copies.

Possible production strategies:

- Short access-token lifetime.
- Token revocation/deny list.
- Per-account token version.
- Refresh-token rotation.

These are beyond the minimum assignment. Document the selected limitation honestly.

---

## 21. `401` and `403` Error Handling

Security failures occur in the filter chain, often before `@RestControllerAdvice` can handle them.

Use:

- `AuthenticationEntryPoint` for authentication failure (`401`).
- `AccessDeniedHandler` for authorization failure (`403`).

```java
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError body = ApiError.of(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Authentication is required or the access token is invalid",
                request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
```

The access-denied handler returns the same error shape with status `403` and a stable code such as `ACCESS_DENIED`.

Do not expose parser exceptions, stack traces, account existence, or token content.

---

## 22. CSRF

CSRF exploits credentials that a browser automatically attaches to cross-site requests, commonly cookies.

For a stateless API that accepts bearer tokens only from the `Authorization` header:

- The browser does not automatically attach that header cross-site.
- Disabling CSRF protection can be a reasonable documented decision.

```java
http.csrf(AbstractHttpConfigurer::disable);
```

However, “the API is stateless” alone is not enough reasoning. A stateless application can still be vulnerable if authentication uses cookies or browser-managed credentials.

If the application stores JWTs in cookies, revisit the CSRF design and cookie protections.

---

## 23. CORS

CORS controls whether browser JavaScript from one origin may call another origin.

It is not:

- Authentication.
- Authorization.
- Protection for non-browser clients.

If no browser frontend exists, CORS configuration may be unnecessary.

If required, allow known origins, methods, and headers:

```text
Allowed origin:  https://eventhub.example
Allowed methods: GET, POST, PUT, DELETE
Allowed headers: Authorization, Content-Type
```

Do not combine wildcard origins with credentialed requests. Do not use `*` merely to silence a browser error.

---

## 24. Token Storage on Clients

The server cannot fully control client storage, but the API design should explain risks.

| Storage | Main risk/consideration |
|---|---|
| In-memory client state | Lost on refresh; less persistence exposure |
| Browser local storage | Readable by injected JavaScript/XSS |
| HttpOnly cookie | Not readable by JS, but automatically attached and needs CSRF design |

For command-line testing, supply the token in the `Authorization` header:

```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  http://localhost:8080/api/participants
```

Never place access tokens in query parameters because URLs are commonly logged and cached.

---

## 25. Account and Login API Design

### Register

```http
POST /api/auth/register
Content-Type: application/json
```

```json
{
  "email": "learner@example.com",
  "password": "correct horse battery staple",
  "fullName": "Nguyen Van A"
}
```

Possible response:

```http
201 Created
Location: /api/participants/42
```

The response does not automatically need to log the user in. Define the contract.

### Login

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "email": "learner@example.com",
  "password": "correct horse battery staple"
}
```

Response:

```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresAt": "2026-07-16T06:15:00Z"
}
```

Do not return the password hash, signing secret, or unnecessary account details.

---

## 26. Testing Security Behavior

Security tests should prove boundaries, not merely that a bean exists.

Important scenarios:

- Registration stores an encoded password.
- Registration cannot choose administrator role.
- Correct credentials produce a valid signed token.
- Wrong credentials produce `401` and no token.
- Public route works anonymously.
- Protected route without a token produces `401`.
- Tampered or expired token produces `401`.
- Authenticated participant on admin route produces `403`.
- Administrator operation succeeds with `EVENT_ADMIN`.
- Participant acts only for their own participant identity.
- Disabled account cannot continue using an old token according to the documented design.

Mock users are useful for testing route authorization:

```java
mockMvc.perform(post("/api/events")
                .with(user("admin@example.com")
                        .authorities(new SimpleGrantedAuthority("EVENT_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated());
```

But mock-user tests do not prove JWT parsing/signature validation. Add focused token-service/filter tests and at least one integrated login-token-protected-request flow.

The next lecture covers test scopes in detail.

---

## 27. Common Security Mistakes

### Plaintext or reversible passwords

Passwords must use an adaptive one-way encoder.

### Public role selection

Never bind `role` from a public registration request.

### Hard-coded signing key

A key committed to Git is not secret. Externalize and rotate it.

### Decode without verify

Base64URL decoding proves nothing about who created the token.

### Accept any algorithm

The verifier must enforce the expected algorithm/key, not trust the token header blindly.

### Long-lived token with no account check

A disabled or demoted account may retain access until expiration. Use short lifetimes and/or load current account state.

### `permitAll()` fallback

A new endpoint may become public accidentally. Prefer deny-by-default.

### Matcher ordering error

A broad rule placed first can shadow a more specific rule.

### Authorization only in the UI

The API must enforce permissions even if a frontend hides buttons.

### Role check without ownership

Every participant having the same role does not mean every participant owns every registration.

### Security disabled in tests

Tests then cannot prove the production boundary.

### Token/password logging

Authorization headers and login bodies must be redacted.

---

## 28. Security Design Checklist

- Accounts are stored in a relational database.
- Email uniqueness is protected by the database.
- Passwords use `PasswordEncoder` and are never returned/logged.
- Public registration always assigns the lowest intended role.
- Login uses `AuthenticationManager` rather than manual comparison.
- JWTs are signed, expire, and contain minimal claims.
- Token validation enforces signature, algorithm, structure, and time.
- Disabled/current account state is handled deliberately.
- Sessions are stateless for bearer-token requests.
- Specific authorization rules precede broad rules.
- Unlisted routes are denied.
- Ownership is checked in addition to role.
- `401` and `403` are distinct and use a safe error contract.
- CSRF and CORS decisions match the actual client credential transport.
- Tests cover authentication, authorization, ownership, and token tampering.

---

## 29. Knowledge Check

1. What is the difference between authentication and authorization?
2. Why does an invalid token produce `401`, while an insufficient role produces `403`?
3. What are the responsibilities of `UserDetailsService` and `PasswordEncoder`?
4. Why must public registration not accept a role field?
5. What does a JWT signature protect, and what does it not protect?
6. Why is Base64-decoding a JWT insufficient?
7. What should the JWT `sub` claim identify?
8. Why might an application load the account on every token-authenticated request?
9. Where does a custom JWT filter belong relative to authorization?
10. Why is a role check insufficient for participant ownership?
11. When is disabling CSRF reasonable for a REST API?
12. Why should unlisted routes be denied by default?

---

## 30. Further Reading

- [Spring Security Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Username/Password Authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/)
- [DaoAuthenticationProvider](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/dao-authentication-provider.html)
- [Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [Authorize HTTP Requests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- [Spring Security MockMvc Support](https://docs.spring.io/spring-security/reference/servlet/test/mockmvc/)
- [JJWT Project](https://github.com/jwtk/jjwt)
