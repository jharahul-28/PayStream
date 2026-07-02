package com.paystream.common.exception;

import com.paystream.common.constant.ErrorCode;

/** Wallet balance cannot cover the requested amount (HTTP 422). */
public class InsufficientFundsException extends PayStreamException {

    private final long currentBalance;
    private final long requestedAmount;

    public InsufficientFundsException(long currentBalance, long requestedAmount) {
        super(ErrorCode.INSUFFICIENT_FUNDS,
              String.format("Insufficient funds: balance=%d, requested=%d", currentBalance, requestedAmount));
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }

    public long getCurrentBalance() {
        return currentBalance;
    }

    public long getRequestedAmount() {
        return requestedAmount;
    }
}
