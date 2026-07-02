package com.paystream.settlement.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "settlement_batches")
public class SettlementBatchEntity {

    @Id
    @Column(nullable = false, length = 26)
    private String id;

    @Column(name = "merchant_id", nullable = false, length = 26)
    private String merchantId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "total_payment_count", nullable = false)
    private int totalPaymentCount;

    @Column(name = "gross_amount", nullable = false)
    private long grossAmount;

    @Column(name = "fee_amount", nullable = false)
    private long feeAmount;

    @Column(name = "net_amount", nullable = false)
    private long netAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "reconciled", nullable = false)
    private boolean reconciled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SettlementItemEntity> items = new ArrayList<>();

    protected SettlementBatchEntity() {}

    public SettlementBatchEntity(String id, String merchantId, String status,
                                  String currency, LocalDate settlementDate) {
        this.id             = id;
        this.merchantId     = merchantId;
        this.status         = status;
        this.currency       = currency;
        this.settlementDate = settlementDate;
        this.createdAt      = Instant.now();
        this.updatedAt      = Instant.now();
    }

    public String      getId()                { return id; }
    public String      getMerchantId()        { return merchantId; }
    public String      getStatus()            { return status; }
    public int         getTotalPaymentCount() { return totalPaymentCount; }
    public long        getGrossAmount()       { return grossAmount; }
    public long        getFeeAmount()         { return feeAmount; }
    public long        getNetAmount()         { return netAmount; }
    public String      getCurrency()          { return currency; }
    public LocalDate   getSettlementDate()    { return settlementDate; }
    public Instant     getSettledAt()         { return settledAt; }
    public boolean     isReconciled()         { return reconciled; }
    public List<SettlementItemEntity> getItems() { return items; }

    public void markReconciled() {
        this.reconciled = true;
        this.updatedAt  = Instant.now();
    }

    public void startProcessing() {
        this.status               = "PROCESSING";
        this.processingStartedAt  = Instant.now();
        this.updatedAt            = Instant.now();
    }

    public void markSettled(int itemCount, long gross, long fee) {
        this.status             = "SETTLED";
        this.totalPaymentCount  = itemCount;
        this.grossAmount        = gross;
        this.feeAmount          = fee;
        this.netAmount          = gross - fee;
        this.settledAt          = Instant.now();
        this.updatedAt          = Instant.now();
    }

    public void markFailed(String reason) {
        this.status        = "FAILED";
        this.failureReason = reason;
        this.updatedAt     = Instant.now();
    }
}
