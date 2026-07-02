package com.paystream.fraud.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.fraud.application.command.EvaluateFraudCommand;
import com.paystream.fraud.application.port.out.FraudRuleRepository;
import com.paystream.fraud.domain.model.FraudDecision;
import com.paystream.fraud.domain.model.FraudRule;
import com.paystream.fraud.domain.model.UserRiskProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudRuleEngineTest {

    @Mock private FraudRuleRepository    ruleRepository;
    @Mock private StringRedisTemplate    redisTemplate;
    @Mock private ValueOperations<String,String> valueOps;

    private FraudRuleEngine engine;
    private UserRiskProfile defaultProfile;

    @BeforeEach
    void setUp() {
        engine = new FraudRuleEngine(ruleRepository, redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(engine, "highAmountThreshold", 500000L);
        ReflectionTestUtils.setField(engine, "velocityDailyLimit", 10);
        ReflectionTestUtils.setField(engine, "blockThreshold", 80);
        ReflectionTestUtils.setField(engine, "reviewThreshold", 50);
        ReflectionTestUtils.setField(engine, "rulesCacheTtl", 300L);

        defaultProfile = new UserRiskProfile("user1", 100000, 8, 22,
                List.of("known-device"), List.of(), 0, 5, Instant.now());

        // Redis returns null (no cache) — forces DB load
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);
    }

    @Test
    void highAmount_triggersFlagAndAddsWeight() {
        stubRules(List.of(rule("RULE_HIGH_AMOUNT", "HIGH_AMOUNT", 20)));

        EvaluateFraudCommand cmd = cmd(600000L, "user1", "dev1", null);
        FraudRuleEngine.EvaluationResult result = engine.evaluate(cmd, defaultProfile);

        assertThat(result.flags()).contains("HIGH_AMOUNT");
        assertThat(result.totalScore()).isEqualTo(20);
        assertThat(result.decision()).isEqualTo(FraudDecision.ALLOW); // 20 < 50
    }

    @Test
    void highVelocity_triggersFlag() {
        stubRules(List.of(rule("RULE_HIGH_VELOCITY", "HIGH_VELOCITY", 30)));
        // Simulate 11 transactions today (over limit of 10)
        when(valueOps.increment(anyString())).thenReturn(11L);
        doNothing().when(redisTemplate).expire(anyString(), anyLong(), any());

        FraudRuleEngine.EvaluationResult result = engine.evaluate(cmd(1000L, "user1", null, null), defaultProfile);

        assertThat(result.flags()).contains("HIGH_VELOCITY");
        assertThat(result.totalScore()).isEqualTo(30);
    }

    @Test
    void newDevice_triggersFlag() {
        stubRules(List.of(rule("RULE_NEW_DEVICE", "NEW_DEVICE", 15)));

        EvaluateFraudCommand cmd = cmd(1000L, "user1", "unknown-device", null);
        FraudRuleEngine.EvaluationResult result = engine.evaluate(cmd, defaultProfile);

        assertThat(result.flags()).contains("NEW_DEVICE");
        assertThat(result.totalScore()).isEqualTo(15);
    }

    @Test
    void allThreeFlags_causeBlockDecision() {
        stubRules(List.of(
                rule("RULE_HIGH_AMOUNT",   "HIGH_AMOUNT",   20),
                rule("RULE_HIGH_VELOCITY", "HIGH_VELOCITY", 30),
                rule("RULE_NEW_DEVICE",    "NEW_DEVICE",    15),
                rule("RULE_HIGH_CHARGEBACK","HIGH_CHARGEBACK_HIST", 35)
        ));
        when(valueOps.increment(anyString())).thenReturn(11L);
        doNothing().when(redisTemplate).expire(anyString(), anyLong(), any());

        UserRiskProfile highRiskProfile = new UserRiskProfile("user1", 100000, 8, 22,
                List.of(), List.of(), 3, 5, Instant.now()); // 3 chargebacks

        FraudRuleEngine.EvaluationResult result = engine.evaluate(
                cmd(600000L, "user1", "new-device", null), highRiskProfile);

        // HIGH_AMOUNT(20) + HIGH_VELOCITY(30) + NEW_DEVICE(15) + HIGH_CHARGEBACK(35) = 100 >= 80 -> BLOCK
        assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
        assertThat(result.totalScore()).isGreaterThanOrEqualTo(80);
    }

    @Test
    void noFlags_allowWithZeroScore() {
        stubRules(List.of(
                rule("RULE_HIGH_AMOUNT",    "HIGH_AMOUNT",   20),
                rule("RULE_HIGH_VELOCITY",  "HIGH_VELOCITY", 30)
        ));
        when(valueOps.increment(anyString())).thenReturn(5L); // under velocity limit

        FraudRuleEngine.EvaluationResult result = engine.evaluate(
                cmd(100L, "user1", "known-device", null), defaultProfile);

        assertThat(result.decision()).isEqualTo(FraudDecision.ALLOW);
        assertThat(result.flags()).isEmpty();
        assertThat(result.totalScore()).isEqualTo(0);
    }

    @Test
    void rulesAreCachedAfterFirstLoad() {
        stubRules(List.of(rule("RULE_HIGH_AMOUNT", "HIGH_AMOUNT", 20)));

        engine.evaluate(cmd(100L, "user1", null, null), defaultProfile);
        engine.evaluate(cmd(100L, "user1", null, null), defaultProfile);

        // DB should be queried only once; second call uses Redis cache
        verify(ruleRepository, times(1)).findAllEnabled();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void stubRules(List<FraudRule> rules) {
        when(ruleRepository.findAllEnabled()).thenReturn(rules);
        when(valueOps.get(any())).thenReturn(null); // force cache miss
        try {
            when(valueOps.increment(anyString())).thenReturn(5L); // safe default for velocity
        } catch (Exception ignored) {}
    }

    private FraudRule rule(String code, String flag, int weight) {
        return new FraudRule("id-" + code, code, code + " rule", flag, weight, true, "1.0");
    }

    private EvaluateFraudCommand cmd(long amount, String userId, String deviceId, String ip) {
        return new EvaluateFraudCommand("payment-1", userId, amount, "INR",
                "wallet-src", "wallet-dst", deviceId, ip);
    }
}
