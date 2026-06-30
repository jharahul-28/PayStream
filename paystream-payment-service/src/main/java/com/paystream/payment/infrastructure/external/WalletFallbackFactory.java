package com.paystream.payment.infrastructure.external;

import com.paystream.common.exception.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback factory for WalletServiceClient.
 * Logs the upstream cause with correlationId and throws ExternalServiceException
 * so payment orchestration can apply compensation logic.
 */
@Component
public class WalletFallbackFactory implements FallbackFactory<WalletServiceClient> {

    private static final Logger log = LoggerFactory.getLogger(WalletFallbackFactory.class);

    @Override
    public WalletServiceClient create(Throwable cause) {
        return new WalletServiceClient() {
            @Override
            public com.paystream.common.api.ApiResponse<WalletResponse> debit(
                    String walletId, String internalKey, DebitCreditBody body) {
                log.error("wallet-service debit fallback walletId={} correlationId={} cause={}",
                        walletId, MDC.get("correlationId"), cause.getMessage());
                throw new ExternalServiceException("wallet-service", cause.getMessage(), cause);
            }

            @Override
            public com.paystream.common.api.ApiResponse<WalletResponse> credit(
                    String walletId, String internalKey, DebitCreditBody body) {
                log.error("wallet-service credit fallback walletId={} correlationId={} cause={}",
                        walletId, MDC.get("correlationId"), cause.getMessage());
                throw new ExternalServiceException("wallet-service", cause.getMessage(), cause);
            }
        };
    }
}
