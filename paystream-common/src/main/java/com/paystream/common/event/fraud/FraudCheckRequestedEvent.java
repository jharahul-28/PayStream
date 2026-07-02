package com.paystream.common.event.fraud;

import java.time.Instant;

public record FraudCheckRequestedEvent(
        String paymentId,
        String userId,
        long   amount,
        String currency,
        String sourceWalletId,
        String destinationWalletId,
        String deviceId,
        String ipAddress,
        Instant paymentCreatedAt,
        String correlationId
) {}
