package com.paystream.ledger.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Records every Kafka event successfully processed by ledger-service.
 * Checked before processing to guarantee idempotency (at-least-once → exactly-once).
 * Saved in the same @Transactional boundary as the ledger entry.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 26)
    private String eventId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {}

    public ProcessedEventEntity(String eventId) {
        this.eventId     = eventId;
        this.processedAt = Instant.now();
    }

    public String  getEventId()     { return eventId; }
    public Instant getProcessedAt() { return processedAt; }
}
