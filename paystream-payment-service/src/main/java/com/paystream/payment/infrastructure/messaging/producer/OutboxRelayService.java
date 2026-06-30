package com.paystream.payment.infrastructure.messaging.producer;

import com.paystream.common.constant.KafkaTopics;
import com.paystream.payment.infrastructure.persistence.entity.OutboxEventEntity;
import com.paystream.payment.infrastructure.persistence.repository.OutboxEventJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Polls outbox_events WHERE published=FALSE and publishes them to Kafka.
 *
 * FOR UPDATE SKIP LOCKED ensures that in a multi-pod deployment, two relay
 * instances never pick the same row. Each row uses REQUIRES_NEW so a single
 * Kafka failure does not roll back previously published rows in the same batch.
 *
 * Publish is synchronous (.get(5, SECONDS)) — we confirm delivery before
 * marking published=TRUE. If Kafka is down the row stays unpublished and
 * the next poll picks it up.
 */
@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private static final int  BATCH_SIZE        = 50;
    private static final long PUBLISH_TIMEOUT_S = 5L;
    private static final long PENDING_WARN_THRESHOLD = 1000L;

    private final OutboxEventJpaRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Counter publishedCounter;

    // Maps event_type -> topic. No if/else chains.
    private static final Map<String, String> EVENT_TYPE_TO_TOPIC = Map.of(
            "PaymentInitiated",  KafkaTopics.PAYMENTS_INITIATED,
            "PaymentCompleted",  KafkaTopics.PAYMENTS_COMPLETED,
            "PaymentFailed",     KafkaTopics.PAYMENTS_FAILED
    );

    public OutboxRelayService(OutboxEventJpaRepository outboxRepo,
                              KafkaTemplate<String, String> kafkaTemplate,
                              MeterRegistry meterRegistry) {
        this.outboxRepo    = outboxRepo;
        this.kafkaTemplate = kafkaTemplate;

        this.publishedCounter = Counter.builder("outbox.relay.published.total")
                .description("Total outbox events successfully published to Kafka")
                .register(meterRegistry);

        // Gauge reflects current pending count
        Gauge.builder("outbox.pending.count", outboxRepo, OutboxEventJpaRepository::countUnpublished)
                .description("Number of outbox events not yet published to Kafka")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 100, initialDelay = 1000)
    public void pollAndPublish() {
        List<OutboxEventEntity> batch = fetchBatch();
        if (batch.isEmpty()) return;

        long pending = outboxRepo.countUnpublished();
        if (pending > PENDING_WARN_THRESHOLD) {
            log.warn("Outbox backlog high pendingCount={}", pending);
        }

        for (OutboxEventEntity event : batch) {
            publishOne(event);
        }
    }

    @Transactional(readOnly = true)
    protected List<OutboxEventEntity> fetchBatch() {
        return outboxRepo.findUnpublishedForUpdate(BATCH_SIZE);
    }

    /**
     * Each row in its own REQUIRES_NEW transaction so failure on one row
     * does not affect rows already published in this batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(OutboxEventEntity event) {
        String topic = EVENT_TYPE_TO_TOPIC.get(event.getEventType());
        if (topic == null) {
            log.error("No topic mapping for eventType={} eventId={} — skipping", event.getEventType(), event.getId());
            return;
        }

        try {
            kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                    .get(PUBLISH_TIMEOUT_S, TimeUnit.SECONDS);

            event.markPublished();
            outboxRepo.save(event);
            publishedCounter.increment();

            log.debug("Outbox published eventId={} eventType={} topic={}", event.getId(), event.getEventType(), topic);

        } catch (Exception e) {
            log.error("Outbox publish failed eventId={} eventType={} topic={} error={}",
                    event.getId(), event.getEventType(), topic, e.getMessage());
            // Row stays published=FALSE — picked up on next poll cycle
        }
    }
}
