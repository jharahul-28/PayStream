package com.paystream.fraud.application.port.in;

import com.paystream.common.fraud.FraudCheckResult;
import com.paystream.fraud.application.command.EvaluateFraudCommand;

public interface FraudEvaluationUseCase {

    /**
     * Stage 1 — synchronous, deterministic rules engine.
     * Target: < 5ms p99.
     * Returns the immutable ALLOW/BLOCK/REVIEW decision.
     * AI enrichment (Stage 2) occurs asynchronously and NEVER overrides this result.
     */
    FraudCheckResult evaluate(EvaluateFraudCommand command);
}
