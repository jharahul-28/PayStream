# ADR-003: Transactional Outbox Pattern for Kafka Publishing

**Status:** Accepted
**Date:** 2024-01-15

---

## Context

Payment-service must publish events to Kafka when a payment completes. Two naive approaches fail:

1. **Publish directly in the transaction**: If Kafka is down, the transaction rolls back — payment lost.
2. **Publish after the transaction commits**: If the app crashes between commit and publish, event is lost.

Both create dual-write inconsistency between the DB state and Kafka.

---

## Decision

Use the **Transactional Outbox Pattern**:

1. In the same DB transaction that saves the payment, write an `outbox_events` row (`published=FALSE`)
2. A separate `OutboxRelayService` polls `outbox_events WHERE published=FALSE ... FOR UPDATE SKIP LOCKED`
3. For each row: publish to Kafka synchronously (`.get(5, SECONDS)`), then mark `published=TRUE`
4. Each row uses `@Transactional(REQUIRES_NEW)` — if Kafka send fails, the row stays unpublished

`FOR UPDATE SKIP LOCKED` ensures multiple relay instances never process the same row.

The relay runs every 100ms with a 1s initial delay — provides at-most-once delivery per poll cycle,
with at-least-once delivery overall (Kafka consumers are idempotent via `processed_events` table).

---

## Consequences

**Positive:**
- Atomicity: payment save and event intent are in the same transaction
- Resilience: Kafka downtime doesn't affect payment processing
- Exactly-once semantics achievable end-to-end (outbox + consumer idempotency)
- Observable: `paystream.outbox.pending.count` gauge in Prometheus

**Negative:**
- Polling relay adds ~100ms latency to event delivery (acceptable for notifications/ledger)
- `outbox_events` table grows without cleanup (mitigated by partitioning/archival)
- Relay must be singleton or use `SKIP LOCKED` (implemented)

**Monitoring:**
- Alert if `outbox.pending.count > 1000` (relay falling behind)
- Alert if relay doesn't run for > 30s (process crash)
