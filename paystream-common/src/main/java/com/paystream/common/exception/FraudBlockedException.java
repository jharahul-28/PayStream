package com.paystream.common.exception;

import com.paystream.common.constant.ErrorCode;

/** Transaction blocked by fraud rules (HTTP 402). */
public class FraudBlockedException extends PayStreamException {

    public FraudBlockedException(String paymentId) {
        super(ErrorCode.FRAUD_BLOCKED, "Payment blocked by fraud prevention: " + paymentId);
    }

    public FraudBlockedException(String message, Throwable cause) {
        super(ErrorCode.FRAUD_BLOCKED, message, cause);
    }
}
