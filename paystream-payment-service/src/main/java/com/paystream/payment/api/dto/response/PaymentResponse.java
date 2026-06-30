package com.paystream.payment.api.dto.response;

import com.paystream.payment.domain.model.PaymentStatus;

import java.time.Instant;

/** Immutable response record returned for all payment operations. */
public record PaymentResponse(
        String        id,
        String        userId,
        String        idempotencyKey,
        String        sourceWalletId,
        String        destinationWalletId,
        long          amount,
        String        currency,
        PaymentStatus status,
        String        failureReason,
        String        failureCode,
        String        note,
        Instant       createdAt,
        Instant       updatedAt
) {}
