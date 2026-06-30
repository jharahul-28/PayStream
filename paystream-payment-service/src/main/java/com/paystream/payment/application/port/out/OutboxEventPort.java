package com.paystream.payment.application.port.out;

/**
 * Output port for persisting outbox events within a payment transaction.
 * Implementations write to the outbox_events table in the same @Transactional
 * boundary as the payment status update.
 */
public interface OutboxEventPort {

    void save(String aggregateId, String aggregateType, String eventType, String payloadJson);
}
