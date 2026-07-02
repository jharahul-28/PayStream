package com.paystream.fraud.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.common.constant.KafkaTopics;
import com.paystream.common.event.BaseEvent;
import com.paystream.common.event.fraud.FraudCheckRequestedEvent;
import com.paystream.common.event.fraud.FraudScoreComputedEvent;
import com.paystream.common.util.IdGenerator;
import com.paystream.fraud.application.port.out.FraudCheckRepository;
import com.paystream.fraud.application.port.out.UserRiskProfileRepository;
import com.paystream.fraud.domain.model.FraudCheck;
import com.paystream.fraud.domain.model.UserRiskProfile;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stage 2 — Async AI enrichment via Kafka.
 *
 * CRITICAL BOUNDARY:
 *   - AI result updates ONLY narrative fields: ai_narrative, ai_risk_score, ai_confidence.
 *   - AI NEVER changes the fraud decision (ALLOW/BLOCK/REVIEW).
 *   - AI NEVER modifies any payment record.
 *   - The Stage 1 deterministic decision is immutable.
 *
 * If |aiScore - ruleScore| > 30: emit a WARNING for human review.
 * Circuit breaker prevents cascading AI provider failures.
 * Nightly retry job picks up ai_processed=FALSE records.
 */
@Component
public class FraudAiEnrichmentConsumer {

    private static final Logger log = LoggerFactory.getLogger(FraudAiEnrichmentConsumer.class);
    private static final int    SCORE_DISAGREEMENT_THRESHOLD = 30;

    @Value("${paystream.fraud.ai-system-prompt}")
    private String systemPrompt;

    private final ChatClient                chatClient;
    private final FraudCheckRepository      fraudCheckRepository;
    private final UserRiskProfileRepository profileRepository;
    private final ObjectMapper              objectMapper;
    private final Counter                   aiDlqCounter;
    private final Counter                   aiErrorCounter;
    private final Timer                     aiDurationTimer;

    public FraudAiEnrichmentConsumer(
            ChatClient.Builder chatClientBuilder,
            FraudCheckRepository fraudCheckRepository,
            UserRiskProfileRepository profileRepository,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.chatClient           = chatClientBuilder.build();
        this.fraudCheckRepository = fraudCheckRepository;
        this.profileRepository    = profileRepository;
        this.objectMapper         = objectMapper;

        this.aiDlqCounter = Counter.builder("paystream.kafka.dlq.messages.total")
                .tag("topic", KafkaTopics.FRAUD_CHECK_REQUESTED)
                .register(meterRegistry);
        this.aiErrorCounter = Counter.builder("paystream.fraud.ai.errors.total")
                .description("AI enrichment errors (provider + parse failures)")
                .register(meterRegistry);
        this.aiDurationTimer = Timer.builder("paystream.fraud.ai.duration.ms")
                .description("AI enrichment call duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    @KafkaListener(topics = KafkaTopics.FRAUD_CHECK_REQUESTED, groupId = "fraud-ai-enrichment")
    @RetryableTopic(
            attempts = "3",
            backoff  = @Backoff(delay = 2000, multiplier = 3.0, maxDelay = 30000),
            dltTopicSuffix = ".dlq",
            autoCreateTopics = "false"
    )
    @CircuitBreaker(name = "ai-enrichment", fallbackMethod = "handleAiFallback")
    public void consume(BaseEvent<FraudCheckRequestedEvent> event) {
        if (event == null || event.payload() == null) return;

        FraudCheckRequestedEvent payload = event.payload();
        MDC.put("correlationId", event.correlationId());

        Optional<FraudCheck> fraudCheckOpt = fraudCheckRepository.findByPaymentId(payload.paymentId());
        if (fraudCheckOpt.isEmpty()) {
            log.warn("FraudCheck not found for paymentId={} — skipping AI enrichment", payload.paymentId());
            return;
        }

        FraudCheck fraudCheck = fraudCheckOpt.get();
        if (fraudCheck.isAiProcessed()) {
            log.debug("FraudCheck already AI-processed fraudCheckId={} — skipping", fraudCheck.getId());
            return;
        }

        UserRiskProfile profile = profileRepository.findByUserId(payload.userId())
                .orElse(null);

        String userPrompt = buildUserPrompt(payload, fraudCheck, profile);

        long startNs = System.nanoTime();
        String aiResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        aiDurationTimer.record(elapsedMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        parseAndApplyAiResponse(fraudCheck, aiResponse);
    }

    /** Circuit breaker fallback — log, record error, do not rethrow. */
    public void handleAiFallback(BaseEvent<FraudCheckRequestedEvent> event, Exception ex) {
        if (event == null || event.payload() == null) return;
        log.warn("AI circuit open or error — skipping enrichment paymentId={} correlationId={} error={}",
                event.payload().paymentId(), event.correlationId(), ex.getMessage());
        aiErrorCounter.increment();

        fraudCheckRepository.findByPaymentId(event.payload().paymentId())
                .ifPresent(fc -> {
                    fc.markAiError("Circuit open: " + ex.getMessage());
                    fraudCheckRepository.updateAiEnrichment(fc);
                });
    }

    /** Nightly retry for ai_processed=FALSE records (handles circuit breaker fallback cases). */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Kolkata")
    public void retryFailedAiEnrichments() {
        List<FraudCheck> pending = fraudCheckRepository.findAiPendingChecks(100);
        log.info("Nightly AI retry: {} pending enrichments", pending.size());
        for (FraudCheck fc : pending) {
            try {
                retryAiForCheck(fc);
            } catch (Exception e) {
                log.warn("AI retry failed fraudCheckId={} error={}", fc.getId(), e.getMessage());
            }
        }
    }

    @DltHandler
    public void handleDlq(BaseEvent<?> event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, Exception ex) {
        log.error("DLQ event received topic={} eventId={} eventType={} error={}",
                topic, event.eventId(), event.eventType(), ex.getMessage());
        aiDlqCounter.increment();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void parseAndApplyAiResponse(FraudCheck fraudCheck, String rawResponse) {
        try {
            // Strip any markdown code fences if present
            String json = rawResponse.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json?", "").replace("```", "").strip();
            }

            JsonNode root       = objectMapper.readTree(json);
            int      aiScore    = root.path("riskScore").asInt(0);
            double   confidence = root.path("confidence").asDouble(0.0);
            String   reasoning  = root.path("reasoning").asText("");

            // Clamp values to valid ranges
            aiScore    = Math.max(0, Math.min(100, aiScore));
            confidence = Math.max(0.0, Math.min(1.0, confidence));
            reasoning  = reasoning.length() > 500 ? reasoning.substring(0, 500) : reasoning;

            fraudCheck.enrichWithAi(reasoning, aiScore, confidence);
            fraudCheckRepository.updateAiEnrichment(fraudCheck);

            // Disagreement detection — AI vs deterministic rules
            int ruleScore = fraudCheck.getRiskScore();
            if (Math.abs(aiScore - ruleScore) > SCORE_DISAGREEMENT_THRESHOLD) {
                log.warn("AI-rules disagreement fraudCheckId={} paymentId={} ruleScore={} aiScore={} diff={}",
                        fraudCheck.getId(), fraudCheck.getPaymentId(), ruleScore, aiScore, Math.abs(aiScore - ruleScore));
            }

            log.info("AI enrichment applied fraudCheckId={} paymentId={} aiScore={} confidence={} correlationId={}",
                    fraudCheck.getId(), fraudCheck.getPaymentId(), aiScore, confidence, MDC.get("correlationId"));

        } catch (Exception e) {
            log.warn("Failed to parse AI response fraudCheckId={} error={}", fraudCheck.getId(), e.getMessage());
            aiErrorCounter.increment();
            fraudCheck.markAiError("Parse error: " + e.getMessage());
            fraudCheckRepository.updateAiEnrichment(fraudCheck);
        }
    }

    private void retryAiForCheck(FraudCheck fc) {
        String userPrompt = "PaymentId: " + fc.getPaymentId() + " | UserId: " + fc.getUserId()
                + " | RiskScore: " + fc.getRiskScore() + " | Flags: " + fc.getFlags()
                + " | Please provide your fraud risk assessment.";
        String aiResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        parseAndApplyAiResponse(fc, aiResponse);
    }

    private String buildUserPrompt(FraudCheckRequestedEvent payload, FraudCheck fc, UserRiskProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Transaction Details:\n");
        sb.append("  paymentId=").append(payload.paymentId()).append("\n");
        sb.append("  userId=").append(payload.userId()).append("\n");
        sb.append("  amount=").append(payload.amount()).append(" ").append(payload.currency()).append("\n");
        sb.append("  deviceId=").append(payload.deviceId()).append("\n");
        sb.append("  ipAddress=").append(payload.ipAddress()).append("\n");
        sb.append("\nDeterministic Rule Result (DO NOT change the ALLOW/BLOCK/REVIEW decision):\n");
        sb.append("  ruleScore=").append(fc.getRiskScore()).append("\n");
        sb.append("  triggeredFlags=").append(fc.getFlags()).append("\n");

        if (profile != null) {
            sb.append("\nUser Risk Profile:\n");
            sb.append("  avgTransactionAmount=").append(profile.getAvgTransactionAmount()).append("\n");
            sb.append("  typicalHours=").append(profile.getTypicalHoursStart()).append("-").append(profile.getTypicalHoursEnd()).append("\n");
            sb.append("  chargeback30d=").append(profile.getChargebackCount30d()).append("\n");
            sb.append("  txCount30d=").append(profile.getTransactionCount30d()).append("\n");
        }
        return sb.toString();
    }
}
