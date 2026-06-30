package com.paystream.ledger.api.dto.request;

import com.paystream.ledger.domain.model.EntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A single leg of a double-entry transaction. */
public record LedgerEntryRequest(
        @NotBlank(message = "accountId is required")
        String accountId,

        @NotNull(message = "entryType is required")
        EntryType entryType,

        @NotNull(message = "amount is required")
        Long amount,  // signed — negative for DEBIT, positive for CREDIT

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "[A-Z]{3}", message = "Currency must be 3-letter ISO 4217 code")
        String currency,

        @Size(max = 500)
        String description
) {}
