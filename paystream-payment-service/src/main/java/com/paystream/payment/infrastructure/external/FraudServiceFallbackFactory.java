package com.paystream.payment.infrastructure.external;

import com.paystream.common.api.ApiResponse;
import com.paystream.common.fraud.FraudCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Feign fallback for fraud-service.
 * When the circuit is open, return FraudCheckResult.allow(0) to avoid blocking all payments.
 * This is logged as a WARN so oncall is aware.
 */
@Component
public class FraudServiceFallbackFactory implements FallbackFactory<FraudServiceClient> {

    private static final Logger log = LoggerFactory.getLogger(FraudServiceFallbackFactory.class);

    @Override
    public FraudServiceClient create(Throwable cause) {
        return (internalKey, request) -> {
            log.warn("Fraud circuit fallback — ALLOW passthrough paymentId={} correlationId={} cause={}",
                    request.paymentId(), MDC.get("correlationId"), cause.getMessage());
            return ApiResponse.success(FraudCheckResult.allow(0));
        };
    }
}
