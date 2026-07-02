package com.paystream.audit;

import com.paystream.audit.infrastructure.messaging.consumer.AuditEventConsumer;
import com.paystream.audit.infrastructure.persistence.entity.AuditLogEntity;
import com.paystream.audit.infrastructure.persistence.repository.AuditLogJpaRepository;
import com.paystream.common.event.BaseEvent;
import com.paystream.common.event.audit.AuditEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditConsumerIdempotencyTest {

    @Mock private AuditLogJpaRepository repository;

    @Test
    void sameEventIdTwice_onlyOneInsert() {
        AuditEventConsumer consumer = new AuditEventConsumer(repository, new SimpleMeterRegistry());

        BaseEvent<AuditEvent> event1 = createEvent("event-001");
        BaseEvent<AuditEvent> event2 = createEvent("event-001"); // same eventId

        // First call — not found
        when(repository.findByEventId("event-001")).thenReturn(Optional.empty());
        consumer.consume(event1);

        // Second call — found (idempotent)
        when(repository.findByEventId("event-001")).thenReturn(Optional.of(mock(AuditLogEntity.class)));
        consumer.consume(event2);

        // save called exactly once
        verify(repository, times(1)).save(any());
    }

    @Test
    void uniqueEventId_persistsSuccessfully() {
        AuditEventConsumer consumer = new AuditEventConsumer(repository, new SimpleMeterRegistry());
        when(repository.findByEventId(anyString())).thenReturn(Optional.empty());

        consumer.consume(createEvent("event-999"));

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("event-999");
        assertThat(captor.getValue().getAction()).isEqualTo("PAYMENT_COMPLETED");
    }

    private BaseEvent<AuditEvent> createEvent(String eventId) {
        AuditEvent payload = new AuditEvent(
                eventId, "PaymentCompleted", "payment-1", "PAYMENT",
                "user-1", "CUSTOMER", "PAYMENT_COMPLETED",
                null, "{\"status\":\"COMPLETED\"}", null,
                "corr-1", "payment-service", null
        );
        return new BaseEvent<>(
                "base-" + eventId, "AuditEvent", "1.0",
                Instant.now(), "corr-1", "payment-service", payload
        );
    }
}
