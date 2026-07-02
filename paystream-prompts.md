=============================================================
PAYSTREAM — MILESTONE 1: FOUNDATION, ARCHITECTURE & AUTH
=============================================================

## YOUR ROLE AND CONTEXT

You are a Staff+ engineer with 12+ years of production Java experience,
specializing in fintech microservices (payment systems, ledger design,
Spring Security). You have been handed an empty Spring Boot multi-module
skeleton and must build it into a banking-grade foundation.

This is Milestone 1 of 5. Everything you build here becomes the
contract all later milestones depend on. Correctness and consistency
now prevents rework later.

---

## READ FIRST — BEFORE WRITING ANY CODE

1. Scan every existing file in the project tree.
2. Identify what already exists: pom.xml structure, any existing
   classes, partial configs, placeholder files.
3. Briefly plan what you will create vs. modify vs. leave untouched.
4. Only then begin implementation.

Do NOT overwrite existing code without explicitly noting the change
and the reason. Do NOT create duplicate classes.

---

## WHAT THIS MILESTONE MUST DELIVER

  [1] paystream-common         — shared library (no Spring, pure Java)
  [2] paystream-eureka-server  — service registry
  [3] paystream-gateway        — API gateway (Spring Cloud Gateway)
  [4] paystream-auth-service   — full JWT auth implementation
  [5] paystream-payment-service  — skeleton only (main class + health)
  [6] paystream-wallet-service   — skeleton only
  [7] paystream-ledger-service   — skeleton only
  [8] docker/docker-compose.yml  — full local dev stack

---

## MANDATORY PACKAGE STRUCTURE

Every service must follow this exact layout. No deviations.

  com.paystream.{service}/
    api/
      controller/    REST controllers. No business logic. One line per method.
      dto/
        request/     Java records. @Valid annotations. Immutable.
        response/    Java records. No setters.
      mapper/        MapStruct interfaces only
      exception/     @RestControllerAdvice GlobalExceptionHandler
      filter/        Servlet filters (CorrelationIdFilter, etc.)
    application/
      service/       Use-case orchestration. Calls ports, not repos.
      port/
        in/          Input port interfaces (use-case contracts)
        out/         Output port interfaces (persistence, messaging)
      command/       Command objects for cross-layer communication
    domain/
      model/         Pure Java. Zero framework annotations.
      event/         Domain events as Java records
      exception/     Business rule exceptions
      valueobject/   Money, UserId, Currency, etc.
    infrastructure/
      persistence/
        entity/      JPA @Entity classes (NOT the domain model)
        repository/  Spring Data JPA interfaces
        adapter/     Implements out/ ports. Translates entity <-> domain
      messaging/
        producer/    Kafka producers (milestone 3)
        consumer/    Kafka consumers (milestone 3)
        event/       Kafka event record schemas
      external/      Feign clients / WebClient (milestone 2)
      config/        @Configuration classes
    security/
      jwt/           JwtService, JwtAuthFilter, JwtProperties
      rbac/          Role enum, method security setup
      config/        SecurityFilterChain
    observability/
      logging/       MDC utilities, structured log helpers
      metrics/       Micrometer custom metrics
    common/
      util/          Shared utilities
      constant/      String/int constants. Zero magic values in code.
      annotation/    Custom annotations

---

## PAYSTREAM-COMMON: WHAT TO BUILD

paystream-common has NO Spring dependency. Pure Java 21.

### 1. ApiResponse<T>
Every controller in every service returns ApiResponse<T>. Never return
raw objects, Page directly, or Map<String,Object>.

GOOD:
  return ResponseEntity.ok(ApiResponse.success(userResponse));
  return ResponseEntity.status(409).body(ApiResponse.error(ErrorCode.DUPLICATE_RESOURCE, "Email already registered"));

BAD:
  return ResponseEntity.ok(userResponse);
  return ResponseEntity.ok(Map.of("status", "ok"));

Structure:
  record ApiResponse<T>(
    boolean success, T data, String errorCode, String errorMessage,
    String timestamp, String traceId
  )

### 2. PayStreamException hierarchy

  PayStreamException extends RuntimeException  (base)
    ├── DomainException           (422)
    ├── ValidationException       (400)
    ├── ResourceNotFoundException (404)
    ├── DuplicateResourceException (409)
    ├── InsufficientFundsException (422)
    ├── FraudBlockedException      (402)
    └── ExternalServiceException   (503)

### 3. ErrorCode enum

  PS-1001  VALIDATION_ERROR
  PS-1002  RESOURCE_NOT_FOUND
  PS-1003  DUPLICATE_RESOURCE
  PS-2001  INSUFFICIENT_FUNDS
  PS-2002  FRAUD_BLOCKED
  PS-2003  CONCURRENT_MODIFICATION
  PS-2004  INVALID_STATE_TRANSITION
  PS-3001  IDEMPOTENCY_CONFLICT
  PS-4001  AUTH_INVALID
  PS-4002  AUTH_EXPIRED
  PS-4003  AUTH_INSUFFICIENT_ROLE
  PS-5001  INTERNAL_ERROR
  PS-5002  EXTERNAL_SERVICE_UNAVAILABLE

### 4. Money value object

GOOD:
  Money amount = Money.of(100000L, "INR");  // 100000 paise = Rs.1000
  long minor = amount.toMinorUnits();

BAD:
  double amount = 1000.0;    // NEVER. Float precision corrupts money.

Money is immutable. Includes: add(), subtract(), percentage(),
isPositive(), isZero(), toMinorUnits(), fromMinorUnits(long, String).

### 5. IdGenerator
Uses ULID (de.huxhorn.sulky:sulky-ulid). All entity PKs are VARCHAR(26).
  IdGenerator.generate() -> String ULID

### 6. CorrelationIdFilter
Generates X-Correlation-Id UUID if absent. Puts into MDC. Adds to response. Clears on exit.

### 7. GlobalExceptionHandler
@RestControllerAdvice. Maps all PayStreamException subtypes to correct
HTTP status + ApiResponse. Never expose stack traces to clients.

---

## AUTH SERVICE: FULL IMPLEMENTATION

### Database (Flyway migrations)

V1__create_users.sql:
  CREATE TABLE users (
    id            VARCHAR(26)  PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts INT  NOT NULL DEFAULT 0,
    locked_until  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version       INT          NOT NULL DEFAULT 0
  );

V2__create_refresh_tokens.sql:
  CREATE TABLE refresh_tokens (
    id          VARCHAR(26)  PRIMARY KEY,
    user_id     VARCHAR(26)  NOT NULL REFERENCES users(id),
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
  );
  CREATE INDEX idx_rt_user_active ON refresh_tokens(user_id, revoked, expires_at);

### Domain model (pure Java, zero annotations)

User entity fields: id, email, passwordHash, fullName, role, status,
failedLoginAttempts, lockedUntil, version

Behaviour methods:
  recordFailedLogin() — increments counter, sets lockedUntil if >= 5
  resetLoginAttempts() — clears counter and lock
  isLocked() — returns true if lockedUntil > now
  isActive() — status == ACTIVE

Role enum: CUSTOMER, MERCHANT, FRAUD_ANALYST, FINANCE_OPS, ADMIN
UserStatus enum: ACTIVE, SUSPENDED, LOCKED

### JWT (RS256)

JwtProperties (@ConfigurationProperties("paystream.security.jwt")):
  privateKeyPath, publicKeyPath, accessTokenTtlSeconds (900), refreshTokenTtlSeconds (604800)

JwtService:
  generateAccessToken(User) -> signed JWT, claims: sub=userId, email, role, jti=UUID
  generateRefreshToken() -> opaque random string (store hash in DB)
  validateToken(String) -> throws AuthInvalidException or AuthExpiredException
  extractUserId(String), extractJti(String), getRemainingTtlSeconds(String)

JwtAuthFilter extends OncePerRequestFilter:
  1. Extract Bearer token
  2. validateToken() — on failure: 401, stop
  3. Check Redis "token:blocklist:{jti}" — if exists: 401, stop
  4. Set SecurityContextHolder

### REST endpoints (fully implemented)

POST /api/v1/auth/register
  Request: { email, password, fullName, role }
  Password: min 8 chars, 1 uppercase, 1 digit, 1 special char (@Pattern)
  Response 201: ApiResponse<UserResponse>

POST /api/v1/auth/login
  Request: { email, password }
  Response: ApiResponse<AuthResponse> { accessToken, refreshToken, expiresIn:900, tokenType:"Bearer", userId, role }
  After 5 failures: lock account 15min (Redis + locked_until). Return 423.

POST /api/v1/auth/refresh
  Validate: hash matches DB, not expired, not revoked. Rotate tokens.
  Response 200: ApiResponse<AuthResponse>

POST /api/v1/auth/logout
  Add jti to Redis: SET "token:blocklist:{jti}" "1" EX {remainingTtl}
  Revoke refresh token in DB.

GET /api/v1/auth/me
  Response 200: ApiResponse<UserResponse>

GET /.well-known/jwks.json
  Public endpoint. RSA public key in JWKS format. Used by gateway.

### Security config
  .sessionManagement(STATELESS), .csrf(disabled)
  BCryptPasswordEncoder strength=12
  @EnableMethodSecurity(prePostEnabled=true)
  Public: /api/v1/auth/**, /.well-known/jwks.json, /actuator/health

### Redis keys
  "token:blocklist:{jti}"   -> "1"    TTL = remaining token seconds
  "login:attempts:{email}"  -> count  TTL = 15 minutes
  "login:locked:{email}"    -> "1"    TTL = lockout duration

---

## API GATEWAY

JwksCache: fetches public key from auth-service on startup, refreshes hourly.

JwtGlobalFilter:
  Skip permitAll paths. Extract + validate Bearer token.
  Add X-User-Id and X-User-Role headers to forwarded requests.
  Remove Authorization header from internal hops.

RateLimitFilter: Redis sliding window. 100 req/min per userId. 429 + Retry-After.

Routes:
  /api/v1/auth/**        -> auth-service        (no auth filter)
  /api/v1/payments/**    -> payment-service
  /api/v1/wallets/**     -> wallet-service
  /api/v1/ledger/**      -> ledger-service
  /api/v1/fraud/**       -> fraud-service       (FRAUD_ANALYST required)
  /api/v1/settlements/** -> settlement-service  (FINANCE_OPS required)

---

## DOCKER COMPOSE

Services (each with healthcheck, named volume, depends_on: service_healthy):
  postgres-auth (5432), postgres-payment (5433), postgres-wallet (5434)
  postgres-ledger (5435), postgres-settlement (5436), postgres-audit (5437)
  redis:7-alpine (6379)
  kafka KRaft mode confluentinc/cp-kafka:7.6 (9092)
  kafka-ui provectuslabs/kafka-ui (8090)
  eureka-server (8761), api-gateway (8080), auth-service (8081)

No plaintext passwords. Use: POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-localdevonly}

---

## ENGINEERING STANDARDS — NON-NEGOTIABLE

Constructor injection only:
  GOOD: AuthService(UserRepository repo) { this.repo = repo; }
  BAD:  @Autowired private UserRepository repo;

No business logic in controllers:
  GOOD: return ApiResponse.success(authService.login(command));
  BAD:  User user = userRepo.findByEmail(req.email()); if (user == null) throw...

No JPA annotations on domain model classes.
No magic strings — all constants in constant/ package.

Logging — SLF4J only, parameterized:
  GOOD: log.warn("Login failed [correlationId={}] email={}", MDC.get("correlationId"), email);
  BAD:  System.out.println("Login failed for " + email);

---

## TESTS REQUIRED

AuthControllerTest (MockMvc):
  register valid data -> 201
  register duplicate email -> 409 PS-1003
  register weak password -> 400 PS-1001
  login correct -> 200, tokens returned
  login wrong password -> 401
  login after 5 failures -> 423
  refresh valid -> 200, new tokens
  refresh expired -> 401 PS-4002
  logout -> 200, same token -> 401
  GET /me with valid token -> 200
  GET /me without token -> 401

JwtServiceTest: generate+parse match, expired -> exception, tampered -> exception
UserDomainTest: 5 failed logins -> isLocked() true, reset -> isLocked() false
UserRepositoryTest (@DataJpaTest + Testcontainers): duplicate email -> exception, Flyway runs
RedisIntegrationTest (Testcontainers): blocklist jti, TTL expires correctly
MoneyValueObjectTest: arithmetic, immutability, invalid currency -> exception

---

## SELF-VERIFICATION BEFORE FINISHING

  [ ] mvn compile runs with zero errors and zero warnings
  [ ] No @Autowired field injection anywhere
  [ ] No domain model class has @Entity or @Table
  [ ] No controller method has business logic
  [ ] No password/secret/API key hardcoded in any .java or .yml
  [ ] All log statements use {} placeholder, no string concatenation
  [ ] All tests use Testcontainers, not H2 in-memory
  [ ] docker-compose: every service has healthcheck and depends_on condition: service_healthy
  [ ] paystream-common has zero Spring Boot dependencies


=============================================================
PAYSTREAM — MILESTONE 2: PAYMENTS, WALLETS & LEDGER
=============================================================

## YOUR ROLE AND CONTEXT

You are the same Staff+ engineer from Milestone 1. You now own the
three core financial services: payment-service, wallet-service,
and ledger-service. Money either moved or it did not. The ledger
either balances or it is wrong.

---

## READ FIRST — MANDATORY

Before writing a single line:
1. Read paystream-common: ApiResponse, ErrorCode, Money, IdGenerator, exceptions.
2. Read auth-service package structure. Your services follow the exact same layout.
3. Read docker-compose.yml. Confirm correct database port for each service.
4. Check wallet/payment/ledger for any existing code. Refactor if misaligned, don't delete.

---

## WALLET SERVICE

### Database (wallet_db)

V1__create_wallets.sql:
  CREATE TABLE wallets (
    id          VARCHAR(26)  PRIMARY KEY,
    user_id     VARCHAR(26)  NOT NULL UNIQUE,
    balance     BIGINT       NOT NULL DEFAULT 0,
    currency    CHAR(3)      NOT NULL DEFAULT 'INR',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version     INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT balance_non_negative CHECK (balance >= 0)
  );

V2__create_wallet_holds.sql:
  CREATE TABLE wallet_holds (
    id          VARCHAR(26)  PRIMARY KEY,
    wallet_id   VARCHAR(26)  NOT NULL REFERENCES wallets(id),
    amount      BIGINT       NOT NULL,
    reason      VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    released    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL
  );

### Domain model (pure Java, no @Entity)

Wallet entity. Behaviour methods:
  debit(long amount):
    if status != ACTIVE -> throw DomainException(INVALID_STATE_TRANSITION)
    if balance < amount -> throw InsufficientFundsException(balance, amount)
    this.balance -= amount; this.version++;

  credit(long amount):
    if status != ACTIVE -> throw DomainException(INVALID_STATE_TRANSITION)
    if amount <= 0 -> throw DomainException("Credit amount must be positive")
    this.balance += amount; this.version++;

  freeze(), isActive()

WalletStatus enum: ACTIVE, FROZEN, CLOSED

### Optimistic locking

JPA entity has @Version private Integer version.

On OptimisticLockingFailureException, retry in WalletApplicationService:

  GOOD:
    int attempts = 0;
    while (attempts < 3) {
      try { return walletPersistenceAdapter.debitAndSave(walletId, amount); }
      catch (OptimisticLockingFailureException e) {
        attempts++;
        if (attempts == 3) throw new DomainException(CONCURRENT_MODIFICATION, "Wallet concurrently modified.");
        Thread.sleep(50L * attempts);
      }
    }

  BAD: walletRepo.save(entity);  // no retry, no handling

### Internal endpoint security

Debit/credit endpoints require X-Internal-Service-Key header.
InternalServiceAuthFilter validates against env var INTERNAL_SERVICE_KEY.
Missing or wrong key -> 403.

  GOOD: paystream.internal.service-key: ${INTERNAL_SERVICE_KEY:dev-only-local-key}
  BAD:  String INTERNAL_KEY = "secret123";

### REST endpoints

POST /api/v1/wallets                           — create wallet (one per user)
GET  /api/v1/wallets/my                        — authenticated user's wallet
POST /api/v1/wallets/{walletId}/debit  [INTERNAL]  — X-Internal-Service-Key required
POST /api/v1/wallets/{walletId}/credit [INTERNAL]
GET  /api/v1/wallets/{walletId}/statement?from=&to=&page=&size=

---

## LEDGER SERVICE

### Core principle: append-only

No UPDATE on ledger_entries. No DELETE. No soft delete. Ever.

V1__create_ledger_entries.sql:
  CREATE TABLE ledger_entries (
    id              VARCHAR(26)  PRIMARY KEY,
    account_id      VARCHAR(26)  NOT NULL,
    entry_type      VARCHAR(6)   NOT NULL,  -- 'DEBIT' or 'CREDIT'
    amount          BIGINT       NOT NULL,  -- negative for DEBIT, positive for CREDIT
    currency        CHAR(3)      NOT NULL,
    reference_id    VARCHAR(26)  NOT NULL,
    reference_type  VARCHAR(20)  NOT NULL,
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
    -- NO updated_at. NO deleted_at. By design.
  );
  CREATE UNIQUE INDEX idx_ledger_idempotency ON ledger_entries(reference_id, account_id, entry_type);
  CREATE INDEX idx_ledger_account_time ON ledger_entries(account_id, created_at DESC);
  CREATE INDEX idx_ledger_reference ON ledger_entries(reference_id);

V2__create_balance_snapshots.sql:
  CREATE TABLE account_balance_snapshots (
    id           VARCHAR(26)  PRIMARY KEY,
    account_id   VARCHAR(26)  NOT NULL UNIQUE,
    balance      BIGINT       NOT NULL,
    snapshot_at  TIMESTAMPTZ  NOT NULL,
    entry_count  BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
  );

### Double-entry invariant

Every money movement creates exactly two entries that sum to zero.

  GOOD:
    LedgerEntry debit  = new LedgerEntry(walletA, DEBIT,  -100000, "INR", paymentId);
    LedgerEntry credit = new LedgerEntry(walletB, CREDIT, +100000, "INR", paymentId);
    assert debit.amount() + credit.amount() == 0;  // enforced before persist

  BAD:
    ledgerRepo.save(creditEntry);  // where is the debit?

Enforce in LedgerApplicationService.createDoubleEntry():
  1. Validate: debitEntry.amount() + creditEntry.amount() == 0
     If not: throw DomainException("Double-entry invariant violated for referenceId=" + id)
  2. Persist both in a single @Transactional method.

### Balance computation

computeBalance(accountId):
  1. Load latest snapshot
  2. If exists: base = snapshot.balance + SUM(amount WHERE created_at > snapshot_at)
  3. If not: SUM(amount) full scan
  Use @Query native SQL — do not load all entries into memory.

### REST endpoints

POST /api/v1/ledger/entries [INTERNAL]       — validates double-entry, persists atomically
GET  /api/v1/ledger/accounts/{id}/balance
GET  /api/v1/ledger/accounts/{id}/entries?from=&to=&page=&size=
GET  /api/v1/ledger/transactions/{referenceId} — both legs, integrityValid flag

---

## PAYMENT SERVICE

### Database (payment_db)

V1__create_payments.sql:
  CREATE TABLE payments (
    id                     VARCHAR(26)  PRIMARY KEY,
    user_id                VARCHAR(26)  NOT NULL,
    idempotency_key        VARCHAR(255) NOT NULL,
    source_wallet_id       VARCHAR(26)  NOT NULL,
    destination_wallet_id  VARCHAR(26)  NOT NULL,
    amount                 BIGINT       NOT NULL,
    currency               CHAR(3)      NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    failure_reason         VARCHAR(500),
    failure_code           VARCHAR(50),
    note                   VARCHAR(500),
    metadata               JSONB,
    version                INT          NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, idempotency_key)
  );
  CREATE INDEX idx_payments_user_time ON payments(user_id, created_at DESC);

V2__create_outbox.sql:
  CREATE TABLE outbox_events (
    id              VARCHAR(26)  PRIMARY KEY,
    aggregate_id    VARCHAR(26)  NOT NULL,
    aggregate_type  VARCHAR(50)  NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
  );
  CREATE INDEX idx_outbox_unpublished ON outbox_events(published, created_at) WHERE published = FALSE;

### Payment state machine

States: PENDING -> PROCESSING -> COMPLETED
                             -> FAILED
        COMPLETED -> REFUNDED

Payment.transitionTo(PaymentStatus next):
  Validate against allowed transitions map.
  If invalid: throw DomainException(INVALID_STATE_TRANSITION, "Cannot transition from X to Y")

### Idempotency

Key: "idempotency:{userId}:{idempotencyKey}"

  Case 1 — absent: SET key "PROCESSING" EX 86400, proceed.
    On success: SET key {serialized response} EX 86400
    On failure: DEL key

  Case 2 — "PROCESSING": return 202 Accepted

  Case 3 — serialized response: deserialize and return 200. No wallet/ledger calls.

### Feign clients

WalletServiceClient: @FeignClient(name="wallet-service", fallbackFactory=WalletFallbackFactory)
  connectTimeout=2000, readTimeout=5000
  Retry on 5xx only, not 4xx.

LedgerServiceClient: same pattern.
Both fallbacks: throw ExternalServiceException(PS-5002) and log with correlationId.

### Payment orchestration

PaymentApplicationService.initiatePayment():
  1. Idempotency check (Redis)
  2. Persist payment PENDING + SET Redis "PROCESSING" — same @Transactional
  3. Fraud check — placeholder: FraudCheckResult.allow(0) (replaced in M4)
     If BLOCK: markFailed, clear Redis, return 402
  4. debitWallet(source) — if fails: markFailed, DEL Redis, throw
  5. creditWallet(dest) — if fails: compensate by crediting source back, markFailed, DEL Redis, throw
  6. Write outbox event + mark COMPLETED — single @Transactional
     SET Redis = serialized response
  7. Call ledger synchronously (moves to Kafka in M3)
     If ledger fails: log CRITICAL "LEDGER INCONSISTENCY paymentId={}" — do NOT reverse wallets

### REST endpoints

POST /api/v1/payments                        — X-Idempotency-Key required (400 if absent)
GET  /api/v1/payments/{paymentId}            — user sees only own payments
GET  /api/v1/payments?page=&size=&status=&from=&to=
POST /api/v1/payments/{paymentId}/refund     — partial refunds supported
GET  /api/v1/payments/admin/all              [ADMIN only]

---

## TESTS REQUIRED

PaymentControllerTest (MockMvc, WireMock for wallet + ledger):
  happy path -> 201, status COMPLETED
  same idempotency key -> 200 cached, wallet NOT called again
  insufficient balance -> 422 PS-2001
  missing X-Idempotency-Key -> 400 PS-1001
  amount = 0 -> 400 PS-1001
  sourceWalletId == destWalletId -> 400 PS-1001
  credit fails after debit -> compensation fires, payment FAILED
  refund of COMPLETED -> 201
  refund of PROCESSING -> 422 PS-2004

WalletControllerTest:
  create wallet -> 201
  create second for same user -> 409 PS-1003
  debit sufficient -> 200, balance decremented
  debit insufficient -> 422 PS-2001
  debit FROZEN -> 422 PS-2004
  debit without X-Internal-Service-Key -> 403
  concurrent debits -> one succeeds, one gets PS-2003 after retries

LedgerApplicationServiceTest (unit):
  valid double-entry (sum=0) -> both entries persisted
  invalid (sum!=0) -> DomainException, nothing persisted
  same reference_id twice -> second ignored (idempotent)

PaymentDomainTest (unit):
  PENDING -> PROCESSING -> allowed
  PENDING -> COMPLETED -> DomainException PS-2004
  COMPLETED -> REFUNDED -> allowed

---

## SELF-VERIFICATION BEFORE FINISHING

  [ ] balance_non_negative CHECK constraint in Flyway SQL
  [ ] Wallet domain entity has no @Entity annotation
  [ ] Optimistic lock retry has exponential backoff
  [ ] Ledger UNIQUE index (reference_id, account_id, entry_type) exists
  [ ] createDoubleEntry validates sum == 0 before persisting
  [ ] Compensation fires if credit fails
  [ ] Ledger failure after COMPLETED logs LEDGER_INCONSISTENCY but does not reverse wallets
  [ ] Idempotency repeat does not invoke Feign clients
  [ ] All money amounts use long (minor units), never double
  [ ] No controller method has if/else or repository calls


=============================================================
PAYSTREAM — MILESTONE 3: KAFKA EVENT-DRIVEN ARCHITECTURE
=============================================================

## YOUR ROLE AND CONTEXT

You are the same Staff+ engineer. Milestones 1 and 2 are complete.
You now add Kafka event streaming, the outbox relay pattern, and
three new services: notification-service, settlement-service,
and webhook-service.

---

## READ FIRST — MANDATORY

1. Read outbox_events table schema in payment_db.
2. Read PaymentApplicationService.initiatePayment() Step 7 (sync ledger call being replaced).
3. Read LedgerServiceClient — this Feign client gets removed.
4. Read docker-compose.yml — kafka and kafka-ui are already defined.
5. Payment-service -> wallet-service remains SYNCHRONOUS. Only ledger and notifications go async.

---

## WHAT CHANGES VS. WHAT STAYS

CHANGES:
  ledger-service: replaces REST inbound with Kafka consumer
  payment-service: removes sync ledger Feign call
  wallet-service: publishes events after each debit/credit

STAYS:
  payment-service -> wallet-service: synchronous REST (no change)
  idempotency logic: unchanged
  payment state machine: unchanged
  all existing REST endpoints: unchanged

NEW:
  notification-service (full)
  settlement-service (full)
  webhook-service (full)
  outbox relay in payment-service

---

## KAFKA INFRASTRUCTURE

### Topics (NewTopic @Bean)

  payments.initiated        partitions=32  key=userId
  payments.completed        partitions=32  key=userId
  payments.failed           partitions=32  key=userId
  wallet.debited            partitions=32  key=walletId
  wallet.credited           partitions=32  key=walletId
  fraud.check.requested     partitions=16  key=paymentId
  fraud.score.computed      partitions=16  key=paymentId
  notifications.send        partitions=8   key=userId
  webhooks.delivery         partitions=8   key=merchantId
  settlements.batch.trigger partitions=4   key=merchantId
  audit.events              partitions=16  key=entityId

Partitioned by userId/walletId so all events for one user arrive at
the same consumer thread — critical for ordered ledger processing.

### Producer config (all services)

  enable.idempotence: true
  acks: all
  retries: 2147483647
  max.in.flight.requests.per.connection: 5
  linger.ms: 5
  compression.type: snappy

### Consumer config

  isolation.level: read_committed
  enable.auto.commit: false
  max.poll.records: 100
  auto.offset.reset: earliest

### Event envelope (paystream-common)

  record BaseEvent<T>(
    String eventId,       // ULID
    String eventType,
    String eventVersion,  // "1.0"
    Instant timestamp,
    String correlationId,
    String sourceService,
    T payload
  ) {}

Jackson ObjectMapper @Bean: JavaTimeModule, WRITE_DATES_AS_TIMESTAMPS=false,
FAIL_ON_UNKNOWN_PROPERTIES=false.

---

## OUTBOX RELAY — PAYMENT SERVICE

OutboxRelayService (@Service):

  @Scheduled(fixedDelay=100, initialDelay=1000)
  void pollAndPublish():

    Step 1: SELECT id, event_type, aggregate_id, payload, created_at
            FROM outbox_events WHERE published=FALSE
            ORDER BY created_at ASC LIMIT 50
            FOR UPDATE SKIP LOCKED
            (SKIP LOCKED prevents two relay pods from picking the same row)

    Step 2: For each row:
      a. Deserialize payload -> correct event type (use event_type field)
      b. Determine topic from event_type via Map (no if/else chains)
      c. kafkaTemplate.send(topic, partitionKey, envelope).get(5, SECONDS)
         (.get() is synchronous — confirm delivery before marking published)
      d. If send succeeds: UPDATE outbox_events SET published=TRUE, published_at=NOW()
      e. If send fails: log error with eventId, leave published=FALSE, continue to next row

    Step 3: Each row uses @Transactional(REQUIRES_NEW) — not one big transaction.

  Metrics:
    Counter: "outbox.relay.published.total"
    Gauge:   "outbox.pending.count" (SELECT COUNT WHERE published=FALSE)
    Log WARN if pending count > 1000.

---

## LEDGER SERVICE — SWITCH TO KAFKA

Remove: POST /api/v1/ledger/entries (REST endpoint from M2)
Remove: LedgerServiceClient from payment-service
Remove: Step 7 sync ledger call from PaymentApplicationService

Add processed_events table (V3 migration in ledger_db):
  CREATE TABLE processed_events (
    event_id    VARCHAR(26)  PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );
  CREATE INDEX idx_pe_age ON processed_events(processed_at);

WalletEventConsumer:

  @KafkaListener(topics={"wallet.debited","wallet.credited"}, groupId="ledger-service-wallet-events")
  @RetryableTopic(
    attempts="4",
    backoff=@Backoff(delay=1000, multiplier=2.0, maxDelay=30000),
    dltTopicSuffix=".dlq",
    autoCreateTopics="false"
  )
  @Transactional
  void consume(BaseEvent<WalletDebitedEvent> event):
    if (processedEventsRepo.existsById(event.eventId())) { return; }  // idempotent
    ledgerApplicationService.createSingleEntry(event.payload());
    processedEventsRepo.save(new ProcessedEvent(event.eventId()));  // same @Transactional

  @DltHandler
  void handleDlq(BaseEvent<?> event, Exception e):
    log.error("DLQ event eventId={} eventType={} error={}", ...)
    // Write to dead_letter_events table. Increment metric "kafka.dlq.messages.total"

---

## WALLET SERVICE — ADD KAFKA PUBLISHING

Publish AFTER DB transaction commits. NOT inside the @Transactional block.

  GOOD:  // After wallet saved: kafkaProducer.publish("wallet.debited", walletId, event)
  BAD:   // Publishing inside @Transactional before commit — if tx rolls back, event already sent

---

## NOTIFICATION SERVICE — FULL IMPLEMENTATION

Database (notification_db):
  CREATE TABLE notifications (
    id               VARCHAR(26)  PRIMARY KEY,
    user_id          VARCHAR(26)  NOT NULL,
    payment_id       VARCHAR(26),
    type             VARCHAR(30)  NOT NULL,   -- EMAIL, SMS, PUSH
    channel          VARCHAR(50)  NOT NULL,   -- PAYMENT_SUCCESS, PAYMENT_FAILED
    status           VARCHAR(20)  NOT NULL,   -- PENDING, SENT, FAILED
    recipient        VARCHAR(255) NOT NULL,
    subject          VARCHAR(500),
    body             TEXT         NOT NULL,
    attempt_count    INT          NOT NULL DEFAULT 0,
    last_attempted_at TIMESTAMPTZ,
    sent_at          TIMESTAMPTZ,
    error_message    VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
  );

PaymentEventConsumer:
  @KafkaListener(topics={"payments.completed","payments.failed"})
  @RetryableTopic(attempts="3", backoff=@Backoff(delay=5000, multiplier=6.0, maxDelay=300000))
  On PaymentCompletedEvent: persist Notification, publish NotificationSendEvent
  On PaymentFailedEvent: same with PAYMENT_FAILED channel

NotificationSendConsumer (strategy pattern):
  EmailNotificationDispatcher: JavaMailSender. Mailhog in docker (port 1025/8025).
  SmsNotificationDispatcher: stub — logs "SMS would be sent". Document: wire Twilio in prod.
  PushNotificationDispatcher: stub.

Add to docker-compose:
  mailhog:
    image: mailhog/mailhog
    ports: ["1025:1025", "8025:8025"]

---

## SETTLEMENT SERVICE — FULL IMPLEMENTATION

Database (settlement_db):
  CREATE TABLE settlement_batches (
    id                    VARCHAR(26)  PRIMARY KEY,
    merchant_id           VARCHAR(26)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    total_payment_count   INT          NOT NULL DEFAULT 0,
    gross_amount          BIGINT       NOT NULL DEFAULT 0,
    fee_amount            BIGINT       NOT NULL DEFAULT 0,
    net_amount            BIGINT       NOT NULL DEFAULT 0,
    currency              CHAR(3)      NOT NULL,
    settlement_date       DATE         NOT NULL,
    processing_started_at TIMESTAMPTZ,
    settled_at            TIMESTAMPTZ,
    failure_reason        VARCHAR(500),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
  );

  CREATE TABLE settlement_items (
    id          VARCHAR(26) PRIMARY KEY,
    batch_id    VARCHAR(26) NOT NULL REFERENCES settlement_batches(id),
    payment_id  VARCHAR(26) NOT NULL,
    amount      BIGINT      NOT NULL,
    fee_amount  BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    settled_at  TIMESTAMPTZ,
    UNIQUE(batch_id, payment_id)
  );

SettlementScheduler:
  @Scheduled(cron="0 0 2 * * *", zone="Asia/Kolkata")
  1. Find PENDING batches with settlement_date <= today
  2. For each: status=PROCESSING (commit first)
  3. Process items in micro-batches of 100 — each batch committed independently (checkpoint pattern)
  4. fee = gross * 0.01 (1% default)
  5. Update batch SETTLED, publish SettlementCompletedEvent
  On failure: mark FAILED, increment metric

REST: GET /settlements/{batchId}, GET /settlements?..., POST /settlements/trigger [FINANCE_OPS]

---

## WEBHOOK SERVICE — FULL IMPLEMENTATION

Database:
  CREATE TABLE webhook_endpoints (
    id           VARCHAR(26)   PRIMARY KEY,
    merchant_id  VARCHAR(26)   NOT NULL,
    url          VARCHAR(2000) NOT NULL,
    secret       VARCHAR(255)  NOT NULL,
    events       TEXT[]        NOT NULL,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
  );
  CREATE TABLE webhook_deliveries (
    id                   VARCHAR(26) PRIMARY KEY,
    endpoint_id          VARCHAR(26) NOT NULL REFERENCES webhook_endpoints(id),
    event_type           VARCHAR(100) NOT NULL,
    payload              JSONB       NOT NULL,
    status               VARCHAR(20) NOT NULL,
    attempt_count        INT         NOT NULL DEFAULT 0,
    last_response_status INT,
    last_response_body   VARCHAR(2000),
    last_error           VARCHAR(500),
    delivered_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );

HMAC signature:
  String signingInput = timestamp + "." + jsonBody;
  signature = HmacSHA256(secret, signingInput)
  Headers: X-PayStream-Signature: sha256={signature}
           X-PayStream-Timestamp: {unix_timestamp}

WebhookDeliveryConsumer:
  @RetryableTopic(attempts="5", backoff=@Backoff(delay=60000, multiplier=5.0, maxDelay=21600000))
  On 2xx: DELIVERED
  On 4xx: EXHAUSTED immediately — throw NO exception, no Kafka retry
  On 5xx/timeout: throw exception -> Kafka retry

REST [MERCHANT role]: POST/GET/DELETE /webhooks/endpoints, GET /webhooks/deliveries, POST retry

---

## TESTS REQUIRED

OutboxRelayTest (Testcontainers Kafka + Postgres):
  unpublished event -> relay publishes to Kafka
  Kafka down -> event stays unpublished=FALSE, no exception escapes @Scheduled
  two relay instances (SKIP LOCKED) -> published once only

WalletEventConsumer idempotency (EmbeddedKafka):
  WalletDebitedEvent -> single ledger entry
  same eventId twice -> still one entry
  malformed JSON -> DLQ after 4 attempts

SettlementSchedulerTest:
  5 payments -> trigger -> batch SETTLED with correct totals
  restart simulation -> already SETTLED items not double-processed

WebhookDeliveryTest (WireMock):
  200 -> DELIVERED, HMAC header present and valid
  500 -> retry triggered (check WireMock call count)
  400 -> EXHAUSTED immediately, no further retries

---

## SELF-VERIFICATION BEFORE FINISHING

  [ ] Outbox relay uses FOR UPDATE SKIP LOCKED
  [ ] Each relay row uses @Transactional(REQUIRES_NEW)
  [ ] Wallet events published AFTER transaction commits, not inside it
  [ ] WalletEventConsumer checks processed_events before every process
  [ ] DLQ handler writes to dead_letter_events and increments metric
  [ ] Webhook 4xx -> EXHAUSTED, no Kafka retry triggered
  [ ] HMAC signature uses timestamp + "." + body
  [ ] Settlement micro-batch: each 100-item batch committed independently
  [ ] Mailhog in docker-compose
  [ ] All topics declared as @Bean NewTopic with correct partition counts


=============================================================
PAYSTREAM — MILESTONE 4: FRAUD, SPRING AI & OBSERVABILITY
=============================================================

## YOUR ROLE AND CONTEXT

You are the same Staff+ engineer. Milestones 1-3 complete. You now
implement fraud-service, audit-service, the reconciliation job, full
distributed observability, and Resilience4j circuit breakers.

---

## READ FIRST — MANDATORY

1. Read PaymentApplicationService Step 3: FraudCheckResult.allow(0). Replace it.
2. Check fraud.check.requested and fraud.score.computed topics — already declared.
3. Read all @Scheduled jobs to avoid conflicts with reconciliation job.
4. Check docker-compose for zipkin/prometheus/grafana. Add if missing.

---

## THE AI DECISION BOUNDARY — CRITICAL

What AI owns:
  - Risk narrative (human-readable explanation)
  - Secondary AI risk score (for dashboards only)
  - Pattern flagging

What AI NEVER owns:
  - The ALLOW / BLOCK / REVIEW decision
  - Any modification of a payment record
  - Any irreversible financial action

Stage 1 (synchronous, <5ms): deterministic rules -> ALLOW/BLOCK/REVIEW. FINAL.
Stage 2 (asynchronous, Kafka): AI enrichment -> updates narrative only.
Stage 2 never overrides Stage 1. Document this in a comment block.

---

## FRAUD SERVICE — DATABASE

V1__create_fraud_checks.sql:
  CREATE TABLE fraud_checks (
    id                  VARCHAR(26)   PRIMARY KEY,
    payment_id          VARCHAR(26)   NOT NULL UNIQUE,
    user_id             VARCHAR(26)   NOT NULL,
    risk_score          SMALLINT      NOT NULL,
    decision            VARCHAR(10)   NOT NULL,   -- ALLOW, BLOCK, REVIEW
    flags               TEXT[]        NOT NULL,
    rule_version        VARCHAR(20)   NOT NULL,
    ai_narrative        TEXT,
    ai_risk_score       SMALLINT,
    ai_confidence       DECIMAL(3,2),
    ai_processed        BOOLEAN       NOT NULL DEFAULT FALSE,
    ai_processing_error VARCHAR(500),
    processing_time_ms  BIGINT,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
  );

V2__create_fraud_rules.sql:
  CREATE TABLE fraud_rules (
    id        VARCHAR(26)  PRIMARY KEY,
    rule_code VARCHAR(50)  NOT NULL UNIQUE,
    rule_name VARCHAR(255) NOT NULL,
    flag_name VARCHAR(50)  NOT NULL,
    weight    INT          NOT NULL DEFAULT 10,
    enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    version   VARCHAR(20)  NOT NULL DEFAULT '1.0',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );

  INSERT initial rules:
    RULE_HIGH_AMOUNT    weight=20  flag=HIGH_AMOUNT
    RULE_HIGH_VELOCITY  weight=30  flag=HIGH_VELOCITY
    RULE_NEW_DEVICE     weight=15  flag=NEW_DEVICE
    RULE_ODD_HOUR       weight=10  flag=ODD_HOUR
    RULE_HIGH_CHARGEBACK weight=35 flag=HIGH_CHARGEBACK_HIST
    RULE_BLOCKED_IP     weight=80  flag=BLOCKED_IP

V3__create_user_risk_profiles.sql:
  CREATE TABLE user_risk_profiles (
    user_id               VARCHAR(26) PRIMARY KEY,
    avg_transaction_amount BIGINT     NOT NULL DEFAULT 0,
    typical_hours_start   INT         NOT NULL DEFAULT 8,
    typical_hours_end     INT         NOT NULL DEFAULT 22,
    known_device_ids      TEXT[]      NOT NULL DEFAULT '{}',
    known_ip_prefixes     TEXT[]      NOT NULL DEFAULT '{}',
    chargeback_count_30d  INT         NOT NULL DEFAULT 0,
    transaction_count_30d INT         NOT NULL DEFAULT 0,
    last_updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );

---

## FRAUD SERVICE — STAGE 1: RULES ENGINE

Rules cached in Redis (TTL 5 min). Key: "fraud:rules:active".

Evaluate each enabled rule:
  RULE_HIGH_AMOUNT:     if amount > 500000 -> flag HIGH_AMOUNT
  RULE_HIGH_VELOCITY:   Redis INCR "velocity:{userId}:{today}", EXPIRE 86400. If count > 10 -> flag
  RULE_NEW_DEVICE:      if deviceId not in profile.knownDeviceIds -> flag
  RULE_ODD_HOUR:        if hour < typicalStart or > typicalEnd -> flag
  RULE_HIGH_CHARGEBACK: if chargebackCount30d >= 2 -> flag
  RULE_BLOCKED_IP:      if ip matches any blocked prefix -> flag

Decision (deterministic, AI has zero influence):
  totalScore >= 80 -> BLOCK
  totalScore >= 50 -> REVIEW (payment proceeds, flagged)
  totalScore < 50  -> ALLOW

Persist fraud_check. Return decision to payment-service. Target: < 5ms p99.

---

## FRAUD SERVICE — STAGE 2: SPRING AI

Spring AI config:
  spring.ai.openai:
    api-key: ${OPENAI_API_KEY}
    chat.options:
      model: ${AI_MODEL:gpt-4o-mini}
      temperature: 0.1
      max-tokens: 250

Never log the API key. Use @ConfigurationProperties for binding.
Test profile: spring.ai.mock.enabled=true

System prompt — stored in application.yml, NOT in Java code:
  paystream.fraud.ai-system-prompt: >
    You are a payment fraud risk analyst. Respond ONLY with valid JSON.
    Schema: { "riskScore": int, "confidence": decimal, "flags": [string], "reasoning": "string max 100 words" }

FraudAiEnrichmentConsumer:
  @KafkaListener(topics="fraud.check.requested")
  @CircuitBreaker(name="ai-enrichment", fallbackMethod="handleAiFallback")

  1. Load fraud_check and user_risk_profiles
  2. Build user prompt from payment + profile data
  3. Call Spring AI ChatClient
  4. Parse and validate JSON response
  5. UPDATE fraud_check: ai_narrative, ai_risk_score, ai_confidence, ai_processed=TRUE
  6. If |aiScore - ruleScore| > 30: log.warn("AI-rules disagreement ...")
  7. Publish FraudScoreComputedEvent

  handleAiFallback:
    log.warn("AI circuit open eventId={}", ...)
    UPDATE fraud_check SET ai_processing_error = error message
    (Nightly @Scheduled retries ai_processed=FALSE records)

Cost optimisation: only publish to fraud.check.requested if riskScore >= 30 OR flags non-empty.

---

## FRAUD SERVICE — REST ENDPOINTS

POST /api/v1/fraud/check [INTERNAL]              — Stage 1 sync. < 10ms SLA.
GET  /api/v1/fraud/payments/{id} [FRAUD_ANALYST] — full analysis incl. ai_narrative
GET  /api/v1/fraud/users/{id}/history [FRAUD_ANALYST]
PUT  /api/v1/fraud/users/{id}/block [FRAUD_ANALYST]  — SET "user:blocked:{userId}" in Redis
DELETE /api/v1/fraud/users/{id}/block [ADMIN]        — DEL from Redis

payment-service checks "user:blocked:{userId}" at payment initiation.

---

## AUDIT SERVICE — FULL IMPLEMENTATION

Database (audit_db) — append-only, partitioned by month:
  CREATE TABLE audit_log (
    id              VARCHAR(26)  PRIMARY KEY,
    event_id        VARCHAR(26)  NOT NULL UNIQUE,
    event_type      VARCHAR(100) NOT NULL,
    entity_id       VARCHAR(26)  NOT NULL,
    entity_type     VARCHAR(50)  NOT NULL,
    actor_id        VARCHAR(26),
    actor_role      VARCHAR(50),
    action          VARCHAR(100) NOT NULL,
    old_state       JSONB,
    new_state       JSONB,
    metadata        JSONB,
    correlation_id  VARCHAR(255),
    source_service  VARCHAR(50)  NOT NULL,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
  ) PARTITION BY RANGE (created_at);

AuditEventConsumer:
  @KafkaListener(topics="audit.events")
  INSERT INTO audit_log ON CONFLICT (event_id) DO NOTHING  -- idempotent

Every service publishes AuditEvent for:
  user: registered, logged in, logged out, locked
  payment: created, completed, failed, refunded
  wallet: created, debited, credited, frozen
  fraud: check performed, user blocked/unblocked

REST [ADMIN or FINANCE_OPS]: GET /audit/logs (filtered), GET /audit/logs/{eventId}

---

## RECONCILIATION JOB (settlement-service)

  @Scheduled(cron="0 30 3 * * *", zone="Asia/Kolkata")
  void reconcile():
    For each SETTLED batch (settled_at >= yesterday 00:00):
      expected = SUM(si.amount) FROM settlement_items WHERE batch_id=?
      actual   = batch.gross_amount
      if expected != actual:
        log.error("RECONCILIATION DISCREPANCY batchId={} expected={} actual={}", ...)
        INSERT INTO reconciliation_alerts(batchId, "AMOUNT_MISMATCH", expected, actual)
        increment metric "settlement.reconciliation.discrepancies.total"
      else:
        UPDATE settlement_batches SET reconciled=TRUE

  CREATE TABLE reconciliation_alerts (
    id               VARCHAR(26) PRIMARY KEY,
    batch_id         VARCHAR(26),
    discrepancy_type VARCHAR(50) NOT NULL,
    expected_amount  BIGINT,
    actual_amount    BIGINT,
    description      TEXT,
    resolved         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );

---

## OBSERVABILITY

OpenTelemetry tracing (all services):
  micrometer-tracing-bridge-otel + opentelemetry-exporter-zipkin
  management.tracing.sampling.probability: 1.0 (dev), 0.1 (prod)
  management.zipkin.tracing.endpoint: http://zipkin:9411/api/v2/spans

Add to docker-compose:
  zipkin: openzipkin/zipkin:3 (port 9411)
  prometheus: prom/prometheus:v2.51.0 (port 9090), scrape all services every 15s
  grafana: grafana/grafana:10.3.0 (port 3000), provisioned datasource + dashboards

Custom metrics (exact names):
  Counters:
    paystream.payments.initiated.total     tags: currency
    paystream.payments.completed.total     tags: currency
    paystream.payments.failed.total        tags: failureCode
    paystream.fraud.checks.total           tags: decision
    paystream.fraud.blocks.total           tags: topFlag
    paystream.kafka.dlq.messages.total     tags: topic
  Histograms (percentiles 0.5, 0.95, 0.99):
    paystream.payment.duration.seconds
    paystream.fraud.rules.duration.ms
    paystream.fraud.ai.duration.ms
  Gauges:
    paystream.outbox.pending.count
    paystream.kafka.consumer.lag           tags: topic, consumerGroup

Grafana dashboards (provision 3):
  payment-operations: payment rate, success %, p99 latency, outbox lag
  system-health: circuit breakers, Kafka lag, JVM heap, HTTP error rate
  fraud-overview: block rate, top flags, AI vs rules score, AI error rate

Resilience4j (payment-service):
  wallet-service: slidingWindowSize=10, failureRateThreshold=50, waitDuration=10s
  fraud-service:  slidingWindowSize=10, failureRateThreshold=60, waitDuration=15s

  walletFallback: throw ExternalServiceException(PS-5002, "Wallet service unavailable")
  fraudFallback:  return FraudCheckResult.allow(0), log.warn("Fraud circuit open — rules-only ALLOW")

---

## TESTS REQUIRED

FraudRuleEngineTest (unit, mocked Redis):
  amount > 500000 -> HIGH_AMOUNT flag, weight added
  velocity > 10 -> HIGH_VELOCITY
  unknown device -> NEW_DEVICE
  all three -> BLOCK decision (sum >= 80)
  no flags -> ALLOW score=0
  rules cached: second call reads Redis, no DB query

FraudAiEnrichmentTest (unit, mock ChatClient):
  valid JSON -> ai_narrative set, ai_processed=TRUE
  malformed response -> ai_processing_error set, no exception
  ChatClient throws -> circuit fallback fires, metric incremented
  |aiScore - ruleScore| > 30 -> warn log emitted

AuditConsumerIdempotencyTest:
  same eventId twice -> one audit_log row

CircuitBreakerTest:
  5/10 wallet calls fail -> circuit opens -> fallback exception
  wait duration -> half-open -> success -> circuit closes
  fraud circuit open -> payment proceeds with rules-only ALLOW

ReconciliationTest:
  matching totals -> reconciled=TRUE, no alert
  mismatch -> reconciliation_alert created, metric incremented

---

## SELF-VERIFICATION BEFORE FINISHING

  [ ] AI score NEVER changes a payment's status
  [ ] System prompt in application.yml, not Java String literal
  [ ] AI call has @CircuitBreaker with fallback that does not rethrow uncontrolled
  [ ] processedEventsRepo.existsById() called before every audit insert
  [ ] Outbox gauge counts WHERE published=FALSE
  [ ] Zipkin in docker-compose
  [ ] Grafana dashboards in docker/ folder, provisioned via volume
  [ ] All Redis keys use constant strings from RedisKeys class
  [ ] Reconciliation creates alert on ANY non-zero discrepancy
  [ ] AI topic only published if riskScore >= 30 or flags non-empty


=============================================================
PAYSTREAM — MILESTONE 5: PRODUCTION HARDENING & DEVOPS
=============================================================

## YOUR ROLE AND CONTEXT

You are the same Staff+ engineer. All 4 milestones complete and
all their tests pass. This milestone adds ZERO new features.
Make everything already built production-worthy.
If you find yourself adding an endpoint, stop.

---

## READ FIRST — MANDATORY

1. Confirm all M1-M4 tests pass. Fix anything broken first.
2. Read all @RestController classes — you will audit every endpoint.
3. Read docker-compose.yml — cross-check against K8s manifests you'll produce.
4. Read all application.yml files — audit for hardcoded secrets.

---

## SECTION 1: SECURITY HARDENING

HTTP headers (API Gateway GlobalFilter, add to every response):
  Strict-Transport-Security: max-age=31536000; includeSubDomains
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  Referrer-Policy: no-referrer
  Cache-Control: no-store, no-cache
  Content-Security-Policy: default-src 'none'

Validation audit — every controller endpoint:
  @RequestBody has @Valid
  String @PathVariable has @Pattern(regexp="[0-9A-HJKMNP-TV-Z]{26}")
  Amount fields have @Positive and @Max(100_000_000L)
  Currency has @Pattern(regexp="[A-Z]{3}")
  Free text has @Size(max=500)
  Add any missing. Document each in a comment.

Log injection audit — every log statement:
  GOOD: log.warn("Login failed correlationId={} email={}", correlationId, email);
  BAD:  log.warn("Login failed for " + email);  // concat = log injection risk
  For user-supplied strings: sanitize newlines before logging:
    String safeEmail = email.replaceAll("[\\r\\n]", "_");

Secrets audit:
  grep -rn --include="*.java" --include="*.yml" -E '(password|secret|api.key)\s*[:=]\s*[^$\{]' src/
  Every hit not reading from env var is a violation. Fix all.

  .gitignore additions: *.env, application-local.yml, *.pem, *.p12, *.key

Rate limiting additions:
  /api/v1/auth/login:    5 req/min per IP
  /api/v1/auth/register: 3 req/min per IP
  /api/v1/payments POST: 50 req/min per userId
  /api/v1/fraud/check:   200 req/min service-wide
  All return 429 with Retry-After header.

RBAC matrix — create docs/SECURITY.md table, verify @PreAuthorize on every endpoint. Fix gaps.

---

## SECTION 2: END-TO-END TEST SUITE

Module: paystream-e2e-tests
Dependencies: Spring Boot Test, Testcontainers compose, RestAssured, WireMock, Awaitility
Tests call API Gateway at http://localhost:8080. No mocking.

### PaymentFlowE2ETest

Scenario 1 — Happy path:
  register userA (CUSTOMER), register userB
  create wallets, fund walletA via admin top-up
  POST /payments (A->B, amount=100000, idempotency="e2e-pay-001")
  poll GET /payments/{id} until status=COMPLETED (Awaitility, timeout 10s)
  assert walletA balance = fundedAmount - 100000
  assert walletB balance = 100000
  assert GET /ledger/transactions/{paymentId} -> 2 entries, sumCheck=0
  assert GET /audit/logs?entityId={paymentId} -> audit entry exists

Scenario 2 — Idempotency:
  POST /payments with SAME key "e2e-pay-001"
  assert same paymentId, same response, balances unchanged, no new ledger entries

Scenario 3 — Insufficient funds:
  POST /payments amount = walletA.balance + 1
  assert 422 PS-2001, wallets unchanged, no ledger entries

Scenario 4 — Concurrent payments (race condition / double-spend guard):
  Fund walletA with exactly 200000
  Fire 5 payments of 100000 each simultaneously:
    List<CompletableFuture<Response>> futures = IntStream.range(0,5)
      .mapToObj(i -> CompletableFuture.supplyAsync(() ->
        given().body(req(walletA, walletB, 100000, "race-"+i)).post("/api/v1/payments")))
      .collect(toList());
  assert exactly 2 responses with 201
  assert exactly 3 responses with 422 PS-2001
  assert walletA balance == 0 (never negative)
  assert all ledger entries sum to zero per referenceId

Scenario 5 — Refund:
  Complete payment from Scenario 1
  POST /payments/{id}/refund body={amount:100000}
  poll until refund COMPLETED
  assert walletA balance == original funded amount
  assert /ledger/transactions/{refundId} -> 2 entries sum=0
  assert /ledger/accounts/{walletA}/entries -> 4 entries total

Scenario 6 — Webhook delivery:
  Start WireMock server on random port
  POST /webhooks/endpoints as MERCHANT: url=wireMock.baseUrl+"/webhook"
  Complete a payment
  Awaitility: WireMock.verifyThat(1, postRequestedFor(urlEqualTo("/webhook"))) within 15s
  Verify X-PayStream-Signature header present and valid (recompute HMAC in test)

### AuthSecurityE2ETest

Lockout: POST /login wrong password x5 -> 423
  Wait TTL expiry (use short lockout in e2e profile, e.g. 5s)
  Login with correct password -> 200

Token lifecycle:
  Login -> T1, R1
  Logout with T1
  GET /me with T1 -> 401 (blocklisted)
  Refresh with R1 -> 401 (revoked on logout)

Refresh rotation:
  Login -> R1
  Refresh with R1 -> T2, R2 (R1 revoked)
  Refresh with R1 again -> 401

### SettlementE2ETest

  Complete 5 payments to merchantB wallet
  POST /settlements/trigger (FINANCE_OPS)
  Poll GET /settlements?merchantId={merchantB}&status=SETTLED within 30s
  assert batch.grossAmount == sum of 5 payments
  assert batch.feeAmount == grossAmount * 0.01
  assert batch.netAmount == grossAmount - feeAmount

---

## SECTION 3: PERFORMANCE TESTING (k6)

Create: k6/

k6/scripts/payment-load-test.js:
  Setup: register 500 users, create wallets, fund each with 10000000
  Test: 500 VUs, 5 minutes, each VU posts 1 payment per 2s
  Thresholds (fail if breached):
    http_req_duration p(99) < 500
    http_req_duration p(95) < 250
    http_req_failed < 0.01
    checks > 0.99
  Checks per iteration:
    status === 201 or 200
    response has paymentId
    X-Correlation-Id header present

k6/scripts/fraud-rules-load-test.js:
  100 VUs, 2 min. Threshold: p(99) < 10ms

k6/PERFORMANCE_BASELINE.md:
  Date, environment specs, TPS, p50/p95/p99, error rate, Kafka lag during test

---

## SECTION 4: KUBERNETES MANIFESTS

Create: k8s/ directory

Per-service (for all 9 services): deployment.yml, service.yml, hpa.yml, pdb.yml

deployment.yml:
  replicas: 2
  strategy: RollingUpdate, maxSurge=1, maxUnavailable=0
  resources: requests {cpu:250m, memory:512Mi} limits {cpu:1000m, memory:1Gi}
  env: JAVA_OPTS: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
  livenessProbe: /actuator/health/liveness initialDelay=45 period=10 failure=3
  readinessProbe: /actuator/health/readiness initialDelay=20 period=5 failure=3
  All secrets from secretKeyRef. All config from configMapKeyRef.

hpa.yml: minReplicas=2, maxReplicas=10, targetCPU=70%
pdb.yml: minAvailable=1

Shared (k8s/shared/):
  Namespace: paystream
  ConfigMap: paystream-config (EUREKA_SERVER_URL, KAFKA_BOOTSTRAP_SERVERS, etc.)
  Secret: paystream-secrets (DB_PASSWORD, REDIS_PASSWORD, JWT keys, OPENAI_API_KEY)
    Comment: use External Secrets Operator in real production

Ingress: paystream.local -> api-gateway, TLS self-signed
Stateful services (Postgres/Redis/Kafka): comment pointing to Bitnami Helm charts + RDS/ElastiCache/MSK note

---

## SECTION 5: GITHUB ACTIONS

Create: .github/workflows/ci.yml

Job: lint (every push + PR to main/develop)
  Checkstyle (google_checks.xml), SpotBugs + FindSecBugs, detect-secrets

Job: test (after lint)
  mvn test (unit only)
  JaCoCo — fail if line coverage < 80% on com.paystream.**
  Exclude: MapStruct generated, @Configuration, main methods

Job: integration-test (after test)
  mvn verify -P integration-tests (Testcontainers, needs Docker)

Job: build (after integration-test)
  mvn package -DskipTests
  docker build + push to GHCR
  Tags: {service}:{sha}, {service}:latest (latest on main only)

Job: security-scan (after build, parallel)
  OWASP Dependency Check — fail on CVSS >= 7.0
  Trivy — fail on CRITICAL
  .trivyignore for accepted CVEs with documented justification

Job: e2e-test (after build, PRs to main only)
  docker-compose up -> mvn test -pl paystream-e2e-tests -> docker-compose down

Job: deploy-dev (push to develop only)
  kubectl set image, rollout status, smoke test /actuator/health

Job: deploy-prod (push to main, manual approval required)
  environment: production with required reviewers
  Blue-green deploy
  Auto-rollback if readiness probe fails within 60s

---

## SECTION 6: CODE QUALITY GATES

JaCoCo: 80% line, 75% branch on domain/ + application/
Checkstyle: max line 120, JavaDoc on public methods in domain/ and application/
OWASP: failBuildOnCVSS=7, owasp-suppressions.xml with documented reasons per CVE

---

## SECTION 7: DOCUMENTATION

README.md — 4-command quick start:
  git clone ... && cd paystream
  cp .env.example .env
  docker compose -f docker/docker-compose.yml up -d

URLs: Gateway :8080, Kafka UI :8090, Grafana :3000, Mailhog :8025, Zipkin :9411

Sections: architecture overview, service map table, env vars reference,
running tests, development workflow, ADR index

docs/ADR/ — 6 ADRs (Title, Status, Context, Decision, Consequences):
  ADR-001-ulid-ids.md
  ADR-002-double-entry.md
  ADR-003-outbox-pattern.md
  ADR-004-fraud-determinism.md
  ADR-005-money-as-bigint.md
  ADR-006-hexagonal.md

---

## PRODUCTION READINESS CHECKLIST

Create PRODUCTION_READINESS.md. Every item must be checked before done.

SECURITY:
  [ ] No secret committed to git (grep passes)
  [ ] All endpoints: authenticated or in explicit permitAll
  [ ] JWT: expire (15min) and revocable (blocklist)
  [ ] Passwords: BCrypt strength 12
  [ ] HTTP security headers on every response
  [ ] Rate limiting on login, register, payment POST
  [ ] All user input logged via {} parameters

RELIABILITY:
  [ ] Circuit breakers on wallet-service and fraud-service
  [ ] Retry with backoff on optimistic lock
  [ ] DLQ for every Kafka consumer
  [ ] Outbox pending count metric + documented alert threshold
  [ ] Graceful shutdown: server.shutdown: graceful, timeout: 30s

DATA INTEGRITY:
  [ ] All money: BIGINT minor units, never double or float
  [ ] Ledger UNIQUE index for Kafka dedup
  [ ] Wallet CHECK constraint: balance >= 0
  [ ] Idempotency: Redis + DB UNIQUE constraint (two layers)
  [ ] Optimistic lock: version column + retry

OBSERVABILITY:
  [ ] JSON structured logging in non-local profiles
  [ ] Correlation ID in every log line via MDC
  [ ] Distributed traces visible in Zipkin
  [ ] Prometheus metrics on all services
  [ ] Grafana 3 dashboards loading data

DEPLOYMENT:
  [ ] Rolling update maxUnavailable=0 on all Deployments
  [ ] PodDisruptionBudget minAvailable=1
  [ ] HPA on all services
  [ ] JAVA_OPTS uses UseContainerSupport
  [ ] Secrets in Kubernetes Secrets, not ConfigMaps

---

## SELF-VERIFICATION BEFORE FINISHING

  [ ] All M1-M4 tests still pass
  [ ] docker compose up -> full stack healthy in < 90 seconds
  [ ] 4-command quick start in README works on a clean checkout
  [ ] All 6 ADRs written with Status: Accepted
  [ ] PRODUCTION_READINESS.md: every item checked
  [ ] ci.yml: valid YAML, correct job dependency order
  [ ] k8s/: one folder per service, each with deployment + service + hpa + pdb
  [ ] Zero new features added
