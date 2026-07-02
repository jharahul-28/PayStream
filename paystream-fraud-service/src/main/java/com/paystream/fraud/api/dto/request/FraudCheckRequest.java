package com.paystream.fraud.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record FraudCheckRequest(
        @NotBlank String paymentId,
        @NotBlank String userId,
        @Positive long   amount,
        @NotBlank String currency,
        @NotBlank String sourceWalletId,
        @NotBlank String destinationWalletId,
        String deviceId,
        String ipAddress
) {}
