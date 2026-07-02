package com.paystream.common.exception;

import com.paystream.common.constant.ErrorCode;

/** Signals a business rule violation (HTTP 422). */
public class DomainException extends PayStreamException {

    public DomainException(String message) {
        super(ErrorCode.INVALID_STATE_TRANSITION, message);
    }

    public DomainException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
