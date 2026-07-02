# PayStream — Project Documentation

> Consolidated documentation covering all five milestones: Foundation & Auth, Payments/Wallets/Ledger, Kafka Event-Driven Architecture, Fraud Detection/Spring AI/Observability, and Production Hardening & DevOps.

---

# Milestone 1 — Foundation, Architecture & Auth

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

### 6. `docker/docker-compose.yml`
6 isolated PostgreSQL instances + Redis + Kafka (KRaft) + Kafka UI + Eureka + Gateway + Auth Service.
Every service has `healthcheck` and `depends_on: condition: service_healthy`. No plaintext passwords.

### Milestone 1 Self-Verification Checklist

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

# Milestone 2 — Payments, Wallets & Ledger

## What Was Built

Full production-grade implementation of the three financial domain services: wallet-service, ledger-service, and payment-service. This milestone establishes the core financial mechanics of PayStream.

---

## paystream-wallet-service

### Architecture
```
domain/
  model/         Wallet.java (pure Java), WalletStatus enum
  exception/     WalletAlreadyExistsException
application/
  port/in/       WalletUseCase
  port/out/      WalletRepository
  command/       DebitCreditCommand
  service/       WalletApplicationService (with optimistic lock retry)
api/
  controller/    WalletController
  dto/request/   CreateWalletRequest, DebitCreditRequest
  dto/response/  WalletResponse
infrastructure/
  persistence/
    entity/      WalletEntity (JPA, @Version)
    repository/  WalletJpaRepository
    adapter/     WalletPersistenceAdapter
  config/        SecurityConfig, WebConfig
security/
  InternalServiceAuthFilter
```

### Key Design Decisions

**Wallet Domain Model** (`Wallet.java`):
- Pure Java, zero framework annotations — enforces DDD discipline
- All state mutation via `debit()`, `credit()`, `freeze()`, `unfreeze()` — business rules encoded in the entity
- `debit()` throws `InsufficientFundsException` if `balance < amount` (also enforced by DB CHECK constraint `balance >= 0`)
- `debit()`/`credit()` throw `DomainException(INVALID_STATE_TRANSITION)` if wallet is not ACTIVE

**Optimistic Locking Retry** (`WalletApplicationService`):
- JPA `@Version` on `WalletEntity` provides OCC
- On `OptimisticLockingFailureException`: retry up to 3 times with 50ms → 100ms exponential backoff
- After 3 failures: throw `DomainException(CONCURRENT_MODIFICATION)`

**Internal Service Security** (`InternalServiceAuthFilter`):
- Debit and credit endpoints require `X-Internal-Service-Key` header
- Key injected from env var `${INTERNAL_SERVICE_KEY:-dev-only-local-key}`
- Missing/wrong key returns 403 immediately — filter short-circuits

### REST Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/wallets` | X-User-Id | Create wallet (one per user per currency) |
| GET | `/api/v1/wallets/my?currency=INR` | X-User-Id | Get authenticated user's wallet |
| GET | `/api/v1/wallets/{walletId}` | X-User-Id | Get wallet by ID |
| POST | `/api/v1/wallets/{walletId}/debit` | X-Internal-Service-Key | Debit wallet (internal) |
| POST | `/api/v1/wallets/{walletId}/credit` | X-Internal-Service-Key | Credit wallet (internal) |
| POST | `/api/v1/wallets/{walletId}/freeze` | X-User-Id | Freeze wallet |
| POST | `/api/v1/wallets/{walletId}/unfreeze` | X-User-Id | Unfreeze wallet |

### Database Schema

```sql
-- V1__create_wallets.sql
wallets(id VARCHAR(26) PK, user_id, balance BIGINT, currency CHAR(3),
        status, version INT, created_at, updated_at,
        CONSTRAINT balance_non_negative CHECK (balance >= 0),
        UNIQUE (user_id, currency))

-- V2__create_wallet_holds.sql
wallet_holds(id, wallet_id FK, amount, reason, expires_at, released BOOLEAN, created_at)
```

---

## paystream-ledger-service

### Architecture
```
domain/
  model/         LedgerEntry (immutable), EntryType enum
application/
  port/in/       LedgerUseCase
  port/out/      LedgerEntryRepository
  service/       LedgerApplicationService
api/
  controller/    LedgerController
  dto/request/   DoubleEntryRequest, LedgerEntryRequest
  dto/response/  LedgerEntryResponse, BalanceResponse, TransactionResponse
infrastructure/
  persistence/
    entity/      LedgerEntryEntity (NO updatable fields — append-only)
    repository/  LedgerEntryJpaRepository (with native SQL balance query)
    adapter/     LedgerPersistenceAdapter
  config/        SecurityConfig, WebConfig
security/
  InternalServiceAuthFilter
```

### Key Design Decisions

**Append-Only Ledger**:
- `LedgerEntryEntity` has `updatable = false` on ALL columns — JPA cannot update any column
- No `updated_at`, no `deleted_at` — by design and enforced in the SQL schema comments
- UNIQUE index on `(reference_id, account_id, entry_type)` provides DB-level idempotency

**Double-Entry Invariant** (`LedgerApplicationService.createDoubleEntry()`):
```
Validation: debit.amount + credit.amount == 0
If invalid: throw DomainException — nothing is persisted
If valid: both entries saved in single @Transactional call
```
Amount convention: DEBIT entries carry negative amounts, CREDIT entries positive.

**Balance Computation** (native SQL, no memory loading):
```sql
-- Uses snapshot + incremental approach for O(entries since snapshot) instead of O(all entries)
SELECT COALESCE(
  snapshot.balance + SUM(entries after snapshot),
  SUM(all entries)    -- fallback when no snapshot exists
)
```

**Idempotency**: Before persisting, checks `existsByReferenceIdAndAccountIdAndEntryType()`. If both legs exist → returns silently (safe replay).

**Integrity Validation**: `getTransaction(referenceId)` returns `integrityValid=true` when `SUM(amounts) == 0`. Corrupted ledgers log `LEDGER INTEGRITY FAILURE` at ERROR level.

### REST Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/ledger/entries` | X-Internal-Service-Key | Create balanced double entry |
| GET | `/api/v1/ledger/accounts/{accountId}/balance` | Bearer | Current balance |
| GET | `/api/v1/ledger/accounts/{accountId}/entries` | Bearer | Paginated entry history |
| GET | `/api/v1/ledger/transactions/{referenceId}` | Bearer | Both legs + integrity flag |

### Database Schema

```sql
-- V1__create_ledger_entries.sql
ledger_entries(id, account_id, entry_type VARCHAR(6), amount BIGINT (signed),
               currency, reference_id, reference_type, description, created_at TIMESTAMPTZ)
UNIQUE INDEX idx_ledger_idempotency ON (reference_id, account_id, entry_type)
INDEX idx_ledger_account_time ON (account_id, created_at DESC)
INDEX idx_ledger_reference ON (reference_id)

-- V2__create_balance_snapshots.sql
account_balance_snapshots(id, account_id UNIQUE, balance, snapshot_at, entry_count, created_at)
```

---

## paystream-payment-service

### Architecture
```
domain/
  model/         Payment.java (pure Java, state machine), PaymentStatus enum
application/
  port/in/       PaymentUseCase
  port/out/      PaymentRepository
  service/       PaymentApplicationService (full orchestration)
api/
  controller/    PaymentController
  dto/request/   InitiatePaymentRequest, RefundRequest
  dto/response/  PaymentResponse
infrastructure/
  persistence/
    entity/      PaymentEntity (JPA, @Version)
    repository/  PaymentJpaRepository
    adapter/     PaymentPersistenceAdapter
  external/
    WalletServiceClient (Feign + FallbackFactory)
    LedgerServiceClient (Feign + FallbackFactory)
    FeignConfig (timeouts, retry)
  config/        SecurityConfig, FeignClientConfig, WebConfig
```

### Payment State Machine

```
PENDING ──────→ PROCESSING ──→ COMPLETED ──→ REFUNDED
   │                └──────→ FAILED
   └───────────────────────→ CANCELLED
```

Enforced in `Payment.transitionTo(PaymentStatus next)` via an immutable transition map. Invalid transitions throw `DomainException(INVALID_STATE_TRANSITION)`.

### Idempotency (Two-Layer)

| Redis Value | Action |
|-------------|--------|
| absent | Proceed — SET "PROCESSING", persist PENDING |
| "PROCESSING" | Return 422 IDEMPOTENCY_CONFLICT (in-flight) |
| "COMPLETED" | Replay from DB — return cached result, skip wallet/ledger |

TTL: 24 hours. Second layer: DB UNIQUE(user_id, idempotency_key) prevents races where Redis key expires before payment is committed.

### Payment Orchestration (`PaymentApplicationService.initiatePayment()`)

```
1. Idempotency check (Redis)
2. Persist PENDING + SET Redis "PROCESSING"
3. Fraud check placeholder (returns ALLOW — replaced in M4)
4. debitWallet(source)
   └─ on failure: markFailed, DEL Redis, rethrow
5. creditWallet(destination)
   └─ on failure: compensate (credit source back), markFailed, DEL Redis, rethrow
6. transitionTo(PROCESSING) → transitionTo(COMPLETED), save, SET Redis "COMPLETED"
7. createDoubleEntry in ledger (synchronous — moves to Kafka in M3)
   └─ on failure: log CRITICAL "LEDGER INCONSISTENCY" — do NOT reverse wallets
```

### Feign Clients

Both clients have:
- `connectTimeout=2s`, `readTimeout=5s`
- `FallbackFactory` logs with `correlationId` and throws `ExternalServiceException`
- Ledger fallback returns `null` (payment already COMPLETED, cannot undo wallets)

### REST Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/payments` | X-User-Id + X-Idempotency-Key | Initiate payment |
| GET | `/api/v1/payments/{paymentId}` | X-User-Id | Get own payment |
| GET | `/api/v1/payments?page=&size=` | X-User-Id | List own payments |
| POST | `/api/v1/payments/{paymentId}/refund` | X-User-Id | Partial/full refund |
| GET | `/api/v1/payments/admin/all` | ADMIN role | List all payments |

### Database Schema

```sql
-- V1__create_payments.sql
payments(id, user_id, idempotency_key, source_wallet_id, destination_wallet_id,
         amount BIGINT, currency, status, failure_reason, failure_code, note,
         metadata JSONB, version INT, created_at, updated_at,
         UNIQUE (user_id, idempotency_key))

-- V2__create_outbox.sql
outbox_events(id, aggregate_id, aggregate_type, event_type, payload JSONB,
              published BOOLEAN, published_at, created_at)
INDEX idx_outbox_unpublished WHERE published = FALSE
```

---

## Docker Compose Updates

Three new services added with full healthchecks and `depends_on: service_healthy`:
- `payment-service` (port 8082) → `postgres-payment:5433`
- `wallet-service` (port 8083) → `postgres-wallet:5434`
- `ledger-service` (port 8084) → `postgres-ledger:5435`

All three services share `INTERNAL_SERVICE_KEY` for internal endpoint authentication.

---

## Tests Written

| Test Class | Type | What It Covers |
|-----------|------|----------------|
| `PaymentDomainTest` | Unit | All state machine transitions (allowed + rejected), markFailed, version increments |
| `WalletDomainTest` | Unit | debit/credit/freeze/unfreeze invariants, InsufficientFundsException, terminal states |
| `LedgerApplicationServiceTest` | Unit (Mockito) | Double-entry invariant (sum=0 vs sum≠0), idempotency, integrity validation |
| `WalletApplicationServiceTest` | Unit (Mockito) | createWallet duplicate, debit/credit success+failure paths, frozen wallet |
| `PaymentApplicationServiceTest` | Unit (Mockito) | Happy path, idempotent replay, PROCESSING conflict, debit/credit failure, compensation, ledger failure after COMPLETED |
| `WalletRepositoryIntegrationTest` | Integration (Testcontainers) | Flyway migrations, save+retrieve, duplicate UNIQUE constraint rejection |
| `MoneyValueObjectTest` | Unit | Arithmetic, immutability, currency mismatch, negative amounts, invalid ISO codes |

---

## Architecture Decisions

### ADR-001: One wallet per user per currency
The UNIQUE(user_id, currency) constraint enforces this at the DB level (not just application). Rationale: prevents accidental duplicate wallets from concurrent requests even if application-level checks race.

### ADR-002: Signed amounts in ledger
DEBIT entries store negative amounts, CREDIT entries positive. This allows balance to be computed as `SUM(amount)` with no conditional logic. The double-entry invariant becomes: `SUM(all amounts for referenceId) == 0`.

### ADR-003: Compensation on credit failure
If `creditWallet(destination)` fails after `debitWallet(source)` succeeds, we immediately credit the source back. This is synchronous compensation — not a saga. Rationale: M2 has no Kafka yet. This is an acceptable trade-off documented here as tech debt for M3.

### ADR-004: Ledger failure does not reverse wallets
Once a payment reaches COMPLETED (wallets moved), a ledger write failure is logged as CRITICAL but does NOT trigger wallet reversal. Rationale: the money has moved correctly; the ledger is a record of truth, not the source of truth for balances. A manual reconciliation job (M4) will detect and alert on any such inconsistency.

### ADR-005: Internal service auth via shared key (not JWT)
Debit/credit/ledger POST endpoints require `X-Internal-Service-Key`. This is a pre-shared symmetric key, not a JWT. Rationale: inter-service calls within the same trust boundary do not need the full JWT overhead. The gateway never forwards this header externally. In production, this would be replaced with mTLS (noted as tech debt).

---

## Assumptions Made

1. **Currency validation**: All currencies validated against Java's `Currency.getInstance()` (ISO 4217). INR is the default.
2. **Gateway header injection**: All services assume the API Gateway has already validated the JWT and injected `X-User-Id` and `X-User-Role` headers. Services do not independently validate JWTs.
3. **Partial refund**: Refund amount is provided in the request body. The service does not enforce `refundAmount <= originalAmount` in M2 (noted as tech debt — add in M3 or M5 hardening).

---

## Technical Debt

| Item | Impact | Planned Resolution |
|------|--------|-------------------|
| Compensation is synchronous — if the compensation credit call also fails, funds are temporarily lost | High | Replace with Saga/outbox pattern in M3 |
| Ledger write is synchronous from payment-service | Medium | Replace with Kafka in M3 (outbox relay) |
| mTLS instead of shared key for inter-service auth | Medium | M5 production hardening |
| Partial refund amount validation | Low | M5 or dedicated refund service |
| Balance snapshots are never updated (no scheduler) | Low | Add scheduled snapshot job in M3/M4 |

---

## Milestone 2 Self-Verification Checklist

- [x] `balance_non_negative` CHECK constraint in Flyway SQL
- [x] Wallet domain entity has no `@Entity` annotation
- [x] Optimistic lock retry has exponential backoff (50ms × attempt)
- [x] Ledger UNIQUE index `(reference_id, account_id, entry_type)` exists
- [x] `createDoubleEntry` validates sum == 0 before persisting
- [x] Compensation fires if credit fails
- [x] Ledger failure after COMPLETED logs LEDGER_INCONSISTENCY but does not reverse wallets
- [x] Idempotency repeat does not invoke Feign clients
- [x] All money amounts use `long` (minor units), never `double`
- [x] No controller method has `if/else` or repository calls

---

## How to Run Milestone 2

```bash
# 1. Start all infrastructure
docker compose -f docker/docker-compose.yml up -d \
  postgres-payment postgres-wallet postgres-ledger redis eureka-server

# 2. Build common library
mvn install -pl paystream-common -am -DskipTests

# 3. Run wallet service
mvn spring-boot:run -pl paystream-wallet-service

# 4. Run ledger service (separate terminal)
mvn spring-boot:run -pl paystream-ledger-service

# 5. Run payment service (separate terminal)
mvn spring-boot:run -pl paystream-payment-service

# 6. Run all Milestone 2 tests
mvn test -pl paystream-wallet-service,paystream-ledger-service,paystream-payment-service,paystream-common
```

### Sample API Calls

```bash
# Create a wallet
curl -X POST http://localhost:8080/api/v1/wallets \
  -H "X-User-Id: USER01" \
  -H "Content-Type: application/json" \
  -d '{"currency": "INR"}'

# Initiate a payment
curl -X POST http://localhost:8080/api/v1/payments \
  -H "X-User-Id: USER01" \
  -H "X-Idempotency-Key: pay-2024-001" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceWalletId": "W01",
    "destinationWalletId": "W02",
    "amount": 100000,
    "currency": "INR",
    "note": "Test payment"
  }'

# Get ledger balance
curl http://localhost:8080/api/v1/ledger/accounts/W01/balance
```

---

# Milestone 3 — Kafka Event-Driven Architecture

## What Was Built

Kafka event streaming, the outbox relay pattern, and three new services: `notification-service`, `settlement-service`, and `webhook-service`. The synchronous ledger call from payment-service is replaced by async Kafka consumption.

---

## What Changed vs. What Stayed

| Component | Change |
|-----------|--------|
| `ledger-service` | Replaces REST inbound with Kafka consumer |
| `payment-service` | Removes sync Feign `LedgerServiceClient`; adds outbox relay |
| `wallet-service` | Publishes events after each debit/credit |
| `payment-service → wallet-service` | **Unchanged** — remains synchronous REST |
| Idempotency logic | **Unchanged** |
| Payment state machine | **Unchanged** |
| All existing REST endpoints | **Unchanged** |

---

## Kafka Infrastructure

### Topics

| Topic | Partitions | Key |
|-------|-----------|-----|
| `payments.initiated` | 32 | `userId` |
| `payments.completed` | 32 | `userId` |
| `payments.failed` | 32 | `userId` |
| `wallet.debited` | 32 | `walletId` |
| `wallet.credited` | 32 | `walletId` |
| `fraud.check.requested` | 16 | `paymentId` |
| `fraud.score.computed` | 16 | `paymentId` |
| `notifications.send` | 8 | `userId` |
| `webhooks.delivery` | 8 | `merchantId` |
| `settlements.batch.trigger` | 4 | `merchantId` |
| `audit.events` | 16 | `entityId` |

Partitioned by `userId`/`walletId` so all events for one user arrive at the same consumer thread — critical for ordered ledger processing.

### Producer Config (all services)

```yaml
enable.idempotence: true
acks: all
retries: 2147483647
max.in.flight.requests.per.connection: 5
linger.ms: 5
compression.type: snappy
```

### Consumer Config

```yaml
isolation.level: read_committed
enable.auto.commit: false
max.poll.records: 100
auto.offset.reset: earliest
```

### Event Envelope (`paystream-common`)

```java
record BaseEvent<T>(
  String eventId,       // ULID
  String eventType,
  String eventVersion,  // "1.0"
  Instant timestamp,
  String correlationId,
  String sourceService,
  T payload
) {}
```

Jackson `ObjectMapper` `@Bean`: `JavaTimeModule`, `WRITE_DATES_AS_TIMESTAMPS=false`, `FAIL_ON_UNKNOWN_PROPERTIES=false`.

---

## Outbox Relay — Payment Service

`OutboxRelayService` polls `outbox_events` every 100ms and publishes unpublished rows to Kafka.

**Flow:**

```
1. SELECT ... FROM outbox_events WHERE published=FALSE
   ORDER BY created_at ASC LIMIT 50 FOR UPDATE SKIP LOCKED
   (SKIP LOCKED prevents two relay pods from processing the same row)

2. For each row:
   a. Deserialize payload → event type (from event_type field)
   b. Resolve topic via Map (no if/else chains)
   c. kafkaTemplate.send(...).get(5, SECONDS)  ← synchronous confirm
   d. On success: UPDATE published=TRUE, published_at=NOW()
   e. On failure: log error, leave published=FALSE, continue

3. Each row runs in @Transactional(REQUIRES_NEW) — no single large transaction
```

**Metrics:**
- `Counter: outbox.relay.published.total`
- `Gauge: outbox.pending.count` (SELECT COUNT WHERE published=FALSE)
- `WARN` log if pending count > 1000

---

## Ledger Service — Switch to Kafka

Removed in this milestone:
- `POST /api/v1/ledger/entries` (REST endpoint)
- `LedgerServiceClient` Feign client in payment-service
- Step 7 synchronous ledger call in `PaymentApplicationService`

Added (V3 migration in `ledger_db`):

```sql
CREATE TABLE processed_events (
  event_id     VARCHAR(26)  PRIMARY KEY,
  processed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

### `WalletEventConsumer`

```
@KafkaListener(topics={"wallet.debited","wallet.credited"}, groupId="ledger-service-wallet-events")
@RetryableTopic(attempts="4", backoff=delay=1000 multiplier=2.0 maxDelay=30000, dltTopicSuffix=".dlq")
@Transactional
void consume(BaseEvent<WalletDebitedEvent> event):
  if processedEventsRepo.existsById(event.eventId()) → return  // idempotent
  ledgerApplicationService.createSingleEntry(event.payload())
  processedEventsRepo.save(...)  // same @Transactional
```

`@DltHandler`: logs error, writes to `dead_letter_events` table, increments `kafka.dlq.messages.total` metric.

---

## Wallet Service — Kafka Publishing

Events are published **after** the DB transaction commits, never inside `@Transactional`.

```
// GOOD: after wallet saved
kafkaProducer.publish("wallet.debited", walletId, event)

// BAD: inside @Transactional — if tx rolls back, event is already sent
```

---

## paystream-notification-service

### Database Schema

```sql
notifications(id, user_id, payment_id, type VARCHAR(30),   -- EMAIL, SMS, PUSH
              channel VARCHAR(50),   -- PAYMENT_SUCCESS, PAYMENT_FAILED
              status VARCHAR(20),    -- PENDING, SENT, FAILED
              recipient, subject, body TEXT,
              attempt_count INT, last_attempted_at, sent_at, error_message, created_at)
```

### Event Flow

```
payments.completed / payments.failed
        ↓
  PaymentEventConsumer  (@RetryableTopic attempts=3, backoff=5000ms×6.0, max=5min)
        ↓
  Persist Notification → publish to notifications.send
        ↓
  NotificationSendConsumer (strategy pattern)
    ├── EmailNotificationDispatcher  → JavaMailSender → Mailhog (local: port 1025/8025)
    ├── SmsNotificationDispatcher    → stub (logs only; wire Twilio in prod)
    └── PushNotificationDispatcher   → stub
```

Docker Compose addition: `mailhog/mailhog` (SMTP: 1025, UI: 8025).

---

## paystream-settlement-service

### Database Schema

```sql
settlement_batches(id, merchant_id, status, total_payment_count INT,
                   gross_amount BIGINT, fee_amount BIGINT, net_amount BIGINT,
                   currency, settlement_date DATE, processing_started_at,
                   settled_at, failure_reason, created_at, updated_at)

settlement_items(id, batch_id FK, payment_id, amount BIGINT, fee_amount BIGINT,
                 status, settled_at,
                 UNIQUE(batch_id, payment_id))
```

### Settlement Scheduler

```
@Scheduled(cron="0 0 2 * * *", zone="Asia/Kolkata")
1. Find PENDING batches with settlement_date <= today
2. Mark each PROCESSING (commit first)
3. Process items in micro-batches of 100 — each batch committed independently (checkpoint pattern)
4. fee = gross × 0.01 (1% default)
5. Mark batch SETTLED, publish SettlementCompletedEvent
   On failure: mark FAILED, increment metric
```

### REST Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/settlements/{batchId}` | Bearer | Get batch |
| GET | `/api/v1/settlements` | Bearer | List batches |
| POST | `/api/v1/settlements/trigger` | FINANCE_OPS | Manual trigger |

---

## paystream-webhook-service

### Database Schema

```sql
webhook_endpoints(id, merchant_id, url VARCHAR(2000), secret VARCHAR(255),
                  events TEXT[], active BOOLEAN, created_at)

webhook_deliveries(id, endpoint_id FK, event_type, payload JSONB, status,
                   attempt_count INT, last_response_status, last_response_body,
                   last_error, delivered_at, created_at)
```

### HMAC Signature

```
signingInput = timestamp + "." + jsonBody
signature    = HmacSHA256(secret, signingInput)

Headers:
  X-PayStream-Signature: sha256={signature}
  X-PayStream-Timestamp: {unix_timestamp}
```

### Delivery Retry Strategy

```
@RetryableTopic(attempts="5", backoff=delay=60000 multiplier=5.0 maxDelay=21600000)

2xx  → DELIVERED
4xx  → EXHAUSTED immediately (throw NO exception — no Kafka retry)
5xx/timeout → throw exception → Kafka retry
```

### REST Endpoints (MERCHANT role)

`POST/GET/DELETE /api/v1/webhooks/endpoints`, `GET /api/v1/webhooks/deliveries`, `POST retry`

---

## Tests Written

| Test Class | Type | What It Covers |
|-----------|------|----------------|
| `OutboxRelayTest` | Integration (Testcontainers Kafka + Postgres) | Relay publishes; Kafka down keeps row unpublished; SKIP LOCKED — published once only |
| `WalletEventConsumerTest` | Integration (EmbeddedKafka) | Single ledger entry; same eventId twice → one entry; malformed JSON → DLQ after 4 attempts |
| `SettlementSchedulerTest` | Integration | 5 payments → SETTLED with correct totals; already-SETTLED items not double-processed |
| `WebhookDeliveryTest` | Integration (WireMock) | 200 → DELIVERED + valid HMAC; 500 → retry; 400 → EXHAUSTED immediately |

---

## Architecture Decisions

### ADR-006: Outbox pattern for at-least-once delivery
The `outbox_events` table in `payment_db` is written in the same transaction as the payment state change. The relay reads it with `FOR UPDATE SKIP LOCKED` and only marks `published=TRUE` after Kafka confirms delivery. Guarantees at-least-once delivery even if the application crashes between the DB commit and the Kafka send.

### ADR-007: Ledger events via Kafka, wallet debit via REST
Payment → wallet-service remains synchronous REST because debit failure must immediately abort the payment. Ledger recording is fire-and-forget from the payment's perspective; Kafka + consumer idempotency (`processed_events` table) gives equivalent consistency guarantees without blocking the payment flow.

---

## Milestone 3 Self-Verification Checklist

- [x] Outbox relay uses `FOR UPDATE SKIP LOCKED`
- [x] Each relay row uses `@Transactional(REQUIRES_NEW)`
- [x] Wallet events published after transaction commits, not inside it
- [x] `WalletEventConsumer` checks `processed_events` before every process
- [x] DLQ handler writes to `dead_letter_events` and increments metric
- [x] Webhook 4xx → EXHAUSTED, no Kafka retry triggered
- [x] HMAC signature uses `timestamp + "." + body`
- [x] Settlement micro-batch: each 100-item batch committed independently
- [x] Mailhog in docker-compose
- [x] All topics declared as `@Bean NewTopic` with correct partition counts

---

# Milestone 4 — Fraud Detection, Spring AI & Observability

## What Was Built

`fraud-service`, `audit-service`, the reconciliation job, distributed observability (Zipkin / Prometheus / Grafana), and Resilience4j circuit breakers. The placeholder fraud check in `PaymentApplicationService` is replaced with a real two-stage pipeline.

---

## The AI Decision Boundary

**Critical constraint — never violate this:**

| Owned by AI | Never owned by AI |
|-------------|------------------|
| Risk narrative (human-readable explanation) | ALLOW / BLOCK / REVIEW decision |
| Secondary AI risk score (dashboards only) | Any modification of a payment record |
| Pattern flagging | Any irreversible financial action |

- **Stage 1** (synchronous, target < 5ms p99): deterministic rules engine → ALLOW / BLOCK / REVIEW. **FINAL.**
- **Stage 2** (asynchronous, via `fraud.check.requested` Kafka topic): AI enrichment → updates `ai_narrative` only.
- Stage 2 **never overrides** Stage 1.

---

## paystream-fraud-service

### Database Schema

```sql
-- V1__create_fraud_checks.sql
fraud_checks(id, payment_id UNIQUE, user_id,
             risk_score SMALLINT, decision VARCHAR(10),  -- ALLOW, BLOCK, REVIEW
             flags TEXT[], rule_version VARCHAR(20),
             ai_narrative TEXT, ai_risk_score SMALLINT, ai_confidence DECIMAL(3,2),
             ai_processed BOOLEAN, ai_processing_error VARCHAR(500),
             processing_time_ms BIGINT, created_at)

-- V2__create_fraud_rules.sql
fraud_rules(id, rule_code UNIQUE, rule_name, flag_name,
            weight INT, enabled BOOLEAN, version VARCHAR(20), updated_at)
-- Seed rules: RULE_HIGH_AMOUNT(20), RULE_HIGH_VELOCITY(30), RULE_NEW_DEVICE(15),
--             RULE_ODD_HOUR(10), RULE_HIGH_CHARGEBACK(35), RULE_BLOCKED_IP(80)

-- V3__create_user_risk_profiles.sql
user_risk_profiles(user_id PK, avg_transaction_amount BIGINT,
                   typical_hours_start INT, typical_hours_end INT,
                   known_device_ids TEXT[], known_ip_prefixes TEXT[],
                   chargeback_count_30d INT, transaction_count_30d INT, last_updated_at)
```

### Stage 1 — Rules Engine

Rules are cached in Redis (TTL 5 min). Key: `fraud:rules:active`.

| Rule | Trigger | Weight |
|------|---------|--------|
| `RULE_HIGH_AMOUNT` | `amount > 500000` | 20 |
| `RULE_HIGH_VELOCITY` | `> 10 txns/day` (Redis INCR) | 30 |
| `RULE_NEW_DEVICE` | `deviceId` not in `knownDeviceIds` | 15 |
| `RULE_ODD_HOUR` | Hour outside `[typicalStart, typicalEnd]` | 10 |
| `RULE_HIGH_CHARGEBACK` | `chargebackCount30d >= 2` | 35 |
| `RULE_BLOCKED_IP` | IP matches blocked prefix | 80 |

**Decision (deterministic):**
```
totalScore >= 80 → BLOCK
totalScore >= 50 → REVIEW (payment proceeds, flagged for analyst)
totalScore < 50  → ALLOW
```

### Stage 2 — Spring AI Enrichment

```yaml
spring.ai.openai:
  api-key: ${OPENAI_API_KEY}    # never logged
  chat.options:
    model: ${AI_MODEL:gpt-4o-mini}
    temperature: 0.1
    max-tokens: 250
```

System prompt stored in `application.yml` (not in Java code):
```yaml
paystream.fraud.ai-system-prompt: >
  You are a payment fraud risk analyst. Respond ONLY with valid JSON.
  Schema: { "riskScore": int, "confidence": decimal, "flags": [string], "reasoning": "string max 100 words" }
```

**`FraudAiEnrichmentConsumer` flow:**
```
@KafkaListener(topics="fraud.check.requested")
@CircuitBreaker(name="ai-enrichment", fallbackMethod="handleAiFallback")

1. Load fraud_check + user_risk_profiles
2. Build user prompt
3. Call Spring AI ChatClient
4. Parse + validate JSON response
5. UPDATE fraud_check: ai_narrative, ai_risk_score, ai_confidence, ai_processed=TRUE
6. If |aiScore - ruleScore| > 30: log.warn("AI-rules disagreement")
7. Publish FraudScoreComputedEvent
```

`handleAiFallback`: logs warn, sets `ai_processing_error`, does not rethrow.
Nightly `@Scheduled` retries records with `ai_processed=FALSE`.

**Cost optimization**: only publish to `fraud.check.requested` if `riskScore >= 30` OR flags are non-empty.

### REST Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/fraud/check` | X-Internal-Service-Key | Stage 1 sync check (<10ms SLA) |
| GET | `/api/v1/fraud/payments/{id}` | FRAUD_ANALYST | Full analysis incl. `ai_narrative` |
| GET | `/api/v1/fraud/users/{id}/history` | FRAUD_ANALYST | User fraud history |
| PUT | `/api/v1/fraud/users/{id}/block` | FRAUD_ANALYST | Block user (Redis) |
| DELETE | `/api/v1/fraud/users/{id}/block` | ADMIN | Unblock user |

Payment-service checks `user:blocked:{userId}` in Redis at payment initiation.

---

## paystream-audit-service

### Database Schema

```sql
-- Append-only, partitioned by month
audit_log(id, event_id UNIQUE, event_type, entity_id, entity_type,
          actor_id, actor_role, action, old_state JSONB, new_state JSONB,
          metadata JSONB, correlation_id, source_service, ip_address, created_at)
PARTITION BY RANGE (created_at)
```

`AuditEventConsumer`:
```sql
INSERT INTO audit_log ... ON CONFLICT (event_id) DO NOTHING  -- idempotent
```

Every service publishes `AuditEvent` to `audit.events` for:
- **auth**: registered, logged in, logged out, locked
- **payment**: created, completed, failed, refunded
- **wallet**: created, debited, credited, frozen
- **fraud**: check performed, user blocked/unblocked

REST (ADMIN or FINANCE_OPS): `GET /api/v1/audit/logs` (filtered), `GET /api/v1/audit/logs/{eventId}`

---

## Reconciliation Job (settlement-service)

```
@Scheduled(cron="0 30 3 * * *", zone="Asia/Kolkata")
For each SETTLED batch (settled_at >= yesterday 00:00):
  expected = SUM(si.amount) FROM settlement_items WHERE batch_id=?
  actual   = batch.gross_amount
  if expected != actual:
    log.error("RECONCILIATION DISCREPANCY batchId={} expected={} actual={}")
    INSERT INTO reconciliation_alerts(batchId, "AMOUNT_MISMATCH", expected, actual)
    increment metric: settlement.reconciliation.discrepancies.total
  else:
    UPDATE settlement_batches SET reconciled=TRUE
```

```sql
reconciliation_alerts(id, batch_id, discrepancy_type VARCHAR(50),
                       expected_amount BIGINT, actual_amount BIGINT,
                       description TEXT, resolved BOOLEAN, created_at)
```

---

## Observability

### Distributed Tracing

```yaml
# Dependencies: micrometer-tracing-bridge-otel + opentelemetry-exporter-zipkin
management.tracing.sampling.probability: 1.0   # dev
                                          0.1   # prod
management.zipkin.tracing.endpoint: http://zipkin:9411/api/v2/spans
```

### Custom Metrics

| Type | Name | Tags |
|------|------|------|
| Counter | `paystream.payments.initiated.total` | `currency` |
| Counter | `paystream.payments.completed.total` | `currency` |
| Counter | `paystream.payments.failed.total` | `failureCode` |
| Counter | `paystream.fraud.checks.total` | `decision` |
| Counter | `paystream.fraud.blocks.total` | `topFlag` |
| Counter | `paystream.kafka.dlq.messages.total` | `topic` |
| Histogram (p50/p95/p99) | `paystream.payment.duration.seconds` | — |
| Histogram (p50/p95/p99) | `paystream.fraud.rules.duration.ms` | — |
| Histogram (p50/p95/p99) | `paystream.fraud.ai.duration.ms` | — |
| Gauge | `paystream.outbox.pending.count` | — |
| Gauge | `paystream.kafka.consumer.lag` | `topic`, `consumerGroup` |

### Grafana Dashboards (3 provisioned)

| Dashboard | Key Panels |
|-----------|-----------|
| `payment-operations` | Payment rate, success %, p99 latency, outbox lag |
| `system-health` | Circuit breakers, Kafka lag, JVM heap, HTTP error rate |
| `fraud-overview` | Block rate, top flags, AI vs rules score, AI error rate |

### Resilience4j (payment-service)

| Target | slidingWindow | failureThreshold | waitDuration | Fallback |
|--------|--------------|-----------------|-------------|---------|
| `wallet-service` | 10 | 50% | 10s | `ExternalServiceException(PS-5002)` |
| `fraud-service` | 10 | 60% | 15s | `FraudCheckResult.allow(0)` + WARN log |

### Docker Compose Additions

- `zipkin` — `openzipkin/zipkin:3` (port 9411)
- `prometheus` — `prom/prometheus:v2.51.0` (port 9090), scrapes all services every 15s
- `grafana` — `grafana/grafana:10.3.0` (port 3000), provisioned datasources + dashboards

---

## Tests Written

| Test Class | Type | What It Covers |
|-----------|------|----------------|
| `FraudRuleEngineTest` | Unit (mocked Redis) | Each rule fires correctly; multi-rule BLOCK; cache hit skips DB |
| `FraudAiEnrichmentTest` | Unit (mock ChatClient) | Valid JSON → fields set; malformed → error stored; circuit fallback; disagreement warn log |
| `AuditConsumerIdempotencyTest` | Integration | Same `eventId` twice → one row in `audit_log` |
| `CircuitBreakerTest` | Integration | 5/10 wallet calls fail → circuit opens; fraud circuit open → payment proceeds (rules-only ALLOW) |
| `ReconciliationTest` | Integration | Matching totals → `reconciled=TRUE`; mismatch → alert created + metric incremented |

---

## Milestone 4 Self-Verification Checklist

- [x] AI score never changes a payment's status
- [x] System prompt in `application.yml`, not a Java string literal
- [x] AI call has `@CircuitBreaker` with fallback that does not rethrow uncontrolled
- [x] `processedEventsRepo.existsById()` called before every audit insert
- [x] Outbox gauge counts `WHERE published=FALSE`
- [x] Zipkin in docker-compose
- [x] Grafana dashboards in `docker/` folder, provisioned via volume mount
- [x] All Redis keys use constant strings from `RedisKeys` class
- [x] Reconciliation creates alert on ANY non-zero discrepancy
- [x] AI topic only published if `riskScore >= 30` or flags non-empty

---

# Milestone 5 — Production Hardening & DevOps

## What Was Built

Zero new features. All functionality from M1–M4 hardened for production: security audit, end-to-end test suite, k6 performance baselines, Kubernetes manifests, GitHub Actions CI/CD pipeline, and full documentation.

---

## Section 1 — Security Hardening

### HTTP Security Headers (API Gateway GlobalFilter)

```
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Cache-Control: no-store, no-cache
Content-Security-Policy: default-src 'none'
```

### Input Validation Audit

Every endpoint enforced:
- `@RequestBody` has `@Valid`
- `@PathVariable` String has `@Pattern(regexp="[0-9A-HJKMNP-TV-Z]{26}")`
- Amount fields: `@Positive` + `@Max(100_000_000L)`
- Currency: `@Pattern(regexp="[A-Z]{3}")`
- Free text: `@Size(max=500)`

### Log Injection Hardening

User-supplied strings sanitized before logging:
```java
String safeEmail = email.replaceAll("[\\r\\n]", "_");
```

### Rate Limiting (additions to M1 gateway limits)

| Endpoint | Limit |
|----------|-------|
| `POST /api/v1/auth/login` | 5 req/min per IP |
| `POST /api/v1/auth/register` | 3 req/min per IP |
| `POST /api/v1/payments` | 50 req/min per userId |
| `POST /api/v1/fraud/check` | 200 req/min service-wide |

All return `429` with `Retry-After` header.

### RBAC

`docs/SECURITY.md` contains the full RBAC matrix. Every endpoint has a verified `@PreAuthorize` annotation.

### Secrets

`.gitignore` additions: `*.env`, `application-local.yml`, `*.pem`, `*.p12`, `*.key`

---

## Section 2 — End-to-End Test Suite (`paystream-e2e-tests`)

**Stack**: Spring Boot Test + Testcontainers Compose + RestAssured + WireMock + Awaitility
Calls API Gateway at `http://localhost:8080`. No mocking.

### `PaymentFlowE2ETest` Scenarios

| # | Scenario | Key Assertions |
|---|----------|---------------|
| 1 | Happy path | `status=COMPLETED`, balances correct, ledger 2-entry sum=0, audit entry exists |
| 2 | Idempotency | Same key → same `paymentId`, balances unchanged, no new ledger entries |
| 3 | Insufficient funds | 422 PS-2001, wallets unchanged, no ledger entries |
| 4 | Concurrent double-spend | Fund=200000, 5×100000 concurrent → exactly 2 succeed, 3 × 422, balance≥0 |
| 5 | Refund | `walletA` balance restored, 4 total ledger entries, refund ledger sum=0 |
| 6 | Webhook delivery | WireMock receives 1 POST within 15s, `X-PayStream-Signature` valid |

### `AuthSecurityE2ETest` Scenarios

- 5 wrong passwords → 423 lockout → auto-unlock → 200
- Logout blocklists access token; revokes refresh token
- Refresh rotation: old refresh token rejected after rotation

### `SettlementE2ETest`

5 completed payments → trigger → batch `SETTLED` with correct `grossAmount`, `feeAmount` (1%), `netAmount`.

---

## Section 3 — Performance Testing (k6)

### `k6/scripts/payment-load-test.js`

- 500 VUs, 5 minutes, 1 payment per VU per 2s
- Setup: 500 users, wallets funded with 10,000,000 paise each

**Thresholds (CI fails if breached):**
```
http_req_duration p(99) < 500ms
http_req_duration p(95) < 250ms
http_req_failed < 1%
checks > 99%
```

### `k6/scripts/fraud-rules-load-test.js`

- 100 VUs, 2 min, threshold: `p(99) < 10ms`

### `k6/PERFORMANCE_BASELINE.md`

Documents: date, environment specs, TPS, p50/p95/p99, error rate, Kafka lag during test.

---

## Section 4 — Kubernetes Manifests (`k8s/`)

Per-service (all 9 services): `deployment.yml`, `service.yml`, `hpa.yml`, `pdb.yml`

### Deployment Spec

```yaml
replicas: 2
strategy: RollingUpdate (maxSurge=1, maxUnavailable=0)
resources:
  requests: {cpu: 250m, memory: 512Mi}
  limits:   {cpu: 1000m, memory: 1Gi}
env:
  JAVA_OPTS: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
livenessProbe:  /actuator/health/liveness  (initialDelay=45s, period=10s, failure=3)
readinessProbe: /actuator/health/readiness (initialDelay=20s, period=5s,  failure=3)
```

All secrets from `secretKeyRef`. All config from `configMapKeyRef`.

### HPA / PDB

- `hpa.yml`: `minReplicas=2`, `maxReplicas=10`, `targetCPU=70%`
- `pdb.yml`: `minAvailable=1`

### Shared Resources (`k8s/shared/`)

- Namespace: `paystream`
- `ConfigMap: paystream-config` (EUREKA_SERVER_URL, KAFKA_BOOTSTRAP_SERVERS, etc.)
- `Secret: paystream-secrets` (DB_PASSWORD, REDIS_PASSWORD, JWT keys, OPENAI_API_KEY)
  - Note: Use External Secrets Operator in real production
- Ingress: `paystream.local` → api-gateway, TLS self-signed

Stateful services (Postgres/Redis/Kafka): comments point to Bitnami Helm charts + RDS/ElastiCache/MSK alternatives.

---

## Section 5 — GitHub Actions CI/CD (`.github/workflows/ci.yml`)

```
lint (every push + PR to main/develop)
  └── Checkstyle (google_checks.xml), SpotBugs + FindSecBugs, detect-secrets

test (after lint)
  └── mvn test (unit), JaCoCo ≥80% line coverage on com.paystream.**

integration-test (after test)
  └── mvn verify -P integration-tests (Testcontainers, requires Docker)

build (after integration-test)
  └── mvn package -DskipTests
  └── docker build + push to GHCR ({service}:{sha}, :latest on main only)

security-scan (after build, parallel)
  └── OWASP Dependency Check (fail CVSS ≥ 7.0)
  └── Trivy (fail on CRITICAL; .trivyignore for documented exceptions)

e2e-test (after build, PRs to main only)
  └── docker-compose up → mvn test -pl paystream-e2e-tests → docker-compose down

deploy-dev (push to develop only)
  └── kubectl set image + rollout status + smoke test /actuator/health

deploy-prod (push to main, manual approval required)
  └── environment: production with required reviewers
  └── Blue-green deploy + auto-rollback if readiness probe fails within 60s
```

---

## Section 6 — Code Quality Gates

| Tool | Rule |
|------|------|
| JaCoCo | 80% line, 75% branch on `domain/` + `application/` |
| Checkstyle | Max line 120; JavaDoc on public methods in `domain/` and `application/` |
| OWASP | `failBuildOnCVSS=7`; `owasp-suppressions.xml` with per-CVE justification |

---

## Section 7 — Documentation Deliverables

**`README.md`** — 4-command quick start:
```bash
git clone ... && cd paystream
cp .env.example .env
docker compose -f docker/docker-compose.yml up -d
```

| URL | Service |
|-----|---------|
| `:8080` | API Gateway |
| `:8090` | Kafka UI |
| `:3000` | Grafana |
| `:8025` | Mailhog |
| `:9411` | Zipkin |

**`docs/ADR/`** — 6 Architecture Decision Records (Status: Accepted):

| File | Decision |
|------|---------|
| `ADR-001-ulid-ids.md` | ULID as primary key type |
| `ADR-002-double-entry.md` | Double-entry bookkeeping for the ledger |
| `ADR-003-outbox-pattern.md` | Outbox pattern for at-least-once Kafka delivery |
| `ADR-004-fraud-determinism.md` | Rules engine (not AI) owns the ALLOW/BLOCK decision |
| `ADR-005-money-as-bigint.md` | Money stored as BIGINT minor units (no float/double) |
| `ADR-006-hexagonal.md` | Hexagonal architecture (ports & adapters) across all services |

---

## Production Readiness Checklist

### Security
- [x] No secret committed to git (grep passes)
- [x] All endpoints: authenticated or in explicit `permitAll`
- [x] JWT: 15-min expiry + Redis blocklist (revocable)
- [x] Passwords: BCrypt strength 12
- [x] HTTP security headers on every response
- [x] Rate limiting on login, register, payment POST
- [x] All user input logged via `{}` parameters

### Reliability
- [x] Circuit breakers on wallet-service and fraud-service
- [x] Retry with backoff on optimistic lock
- [x] DLQ for every Kafka consumer
- [x] Outbox pending count metric + documented alert threshold
- [x] Graceful shutdown: `server.shutdown: graceful`, timeout 30s

### Data Integrity
- [x] All money: BIGINT minor units, never `double` or `float`
- [x] Ledger UNIQUE index for Kafka dedup
- [x] Wallet `CHECK` constraint: `balance >= 0`
- [x] Idempotency: Redis + DB UNIQUE constraint (two layers)
- [x] Optimistic lock: version column + retry

### Observability
- [x] JSON structured logging in non-local profiles
- [x] Correlation ID in every log line via MDC
- [x] Distributed traces visible in Zipkin
- [x] Prometheus metrics on all services
- [x] Grafana 3 dashboards loading data

### Deployment
- [x] Rolling update `maxUnavailable=0` on all Deployments
- [x] `PodDisruptionBudget minAvailable=1`
- [x] HPA on all services
- [x] `JAVA_OPTS` uses `UseContainerSupport`
- [x] Secrets in Kubernetes Secrets, not ConfigMaps

---

## Milestone 5 Self-Verification Checklist

- [x] All M1–M4 tests still pass
- [x] `docker compose up` → full stack healthy in < 90 seconds
- [x] 4-command quick start in README works on a clean checkout
- [x] All 6 ADRs written with `Status: Accepted`
- [x] `PRODUCTION_READINESS.md` every item checked
- [x] `ci.yml`: valid YAML, correct job dependency order
- [x] `k8s/`: one folder per service, each with `deployment + service + hpa + pdb`
- [x] Zero new features added
