package com.paystream.common.fraud;

import java.util.List;

/**
 * The deterministic fraud decision returned synchronously by fraud-service Stage 1.
 * AI enrichment (Stage 2) is async and NEVER modifies this result.
 */
public record FraudCheckResult(
        String      fraudCheckId,
        FraudDecision decision,
        int         riskScore,
        List<String> flags
) {

    public enum FraudDecision {
        ALLOW, BLOCK, REVIEW
    }

    public boolean isBlocked() {
        return decision == FraudDecision.BLOCK;
    }

    public boolean requiresReview() {
        return decision == FraudDecision.REVIEW;
    }

    /** Convenience factory — used as a safe fallback when the fraud circuit is open. */
    public static FraudCheckResult allow(int score) {
        return new FraudCheckResult("fallback", FraudDecision.ALLOW, score, List.of());
    }
}
