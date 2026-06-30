package com.paystream.wallet.application.command;

/** Command object passed from controller → service for debit or credit operations. */
public record DebitCreditCommand(
        String walletId,
        long   amount,
        String currency,
        String reason
) {}
