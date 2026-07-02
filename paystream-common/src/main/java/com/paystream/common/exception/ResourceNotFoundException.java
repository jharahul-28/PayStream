package com.paystream.common.exception;

import com.paystream.common.constant.ErrorCode;

/** Resource does not exist (HTTP 404). */
public class ResourceNotFoundException extends PayStreamException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public ResourceNotFoundException(String resourceType, String id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resourceType + " not found: " + id);
    }
}
