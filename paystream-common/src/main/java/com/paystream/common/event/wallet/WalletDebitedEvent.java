package com.paystream.common.event.wallet;

public record WalletDebitedEvent(
        String walletId,
        String userId,
        long   amount,
        String currency,
        long   balanceAfter,
        String referenceId,    // paymentId that caused this debit
        String referenceType,  // "PAYMENT", "REFUND", etc.
        String correlationId
) {}
