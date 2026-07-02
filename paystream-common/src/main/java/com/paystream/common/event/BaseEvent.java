package com.paystream.common.event;

import java.time.Instant;

/**
 * Envelope wrapping every Kafka message in PayStream.
 *
 * All consumers must check eventId for idempotency before processing payload.
 * sourceService enables event-source attribution in logs and dashboards.
 */
public record BaseEvent<T>(
        String  eventId,        // ULID — globally unique, time-sortable
        String  eventType,      // e.g. "PaymentCompleted"
        String  eventVersion,   // "1.0" — bump when payload schema changes
        Instant timestamp,
        String  correlationId,  // from MDC — propagated end-to-end
        String  sourceService,  // originating service name
        T       payload
) {}
