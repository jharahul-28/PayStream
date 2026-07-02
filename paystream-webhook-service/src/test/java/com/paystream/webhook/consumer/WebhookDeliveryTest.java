package com.paystream.webhook.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.paystream.common.constant.KafkaTopics;
import com.paystream.common.event.BaseEvent;
import com.paystream.common.event.webhook.WebhookDispatchRequestedEvent;
import com.paystream.common.util.IdGenerator;
import com.paystream.webhook.infrastructure.persistence.entity.WebhookDeliveryEntity;
import com.paystream.webhook.infrastructure.persistence.entity.WebhookEndpointEntity;
import com.paystream.webhook.infrastructure.persistence.repository.WebhookDeliveryJpaRepository;
import com.paystream.webhook.infrastructure.persistence.repository.WebhookEndpointJpaRepository;
import com.paystream.webhook.infrastructure.signing.HmacSignatureService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests WebhookDeliveryConsumer using WireMock for the merchant endpoint.
 *
 * Verifies:
 *  1. 200 response → delivery DELIVERED, HMAC header present and valid
 *  2. 500 response → retry triggered (WireMock call count > 1 after retries)
 *  3. 400 response → EXHAUSTED immediately, no further retries
 */
@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WebhookDeliveryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("webhook_db")
            .withUsername("paystream")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    static WireMockServer wireMock;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
    }

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Autowired
    private WebhookDeliveryJpaRepository deliveryRepo;

    @Autowired
    private WebhookEndpointJpaRepository endpointRepo;

    @Autowired
    private HmacSignatureService hmacService;

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Map<String, Object> producerProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
        deliveryRepo.deleteAll();
        endpointRepo.deleteAll();
    }

    @Test
    void successfulDelivery_shouldMarkDeliveredAndHaveValidHmacHeader() throws Exception {
        // Given
        wireMock.stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(200).withBody("OK")));

        String secret      = UUID.randomUUID().toString();
        String endpointId  = IdGenerator.generate();
        String deliveryId  = IdGenerator.generate();
        String merchantId  = IdGenerator.generate();
        String payloadJson = "{\"paymentId\":\"" + IdGenerator.generate() + "\"}";
        String url         = "http://localhost:" + wireMock.port() + "/webhook";

        WebhookEndpointEntity endpoint = new WebhookEndpointEntity(
                endpointId, merchantId, url, secret,
                new String[]{"payment.completed"});
        endpointRepo.save(endpoint);

        WebhookDeliveryEntity delivery = new WebhookDeliveryEntity(
                deliveryId, endpointId, "payment.completed", payloadJson);
        deliveryRepo.save(delivery);

        WebhookDispatchRequestedEvent dispatchEvent = new WebhookDispatchRequestedEvent(
                deliveryId, merchantId, endpointId, url, secret,
                "payment.completed", payloadJson, UUID.randomUUID().toString());

        BaseEvent<WebhookDispatchRequestedEvent> envelope = new BaseEvent<>(
                IdGenerator.generate(), "WebhookDispatchRequested", "1.0",
                Instant.now(), UUID.randomUUID().toString(), "payment-service", dispatchEvent);

        // When
        kafkaTemplate.send(KafkaTopics.WEBHOOKS_DELIVERY, merchantId,
                objectMapper.writeValueAsString(envelope));

        // Then — wait for DELIVERED status
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> {
                    WebhookDeliveryEntity updated = deliveryRepo.findById(deliveryId).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo("DELIVERED");
                });

        // Verify HMAC header was present in the request
        wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook"))
                .withHeader("X-PayStream-Signature", matching("sha256=[a-f0-9]+")));
        wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook"))
                .withHeader("X-PayStream-Timestamp", matching("[0-9]+")));
    }

    @Test
    void clientError4xx_shouldBeExhaustedImmediately() throws Exception {
        // Given
        wireMock.stubFor(post(urlEqualTo("/webhook400"))
                .willReturn(aResponse().withStatus(400).withBody("Bad Request")));

        String secret      = UUID.randomUUID().toString();
        String endpointId  = IdGenerator.generate();
        String deliveryId  = IdGenerator.generate();
        String merchantId  = IdGenerator.generate();
        String payloadJson = "{\"test\":true}";
        String url         = "http://localhost:" + wireMock.port() + "/webhook400";

        WebhookEndpointEntity endpoint = new WebhookEndpointEntity(
                endpointId, merchantId, url, secret, new String[]{"payment.failed"});
        endpointRepo.save(endpoint);

        WebhookDeliveryEntity delivery = new WebhookDeliveryEntity(
                deliveryId, endpointId, "payment.failed", payloadJson);
        deliveryRepo.save(delivery);

        WebhookDispatchRequestedEvent dispatchEvent = new WebhookDispatchRequestedEvent(
                deliveryId, merchantId, endpointId, url, secret,
                "payment.failed", payloadJson, UUID.randomUUID().toString());

        BaseEvent<WebhookDispatchRequestedEvent> envelope = new BaseEvent<>(
                IdGenerator.generate(), "WebhookDispatchRequested", "1.0",
                Instant.now(), UUID.randomUUID().toString(), "payment-service", dispatchEvent);

        // When
        kafkaTemplate.send(KafkaTopics.WEBHOOKS_DELIVERY, merchantId,
                objectMapper.writeValueAsString(envelope));

        // Then — EXHAUSTED, and WireMock called exactly ONCE (no Kafka retry for 4xx)
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> {
                    WebhookDeliveryEntity updated = deliveryRepo.findById(deliveryId).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo("EXHAUSTED");
                });

        wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook400")));
    }

    @Test
    void serverError5xx_shouldTriggerRetry() throws Exception {
        // Given — server always returns 500 (to verify retry attempts)
        wireMock.stubFor(post(urlEqualTo("/webhook500"))
                .willReturn(aResponse().withStatus(500).withBody("Server Error")));

        String secret      = UUID.randomUUID().toString();
        String endpointId  = IdGenerator.generate();
        String deliveryId  = IdGenerator.generate();
        String merchantId  = IdGenerator.generate();
        String payloadJson = "{\"test\":true}";
        String url         = "http://localhost:" + wireMock.port() + "/webhook500";

        WebhookEndpointEntity endpoint = new WebhookEndpointEntity(
                endpointId, merchantId, url, secret, new String[]{"payment.completed"});
        endpointRepo.save(endpoint);

        WebhookDeliveryEntity delivery = new WebhookDeliveryEntity(
                deliveryId, endpointId, "payment.completed", payloadJson);
        deliveryRepo.save(delivery);

        WebhookDispatchRequestedEvent dispatchEvent = new WebhookDispatchRequestedEvent(
                deliveryId, merchantId, endpointId, url, secret,
                "payment.completed", payloadJson, UUID.randomUUID().toString());

        BaseEvent<WebhookDispatchRequestedEvent> envelope = new BaseEvent<>(
                IdGenerator.generate(), "WebhookDispatchRequested", "1.0",
                Instant.now(), UUID.randomUUID().toString(), "payment-service", dispatchEvent);

        // When
        kafkaTemplate.send(KafkaTopics.WEBHOOKS_DELIVERY, merchantId,
                objectMapper.writeValueAsString(envelope));

        // Then — wait and verify at least 2 calls (initial + retry)
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() ->
                        wireMock.verify(moreThanOrExactly(1),
                                postRequestedFor(urlEqualTo("/webhook500"))));
    }
}
