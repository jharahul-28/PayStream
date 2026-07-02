package com.paystream.common.exception;

import com.paystream.common.api.ApiResponse;
import com.paystream.common.constant.ErrorCode;
import com.paystream.common.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralised exception-to-HTTP mapping for all PayStream services.
 *
 * Contract:
 *  - Stack traces are NEVER exposed to clients.
 *  - Every error response uses {@link ApiResponse#error}.
 *  - HTTP status codes are fixed by exception type.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // -------------------------------------------------------------------------
    // Validation errors (400)
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("Validation failed correlationId={} path={} errors={}",
                correlationId(), request.getRequestURI(), detail);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, detail, correlationId()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(ValidationException ex) {
        log.warn("Validation error correlationId={} message={}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    // -------------------------------------------------------------------------
    // Auth errors (401, 402, 403)
    // -------------------------------------------------------------------------

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthException ex) {
        log.warn("Auth error correlationId={} code={} message={}",
                correlationId(), ex.getErrorCode(), ex.getMessage());

        HttpStatus status = switch (ex.getErrorCode()) {
            case AUTH_EXPIRED           -> HttpStatus.UNAUTHORIZED;
            case AUTH_INVALID           -> HttpStatus.UNAUTHORIZED;
            case AUTH_INSUFFICIENT_ROLE -> HttpStatus.FORBIDDEN;
            default                     -> HttpStatus.UNAUTHORIZED;
        };

        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    // -------------------------------------------------------------------------
    // Resource errors (404, 409)
    // -------------------------------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found correlationId={} message={}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
        log.warn("Duplicate resource correlationId={} message={}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    // -------------------------------------------------------------------------
    // Financial domain errors (402, 422)
    // -------------------------------------------------------------------------

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientFunds(InsufficientFundsException ex) {
        log.warn("Insufficient funds correlationId={} balance={} requested={}",
                correlationId(), ex.getCurrentBalance(), ex.getRequestedAmount());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(FraudBlockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleFraudBlocked(FraudBlockedException ex) {
        log.warn("Fraud block correlationId={} message={}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex) {
        log.warn("Domain error correlationId={} code={} message={}",
                correlationId(), ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    // -------------------------------------------------------------------------
    // External service (503)
    // -------------------------------------------------------------------------

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleExternal(ExternalServiceException ex) {
        log.error("External service failure correlationId={} message={}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), correlationId()));
    }

    // -------------------------------------------------------------------------
    // Base PayStream exception catch-all (should not happen if above are complete)
    // -------------------------------------------------------------------------

    @ExceptionHandler(PayStreamException.class)
    public ResponseEntity<ApiResponse<Void>> handlePayStream(PayStreamException ex) {
        log.error("Unhandled PayStream exception correlationId={} code={} message={}",
                correlationId(), ex.getErrorCode(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", correlationId()));
    }

    // -------------------------------------------------------------------------
    // Last-resort (500)
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        // Log full exception server-side; never expose cause to client
        log.error("Unhandled exception correlationId={}", correlationId(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", correlationId()));
    }

    // -------------------------------------------------------------------------

    private static String correlationId() {
        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
        return id != null ? id : "none";
    }
}
