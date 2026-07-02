# ADR-002: Double-Entry Bookkeeping in the Ledger Service

**Status:** Accepted
**Date:** 2024-01-15

---

## Context

Financial systems require a provable, auditable record of every money movement. Two common approaches:
1. **Single-entry**: One row per transaction (simple, but balance can be computed incorrectly)
2. **Double-entry**: Every movement creates two rows that sum to zero (accounting standard)

In PayStream, money moves between wallets. The ledger must be the source of truth for all financial history.

---

## Decision

The ledger service implements **double-entry bookkeeping**:
- Every payment creates exactly **two ledger entries**: a DEBIT (negative) and a CREDIT (positive)
- The sum of both entries must equal **zero** — enforced before any persistence
- The `ledger_entries` table is **append-only**: no UPDATE, no DELETE, no soft delete
- A UNIQUE index on `(reference_id, account_id, entry_type)` prevents duplicate entries (Kafka idempotency)

Invariant enforced in `LedgerApplicationService.createDoubleEntry()`:
```java
assert debitEntry.amount() + creditEntry.amount() == 0;
```

---

## Consequences

**Positive:**
- Ledger always balances — any discrepancy is detectable
- Supports balance computation from raw entries at any point in time
- Provides forensic audit trail: impossible to hide a money movement
- Reconciliation job can verify settlement totals against ledger

**Negative:**
- Two DB writes per payment (acceptable — ledger is async via Kafka)
- Balance computation requires aggregation (mitigated by `account_balance_snapshots` table)
- More complex schema than single-entry

**Constraints:**
- Never use UPDATE/DELETE on `ledger_entries` — this is a permanent constraint
- Wallet balances (in `wallet_db`) are the operational truth; ledger is the accounting truth
- Both must agree — the reconciliation job verifies this nightly
