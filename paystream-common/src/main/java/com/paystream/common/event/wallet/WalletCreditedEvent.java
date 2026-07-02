package com.paystream.common.event.wallet;

public record WalletCreditedEvent(
        String walletId,
        String userId,
        long   amount,
        String currency,
        long   balanceAfter,
        String referenceId,
        String referenceType,
        String correlationId
) {}
