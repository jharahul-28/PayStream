package com.paystream.ledger.api.dto.response;

/** Balance computed from ledger entries (snapshot + incremental). */
public record BalanceResponse(
        String accountId,
        long   balance,
        String currency
) {}
