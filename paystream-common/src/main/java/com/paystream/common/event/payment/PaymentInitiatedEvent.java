package com.paystream.common.event.payment;

public record PaymentInitiatedEvent(
        String paymentId,
        String userId,
        String sourceWalletId,
        String destinationWalletId,
        long   amount,
        String currency,
        String correlationId
) {}
