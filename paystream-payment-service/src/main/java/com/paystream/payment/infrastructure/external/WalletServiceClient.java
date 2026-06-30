package com.paystream.payment.infrastructure.external;

import com.paystream.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for the wallet-service.
 * connectTimeout=2000ms, readTimeout=5000ms.
 * Retry is handled at the Feign level for 5xx only (configured in FeignConfig).
 * 4xx errors propagate directly — no retry.
 */
@FeignClient(
        name = "paystream-wallet-service",
        fallbackFactory = WalletFallbackFactory.class,
        configuration = FeignConfig.class
)
public interface WalletServiceClient {

    @PostMapping("/api/v1/wallets/{walletId}/debit")
    ApiResponse<WalletResponse> debit(
            @PathVariable("walletId") String walletId,
            @RequestHeader("X-Internal-Service-Key") String internalKey,
            @RequestBody DebitCreditBody body
    );

    @PostMapping("/api/v1/wallets/{walletId}/credit")
    ApiResponse<WalletResponse> credit(
            @PathVariable("walletId") String walletId,
            @RequestHeader("X-Internal-Service-Key") String internalKey,
            @RequestBody DebitCreditBody body
    );

    /** Minimal inline DTO to avoid cross-service class sharing. */
    record DebitCreditBody(long amount, String currency, String reason) {}

    /** Minimal inline response DTO — only the fields payment-service needs. */
    record WalletResponse(String id, long balance, String currency, String status) {}
}
