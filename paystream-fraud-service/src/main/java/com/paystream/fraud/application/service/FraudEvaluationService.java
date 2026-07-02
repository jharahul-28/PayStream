package com.paystream.fraud.application.service;

import com.paystream.common.constant.KafkaTopics;
import com.paystream.common.constant.RedisKeys;
import com.paystream.common.event.BaseEvent;
import com.paystream.common.event.fraud.FraudCheckRequestedEvent;
import com.paystream.common.fraud.FraudCheckResult;
import com.paystream.common.util.IdGenerator;
import com.paystream.fraud.application.command.EvaluateFraudCommand;
import com.paystream.fraud.application.port.in.FraudEvaluationUseCase;
import com.paystream.fraud.application.port.out.FraudCheckRepository;
import com.paystream.fraud.application.port.out.UserRiskProfileRepository;
import com.paystream.fraud.domain.model.FraudCheck;
import com.paystream.fraud.domain.model.FraudDecision;
import com.paystream.fraud.domain.model.UserRiskProfile;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Orchestrates the two-stage fraud evaluation:
 *
 * Stage 1 (sync, this class):   deterministic rules -> ALLOW/BLOCK/REVIEW. FINAL.
 * Stage 2 (async, Kafka + AI):  enrichment only — updates narrative, never the decision.
 *
 * AI cost optimization: only publish to fraud.check.requested if riskScore >= aiMinScore or flags non-empty.
 */
@Service
public class FraudEvaluationService implements FraudEvaluationUseCase {

    private static final Logger log = LoggerFactory.getLogger(FraudEvaluationService.class);

    @Value("${paystream.fraud.ai-min-score-for-enrichment:30}")
    private int aiMinScoreForEnrichment;

    @Value("${paystream.fraud.rule-version:1.0}")
    private String ruleVersion;

    private final FraudRuleEngine               ruleEngine;
    private final FraudCheckRepository          fraudCheckRepository;
    private final UserRiskProfileRepository     profileRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate           redisTemplate;
    private final MeterRegistry                 meterRegistry;
    private final Timer                         rulesDurationTimer;

    public FraudEvaluationService(
            FraudRuleEngine ruleEngine,
            FraudCheckRepository fraudCheckRepository,
            UserRiskProfileRepository profileRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry) {
        this.ruleEngine           = ruleEngine;
        this.fraudCheckRepository = fraudCheckRepository;
        this.profileRepository    = profileRepository;
        this.kafkaTemplate        = kafkaTemplate;
        this.redisTemplate        = redisTemplate;
        this.meterRegistry        = meterRegistry;

        this.rulesDurationTimer = Timer.builder("paystream.fraud.rules.duration.ms")
                .description("Time taken by Stage 1 rules engine (ms)")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    @Override
    public FraudCheckResult evaluate(EvaluateFraudCommand cmd) {
        // Check if user is explicitly blocked (admin action stored in Redis)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.userBlocked(cmd.userId())))) {
            log.warn("Payment blocked — user explicitly blocked userId={} paymentId={} correlationId={}",
                    cmd.userId(), cmd.paymentId(), MDC.get("correlationId"));
            recordMetrics(FraudDecision.BLOCK, List.of("USER_BLOCKED"));
            return new FraudCheckResult("USER_BLOCKED", FraudCheckResult.FraudDecision.BLOCK, 100, List.of("USER_BLOCKED"));
        }

        // Load user risk profile (default if absent — new user gets safe baseline)
        UserRiskProfile profile = profileRepository.findByUserId(cmd.userId())
                .orElse(defaultProfile(cmd.userId()));

        // Stage 1 — deterministic rules (FINAL decision)
        long startNs = System.nanoTime();
        FraudRuleEngine.EvaluationResult result = ruleEngine.evaluate(cmd, profile);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        rulesDurationTimer.record(elapsedMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        FraudCheck fraudCheck = new FraudCheck(
                IdGenerator.generate(), cmd.paymentId(), cmd.userId(),
                result.totalScore(), result.decision(), result.flags(),
                ruleVersion, elapsedMs, Instant.now()
        );
        fraudCheckRepository.save(fraudCheck);

        recordMetrics(result.decision(), result.flags());

        if (result.decision() == FraudDecision.BLOCK) {
            log.warn("Payment BLOCKED paymentId={} userId={} score={} flags={} correlationId={}",
                    cmd.paymentId(), cmd.userId(), result.totalScore(), result.flags(), MDC.get("correlationId"));
        }

        // Stage 2 — AI enrichment (async Kafka) — only if score warrants it (cost control)
        if (result.totalScore() >= aiMinScoreForEnrichment || !result.flags().isEmpty()) {
            publishAiEnrichmentRequest(cmd, fraudCheck.getId());
        }

        return new FraudCheckResult(
                fraudCheck.getId(),
                FraudCheckResult.FraudDecision.valueOf(result.decision().name()),
                result.totalScore(),
                result.flags()
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void recordMetrics(FraudDecision decision, List<String> flags) {
        Counter.builder("paystream.fraud.checks.total")
                .description("Total fraud evaluations")
                .tag("decision", decision.name())
                .register(meterRegistry)
                .increment();

        if (decision == FraudDecision.BLOCK) {
            String topFlag = flags.isEmpty() ? "NONE" : flags.get(0);
            Counter.builder("paystream.fraud.blocks.total")
                    .description("Total payments blocked by fraud")
                    .tag("topFlag", topFlag)
                    .register(meterRegistry)
                    .increment();
        }
    }

    private void publishAiEnrichmentRequest(EvaluateFraudCommand cmd, String fraudCheckId) {
        try {
            FraudCheckRequestedEvent payload = new FraudCheckRequestedEvent(
                    cmd.paymentId(), cmd.userId(), cmd.amount(), cmd.currency(),
                    cmd.sourceWalletId(), cmd.destinationWalletId(),
                    cmd.deviceId(), cmd.ipAddress(), Instant.now(), MDC.get("correlationId")
            );
            BaseEvent<FraudCheckRequestedEvent> envelope = new BaseEvent<>(
                    IdGenerator.generate(), "FraudCheckRequested", "1.0", Instant.now(),
                    MDC.get("correlationId"), "fraud-service", payload
            );
            kafkaTemplate.send(KafkaTopics.FRAUD_CHECK_REQUESTED, cmd.paymentId(), envelope);
            log.debug("AI enrichment request published fraudCheckId={} paymentId={}", fraudCheckId, cmd.paymentId());
        } catch (Exception e) {
            // Non-fatal — Stage 1 decision already persisted
            log.warn("Failed to publish AI enrichment request fraudCheckId={} correlationId={} error={}",
                    fraudCheckId, MDC.get("correlationId"), e.getMessage());
        }
    }

    private UserRiskProfile defaultProfile(String userId) {
        return new UserRiskProfile(userId, 0, 8, 22, List.of(), List.of(), 0, 0, Instant.now());
    }
}
