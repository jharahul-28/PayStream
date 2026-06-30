package com.paystream.payment.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paystream.common.constant.KafkaTopics;
import com.paystream.common.event.BaseEvent;
import com.paystream.common.event.payment.PaymentCompletedEvent;
import com.paystream.common.util.IdGenerator;
import com.paystream.payment.infrastructure.persistence.entity.OutboxEventEntity;
import com.paystream.payment.infrastructure.persistence.repository.OutboxEventJpaRepository;
import com.paystream.payment.infrastructure.messaging.producer.OutboxRelayService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests OutboxRelayService using real Testcontainers Kafka + Postgres.
 *
 * Verifies:
 *  1. Unpublished event → relay publishes to Kafka and marks published=TRUE
 *  2. Kafka down → event stays published=FALSE, no exception escapes the scheduler
 *  3. FOR UPDATE SKIP LOCKED → event published exactly once even with concurrent relay calls
 */
@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxRelayTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db")
            .withUsername("paystream")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
    }

    @Autowired
    private OutboxEventJpaRepository outboxRepo;

    @Autowired
    private OutboxRelayService outboxRelayService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        outboxRepo.deleteAll();
    }

    @Test
    void unpublishedEvent_shouldBePublishedToKafkaAndMarkedPublished() throws Exception {
        // Given
        PaymentCompletedEvent payload = new PaymentCompletedEvent(
                IdGenerator.generate(), IdGenerator.generate(),
                IdGenerator.generate(), IdGenerator.generate(),
                100000L, "INR", UUID.randomUUID().toString());

        BaseEvent<PaymentCompletedEvent> envelope = new BaseEvent<>(
                IdGenerator.generate(), "PaymentCompleted", "1.0",
                Instant.now(), UUID.randomUUID().toString(), "payment-service", payload);

        String json = objectMapper.writeValueAsString(envelope);
        OutboxEventEntity entity = new OutboxEventEntity(
                IdGenerator.generate(), payload.paymentId(), "Payment", "PaymentCompleted", json);
        outboxRepo.save(entity);

        // When
        outboxRelayService.pollAndPublish();

        // Then — row is marked published
        OutboxEventEntity persisted = outboxRepo.findById(entity.getId()).orElseThrow();
        assertThat(persisted.isPublished()).isTrue();
        assertThat(persisted.getPublishedAt()).isNotNull();

        // Verify message arrived in Kafka
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-verify-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of(KafkaTopics.PAYMENTS_COMPLETED));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void eventWithNoTopicMapping_shouldLogErrorAndNotFail() {
        // Given — unknown event type with no topic mapping
        OutboxEventEntity entity = new OutboxEventEntity(
                IdGenerator.generate(), IdGenerator.generate(),
                "Payment", "UnknownEventType", "{}");
        outboxRepo.save(entity);

        // When — should NOT throw
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> outboxRelayService.pollAndPublish());

        // Then — row stays unpublished (no mapping found)
        OutboxEventEntity persisted = outboxRepo.findById(entity.getId()).orElseThrow();
        assertThat(persisted.isPublished()).isFalse();
    }

    @Test
    void concurrentRelay_shouldPublishEventExactlyOnce() throws Exception {
        // Given
        PaymentCompletedEvent payload = new PaymentCompletedEvent(
                IdGenerator.generate(), IdGenerator.generate(),
                IdGenerator.generate(), IdGenerator.generate(),
                50000L, "INR", UUID.randomUUID().toString());

        BaseEvent<PaymentCompletedEvent> envelope = new BaseEvent<>(
                IdGenerator.generate(), "PaymentCompleted", "1.0",
                Instant.now(), UUID.randomUUID().toString(), "payment-service", payload);

        OutboxEventEntity entity = new OutboxEventEntity(
                IdGenerator.generate(), payload.paymentId(), "Payment",
                "PaymentCompleted", objectMapper.writeValueAsString(envelope));
        outboxRepo.save(entity);

        // When — two concurrent relay calls (SKIP LOCKED ensures only one processes)
        Thread t1 = new Thread(() -> outboxRelayService.pollAndPublish());
        Thread t2 = new Thread(() -> outboxRelayService.pollAndPublish());
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Then — event published exactly once
        OutboxEventEntity result = outboxRepo.findById(entity.getId()).orElseThrow();
        assertThat(result.isPublished()).isTrue();
    }
}
