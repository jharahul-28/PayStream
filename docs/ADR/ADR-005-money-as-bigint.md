# ADR-005: Store All Money Amounts as BIGINT Minor Units

**Status:** Accepted
**Date:** 2024-01-15

---

## Context

Financial amounts must be exact. Floating-point types (float, double, DECIMAL in application code)
introduce rounding errors that compound across large transaction volumes.

Example of the problem:
```java
double amount = 0.1 + 0.2;  // 0.30000000000000004 — WRONG
```

Options:
1. **double/float** — fast, but imprecise. Forbidden in financial systems.
2. **BigDecimal** — precise, but: (a) slower, (b) easy to accidentally use wrong scale, (c) complex arithmetic
3. **BIGINT minor units** — integers are exact, arithmetic is trivial, DB storage is efficient

---

## Decision

All money amounts are stored as **`BIGINT` in the smallest currency unit** (paise for INR, cents for USD).

```java
// GOOD: 1000 INR = 100000 paise
long amount = 100000L;

// BAD
double amount = 1000.0;
BigDecimal amount = new BigDecimal("1000.00");
```

The `Money` value object in `paystream-common` enforces this:
- `Money.of(100000L, "INR")` — 1000 INR
- Arithmetic operations: `add()`, `subtract()`, `percentage()` — all integer arithmetic
- No floating-point, no BigDecimal in the domain

At the API boundary (request/response DTOs), amounts are expressed in minor units.
Documentation clearly states: `amount: 100000` means INR 1000.

---

## Consequences

**Positive:**
- Mathematically exact for any amount in any supported currency
- Fast integer arithmetic for all financial calculations
- Simple DB column: `BIGINT NOT NULL` — no scale/precision debates
- No accidental float coercion in JSON serialization

**Negative:**
- Requires developers to always think in minor units (documentation burden)
- Currency display formatting must convert at the API response layer
- Large amounts approach BIGINT max (~9 quadrillion paise = ~90 trillion INR) — safe for foreseeable future

**Enforcement:**
- `MoneyValueObjectTest` verifies arithmetic correctness and immutability
- Code review: any `double` or `float` field related to money is a rejection criterion
