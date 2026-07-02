package com.paystream.audit.infrastructure.messaging.consumer;

import com.paystream.common.constant.KafkaTopics;
import com.paystream.common.event.BaseEvent;
import com.paystream.common.event.audit.AuditEvent;
import com.paystream.common.util.IdGenerator;
import com.paystream.audit.infrastructure.persistence.entity.AuditLogEntity;
import com.paystream.audit.infrastructure.persistence.repository.AuditLogJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Consumes from audit.events and inserts idempotently into audit_log.
 * ON CONFLICT (event_id) DO NOTHING — guaranteed exactly-once via the unique constraint.
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditLogJpaRepository auditLogRepository;
    private final Counter               dlqCounter;
    private final Counter               insertedCounter;

    public AuditEventConsumer(AuditLogJpaRepository auditLogRepository,
                               MeterRegistry meterRegistry) {
        this.auditLogRepository = auditLogRepository;
        this.dlqCounter = Counter.builder("paystream.kafka.dlq.messages.total")
                .tag("topic", KafkaTopics.AUDIT_EVENTS)
                .register(meterRegistry);
        this.insertedCounter = Counter.builder("paystream.audit.events.inserted.total")
                .description("Audit log events successfully inserted")
                .register(meterRegistry);
    }

    @KafkaListener(topics = KafkaTopics.AUDIT_EVENTS, groupId = "audit-service")
    @RetryableTopic(
            attempts = "4",
            backoff  = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000),
            dltTopicSuffix = ".dlq",
            autoCreateTopics = "false"
    )
    @Transactional
    public void consume(BaseEvent<AuditEvent> event) {
        if (event == null || event.payload() == null) return;

        AuditEvent payload = event.payload();
        MDC.put("correlationId", event.correlationId());

        // Idempotency — ON CONFLICT via unique constraint on event_id
        if (auditLogRepository.findByEventId(payload.eventId()).isPresent()) {
            log.debug("Duplicate audit event eventId={} — skipping", payload.eventId());
            return;
        }

        AuditLogEntity entity = new AuditLogEntity(
                IdGenerator.generate(),
                payload.eventId(),
                payload.eventType(),
                payload.entityId(),
                payload.entityType(),
                payload.actorId(),
                payload.actorRole(),
                payload.action(),
                payload.oldStateJson(),
                payload.newStateJson(),
                payload.metadataJson(),
                payload.correlationId() != null ? payload.correlationId() : event.correlationId(),
                payload.sourceService() != null ? payload.sourceService() : event.sourceService(),
                payload.ipAddress(),
                Instant.now()
        );

        try {
            auditLogRepository.save(entity);
            insertedCounter.increment();
            log.debug("Audit event persisted eventId={} entityType={} action={}",
                    payload.eventId(), payload.entityType(), payload.action());
        } catch (DataIntegrityViolationException e) {
            // Race condition — concurrent insert of same event_id; safe to ignore
            log.debug("Audit event already exists eventId={} — concurrent insert ignored", payload.eventId());
        }
    }

    @DltHandler
    public void handleDlq(BaseEvent<?> event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, Exception ex) {
        log.error("DLQ event received topic={} eventId={} eventType={} error={}",
                topic, event.eventId(), event.eventType(), ex.getMessage());
        dlqCounter.increment();
    }
}
