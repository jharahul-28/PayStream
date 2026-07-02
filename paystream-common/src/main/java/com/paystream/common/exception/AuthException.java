package com.paystream.common.exception;

import com.paystream.common.constant.ErrorCode;

/** JWT authentication failures — invalid token, expired token, or insufficient role. */
public class AuthException extends PayStreamException {

    public AuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static AuthException invalid(String detail) {
        return new AuthException(ErrorCode.AUTH_INVALID, "Invalid token: " + detail);
    }

    public static AuthException expired() {
        return new AuthException(ErrorCode.AUTH_EXPIRED, "Token has expired");
    }

    public static AuthException insufficientRole(String required) {
        return new AuthException(ErrorCode.AUTH_INSUFFICIENT_ROLE,
                "Required role not present: " + required);
    }
}
