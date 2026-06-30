package com.paystream.ledger.infrastructure.messaging.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.common.constant.KafkaTopics;
import com.paystream.common.event.BaseEvent;
import com.paystream.common.event.wallet.WalletCreditedEvent;
import com.paystream.common.event.wallet.WalletDebitedEvent;
import com.paystream.common.util.IdGenerator;
import com.paystream.ledger.application.port.in.LedgerUseCase;
import com.paystream.ledger.infrastructure.persistence.entity.DeadLetterEventEntity;
import com.paystream.ledger.infrastructure.persistence.entity.ProcessedEventEntity;
import com.paystream.ledger.infrastructure.persistence.repository.DeadLetterEventJpaRepository;
import com.paystream.ledger.infrastructure.persistence.repository.ProcessedEventJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes WalletDebited and WalletCredited events and writes single ledger entries.
 *
 * Idempotency: processed_events table is checked before every process.
 * Both the ledger save and the processed_events insert happen in the same @Transactional,
 * so there is no window where one commits without the other.
 *
 * Retry: @RetryableTopic provides 4 attempts with exponential backoff (1s → 30s).
 * After retry exhaustion, the message is routed to the .dlq topic and @DltHandler fires.
 *
 * Poison messages: deserialization errors are caught; malformed JSON is immediately
 * routed to DLQ without retrying (avoid burning retry quota on bad messages).
 */
@Component
public class WalletEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WalletEventConsumer.class);

    private final LedgerUseCase                 ledgerUseCase;
    private final ProcessedEventJpaRepository   processedEventsRepo;
    private final DeadLetterEventJpaRepository  deadLetterRepo;
    private final ObjectMapper                  objectMapper;
    private final Counter                       dlqCounter;

    public WalletEventConsumer(LedgerUseCase ledgerUseCase,
                               ProcessedEventJpaRepository processedEventsRepo,
                               DeadLetterEventJpaRepository deadLetterRepo,
                               ObjectMapper objectMapper,
                               MeterRegistry meterRegistry) {
        this.ledgerUseCase       = ledgerUseCase;
        this.processedEventsRepo = processedEventsRepo;
        this.deadLetterRepo      = deadLetterRepo;
        this.objectMapper        = objectMapper;
        this.dlqCounter = Counter.builder("kafka.dlq.messages.total")
                .tag("topic", "wallet")
                .description("Total messages routed to DLQ from wallet event topics")
                .register(meterRegistry);
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000),
            dltTopicSuffix = ".dlq",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "false"
    )
    @KafkaListener(
            topics = { KafkaTopics.WALLET_DEBITED, KafkaTopics.WALLET_CREDITED },
            groupId = "ledger-service-wallet-events",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        String topic   = record.topic();
        String payload = record.value();

        try {
            if (KafkaTopics.WALLET_DEBITED.equals(topic)) {
                BaseEvent<WalletDebitedEvent> event = objectMapper.readValue(
                        payload, new TypeReference<>() {});
                processDebited(event, payload, topic);
            } else if (KafkaTopics.WALLET_CREDITED.equals(topic)) {
                BaseEvent<WalletCreditedEvent> event = objectMapper.readValue(
                        payload, new TypeReference<>() {});
                processCredited(event, payload, topic);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Malformed JSON — routing directly to DLQ topic={} error={}", topic, e.getMessage());
            deadLetterRepo.save(new DeadLetterEventEntity(
                    null, "UNKNOWN", topic, payload, "JsonProcessingException: " + e.getMessage()));
            dlqCounter.increment();
            // Do NOT rethrow — prevents Kafka retry on a message that will always fail
        }
    }

    private void processDebited(BaseEvent<WalletDebitedEvent> event, String raw, String topic) {
        String eventId = event.eventId();

        if (processedEventsRepo.existsById(eventId)) {
            log.info("Duplicate WalletDebited event skipped eventId={}", eventId);
            return;
        }

        ledgerUseCase.recordDebitFromWalletEvent(event.payload());
        processedEventsRepo.save(new ProcessedEventEntity(eventId));

        log.info("WalletDebited processed eventId={} walletId={} amount={}",
                eventId, event.payload().walletId(), event.payload().amount());
    }

    private void processCredited(BaseEvent<WalletCreditedEvent> event, String raw, String topic) {
        String eventId = event.eventId();

        if (processedEventsRepo.existsById(eventId)) {
            log.info("Duplicate WalletCredited event skipped eventId={}", eventId);
            return;
        }

        ledgerUseCase.recordCreditFromWalletEvent(event.payload());
        processedEventsRepo.save(new ProcessedEventEntity(eventId));

        log.info("WalletCredited processed eventId={} walletId={} amount={}",
                eventId, event.payload().walletId(), event.payload().amount());
    }

    @DltHandler
    @Transactional
    public void handleDlq(ConsumerRecord<String, String> record, Exception exception) {
        String payload = record.value();
        String topic   = record.topic();

        log.error("DLQ event received topic={} error={} payload={}",
                topic, exception.getMessage(), payload);

        deadLetterRepo.save(new DeadLetterEventEntity(
                null, "DLQ", topic, payload,
                exception != null ? exception.getMessage() : "Unknown DLQ error"));
        dlqCounter.increment();
    }
}
