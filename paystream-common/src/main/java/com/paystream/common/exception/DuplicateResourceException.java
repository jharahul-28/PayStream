package com.paystream.common.exception;

import com.paystream.common.constant.ErrorCode;

/** Resource already exists — idempotent re-creation attempted (HTTP 409). */
public class DuplicateResourceException extends PayStreamException {

    public DuplicateResourceException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message);
    }
}
