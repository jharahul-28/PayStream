package com.paystream.payment.domain.model;

import com.paystream.common.constant.ErrorCode;
import com.paystream.common.exception.DomainException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Payment domain entity. Pure Java — zero framework annotations.
 *
 * State machine transitions are validated by {@link #transitionTo(PaymentStatus)}.
 * No caller may set {@code status} directly — all transitions are explicit and validated.
 */
public class Payment {

    // Allowed transitions: key -> set of valid next states
    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
            PaymentStatus.PENDING,     EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.CANCELLED),
            PaymentStatus.PROCESSING,  EnumSet.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED),
            PaymentStatus.COMPLETED,   EnumSet.of(PaymentStatus.REFUNDED),
            PaymentStatus.FAILED,      EnumSet.noneOf(PaymentStatus.class),
            PaymentStatus.REFUNDED,    EnumSet.noneOf(PaymentStatus.class),
            PaymentStatus.CANCELLED,   EnumSet.noneOf(PaymentStatus.class)
    );

    private final String        id;
    private final String        userId;
    private final String        idempotencyKey;
    private final String        sourceWalletId;
    private final String        destinationWalletId;
    private final long          amount;
    private final String        currency;
    private PaymentStatus       status;
    private String              failureReason;
    private String              failureCode;
    private final String        note;
    private int                 version;
    private final Instant       createdAt;
    private Instant             updatedAt;

    public Payment(String id, String userId, String idempotencyKey,
                   String sourceWalletId, String destinationWalletId,
                   long amount, String currency, PaymentStatus status,
                   String failureReason, String failureCode, String note,
                   int version, Instant createdAt, Instant updatedAt) {
        this.id                   = id;
        this.userId               = userId;
        this.idempotencyKey       = idempotencyKey;
        this.sourceWalletId       = sourceWalletId;
        this.destinationWalletId  = destinationWalletId;
        this.amount               = amount;
        this.currency             = currency;
        this.status               = status;
        this.failureReason        = failureReason;
        this.failureCode          = failureCode;
        this.note                 = note;
        this.version              = version;
        this.createdAt            = createdAt;
        this.updatedAt            = updatedAt;
    }

    // -------------------------------------------------------------------------
    // State machine
    // -------------------------------------------------------------------------

    /**
     * Transitions the payment to {@code next} state.
     * Throws {@link DomainException} if the transition is not allowed.
     */
    public void transitionTo(PaymentStatus next) {
        Set<PaymentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(this.status, EnumSet.noneOf(PaymentStatus.class));
        if (!allowed.contains(next)) {
            throw new DomainException(ErrorCode.INVALID_STATE_TRANSITION,
                    String.format("Cannot transition payment from %s to %s", this.status, next));
        }
        this.status    = next;
        this.version++;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason, String code) {
        transitionTo(PaymentStatus.FAILED);
        this.failureReason = reason;
        this.failureCode   = code;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

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
    public int           getVersion()             { return version; }
    public Instant       getCreatedAt()           { return createdAt; }
    public Instant       getUpdatedAt()           { return updatedAt; }
}
