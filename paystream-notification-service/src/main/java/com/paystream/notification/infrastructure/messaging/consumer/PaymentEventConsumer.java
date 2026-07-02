package com.paystream.notification.infrastructure.messaging.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.common.constant.KafkaTopics;
import com.paystream.common.event.BaseEvent;
import com.paystream.common.event.payment.PaymentCompletedEvent;
import com.paystream.common.event.payment.PaymentFailedEvent;
import com.paystream.common.util.IdGenerator;
import com.paystream.notification.infrastructure.dispatcher.NotificationDispatcher;
import com.paystream.notification.infrastructure.persistence.entity.NotificationEntity;
import com.paystream.notification.infrastructure.persistence.entity.NotificationProcessedEventEntity;
import com.paystream.notification.infrastructure.persistence.repository.NotificationJpaRepository;
import com.paystream.notification.infrastructure.persistence.repository.NotificationProcessedEventJpaRepository;
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consumes payment completed/failed events and dispatches notifications.
 *
 * Strategy pattern: dispatcher is selected by notification type (EMAIL/SMS/PUSH).
 * Idempotency: notification_processed_events table checked before processing.
 * Retry: 3 attempts with exponential backoff (5s → 300s).
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final NotificationJpaRepository              notificationRepo;
    private final NotificationProcessedEventJpaRepository processedRepo;
    private final Map<String, NotificationDispatcher>    dispatchers;
    private final ObjectMapper                           objectMapper;

    public PaymentEventConsumer(NotificationJpaRepository notificationRepo,
                                NotificationProcessedEventJpaRepository processedRepo,
                                List<NotificationDispatcher> dispatcherList,
                                ObjectMapper objectMapper) {
        this.notificationRepo = notificationRepo;
        this.processedRepo    = processedRepo;
        this.objectMapper     = objectMapper;
        this.dispatchers = dispatcherList.stream()
                .collect(Collectors.toMap(NotificationDispatcher::supportedType, Function.identity()));
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 5000, multiplier = 6.0, maxDelay = 300000),
            dltTopicSuffix = ".dlq",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "false"
    )
    @KafkaListener(
            topics = { KafkaTopics.PAYMENTS_COMPLETED, KafkaTopics.PAYMENTS_FAILED },
            groupId = "notification-service-payment-events"
    )
    @Transactional
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        String topic   = record.topic();
        String payload = record.value();

        if (KafkaTopics.PAYMENTS_COMPLETED.equals(topic)) {
            BaseEvent<PaymentCompletedEvent> event = objectMapper.readValue(
                    payload, new TypeReference<>() {});
            if (processedRepo.existsById(event.eventId())) {
                log.info("Duplicate PaymentCompleted notification event skipped eventId={}", event.eventId());
                return;
            }
            handleCompleted(event.eventId(), event.payload());
            processedRepo.save(new NotificationProcessedEventEntity(event.eventId()));

        } else if (KafkaTopics.PAYMENTS_FAILED.equals(topic)) {
            BaseEvent<PaymentFailedEvent> event = objectMapper.readValue(
                    payload, new TypeReference<>() {});
            if (processedRepo.existsById(event.eventId())) {
                log.info("Duplicate PaymentFailed notification event skipped eventId={}", event.eventId());
                return;
            }
            handleFailed(event.eventId(), event.payload());
            processedRepo.save(new NotificationProcessedEventEntity(event.eventId()));
        }
    }

    private void handleCompleted(String eventId, PaymentCompletedEvent evt) {
        NotificationEntity notification = new NotificationEntity(
                IdGenerator.generate(), evt.userId(), evt.paymentId(),
                "EMAIL", "PAYMENT_SUCCESS", "PENDING",
                evt.userId() + "@paystream.local",
                "Payment Successful",
                String.format("Your payment of %d %s (ID: %s) was completed successfully.",
                        evt.amount(), evt.currency(), evt.paymentId()));

        notificationRepo.save(notification);
        dispatchAndUpdate(notification);
    }

    private void handleFailed(String eventId, PaymentFailedEvent evt) {
        NotificationEntity notification = new NotificationEntity(
                IdGenerator.generate(), evt.userId(), evt.paymentId(),
                "EMAIL", "PAYMENT_FAILED", "PENDING",
                evt.userId() + "@paystream.local",
                "Payment Failed",
                String.format("Your payment of %d %s (ID: %s) failed. Reason: %s.",
                        evt.amount(), evt.currency(), evt.paymentId(), evt.failureReason()));

        notificationRepo.save(notification);
        dispatchAndUpdate(notification);
    }

    private void dispatchAndUpdate(NotificationEntity notification) {
        NotificationDispatcher dispatcher = dispatchers.get(notification.getType());
        if (dispatcher == null) {
            log.warn("No dispatcher for type={} notificationId={}", notification.getType(), notification.getId());
            notification.markFailed("No dispatcher for type: " + notification.getType());
            notificationRepo.save(notification);
            return;
        }
        try {
            dispatcher.dispatch(notification);
            notification.markSent();
        } catch (Exception e) {
            log.error("Dispatch failed notificationId={} type={} error={}",
                    notification.getId(), notification.getType(), e.getMessage());
            notification.markFailed(e.getMessage());
        }
        notificationRepo.save(notification);
    }

    @DltHandler
    public void handleDlq(ConsumerRecord<String, String> record, Exception exception) {
        log.error("DLQ notification event topic={} error={}", record.topic(),
                exception != null ? exception.getMessage() : "unknown");
    }
}
