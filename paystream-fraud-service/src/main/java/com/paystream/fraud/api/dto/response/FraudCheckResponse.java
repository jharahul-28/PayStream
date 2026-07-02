package com.paystream.fraud.api.dto.response;

import java.time.Instant;
import java.util.List;

public record FraudCheckResponse(
        String      fraudCheckId,
        String      paymentId,
        String      userId,
        int         riskScore,
        String      decision,
        List<String> flags,
        String      ruleVersion,
        Long        processingTimeMs,
        String      aiNarrative,
        Integer     aiRiskScore,
        Double      aiConfidence,
        boolean     aiProcessed,
        Instant     createdAt
) {}
