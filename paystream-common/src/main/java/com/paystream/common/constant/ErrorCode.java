package com.paystream.common.constant;

/**
 * Canonical error codes for all PayStream services.
 * Every error returned to a client must use one of these codes.
 * No service may invent ad-hoc error strings.
 */
public enum ErrorCode {

    // 1xxx — input / resource errors
    VALIDATION_ERROR          ("PS-1001", "Validation failed"),
    RESOURCE_NOT_FOUND        ("PS-1002", "Resource not found"),
    DUPLICATE_RESOURCE        ("PS-1003", "Resource already exists"),

    // 2xxx — financial domain errors
    INSUFFICIENT_FUNDS        ("PS-2001", "Insufficient funds"),
    FRAUD_BLOCKED             ("PS-2002", "Transaction blocked by fraud prevention"),
    CONCURRENT_MODIFICATION   ("PS-2003", "Resource was concurrently modified"),
    INVALID_STATE_TRANSITION  ("PS-2004", "Invalid state transition"),

    // 3xxx — idempotency
    IDEMPOTENCY_CONFLICT      ("PS-3001", "Idempotency key conflict"),

    // 4xxx — authentication / authorisation
    AUTH_INVALID              ("PS-4001", "Invalid authentication credentials"),
    AUTH_EXPIRED              ("PS-4002", "Authentication token expired"),
    AUTH_INSUFFICIENT_ROLE    ("PS-4003", "Insufficient role for this operation"),

    // 5xxx — system / external
    INTERNAL_ERROR            ("PS-5001", "An internal error occurred"),
    EXTERNAL_SERVICE_UNAVAILABLE("PS-5002", "External service is unavailable");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    @Override
    public String toString() {
        return code;
    }
}
