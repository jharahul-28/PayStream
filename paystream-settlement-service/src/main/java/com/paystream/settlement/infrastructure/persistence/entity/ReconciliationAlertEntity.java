package com.paystream.settlement.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reconciliation_alerts")
public class ReconciliationAlertEntity {

    @Id
    @Column(length = 26, nullable = false, updatable = false)
    private String id;

    @Column(name = "batch_id", length = 26)
    private String batchId;

    @Column(name = "discrepancy_type", length = 50, nullable = false)
    private String discrepancyType;

    @Column(name = "expected_amount")
    private Long expectedAmount;

    @Column(name = "actual_amount")
    private Long actualAmount;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReconciliationAlertEntity() {}

    public ReconciliationAlertEntity(String id, String batchId, String discrepancyType,
                                      Long expectedAmount, Long actualAmount,
                                      String description, Instant createdAt) {
        this.id               = id;
        this.batchId          = batchId;
        this.discrepancyType  = discrepancyType;
        this.expectedAmount   = expectedAmount;
        this.actualAmount     = actualAmount;
        this.description      = description;
        this.resolved         = false;
        this.createdAt        = createdAt;
    }

    public String  getId()               { return id; }
    public String  getBatchId()          { return batchId; }
    public String  getDiscrepancyType()  { return discrepancyType; }
    public Long    getExpectedAmount()   { return expectedAmount; }
    public Long    getActualAmount()     { return actualAmount; }
    public String  getDescription()      { return description; }
    public boolean isResolved()          { return resolved; }
    public Instant getCreatedAt()        { return createdAt; }
}
