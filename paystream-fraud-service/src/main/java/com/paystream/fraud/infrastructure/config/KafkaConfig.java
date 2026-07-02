package com.paystream.fraud.infrastructure.config;

import com.paystream.common.constant.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic fraudCheckRequestedTopic() {
        return TopicBuilder.name(KafkaTopics.FRAUD_CHECK_REQUESTED).partitions(16).replicas(1).build();
    }

    @Bean
    public NewTopic fraudScoreComputedTopic() {
        return TopicBuilder.name(KafkaTopics.FRAUD_SCORE_COMPUTED).partitions(16).replicas(1).build();
    }

    @Bean
    public NewTopic fraudCheckRequestedDlqTopic() {
        return TopicBuilder.name(KafkaTopics.FRAUD_CHECK_REQUESTED + ".dlq").partitions(4).replicas(1).build();
    }

    @Bean
    public NewTopic auditEventsTopic() {
        return TopicBuilder.name(KafkaTopics.AUDIT_EVENTS).partitions(16).replicas(1).build();
    }
}
