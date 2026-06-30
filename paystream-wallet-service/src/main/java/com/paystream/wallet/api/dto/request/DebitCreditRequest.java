package com.paystream.wallet.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request body for wallet debit or credit operations. */
public record DebitCreditRequest(
        @NotNull(message = "Amount is required")
        @Min(value = 1, message = "Amount must be at least 1 minor unit")
        Long amount,

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter ISO 4217 code")
        String currency,

        @Size(max = 255, message = "Reason must not exceed 255 characters")
        String reason
) {}
