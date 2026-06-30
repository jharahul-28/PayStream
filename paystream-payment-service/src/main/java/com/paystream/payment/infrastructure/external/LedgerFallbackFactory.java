package com.paystream.payment.infrastructure.external;

import com.paystream.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback for LedgerServiceClient.
 *
 * IMPORTANT: per the spec, a ledger failure AFTER a COMPLETED payment must NOT reverse wallets.
 * The fallback logs LEDGER_INCONSISTENCY at CRITICAL level and returns null — the caller decides next steps.
 */
@Component
public class LedgerFallbackFactory implements FallbackFactory<LedgerServiceClient> {

    private static final Logger log = LoggerFactory.getLogger(LedgerFallbackFactory.class);

    @Override
    public LedgerServiceClient create(Throwable cause) {
        return (internalKey, body) -> {
            log.error("LEDGER INCONSISTENCY referenceId={} correlationId={} cause={}",
                    body != null ? body.referenceId() : "unknown",
                    MDC.get("correlationId"), cause.getMessage(), cause);
            // Return null — caller (PaymentApplicationService) logs this and does NOT reverse wallets
            return null;
        };
    }
}
