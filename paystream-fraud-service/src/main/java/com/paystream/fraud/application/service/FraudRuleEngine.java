package com.paystream.fraud.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.common.constant.RedisKeys;
import com.paystream.fraud.application.command.EvaluateFraudCommand;
import com.paystream.fraud.application.port.out.FraudRuleRepository;
import com.paystream.fraud.domain.model.FraudDecision;
import com.paystream.fraud.domain.model.FraudRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Deterministic Stage 1 fraud rules engine.
 *
 * Rules are loaded from DB and cached in Redis (TTL = rulesCacheTtlSeconds).
 * No AI involvement. Decision is final and immutable once returned.
 *
 * Decision thresholds (from spec):
 *   totalScore >= 80 -> BLOCK
 *   totalScore >= 50 -> REVIEW  (payment proceeds but is flagged)
 *   totalScore <  50 -> ALLOW
 */
@Component
public class FraudRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(FraudRuleEngine.class);

    private final FraudRuleRepository   ruleRepository;
    private final StringRedisTemplate   redisTemplate;
    private final ObjectMapper          objectMapper;

    @Value("${paystream.fraud.high-amount-threshold:500000}")
    private long highAmountThreshold;

    @Value("${paystream.fraud.velocity-daily-limit:10}")
    private int velocityDailyLimit;

    @Value("${paystream.fraud.block-score-threshold:80}")
    private int blockThreshold;

    @Value("${paystream.fraud.review-score-threshold:50}")
    private int reviewThreshold;

    @Value("${paystream.fraud.rules-cache-ttl-seconds:300}")
    private long rulesCacheTtl;

    public FraudRuleEngine(FraudRuleRepository ruleRepository,
                           StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.redisTemplate  = redisTemplate;
        this.objectMapper   = objectMapper;
    }

    public record EvaluationResult(int totalScore, FraudDecision decision, List<String> flags) {}

    /**
     * Evaluates all enabled rules against the command.
     * Profile is passed in (loaded by the caller) to keep this method pure / testable.
     */
    public EvaluationResult evaluate(EvaluateFraudCommand cmd,
                                     com.paystream.fraud.domain.model.UserRiskProfile profile) {
        List<FraudRule> rules     = loadRulesCached();
        List<String>    triggered = new ArrayList<>();
        int             score     = 0;

        for (FraudRule rule : rules) {
            if (!rule.enabled()) continue;

            boolean fired = switch (rule.ruleCode()) {
                case "RULE_HIGH_AMOUNT"    -> evaluateHighAmount(cmd);
                case "RULE_HIGH_VELOCITY"  -> evaluateHighVelocity(cmd);
                case "RULE_NEW_DEVICE"     -> evaluateNewDevice(cmd, profile);
                case "RULE_ODD_HOUR"       -> evaluateOddHour(profile);
                case "RULE_HIGH_CHARGEBACK"-> evaluateHighChargeback(profile);
                case "RULE_BLOCKED_IP"     -> evaluateBlockedIp(cmd, profile);
                default -> {
                    log.warn("Unknown rule code={} — skipping correlationId={}", rule.ruleCode(), MDC.get("correlationId"));
                    yield false;
                }
            };

            if (fired) {
                triggered.add(rule.flagName());
                score += rule.weight();
                log.debug("Rule fired rule={} flag={} weight={} paymentId={}", rule.ruleCode(), rule.flagName(), rule.weight(), cmd.paymentId());
            }
        }

        FraudDecision decision = score >= blockThreshold  ? FraudDecision.BLOCK
                               : score >= reviewThreshold ? FraudDecision.REVIEW
                               : FraudDecision.ALLOW;

        log.info("Fraud evaluation complete paymentId={} score={} decision={} flags={} correlationId={}",
                cmd.paymentId(), score, decision, triggered, MDC.get("correlationId"));

        return new EvaluationResult(score, decision, triggered);
    }

    // -------------------------------------------------------------------------
    // Rule evaluators
    // -------------------------------------------------------------------------

    private boolean evaluateHighAmount(EvaluateFraudCommand cmd) {
        return cmd.amount() > highAmountThreshold;
    }

    private boolean evaluateHighVelocity(EvaluateFraudCommand cmd) {
        String key   = RedisKeys.velocity(cmd.userId(), LocalDate.now().toString());
        Long   count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 86400, TimeUnit.SECONDS);
        return count != null && count > velocityDailyLimit;
    }

    private boolean evaluateNewDevice(EvaluateFraudCommand cmd,
                                       com.paystream.fraud.domain.model.UserRiskProfile profile) {
        if (cmd.deviceId() == null || cmd.deviceId().isBlank()) return false;
        return !profile.getKnownDeviceIds().contains(cmd.deviceId());
    }

    private boolean evaluateOddHour(com.paystream.fraud.domain.model.UserRiskProfile profile) {
        int hour = java.time.LocalTime.now().getHour();
        return hour < profile.getTypicalHoursStart() || hour > profile.getTypicalHoursEnd();
    }

    private boolean evaluateHighChargeback(com.paystream.fraud.domain.model.UserRiskProfile profile) {
        return profile.getChargebackCount30d() >= 2;
    }

    private boolean evaluateBlockedIp(EvaluateFraudCommand cmd,
                                       com.paystream.fraud.domain.model.UserRiskProfile profile) {
        if (cmd.ipAddress() == null || cmd.ipAddress().isBlank()) return false;
        return profile.getKnownIpPrefixes().stream()
                .anyMatch(prefix -> prefix.startsWith("BLOCKED:") &&
                        cmd.ipAddress().startsWith(prefix.substring(8)));
    }

    // -------------------------------------------------------------------------
    // Rules cache — Redis, TTL 5 min
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<FraudRule> loadRulesCached() {
        String cached = redisTemplate.opsForValue().get(RedisKeys.FRAUD_RULES_ACTIVE);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<FraudRule>>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize cached fraud rules — reloading from DB");
            }
        }

        List<FraudRule> rules = ruleRepository.findAllEnabled();
        try {
            redisTemplate.opsForValue().set(
                    RedisKeys.FRAUD_RULES_ACTIVE,
                    objectMapper.writeValueAsString(rules),
                    rulesCacheTtl, TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("Failed to cache fraud rules in Redis — proceeding without cache");
        }
        return rules;
    }
}
