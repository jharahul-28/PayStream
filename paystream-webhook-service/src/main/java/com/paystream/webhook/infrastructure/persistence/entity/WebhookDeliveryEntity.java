package com.paystream.webhook.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDeliveryEntity {

    @Id
    @Column(nullable = false, length = 26)
    private String id;

    @Column(name = "endpoint_id", nullable = false, length = 26)
    private String endpointId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false, length = 20)
    private String status;    // PENDING, DELIVERED, FAILED, EXHAUSTED

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_response_status")
    private Integer lastResponseStatus;

    @Column(name = "last_response_body", length = 2000)
    private String lastResponseBody;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WebhookDeliveryEntity() {}

    public WebhookDeliveryEntity(String id, String endpointId, String eventType, String payload) {
        this.id         = id;
        this.endpointId = endpointId;
        this.eventType  = eventType;
        this.payload    = payload;
        this.status     = "PENDING";
        this.createdAt  = Instant.now();
    }

    public String  getId()                { return id; }
    public String  getEndpointId()        { return endpointId; }
    public String  getEventType()         { return eventType; }
    public String  getPayload()           { return payload; }
    public String  getStatus()            { return status; }
    public int     getAttemptCount()      { return attemptCount; }
    public Integer getLastResponseStatus(){ return lastResponseStatus; }

    public void markDelivered(int responseStatus, String responseBody) {
        this.status             = "DELIVERED";
        this.lastResponseStatus = responseStatus;
        this.lastResponseBody   = truncate(responseBody, 2000);
        this.deliveredAt        = Instant.now();
        this.attemptCount++;
    }

    public void markFailed(int responseStatus, String responseBody, String error) {
        this.status             = "FAILED";
        this.lastResponseStatus = responseStatus;
        this.lastResponseBody   = truncate(responseBody, 2000);
        this.lastError          = truncate(error, 500);
        this.attemptCount++;
    }

    public void markExhausted(String error) {
        this.status    = "EXHAUSTED";
        this.lastError = truncate(error, 500);
        this.attemptCount++;
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
