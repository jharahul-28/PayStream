package com.paystream.webhook.infrastructure.messaging.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.common.constant.KafkaTopics;
import com.paystream.common.event.BaseEvent;
import com.paystream.common.event.webhook.WebhookDispatchRequestedEvent;
import com.paystream.webhook.infrastructure.persistence.entity.WebhookDeliveryEntity;
import com.paystream.webhook.infrastructure.persistence.repository.WebhookDeliveryJpaRepository;
import com.paystream.webhook.infrastructure.signing.HmacSignatureService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Delivers webhook payloads to merchant endpoints with HMAC-SHA256 signing.
 *
 * Retry strategy:
 *   5 attempts, backoff: 60s → 300s → 1500s → 7500s → 21600s (6h)
 *
 * 4xx client errors: mark EXHAUSTED immediately, do NOT throw — prevents Kafka retry.
 *   Rationale: merchant endpoint returned a definitive client error; retrying won't help.
 *
 * 5xx server errors or timeouts: throw exception → Kafka retries per @RetryableTopic config.
 *   Rationale: transient server-side failure; retry may succeed.
 *
 * HMAC signature: X-PayStream-Signature: sha256={sig}  (signing input: "{ts}.{body}")
 *                 X-PayStream-Timestamp: {unix_ts}
 */
@Component
public class WebhookDeliveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryConsumer.class);

    private final WebhookDeliveryJpaRepository deliveryRepo;
    private final HmacSignatureService         signatureService;
    private final RestTemplate                 restTemplate;
    private final ObjectMapper                 objectMapper;
    private final Counter                      dlqCounter;

    public WebhookDeliveryConsumer(WebhookDeliveryJpaRepository deliveryRepo,
                                   HmacSignatureService signatureService,
                                   ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry) {
        this.deliveryRepo     = deliveryRepo;
        this.signatureService = signatureService;
        this.objectMapper     = objectMapper;
        this.restTemplate     = new RestTemplate();
        this.dlqCounter = Counter.builder("kafka.dlq.messages.total")
                .tag("topic", "webhooks")
                .register(meterRegistry);
    }

    @RetryableTopic(
            attempts = "5",
            backoff = @Backoff(delay = 60000, multiplier = 5.0, maxDelay = 21600000),
            dltTopicSuffix = ".dlq",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "false"
    )
    @KafkaListener(
            topics = KafkaTopics.WEBHOOKS_DELIVERY,
            groupId = "webhook-service-delivery"
    )
    @Transactional
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        BaseEvent<WebhookDispatchRequestedEvent> event = objectMapper.readValue(
                record.value(), new TypeReference<>() {});
        WebhookDispatchRequestedEvent payload = event.payload();

        WebhookDeliveryEntity delivery = deliveryRepo.findById(payload.deliveryId())
                .orElseGet(() -> {
                    WebhookDeliveryEntity d = new WebhookDeliveryEntity(
                            payload.deliveryId(), payload.endpointId(),
                            payload.eventType(), payload.payloadJson());
                    return deliveryRepo.save(d);
                });

        long timestamp = Instant.now().getEpochSecond();
        String signature = signatureService.signatureHeader(payload.secret(), timestamp, payload.payloadJson());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-PayStream-Signature", signature);
        headers.set("X-PayStream-Timestamp", String.valueOf(timestamp));

        HttpEntity<String> request = new HttpEntity<>(payload.payloadJson(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    payload.url(), HttpMethod.POST, request, String.class);

            delivery.markDelivered(response.getStatusCode().value(),
                    response.getBody());
            deliveryRepo.save(delivery);
            log.info("Webhook delivered deliveryId={} url={} status={}",
                    delivery.getId(), payload.url(), response.getStatusCode().value());

        } catch (HttpClientErrorException e) {
            // 4xx: EXHAUSTED — do NOT rethrow, prevents Kafka retry
            delivery.markExhausted("4xx client error: " + e.getStatusCode().value() + " " + e.getMessage());
            deliveryRepo.save(delivery);
            log.warn("Webhook 4xx exhausted deliveryId={} url={} status={}",
                    delivery.getId(), payload.url(), e.getStatusCode().value());
            // Intentional: no throw here

        } catch (Exception e) {
            // 5xx / timeout: mark failed and rethrow → triggers Kafka retry
            delivery.markFailed(0, null, e.getMessage());
            deliveryRepo.save(delivery);
            log.error("Webhook delivery failed deliveryId={} url={} error={}",
                    delivery.getId(), payload.url(), e.getMessage());
            throw e;
        }
    }

    @DltHandler
    @Transactional
    public void handleDlq(ConsumerRecord<String, String> record, Exception exception) {
        log.error("Webhook DLQ topic={} error={}", record.topic(),
                exception != null ? exception.getMessage() : "unknown");
        dlqCounter.increment();

        try {
            BaseEvent<WebhookDispatchRequestedEvent> event = objectMapper.readValue(
                    record.value(), new TypeReference<>() {});
            deliveryRepo.findById(event.payload().deliveryId()).ifPresent(d -> {
                d.markExhausted("DLQ: retry exhausted. Last error: " +
                        (exception != null ? exception.getMessage() : "unknown"));
                deliveryRepo.save(d);
            });
        } catch (Exception e) {
            log.error("DLQ handler failed to update delivery record: {}", e.getMessage());
        }
    }
}
