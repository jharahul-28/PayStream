# ADR-006: Hexagonal Architecture for All Services

**Status:** Accepted
**Date:** 2024-01-15

---

## Context

PayStream is a long-lived fintech platform. The architecture must support:
- Testability in isolation (domain logic testable without Spring, DB, or Kafka)
- Technology swappability (swap Postgres for another DB without touching business logic)
- Consistency across 9+ services (single mental model for every engineer)
- Maintainability as the team grows

Options considered:
1. **Layered (MVC)**: Simple but domain and infrastructure often bleed into each other
2. **CQRS**: Powerful but over-engineered for CRUD-heavy services like auth/wallet
3. **Hexagonal (Ports & Adapters)**: Explicit separation between domain and infrastructure

---

## Decision

All services follow **Hexagonal Architecture** with this package structure:

```
com.paystream.{service}/
  api/            ← Input adapters (REST controllers, filters)
  application/    ← Use cases (orchestration), ports in/out
    service/      ← Implements in/ ports
    port/in/      ← Use case interfaces
    port/out/     ← Persistence + messaging interfaces
    command/      ← Command objects
  domain/         ← Pure Java, zero framework annotations
    model/        ← Entities with behavior methods
    event/        ← Domain events as records
    exception/    ← Business rule exceptions
    valueobject/  ← Money, UserId, Currency
  infrastructure/ ← Output adapters
    persistence/  ← JPA entities + repositories + adapters
    messaging/    ← Kafka producers + consumers
    external/     ← Feign clients
    config/       ← @Configuration classes
  security/       ← JWT, RBAC, SecurityFilterChain
  observability/  ← MDC, Micrometer custom metrics
```

**Key rules enforced:**
- Domain classes: zero Spring/JPA/Kafka annotations
- Controllers: zero business logic (one line per method)
- Application services: call ports, not repositories directly
- Infrastructure adapters: translate between domain and persistence models

---

## Consequences

**Positive:**
- Domain logic fully testable with plain JUnit (no Spring context)
- Infrastructure replaceable without touching business logic
- Clear dependency direction: domain knows nothing about infrastructure
- Consistent onboarding: any engineer can navigate any service

**Negative:**
- More files and packages than MVC (mapper, adapter, port layers)
- MapStruct boilerplate for entity ↔ domain translations
- Discipline required — violations (e.g., JPA in domain) easy to introduce accidentally

**Enforcement:**
- CI Checkstyle rule: no `@Entity` import in `domain/` packages
- Code review: any `@Autowired` in domain or application layer is a rejection criterion
- `paystream-common` has zero Spring dependencies (verified by pom.xml scope)
