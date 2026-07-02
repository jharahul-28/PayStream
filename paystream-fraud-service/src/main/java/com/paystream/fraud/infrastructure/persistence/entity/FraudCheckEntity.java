package com.paystream.fraud.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "fraud_checks")
public class FraudCheckEntity {

    @Id
    @Column(length = 26, nullable = false, updatable = false)
    private String id;

    @Column(name = "payment_id", length = 26, nullable = false, unique = true)
    private String paymentId;

    @Column(name = "user_id", length = 26, nullable = false)
    private String userId;

    @Column(name = "risk_score", nullable = false)
    private short riskScore;

    @Column(name = "decision", length = 10, nullable = false)
    private String decision;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "flags", columnDefinition = "text[]", nullable = false)
    private List<String> flags;

    @Column(name = "rule_version", length = 20, nullable = false)
    private String ruleVersion;

    @Column(name = "ai_narrative", columnDefinition = "TEXT")
    private String aiNarrative;

    @Column(name = "ai_risk_score")
    private Short aiRiskScore;

    @Column(name = "ai_confidence", precision = 3, scale = 2)
    private java.math.BigDecimal aiConfidence;

    @Column(name = "ai_processed", nullable = false)
    private boolean aiProcessed = false;

    @Column(name = "ai_processing_error", length = 500)
    private String aiProcessingError;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FraudCheckEntity() {}

    public FraudCheckEntity(String id, String paymentId, String userId,
                            short riskScore, String decision, List<String> flags,
                            String ruleVersion, Long processingTimeMs, Instant createdAt) {
        this.id               = id;
        this.paymentId        = paymentId;
        this.userId           = userId;
        this.riskScore        = riskScore;
        this.decision         = decision;
        this.flags            = flags;
        this.ruleVersion      = ruleVersion;
        this.processingTimeMs = processingTimeMs;
        this.createdAt        = createdAt;
    }

    // Getters and setters
    public String       getId()                { return id; }
    public String       getPaymentId()         { return paymentId; }
    public String       getUserId()            { return userId; }
    public short        getRiskScore()         { return riskScore; }
    public String       getDecision()          { return decision; }
    public List<String> getFlags()             { return flags; }
    public String       getRuleVersion()       { return ruleVersion; }
    public String       getAiNarrative()       { return aiNarrative; }
    public Short        getAiRiskScore()       { return aiRiskScore; }
    public java.math.BigDecimal getAiConfidence() { return aiConfidence; }
    public boolean      isAiProcessed()        { return aiProcessed; }
    public String       getAiProcessingError() { return aiProcessingError; }
    public Long         getProcessingTimeMs()  { return processingTimeMs; }
    public Instant      getCreatedAt()         { return createdAt; }

    public void setAiNarrative(String v)          { this.aiNarrative = v; }
    public void setAiRiskScore(Short v)            { this.aiRiskScore = v; }
    public void setAiConfidence(java.math.BigDecimal v) { this.aiConfidence = v; }
    public void setAiProcessed(boolean v)         { this.aiProcessed = v; }
    public void setAiProcessingError(String v)    { this.aiProcessingError = v; }
}
