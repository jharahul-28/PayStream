# PayStream — Banking-Grade Real-Time Payment Engine

A production-grade fintech backend platform built with Spring Boot 3, Kafka, Redis, and PostgreSQL.
Implements clean hexagonal architecture, event-driven consistency, distributed observability, and enterprise security.

---

## Quick Start (4 commands)

```bash
git clone https://github.com/your-org/paystream.git && cd paystream
cp .env.example .env
docker compose -f docker/docker-compose.yml up -d
# Stack is ready when gateway responds:
curl http://localhost:8080/actuator/health
```

> **First boot**: all Flyway migrations run automatically. Expect ~60s for all services to become healthy.

---

## Service URLs

| Service         | URL                              | Purpose                          |
|----------------|----------------------------------|----------------------------------|
| API Gateway     | http://localhost:8080            | Single entry point for all APIs  |
| Kafka UI        | http://localhost:8090            | Inspect topics, consumer lag     |
| Grafana         | http://localhost:3000            | Dashboards (admin/admin)         |
| Mailhog         | http://localhost:8025            | Capture email notifications      |
| Zipkin          | http://localhost:9411            | Distributed traces               |
| Prometheus      | http://localhost:9090            | Metrics scrape target            |
| Eureka          | http://localhost:8761            | Service registry                 |

---

## Architecture Overview

```
                          ┌───────────────────────────────────────────────────────┐
                          │                   API Gateway :8080                   │
                          │   JWT validation · Rate limiting · Security headers   │
                          └─────────────┬──────────────────────────────┬──────────┘
                                        │                              │
              ┌─────────────────────────┼───────────────┐             │
              ▼                         ▼               ▼             ▼
     auth-service :8081     payment-service :8082   wallet-service :8083
     └─ RS256 JWT           └─ Idempotency          └─ Optimistic lock
     └─ Refresh tokens      └─ Outbox pattern        └─ Double-entry
     └─ Redis blocklist     └─ Circuit breakers       └─ Kafka events

              ▼ Kafka (async)                          ▼ Kafka (async)
     ledger-service :8084   fraud-service :8088    settlement-service :8086
     └─ Append-only         └─ Rules engine         └─ Daily scheduler
     └─ Double-entry        └─ Spring AI Stage 2     └─ Reconciliation
     └─ Idempotent consume  └─ <5ms SLA              └─ Micro-batches

     notification-service :8085    webhook-service :8087    audit-service :8089
     └─ Email/SMS/Push             └─ HMAC signatures       └─ Append-only log
     └─ Kafka consumer             └─ Retry with backoff    └─ Kafka consumer
```

---

## Service Map

| Module                       | Port | Database         | Description                                |
|-----------------------------|------|------------------|--------------------------------------------|
| paystream-eureka-server     | 8761 | —                | Service registry (Spring Cloud Eureka)     |
| paystream-gateway           | 8080 | —                | API Gateway (Spring Cloud Gateway)         |
| paystream-auth-service      | 8081 | auth_db (5432)   | JWT auth, users, refresh tokens            |
| paystream-payment-service   | 8082 | payment_db (5433)| Payment orchestration, idempotency, outbox |
| paystream-wallet-service    | 8083 | wallet_db (5434) | Balances, debit/credit, holds              |
| paystream-ledger-service    | 8084 | ledger_db (5435) | Append-only double-entry ledger            |
| paystream-notification-service | 8085 | notification_db (5438) | Email/SMS/Push via Kafka        |
| paystream-settlement-service| 8086 | settlement_db (5436) | Daily settlement batches              |
| paystream-webhook-service   | 8087 | webhook_db (5439)| Webhook delivery with HMAC signing         |
| paystream-fraud-service     | 8088 | fraud_db (5440)  | Rules engine + Spring AI enrichment        |
| paystream-audit-service     | 8089 | audit_db (5437)  | Immutable audit log                        |

---

## Environment Variables

Copy `.env.example` to `.env` and set:

| Variable              | Default             | Description                              |
|-----------------------|---------------------|------------------------------------------|
| POSTGRES_PASSWORD     | localdevonly        | PostgreSQL password for all DBs          |
| REDIS_PASSWORD        | localdevonly        | Redis auth password                      |
| INTERNAL_SERVICE_KEY  | dev-only-local-key  | Service-to-service auth key              |
| OPENAI_API_KEY        | dummy-key-for-local | OpenAI key for fraud AI enrichment       |
| GRAFANA_PASSWORD      | admin               | Grafana admin password                   |

> **Production**: Never use defaults. Rotate all secrets before first deploy.

---

## Running Tests

```bash
# Unit tests only (fast, no containers)
mvn test -DexcludedGroups=integration,e2e

# Integration tests (Testcontainers — needs Docker)
mvn verify -P integration-tests

# E2E tests (needs full docker-compose stack)
docker compose -f docker/docker-compose.yml up -d
mvn test -pl paystream-e2e-tests

# Performance tests (needs k6 + running stack)
k6 run k6/scripts/payment-load-test.js
k6 run k6/scripts/fraud-rules-load-test.js
```

---

## Development Workflow

1. **Start infrastructure only**: `docker compose -f docker/docker-compose.yml up postgres-auth redis kafka -d`
2. **Run a single service locally**: `cd paystream-auth-service && mvn spring-boot:run`
3. **View Kafka topics**: http://localhost:8090
4. **View traces**: http://localhost:9411
5. **View metrics**: http://localhost:3000 (Grafana)

---

## ADR Index

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-001](docs/ADR/ADR-001-ulid-ids.md) | Use ULID for all entity IDs | Accepted |
| [ADR-002](docs/ADR/ADR-002-double-entry.md) | Double-entry bookkeeping in ledger | Accepted |
| [ADR-003](docs/ADR/ADR-003-outbox-pattern.md) | Transactional outbox for Kafka publishing | Accepted |
| [ADR-004](docs/ADR/ADR-004-fraud-determinism.md) | AI never influences fraud decisions | Accepted |
| [ADR-005](docs/ADR/ADR-005-money-as-bigint.md) | Store money as BIGINT minor units | Accepted |
| [ADR-006](docs/ADR/ADR-006-hexagonal.md) | Hexagonal architecture for all services | Accepted |

---

## Further Reading

- [Security Reference](docs/SECURITY.md) — auth flow, RBAC matrix, webhook signing
- [Kubernetes Guide](k8s/README.md) — deploying to K8s
- [Performance Baseline](k6/PERFORMANCE_BASELINE.md) — load test targets and results
- [Production Readiness](PRODUCTION_READINESS.md) — go-live checklist
