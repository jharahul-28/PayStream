package com.paystream.ledger.infrastructure.persistence.entity;

import com.paystream.common.util.IdGenerator;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "dead_letter_events")
public class DeadLetterEventEntity {

    @Id
    @Column(nullable = false, length = 26)
    private String id;

    @Column(name = "event_id", length = 26)
    private String eventId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(length = 255)
    private String topic;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DeadLetterEventEntity() {}

    public DeadLetterEventEntity(String eventId, String eventType, String topic,
                                 String payload, String errorMessage) {
        this.id           = IdGenerator.generate();
        this.eventId      = eventId;
        this.eventType    = eventType;
        this.topic        = topic;
        this.payload      = payload;
        this.errorMessage = errorMessage;
        this.createdAt    = Instant.now();
    }

    public String getId()           { return id; }
    public String getEventId()      { return eventId; }
    public String getErrorMessage() { return errorMessage; }
}
