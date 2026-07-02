package com.paystream.common.exception;

import com.paystream.common.constant.ErrorCode;

/** Downstream/external service call failed (HTTP 503). */
public class ExternalServiceException extends PayStreamException {

    public ExternalServiceException(String serviceName, String detail) {
        super(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
              String.format("External service '%s' unavailable: %s", serviceName, detail));
    }

    public ExternalServiceException(String serviceName, String detail, Throwable cause) {
        super(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
              String.format("External service '%s' unavailable: %s", serviceName, detail), cause);
    }
}
