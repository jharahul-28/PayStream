package com.paystream.notification.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @Column(nullable = false, length = 26)
    private String id;

    @Column(name = "user_id",    nullable = false, length = 26)
    private String userId;

    @Column(name = "payment_id", length = 26)
    private String paymentId;

    @Column(nullable = false, length = 30)
    private String type;      // EMAIL, SMS, PUSH

    @Column(nullable = false, length = 50)
    private String channel;   // PAYMENT_SUCCESS, PAYMENT_FAILED

    @Column(nullable = false, length = 20)
    private String status;    // PENDING, SENT, FAILED

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(length = 500)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationEntity() {}

    public NotificationEntity(String id, String userId, String paymentId,
                              String type, String channel, String status,
                              String recipient, String subject, String body) {
        this.id        = id;
        this.userId    = userId;
        this.paymentId = paymentId;
        this.type      = type;
        this.channel   = channel;
        this.status    = status;
        this.recipient = recipient;
        this.subject   = subject;
        this.body      = body;
        this.createdAt = Instant.now();
    }

    public String  getId()              { return id; }
    public String  getUserId()          { return userId; }
    public String  getPaymentId()       { return paymentId; }
    public String  getType()            { return type; }
    public String  getChannel()         { return channel; }
    public String  getStatus()          { return status; }
    public String  getRecipient()       { return recipient; }
    public String  getSubject()         { return subject; }
    public String  getBody()            { return body; }
    public int     getAttemptCount()    { return attemptCount; }
    public Instant getSentAt()          { return sentAt; }
    public String  getErrorMessage()    { return errorMessage; }

    public void markSent() {
        this.status          = "SENT";
        this.sentAt          = Instant.now();
        this.lastAttemptedAt = Instant.now();
        this.attemptCount++;
    }

    public void markFailed(String error) {
        this.status          = "FAILED";
        this.errorMessage    = error;
        this.lastAttemptedAt = Instant.now();
        this.attemptCount++;
    }
}
