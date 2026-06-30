package com.paystream.payment.api.dto.request;

import jakarta.validation.constraints.*;

/** Request body for POST /api/v1/payments. */
public record InitiatePaymentRequest(
        @NotBlank(message = "sourceWalletId is required")
        String sourceWalletId,

        @NotBlank(message = "destinationWalletId is required")
        String destinationWalletId,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        @Max(value = 100_000_000L, message = "amount exceeds maximum allowed")
        Long amount,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        @Size(max = 500, message = "note must not exceed 500 characters")
        String note
) {}
