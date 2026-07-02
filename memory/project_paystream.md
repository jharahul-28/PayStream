---
name: PayStream Project State
description: Milestone completion status, tech stack, architecture decisions, and module ports for the PayStream payment engine
type: project
---

Milestone 1 (Foundation & Auth) and Milestone 2 (Payments, Wallets & Ledger) are COMPLETE.

**Why:** Building a banking-grade real-time payment processing engine as a Spring Boot microservices project.

**How to apply:** When asked to implement future milestones, check this memory first to understand what is already done. Do not re-implement completed work.

## Tech Stack
- Java 21, Spring Boot 3.3.5, Spring Cloud 2023.0.3
- PostgreSQL 16, Redis 7, Kafka (KRaft mode, Confluent 7.6.1)
- Flyway migrations, MapStruct, Lombok, JJWT (RS256), Testcontainers

## Module Ports
- paystream-eureka-server: 8761
- paystream-gateway: 8080
- paystream-auth-service: 8081
- paystream-payment-service: 8082
- paystream-wallet-service: 8083
- paystream-ledger-service: 8084

## Database Ports (Docker)
- postgres-auth: 5432 (auth_db)
- postgres-payment: 5433 (payment_db)
- postgres-wallet: 5434 (wallet_db)
- postgres-ledger: 5435 (ledger_db)
- postgres-settlement: 5436 (settlement_db)
- postgres-audit: 5437 (audit_db)

## Architecture Pattern
Hexagonal (Ports & Adapters) with DDD-inspired package structure. Every service follows:
- domain/model: pure Java, zero framework annotations
- application/port/in + out: use-case contracts
- application/service: orchestration
- infrastructure/persistence: JPA entities + adapters
- api/controller: thin, one-line delegation only

## Milestone 2 Key Facts
- Wallet: UNIQUE(user_id, currency), optimistic lock retry (3 attempts, 50ms backoff), balance CHECK >= 0
- Ledger: append-only, UNIQUE(reference_id, account_id, entry_type) for idempotency, signed amounts (DEBIT negative)
- Payment: two-layer idempotency (Redis + DB UNIQUE), state machine with explicit allowed transitions, compensation on credit failure
- Internal service auth: X-Internal-Service-Key header for debit/credit/ledger POST endpoints
- Ledger write failure after COMPLETED: logs CRITICAL but does NOT reverse wallets

## Next Milestone (M3)
- Kafka event streaming
- Outbox relay pattern
- notification-service, settlement-service, webhook-service
- Replace sync ledger call with async Kafka consumer
