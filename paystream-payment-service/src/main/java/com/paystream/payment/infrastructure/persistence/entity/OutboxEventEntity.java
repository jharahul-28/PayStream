package com.paystream.payment.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity for the outbox_events table.
 * Written in the same DB transaction as the payment state change.
 * The relay poller reads rows WHERE published=FALSE FOR UPDATE SKIP LOCKED.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    @Column(nullable = false, length = 26)
    private String id;

    @Column(name = "aggregate_id", nullable = false, length = 26)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OutboxEventEntity() {}

    public OutboxEventEntity(String id, String aggregateId, String aggregateType,
                             String eventType, String payload) {
        this.id            = id;
        this.aggregateId   = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType     = eventType;
        this.payload       = payload;
        this.published     = false;
        this.createdAt     = Instant.now();
    }

    public String getId()            { return id; }
    public String getAggregateId()   { return aggregateId; }
    public String getAggregateType() { return aggregateType; }
    public String getEventType()     { return eventType; }
    public String getPayload()       { return payload; }
    public boolean isPublished()     { return published; }
    public Instant getPublishedAt()  { return publishedAt; }
    public Instant getCreatedAt()    { return createdAt; }

    public void markPublished() {
        this.published   = true;
        this.publishedAt = Instant.now();
    }
}
