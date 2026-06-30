package com.paystream.ledger.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request to record a balanced double-entry transaction.
 * The service validates that debit.amount + credit.amount == 0 before persisting.
 */
public record DoubleEntryRequest(
        @NotBlank(message = "referenceId is required")
        String referenceId,

        @NotBlank(message = "referenceType is required")
        @Pattern(regexp = "PAYMENT|REFUND|TOPUP|FEE", message = "referenceType must be PAYMENT, REFUND, TOPUP or FEE")
        String referenceType,

        @NotNull(message = "debitEntry is required")
        @Valid
        LedgerEntryRequest debitEntry,

        @NotNull(message = "creditEntry is required")
        @Valid
        LedgerEntryRequest creditEntry
) {}
