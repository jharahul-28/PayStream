package com.paystream.common.event.fraud;

import java.util.List;

public record FraudScoreComputedEvent(
        String      fraudCheckId,
        String      paymentId,
        String      userId,
        int         riskScore,
        String      decision,          // ALLOW, BLOCK, REVIEW
        List<String> flags,
        Integer     aiRiskScore,       // null if AI not yet processed
        Double      aiConfidence,
        String      aiNarrative,
        String      correlationId
) {}
