package com.paystream.fraud.application.command;

public record EvaluateFraudCommand(
        String paymentId,
        String userId,
        long   amount,
        String currency,
        String sourceWalletId,
        String destinationWalletId,
        String deviceId,
        String ipAddress
) {}
