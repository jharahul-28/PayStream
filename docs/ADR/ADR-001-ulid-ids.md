# ADR-001: Use ULID for All Entity Primary Keys

**Status:** Accepted
**Date:** 2024-01-15

---

## Context

Every entity in PayStream needs a unique identifier. The system is distributed — multiple service instances generate IDs independently. We need IDs that are:
- Globally unique without coordination
- Sortable (to support natural ordering in queries)
- Safe to expose in URLs (no information leakage)
- Compact (for foreign key storage efficiency)

Options considered:
1. **UUID v4** — random, globally unique, not sortable, 36 chars with dashes
2. **UUID v7** — time-ordered, requires Java 22+
3. **Database SERIAL/SEQUENCE** — not globally unique across services
4. **ULID** — Universally Unique Lexicographically Sortable Identifier, 26 chars, time-sortable

---

## Decision

Use **ULID** (via `de.huxhorn.sulky:sulky-ulid`) for all entity primary keys.

All PKs are `VARCHAR(26)` in PostgreSQL. Generated via `IdGenerator.generate()` in the domain layer.

---

## Consequences

**Positive:**
- Natural chronological ordering of records with standard `ORDER BY id`
- No coordination between service instances
- 26 chars vs 36 for UUID — smaller foreign keys
- Human-readable in logs (time component visible)
- Lexicographic sort = insertion order sort

**Negative:**
- Not a standard that all ORMs natively understand (minor — just a String column)
- Slightly more entropy complexity than UUID v4 (acceptable)
- `sulky-ulid` library adds a dependency (small, no transitive deps)

**Constraints:**
- `IdGenerator` lives in `paystream-common` — no Spring dependency
- Never generate IDs in the infrastructure layer or controllers
