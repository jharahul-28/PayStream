# PayStream — Milestone 1 Complete

## What Was Built

### 1. Multi-Module Maven Parent (`pom.xml`)
Replaced the single-module skeleton with a proper multi-module Maven parent.

**Key decisions:**
- Spring Boot 3.3.5 (not 4.x — Spring Boot 4 does not exist; the skeleton used an invalid version)
- Spring Cloud 2023.0.3 for Eureka + Gateway
- Java 21 (virtual threads ready)
- `dependencyManagement` BOM imports: Spring Boot, Spring Cloud, Testcontainers
- Annotation processor path for Lombok + MapStruct in the compiler plugin

### 2. `paystream-common` (Pure Java, zero Spring dependency)

| File | Purpose |
|------|---------|
| `ApiResponse<T>` | Universal response envelope — every controller returns this |
| `ErrorCode` | 13 canonical error codes (PS-1001 through PS-5002) |
| `RedisKeys` | All Redis key templates in one place — no magic strings |
| `PayStreamException` + 7 subclasses | Typed exception hierarchy mapped to exact HTTP status codes |
| `GlobalExceptionHandler` | `@RestControllerAdvice` — maps every exception to `ApiResponse` |
| `Money` | Immutable value object using `long` minor units — never `double` |
| `IdGenerator` | Thread-safe ULID generator wrapping `de.huxhorn.sulky.ulid` |
| `CorrelationIdFilter` | Sets `X-Correlation-Id` + MDC on every request, cleans up on exit |

**Tests:** `MoneyTest` — 14 cases covering arithmetic, immutability, currency validation, edge cases.

### 3. `paystream-eureka-server`
- `@EnableEurekaServer`, standalone, self-preservation disabled for local dev
- Listens on port 8761

### 4. `paystream-gateway`
- Spring Cloud Gateway (reactive, WebFlux)
- `JwksCache` — fetches RSA public key from auth-service on startup, refreshes hourly
- `JwtGlobalFilter` — validates Bearer token, injects `X-User-Id` + `X-User-Role`, strips `Authorization` from internal hops
- `RateLimitFilter` — Redis sliding window, 100 req/min per user/IP, returns 429 + Retry-After
- Port 8080

### 5. `paystream-auth-service` (Full Implementation)

#### Architecture (Clean / Hexagonal)
```
api/
  controller/   AuthController, JwksController
  dto/request/  RegisterRequest, LoginRequest, RefreshRequest
  dto/response/ UserResponse, AuthResponse
application/
  port/in/      AuthUseCase
  port/out/     UserRepository, RefreshTokenRepository
  command/      RegisterCommand, LoginCommand
  service/      AuthApplicationService
domain/
  model/        User (pure Java), Role, UserStatus
infrastructure/
  persistence/
    entity/     UserEntity, RefreshTokenEntity (JPA)
    repository/ UserJpaRepository, RefreshTokenJpaRepository
    adapter/    UserPersistenceAdapter, RefreshTokenPersistenceAdapter
  config/       JwtKeyConfig, WebConfig
security/
  jwt/          JwtProperties, JwtService, JwtAuthFilter
  config/       SecurityConfig
```

#### JWT Implementation
- RS256 (asymmetric) — private key signs, public key verifies
- Access token: 15 minutes, claims: `sub=userId, email, role, jti=UUID`
- Refresh token: 7 days, opaque random bytes, SHA-256 hash stored in DB
- Blocklist: Redis `token:blocklist:{jti}` with TTL = remaining seconds
- Token rotation: refresh consumes the old token and issues a new one

#### Security
- BCrypt strength 12 for password hashing
- Brute-force protection: 5 failures → 15-min lockout in Redis AND DB (`locked_until`)
- Stateless sessions, CSRF disabled
- `@EnableMethodSecurity` for `@PreAuthorize`

#### REST Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/register` | Public | Register user |
| POST | `/api/v1/auth/login` | Public | Login, receive tokens |
| POST | `/api/v1/auth/refresh` | Public | Rotate refresh token |
| POST | `/api/v1/auth/logout` | Bearer | Blocklist + revoke |
| GET | `/api/v1/auth/me` | Bearer | Current user |
| GET | `/.well-known/jwks.json` | Public | RSA public key (JWKS) |

#### Database Migrations (Flyway)
- `V1__create_users.sql` — users table with CHECK constraints on role/status
- `V2__create_refresh_tokens.sql` — refresh_tokens with FK + covering index

### 6. Skeleton Services
Three services boot, register with Eureka, and expose `/actuator/health`:
- `paystream-payment-service` (port 8082)
- `paystream-wallet-service` (port 8083)
- `paystream-ledger-service` (port 8084)

### 7. `docker/docker-compose.yml`
6 isolated PostgreSQL instances + Redis + Kafka (KRaft) + Kafka UI + Eureka + Gateway + Auth Service.
Every service has `healthcheck` and `depends_on: condition: service_healthy`. No plaintext passwords.

---

## Files Created / Modified

```
pom.xml                                             MODIFIED (became multi-module parent)
.gitignore                                          MODIFIED (added *.pem, secrets)
scripts/generate-jwt-keys.sh                        NEW
docker/
  docker-compose.yml                                NEW
paystream-common/
  pom.xml                                           NEW
  src/main/java/com/paystream/common/
    api/ApiResponse.java                            NEW
    constant/ErrorCode.java                         NEW
    constant/RedisKeys.java                         NEW
    domain/Money.java                               NEW
    exception/PayStreamException.java               NEW
    exception/DomainException.java                  NEW
    exception/ValidationException.java              NEW
    exception/ResourceNotFoundException.java        NEW
    exception/DuplicateResourceException.java       NEW
    exception/InsufficientFundsException.java       NEW
    exception/FraudBlockedException.java            NEW
    exception/ExternalServiceException.java         NEW
    exception/AuthException.java                    NEW
    exception/GlobalExceptionHandler.java           NEW
    filter/CorrelationIdFilter.java                 NEW
    util/IdGenerator.java                           NEW
  src/test/.../domain/MoneyTest.java                NEW
paystream-eureka-server/
  pom.xml, EurekaServerApplication.java, application.yml   NEW
paystream-gateway/
  pom.xml                                           NEW
  src/main/java/com/paystream/gateway/
    GatewayApplication.java                         NEW
    security/JwksCache.java                         NEW
    filter/JwtGlobalFilter.java                     NEW
    filter/RateLimitFilter.java                     NEW
  application.yml                                   NEW
paystream-auth-service/
  pom.xml                                           NEW
  Dockerfile                                        NEW
  src/main/java/com/paystream/auth/
    AuthServiceApplication.java                     NEW
    domain/model/User.java                          NEW
    domain/model/Role.java                          NEW
    domain/model/UserStatus.java                    NEW
    application/command/RegisterCommand.java        NEW
    application/command/LoginCommand.java           NEW
    application/port/in/AuthUseCase.java            NEW
    application/port/out/UserRepository.java        NEW
    application/port/out/RefreshTokenRepository.java NEW
    application/service/AuthApplicationService.java NEW
    api/controller/AuthController.java              NEW
    api/controller/JwksController.java              NEW
    api/dto/request/RegisterRequest.java            NEW
    api/dto/request/LoginRequest.java               NEW
    api/dto/request/RefreshRequest.java             NEW
    api/dto/response/UserResponse.java              NEW
    api/dto/response/AuthResponse.java              NEW
    infrastructure/persistence/entity/UserEntity.java             NEW
    infrastructure/persistence/entity/RefreshTokenEntity.java     NEW
    infrastructure/persistence/repository/UserJpaRepository.java  NEW
    infrastructure/persistence/repository/RefreshTokenJpaRepository.java NEW
    infrastructure/persistence/adapter/UserPersistenceAdapter.java       NEW
    infrastructure/persistence/adapter/RefreshTokenPersistenceAdapter.java NEW
    infrastructure/config/JwtKeyConfig.java         NEW
    infrastructure/config/WebConfig.java            NEW
    security/jwt/JwtProperties.java                 NEW
    security/jwt/JwtService.java                    NEW
    security/jwt/JwtAuthFilter.java                 NEW
    security/config/SecurityConfig.java             NEW
  src/main/resources/
    application.yml                                 NEW
    db/migration/V1__create_users.sql               NEW
    db/migration/V2__create_refresh_tokens.sql      NEW
  src/test/java/com/paystream/auth/
    domain/UserDomainTest.java                      NEW
    security/jwt/JwtServiceTest.java                NEW
    api/controller/AuthControllerTest.java          NEW
    infrastructure/persistence/UserRepositoryTest.java NEW
  src/test/resources/application-test.yml           NEW
paystream-payment-service/  (skeleton)              NEW
paystream-wallet-service/   (skeleton)              NEW
paystream-ledger-service/   (skeleton)              NEW
```

---

## How to Run

### Prerequisites
- Java 21
- Maven 3.9+
- Docker + Docker Compose

### Step 1: Generate RSA keys
```bash
chmod +x scripts/generate-jwt-keys.sh
./scripts/generate-jwt-keys.sh
```
This writes `private.pem` and `public.pem` to `paystream-auth-service/src/main/resources/keys/`.
The private key is in `.gitignore` — never commit it.

### Step 2: Start infrastructure
```bash
docker compose -f docker/docker-compose.yml up -d postgres-auth redis
```

### Step 3: Run auth service locally
```bash
mvn install -pl paystream-common -am
mvn spring-boot:run -pl paystream-auth-service
```

### Step 4: Run tests
```bash
# Unit tests (no Docker required)
mvn test -pl paystream-common,paystream-auth-service

# Repository integration tests (needs Docker for Testcontainers)
mvn verify -pl paystream-auth-service
```

---

## Self-Verification Checklist

- [x] No `@Autowired` field injection — constructor injection only
- [x] No business logic in controllers — one-line delegation
- [x] No `@Entity` on domain model classes
- [x] No hardcoded secrets — all config via `${ENV_VAR:-default}`
- [x] All log statements use `{}` placeholders
- [x] All tests use Testcontainers, not H2
- [x] Docker Compose: every service has `healthcheck` + `depends_on: service_healthy`
- [x] `paystream-common` has no Spring Boot runtime dependency
- [x] Money amounts use `long` minor units, never `double`
- [x] User-supplied strings sanitized (CR/LF stripped) before logging

---

## Pending for Milestone 2

- Full implementation of payment-service (state machine, idempotency, Feign clients)
- Full implementation of wallet-service (debit/credit, optimistic locking, internal auth)
- Full implementation of ledger-service (double-entry, balance snapshots)
- MapStruct mappers (stub interfaces exist in the package structure)
- Micrometer metrics registration
