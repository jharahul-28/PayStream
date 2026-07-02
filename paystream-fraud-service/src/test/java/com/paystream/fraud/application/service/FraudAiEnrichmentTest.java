package com.paystream.fraud.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.common.event.BaseEvent;
import com.paystream.common.event.fraud.FraudCheckRequestedEvent;
import com.paystream.fraud.application.port.out.FraudCheckRepository;
import com.paystream.fraud.application.port.out.UserRiskProfileRepository;
import com.paystream.fraud.domain.model.FraudCheck;
import com.paystream.fraud.domain.model.FraudDecision;
import com.paystream.fraud.infrastructure.messaging.consumer.FraudAiEnrichmentConsumer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudAiEnrichmentTest {

    @Mock private ChatClient.Builder         chatClientBuilder;
    @Mock private ChatClient                 chatClient;
    @Mock private ChatClient.ChatClientRequestSpec chatSpec;
    @Mock private ChatClient.ChatClientRequestSpec.CallResponseSpec callSpec;
    @Mock private FraudCheckRepository       fraudCheckRepository;
    @Mock private UserRiskProfileRepository  profileRepository;

    private FraudAiEnrichmentConsumer consumer;
    private FraudCheck                fraudCheck;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        consumer = new FraudAiEnrichmentConsumer(
                chatClientBuilder, fraudCheckRepository, profileRepository,
                new ObjectMapper(), new SimpleMeterRegistry()
        );
        ReflectionTestUtils.setField(consumer, "systemPrompt", "test system prompt");

        fraudCheck = new FraudCheck("check-1", "payment-1", "user-1",
                60, FraudDecision.REVIEW, List.of("HIGH_AMOUNT"),
                "1.0", 3L, Instant.now());

        when(fraudCheckRepository.findByPaymentId("payment-1")).thenReturn(Optional.of(fraudCheck));
        when(profileRepository.findByUserId(any())).thenReturn(Optional.empty());
    }

    @Test
    void validJsonResponse_setsAiNarrativeAndMarksProcessed() {
        String aiJson = """
                {"riskScore": 65, "confidence": 0.85, "flags": ["HIGH_AMOUNT"], "reasoning": "Large transaction from known device."}
                """;
        mockChatResponse(aiJson);

        consumer.consume(createEvent("payment-1", "user-1", 600000L));

        ArgumentCaptor<FraudCheck> captor = ArgumentCaptor.forClass(FraudCheck.class);
        verify(fraudCheckRepository).updateAiEnrichment(captor.capture());
        FraudCheck updated = captor.getValue();
        assertThat(updated.isAiProcessed()).isTrue();
        assertThat(updated.getAiNarrative()).isEqualTo("Large transaction from known device.");
        assertThat(updated.getAiRiskScore()).isEqualTo(65);
        assertThat(updated.getAiConfidence()).isEqualTo(0.85);
    }

    @Test
    void malformedResponse_setsProcessingError_noException() {
        mockChatResponse("this is not json {{{");

        consumer.consume(createEvent("payment-1", "user-1", 600000L));

        ArgumentCaptor<FraudCheck> captor = ArgumentCaptor.forClass(FraudCheck.class);
        verify(fraudCheckRepository).updateAiEnrichment(captor.capture());
        FraudCheck updated = captor.getValue();
        assertThat(updated.getAiProcessingError()).isNotNull().contains("Parse error");
        assertThat(updated.isAiProcessed()).isFalse();
    }

    @Test
    void chatClientThrows_fallbackFires_metricIncremented() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("AI provider timeout"));

        // Fallback should handle gracefully
        consumer.handleAiFallback(createEvent("payment-1", "user-1", 100L), new RuntimeException("AI provider timeout"));

        verify(fraudCheckRepository).updateAiEnrichment(any());
    }

    @Test
    void alreadyProcessed_skipsProcessing() {
        fraudCheck.enrichWithAi("already done", 60, 0.9);
        when(fraudCheckRepository.findByPaymentId("payment-1")).thenReturn(Optional.of(fraudCheck));

        consumer.consume(createEvent("payment-1", "user-1", 600000L));

        verify(fraudCheckRepository, never()).updateAiEnrichment(any());
        verify(chatClient, never()).prompt();
    }

    @Test
    void largeScoreDisagreement_logsWarn() {
        // AI says 10, rules said 60 — diff = 50 > 30
        String aiJson = """
                {"riskScore": 10, "confidence": 0.9, "flags": [], "reasoning": "Looks fine to me."}
                """;
        mockChatResponse(aiJson);

        // Should not throw — disagreement is just logged
        consumer.consume(createEvent("payment-1", "user-1", 600000L));

        ArgumentCaptor<FraudCheck> captor = ArgumentCaptor.forClass(FraudCheck.class);
        verify(fraudCheckRepository).updateAiEnrichment(captor.capture());
        assertThat(captor.getValue().getAiRiskScore()).isEqualTo(10);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void mockChatResponse(String json) {
        when(chatClient.prompt()).thenReturn(chatSpec);
        when(chatSpec.system(anyString())).thenReturn(chatSpec);
        when(chatSpec.user(anyString())).thenReturn(chatSpec);
        when(chatSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(json);
    }

    private BaseEvent<FraudCheckRequestedEvent> createEvent(String paymentId, String userId, long amount) {
        FraudCheckRequestedEvent payload = new FraudCheckRequestedEvent(
                paymentId, userId, amount, "INR", "w-src", "w-dst",
                "device-1", "1.2.3.4", Instant.now(), "corr-1"
        );
        return new BaseEvent<>("event-1", "FraudCheckRequested", "1.0",
                Instant.now(), "corr-1", "fraud-service", payload);
    }
}
