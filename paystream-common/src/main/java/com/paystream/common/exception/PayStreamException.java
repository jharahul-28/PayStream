package com.paystream.common.exception;

import com.paystream.common.constant.ErrorCode;

/**
 * Base exception for all PayStream domain and infrastructure errors.
 * Every thrown exception in the system must extend this class so that
 * {@code GlobalExceptionHandler} can map it to the correct HTTP status.
 */
public class PayStreamException extends RuntimeException {

    private final ErrorCode errorCode;

    public PayStreamException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PayStreamException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
