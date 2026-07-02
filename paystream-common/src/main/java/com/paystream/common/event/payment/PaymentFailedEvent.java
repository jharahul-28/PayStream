package com.paystream.common.event.payment;

public record PaymentFailedEvent(
        String paymentId,
        String userId,
        String sourceWalletId,
        String destinationWalletId,
        long   amount,
        String currency,
        String failureReason,
        String failureCode,
        String correlationId
) {}
