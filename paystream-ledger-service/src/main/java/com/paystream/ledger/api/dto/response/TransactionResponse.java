package com.paystream.ledger.api.dto.response;

import java.util.List;

/**
 * Both legs of a double-entry transaction plus an integrity flag.
 * integrityValid is true when debit.amount + credit.amount == 0.
 */
public record TransactionResponse(
        String                    referenceId,
        String                    referenceType,
        List<LedgerEntryResponse> entries,
        boolean                   integrityValid
) {}
