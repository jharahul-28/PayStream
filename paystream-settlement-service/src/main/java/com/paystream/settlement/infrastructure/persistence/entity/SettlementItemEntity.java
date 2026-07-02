package com.paystream.settlement.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "settlement_items",
       uniqueConstraints = @UniqueConstraint(columnNames = {"batch_id", "payment_id"}))
public class SettlementItemEntity {

    @Id
    @Column(nullable = false, length = 26)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private SettlementBatchEntity batch;

    @Column(name = "payment_id", nullable = false, length = 26)
    private String paymentId;

    @Column(nullable = false)
    private long amount;

    @Column(name = "fee_amount", nullable = false)
    private long feeAmount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "settled_at")
    private Instant settledAt;

    protected SettlementItemEntity() {}

    public SettlementItemEntity(String id, SettlementBatchEntity batch,
                                 String paymentId, long amount, long feeAmount) {
        this.id        = id;
        this.batch     = batch;
        this.paymentId = paymentId;
        this.amount    = amount;
        this.feeAmount = feeAmount;
        this.status    = "PENDING";
    }

    public String getId()         { return id; }
    public String getPaymentId()  { return paymentId; }
    public long   getAmount()     { return amount; }
    public long   getFeeAmount()  { return feeAmount; }
    public String getStatus()     { return status; }

    public void markSettled() {
        this.status    = "SETTLED";
        this.settledAt = Instant.now();
    }
}
