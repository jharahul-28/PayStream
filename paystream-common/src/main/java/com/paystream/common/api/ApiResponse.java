package com.paystream.common.api;

import com.paystream.common.constant.ErrorCode;

import java.time.Instant;

/**
 * Unified response envelope for every REST endpoint across all PayStream services.
 * Controllers MUST return {@code ResponseEntity<ApiResponse<T>>}; returning raw
 * domain objects or {@code Map<String,Object>} is a violation of the API contract.
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String errorCode,
        String errorMessage,
        String timestamp,
        String traceId
) {

    /** Successful response — data payload present, no error fields. */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null, Instant.now().toString(), null);
    }

    /** Successful response with explicit trace/correlation ID. */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(true, data, null, null, Instant.now().toString(), traceId);
    }

    /** Error response derived from a typed {@link ErrorCode}. */
    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, code.getCode(), message, Instant.now().toString(), null);
    }

    /** Error response with correlation ID for full traceability. */
    public static <T> ApiResponse<T> error(ErrorCode code, String message, String traceId) {
        return new ApiResponse<>(false, null, code.getCode(), message, Instant.now().toString(), traceId);
    }

    /** Generic error with a raw code string — prefer the typed overload. */
    public static <T> ApiResponse<T> error(String rawCode, String message, String traceId) {
        return new ApiResponse<>(false, null, rawCode, message, Instant.now().toString(), traceId);
    }
}
