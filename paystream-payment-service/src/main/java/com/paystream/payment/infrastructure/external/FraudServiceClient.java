package com.paystream.payment.infrastructure.external;

import com.paystream.common.api.ApiResponse;
import com.paystream.common.fraud.FraudCheckResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for Stage 1 fraud check (synchronous, < 5ms target).
 * Circuit breaker is applied at the call site in PaymentApplicationService.
 */
@FeignClient(
        name = "fraud-service",
        fallbackFactory = FraudServiceFallbackFactory.class,
        configuration = FraudServiceFeignConfig.class
)
public interface FraudServiceClient {

    @PostMapping("/api/v1/fraud/check")
    ApiResponse<FraudCheckResult> check(
            @RequestHeader("X-Internal-Service-Key") String internalKey,
            @RequestBody FraudCheckRequest request
    );

    record FraudCheckRequest(
            String paymentId,
            String userId,
            long   amount,
            String currency,
            String sourceWalletId,
            String destinationWalletId,
            String deviceId,
            String ipAddress
    ) {}
}
