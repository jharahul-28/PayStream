package com.paystream.payment.api.controller;

import com.paystream.common.api.ApiResponse;
import com.paystream.common.exception.ValidationException;
import com.paystream.payment.api.dto.request.InitiatePaymentRequest;
import com.paystream.payment.api.dto.request.RefundRequest;
import com.paystream.payment.api.dto.response.PaymentResponse;
import com.paystream.payment.application.port.in.PaymentUseCase;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for payment operations.
 * Each method is a one-line delegation — zero business logic.
 * X-Idempotency-Key is mandatory for POST /payments (400 if absent).
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentUseCase paymentUseCase;

    public PaymentController(PaymentUseCase paymentUseCase) {
        this.paymentUseCase = paymentUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InitiatePaymentRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("X-Idempotency-Key header is required");
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(paymentUseCase.initiatePayment(userId, idempotencyKey, request)));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @PathVariable String paymentId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(paymentUseCase.getPayment(paymentId, userId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> listPayments(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(paymentUseCase.listPayments(userId, pageable)));
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(
            @PathVariable String paymentId,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(paymentUseCase.refund(paymentId, userId, request)));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> listAllPayments(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(paymentUseCase.listAllPayments(pageable)));
    }
}
