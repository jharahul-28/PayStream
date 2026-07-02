package com.paystream.common.exception;

import com.paystream.common.constant.ErrorCode;

/** Signals invalid input from the caller (HTTP 400). */
public class ValidationException extends PayStreamException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}
