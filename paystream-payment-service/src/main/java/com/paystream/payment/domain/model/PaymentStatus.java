package com.paystream.payment.domain.model;

/**
 * Payment lifecycle states.
 *
 * Valid transitions (enforced by {@link Payment#transitionTo}):
 *   PENDING     -> PROCESSING
 *   PROCESSING  -> COMPLETED
 *   PROCESSING  -> FAILED
 *   COMPLETED   -> REFUNDED
 *   PENDING     -> CANCELLED
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REFUNDED,
    CANCELLED
}
