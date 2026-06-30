package com.paystream.payment.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Request body for POST /api/v1/payments/{id}/refund. Partial refunds are supported. */
public record RefundRequest(
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        @Max(value = 100_000_000L, message = "amount exceeds maximum allowed")
        Long amount,

        @Size(max = 255)
        String reason
) {}
