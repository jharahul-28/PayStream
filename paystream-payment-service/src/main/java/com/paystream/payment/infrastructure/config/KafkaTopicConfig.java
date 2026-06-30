package com.paystream.payment.infrastructure.config;

import com.paystream.common.constant.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares all Kafka topics owned by or consumed from payment-service.
 * Partitioned by userId so all events for one user arrive at the same consumer thread.
 * DLQ/retry topics are auto-created by @RetryableTopic in consumers.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic paymentsInitiatedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENTS_INITIATED).partitions(32).replicas(1).build();
    }

    @Bean
    public NewTopic paymentsCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENTS_COMPLETED).partitions(32).replicas(1).build();
    }

    @Bean
    public NewTopic paymentsFailedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENTS_FAILED).partitions(32).replicas(1).build();
    }

    @Bean
    public NewTopic walletDebitedTopic() {
        return TopicBuilder.name(KafkaTopics.WALLET_DEBITED).partitions(32).replicas(1).build();
    }

    @Bean
    public NewTopic walletCreditedTopic() {
        return TopicBuilder.name(KafkaTopics.WALLET_CREDITED).partitions(32).replicas(1).build();
    }

    @Bean
    public NewTopic fraudCheckRequestedTopic() {
        return TopicBuilder.name(KafkaTopics.FRAUD_CHECK_REQUESTED).partitions(16).replicas(1).build();
    }

    @Bean
    public NewTopic fraudScoreComputedTopic() {
        return TopicBuilder.name(KafkaTopics.FRAUD_SCORE_COMPUTED).partitions(16).replicas(1).build();
    }

    @Bean
    public NewTopic notificationsSendTopic() {
        return TopicBuilder.name(KafkaTopics.NOTIFICATIONS_SEND).partitions(8).replicas(1).build();
    }

    @Bean
    public NewTopic webhooksDeliveryTopic() {
        return TopicBuilder.name(KafkaTopics.WEBHOOKS_DELIVERY).partitions(8).replicas(1).build();
    }

    @Bean
    public NewTopic settlementsBatchTriggerTopic() {
        return TopicBuilder.name(KafkaTopics.SETTLEMENTS_BATCH_TRIGGER).partitions(4).replicas(1).build();
    }

    @Bean
    public NewTopic auditEventsTopic() {
        return TopicBuilder.name(KafkaTopics.AUDIT_EVENTS).partitions(16).replicas(1).build();
    }
}
