package com.paystream.payment.infrastructure.external;

import com.paystream.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Feign client for the ledger-service. Fallback logs LEDGER_INCONSISTENCY but does not fail the payment. */
@FeignClient(
        name = "paystream-ledger-service",
        fallbackFactory = LedgerFallbackFactory.class,
        configuration = FeignConfig.class
)
public interface LedgerServiceClient {

    @PostMapping("/api/v1/ledger/entries")
    ApiResponse<Void> createDoubleEntry(
            @RequestHeader("X-Internal-Service-Key") String internalKey,
            @RequestBody DoubleEntryBody body
    );

    record LedgerEntryBody(String accountId, String entryType, long amount, String currency, String description) {}

    record DoubleEntryBody(String referenceId, String referenceType,
                           LedgerEntryBody debitEntry, LedgerEntryBody creditEntry) {}
}
