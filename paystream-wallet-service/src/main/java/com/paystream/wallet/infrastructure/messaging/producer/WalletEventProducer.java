package com.paystream.wallet.infrastructure.messaging.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.common.constant.KafkaTopics;
import com.paystream.common.event.BaseEvent;
import com.paystream.common.event.wallet.WalletCreditedEvent;
import com.paystream.common.event.wallet.WalletDebitedEvent;
import com.paystream.common.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publishes wallet domain events to Kafka.
 *
 * CRITICAL: must be called AFTER the @Transactional boundary closes.
 * Publishing inside a transaction risks sending an event that later rolls back.
 * Callers in WalletApplicationService use TransactionSynchronization.afterCommit()
 * to ensure the event is sent only on successful commit.
 */
@Component
public class WalletEventProducer {

    private static final Logger log = LoggerFactory.getLogger(WalletEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public WalletEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper  = objectMapper;
    }

    public void publishDebited(WalletDebitedEvent payload) {
        publish(KafkaTopics.WALLET_DEBITED, payload.walletId(),
                "WalletDebited", "1.0", payload);
    }

    public void publishCredited(WalletCreditedEvent payload) {
        publish(KafkaTopics.WALLET_CREDITED, payload.walletId(),
                "WalletCredited", "1.0", payload);
    }

    private <T> void publish(String topic, String partitionKey,
                             String eventType, String version, T payload) {
        try {
            BaseEvent<T> envelope = new BaseEvent<>(
                    IdGenerator.generate(), eventType, version, Instant.now(),
                    MDC.get("correlationId"), "wallet-service", payload);

            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, partitionKey, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka send failed topic={} key={} eventType={} error={}",
                                    topic, partitionKey, eventType, ex.getMessage());
                        } else {
                            log.debug("Kafka published topic={} key={} eventType={} offset={}",
                                    topic, partitionKey, eventType,
                                    result.getRecordMetadata().offset());
                        }
                    });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize wallet event eventType={} key={} error={}",
                    eventType, partitionKey, e.getMessage());
        }
    }
}
