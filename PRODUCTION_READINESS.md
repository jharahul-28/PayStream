# PayStream — Production Readiness Checklist

This document must be reviewed and signed off before any production deployment.
Every item must be checked (✅) or explicitly accepted with documented risk.

---

## SECURITY

| # | Check | Status | Notes |
|---|-------|--------|-------|
| S1 | No secret committed to git (`grep` scan passes) | ✅ | CI `detect-secrets` step enforces this |
| S2 | All endpoints: authenticated or in explicit `permitAll` | ✅ | RBAC matrix in `docs/SECURITY.md` |
| S3 | JWT: access tokens expire in 15min (900s) | ✅ | `JwtProperties.accessTokenTtlSeconds=900` |
| S4 | JWT: tokens revocable via Redis blocklist | ✅ | `JwtAuthFilter` checks `token:blocklist:{jti}` |
| S5 | Passwords: BCrypt strength 12 | ✅ | `BCryptPasswordEncoder(12)` in `SecurityConfig` |
| S6 | HTTP security headers on every response | ✅ | `SecurityHeadersFilter` in gateway |
| S7 | Rate limiting: login (5/min/IP), register (3/min/IP), payment POST (50/min/user) | ✅ | `AdvancedRateLimitFilter` + `RateLimitFilter` |
| S8 | All user input logged via `{}` parameters, no string concatenation | ✅ | SLF4J parameterized logging enforced |
| S9 | Sensitive fields (API keys, passwords) never logged | ✅ | `@ConfigurationProperties` binding only |
| S10 | Webhook HMAC signatures verified by merchants | ✅ | `X-PayStream-Signature: sha256={hmac}` |
| S11 | Internal endpoints protected by `X-Internal-Service-Key` | ✅ | `InternalServiceAuthFilter` in wallet/fraud |
| S12 | Account lockout after 5 failed logins (15 min) | ✅ | `AuthApplicationService.recordFailedLogin()` |
| S13 | AI (Stage 2) never influences payment ALLOW/BLOCK decision | ✅ | Architecture boundary — see ADR-004 |
| S14 | Kubernetes Secrets used for all sensitive config (not ConfigMaps) | ✅ | All secrets from `secretKeyRef` in K8s YAMLs |
| S15 | `.gitignore` covers `*.pem`, `*.env`, `application-local.yml` | ✅ | Updated in `.gitignore` |

---

## RELIABILITY

| # | Check | Status | Notes |
|---|-------|--------|-------|
| R1 | Circuit breakers on wallet-service and fraud-service | ✅ | Resilience4j in `payment-service` |
| R2 | Retry with exponential backoff on optimistic lock | ✅ | `WalletApplicationService` retry loop (3 attempts, 50ms*n) |
| R3 | DLQ for every Kafka consumer | ✅ | `@RetryableTopic(dltTopicSuffix=".dlq")` |
| R4 | Outbox pending count metric + alert threshold documented | ✅ | Gauge `paystream.outbox.pending.count`; alert if > 1000 |
| R5 | Graceful shutdown: `server.shutdown: graceful` + 30s timeout | ✅ | All services have `server.shutdown: graceful` |
| R6 | `FOR UPDATE SKIP LOCKED` in outbox relay (multi-pod safe) | ✅ | `OutboxRelayService` native SQL query |
| R7 | Wallet domain enforces `balance >= 0` (DB CHECK + domain guard) | ✅ | `CONSTRAINT balance_non_negative` + `debit()` throws |
| R8 | Ledger UNIQUE index prevents duplicate Kafka-driven entries | ✅ | `idx_ledger_idempotency(reference_id, account_id, entry_type)` |
| R9 | Compensation fires if credit fails after debit | ✅ | `PaymentApplicationService.initiatePayment()` step 5 |
| R10 | Settlement micro-batch: each 100-item batch committed independently | ✅ | `SettlementScheduler` checkpoint pattern |

---

## DATA INTEGRITY

| # | Check | Status | Notes |
|---|-------|--------|-------|
| D1 | All money: BIGINT minor units, never double or float | ✅ | `Money` value object; enforced by code review + ADR-005 |
| D2 | Ledger UNIQUE index for Kafka dedup | ✅ | See R8 above |
| D3 | Wallet `CHECK` constraint: `balance >= 0` | ✅ | Flyway `V1__create_wallets.sql` |
| D4 | Idempotency: Redis `PROCESSING` sentinel + DB `UNIQUE(user_id, idempotency_key)` | ✅ | Two-layer dedup in payment-service |
| D5 | Optimistic lock: `@Version` column + 3-attempt retry | ✅ | Wallet JPA entity + service retry |
| D6 | Double-entry invariant: debit + credit sum = 0 before persist | ✅ | `LedgerApplicationService.createDoubleEntry()` |
| D7 | Ledger is append-only (no UPDATE/DELETE) | ✅ | By design; no UPDATE queries in ledger service |
| D8 | Flyway migrations version-controlled, rollback scripts provided | ✅ | All migrations in `db/migration/` per service |
| D9 | Reconciliation alert on any non-zero discrepancy | ✅ | `ReconciliationJob` inserts `reconciliation_alerts` |

---

## OBSERVABILITY

| # | Check | Status | Notes |
|---|-------|--------|-------|
| O1 | JSON structured logging in non-local profiles | ✅ | Logback JSON pattern in all `application.yml` |
| O2 | Correlation ID in every log line via MDC | ✅ | `CorrelationIdFilter` in all services |
| O3 | Distributed traces visible in Zipkin | ✅ | Micrometer OTel bridge + Zipkin exporter |
| O4 | Prometheus metrics on all services (`/actuator/prometheus`) | ✅ | All services expose Prometheus endpoint |
| O5 | Grafana 3 dashboards loading data | ✅ | Provisioned in `docker/grafana/` |
| O6 | Custom business metrics: payments.initiated, payments.completed, fraud.blocks, etc. | ✅ | Counters/histograms in each service |
| O7 | Outbox gauge `paystream.outbox.pending.count` | ✅ | `OutboxRelayService` Micrometer gauge |
| O8 | DLQ metric `paystream.kafka.dlq.messages.total` | ✅ | `@DltHandler` increments counter |

---

## DEPLOYMENT

| # | Check | Status | Notes |
|---|-------|--------|-------|
| P1 | Rolling update `maxUnavailable=0` on all Deployments | ✅ | All K8s `deployment.yml` files |
| P2 | `PodDisruptionBudget` `minAvailable=1` | ✅ | All K8s `pdb.yml` files |
| P3 | HPA on all services (min=2, max=10, CPU=70%) | ✅ | All K8s `hpa.yml` files |
| P4 | `JAVA_OPTS` uses `UseContainerSupport` and `MaxRAMPercentage=75.0` | ✅ | All Dockerfiles + K8s ConfigMap |
| P5 | Secrets in Kubernetes Secrets, not ConfigMaps | ✅ | `secretKeyRef` in all K8s deployments |
| P6 | Liveness probe: `/actuator/health/liveness` | ✅ | `probes.enabled=true` in all `application.yml` |
| P7 | Readiness probe: `/actuator/health/readiness` | ✅ | `probes.enabled=true` in all `application.yml` |
| P8 | Multi-stage Docker builds (JDK build → JRE runtime) | ✅ | All `Dockerfile` files |
| P9 | Non-root user in all containers (`runAsUser=1000`) | ✅ | All K8s deployments + Dockerfiles |
| P10 | Auto-rollback if readiness probe fails within 60s (CI deploy job) | ✅ | `deploy-prod` job in `ci.yml` |

---

## TESTED

| # | Check | Status | Notes |
|---|-------|--------|-------|
| T1 | Unit tests pass (`mvn test`) | ✅ | |
| T2 | Integration tests pass (`mvn verify -P integration-tests`) | ✅ | Testcontainers |
| T3 | E2E tests pass against live stack | ✅ | `paystream-e2e-tests` module |
| T4 | JaCoCo line coverage ≥ 80% on domain + application | ✅ | Enforced in CI `test` job |
| T5 | OWASP Dependency Check passes (no CVSS ≥ 7 unfixed) | ✅ | CI `security-scan` job |
| T6 | Trivy CRITICAL vulnerabilities: zero unfixed | ✅ | CI `security-scan` job |
| T7 | Performance thresholds met: p99 payment < 500ms, p99 fraud < 10ms | ✅ | `k6/PERFORMANCE_BASELINE.md` |

---

## DOCUMENTATION

| # | Check | Status | Notes |
|---|-------|--------|-------|
| Doc1 | README.md: 4-command quick start works on clean checkout | ✅ | Tested |
| Doc2 | All 6 ADRs written with `Status: Accepted` | ✅ | `docs/ADR/` |
| Doc3 | RBAC matrix current in `docs/SECURITY.md` | ✅ | All endpoints documented |
| Doc4 | K8s deployment guide current | ✅ | `k8s/README.md` |
| Doc5 | Performance baseline documented | ✅ | `k6/PERFORMANCE_BASELINE.md` |

---

## Sign-Off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Lead Engineer | | | |
| Security Review | | | |
| Operations Lead | | | |
| Product Owner | | | |
