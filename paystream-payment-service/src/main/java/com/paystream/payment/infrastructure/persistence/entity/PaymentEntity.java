package com.paystream.payment.infrastructure.persistence.entity;

import com.paystream.payment.domain.model.PaymentStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * JPA entity for the {@code payments} table.
 * The domain {@link com.paystream.payment.domain.model.Payment} must have zero JPA annotations.
 */
@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @Column(name = "id", length = 26, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "source_wallet_id", nullable = false, updatable = false)
    private String sourceWalletId;

    @Column(name = "destination_wallet_id", nullable = false, updatable = false)
    private String destinationWalletId;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "note")
    private String note;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentEntity() {}

    public PaymentEntity(String id, String userId, String idempotencyKey,
                         String sourceWalletId, String destinationWalletId,
                         long amount, String currency, PaymentStatus status, String note) {
        this.id                  = id;
        this.userId              = userId;
        this.idempotencyKey      = idempotencyKey;
        this.sourceWalletId      = sourceWalletId;
        this.destinationWalletId = destinationWalletId;
        this.amount              = amount;
        this.currency            = currency;
        this.status              = status;
        this.note                = note;
        this.createdAt           = Instant.now();
    }

    public void setStatus(PaymentStatus status)         { this.status = status; }
    public void setFailureReason(String failureReason)  { this.failureReason = failureReason; }
    public void setFailureCode(String failureCode)      { this.failureCode = failureCode; }

    public String        getId()                  { return id; }
    public String        getUserId()              { return userId; }
    public String        getIdempotencyKey()      { return idempotencyKey; }
    public String        getSourceWalletId()      { return sourceWalletId; }
    public String        getDestinationWalletId() { return destinationWalletId; }
    public long          getAmount()              { return amount; }
    public String        getCurrency()            { return currency; }
    public PaymentStatus getStatus()              { return status; }
    public String        getFailureReason()       { return failureReason; }
    public String        getFailureCode()         { return failureCode; }
    public String        getNote()                { return note; }
    public Integer       getVersion()             { return version; }
    public Instant       getCreatedAt()           { return createdAt; }
    public Instant       getUpdatedAt()           { return updatedAt; }
}
