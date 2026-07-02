package com.paystream.notification.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "notification_processed_events")
public class NotificationProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 26)
    private String eventId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected NotificationProcessedEventEntity() {}

    public NotificationProcessedEventEntity(String eventId) {
        this.eventId     = eventId;
        this.processedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
}
