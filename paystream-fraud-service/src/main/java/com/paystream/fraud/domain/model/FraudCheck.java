package com.paystream.fraud.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Pure domain model — zero framework annotations.
 * Represents the complete fraud evaluation for a single payment.
 *
 * Stage 1 (deterministic rules) populates: riskScore, decision, flags, ruleVersion.
 * Stage 2 (async AI) populates: aiNarrative, aiRiskScore, aiConfidence, aiProcessed.
 * Stage 2 NEVER changes the decision — that is set once by Stage 1 and is immutable.
 */
public class FraudCheck {

    private final String      id;
    private final String      paymentId;
    private final String      userId;
    private final int         riskScore;
    private final FraudDecision decision;
    private final List<String>  flags;
    private final String      ruleVersion;
    private final Long        processingTimeMs;

    // Stage 2 — mutable, populated asynchronously
    private String  aiNarrative;
    private Integer aiRiskScore;
    private Double  aiConfidence;
    private boolean aiProcessed;
    private String  aiProcessingError;

    private final Instant createdAt;

    public FraudCheck(String id, String paymentId, String userId,
                      int riskScore, FraudDecision decision, List<String> flags,
                      String ruleVersion, Long processingTimeMs, Instant createdAt) {
        this.id              = id;
        this.paymentId       = paymentId;
        this.userId          = userId;
        this.riskScore       = riskScore;
        this.decision        = decision;
        this.flags           = List.copyOf(flags);
        this.ruleVersion     = ruleVersion;
        this.processingTimeMs = processingTimeMs;
        this.aiProcessed     = false;
        this.createdAt       = createdAt;
    }

    /** Called by Stage 2 AI enrichment — only updates narrative fields, never the decision. */
    public void enrichWithAi(String narrative, int aiScore, double confidence) {
        this.aiNarrative   = narrative;
        this.aiRiskScore   = aiScore;
        this.aiConfidence  = confidence;
        this.aiProcessed   = true;
        this.aiProcessingError = null;
    }

    public void markAiError(String errorMessage) {
        this.aiProcessingError = errorMessage;
    }

    // Accessors
    public String       getId()               { return id; }
    public String       getPaymentId()        { return paymentId; }
    public String       getUserId()           { return userId; }
    public int          getRiskScore()        { return riskScore; }
    public FraudDecision getDecision()        { return decision; }
    public List<String> getFlags()            { return flags; }
    public String       getRuleVersion()      { return ruleVersion; }
    public Long         getProcessingTimeMs() { return processingTimeMs; }
    public String       getAiNarrative()      { return aiNarrative; }
    public Integer      getAiRiskScore()      { return aiRiskScore; }
    public Double       getAiConfidence()     { return aiConfidence; }
    public boolean      isAiProcessed()       { return aiProcessed; }
    public String       getAiProcessingError(){ return aiProcessingError; }
    public Instant      getCreatedAt()        { return createdAt; }
}
