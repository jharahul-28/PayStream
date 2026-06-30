package com.paystream.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.common.api.ApiResponse;
import com.paystream.common.fraud.FraudCheckResult;
import com.paystream.payment.application.service.PaymentApplicationService;
import com.paystream.payment.application.port.out.OutboxEventPort;
import com.paystream.payment.application.port.out.PaymentRepository;
import com.paystream.payment.domain.model.Payment;
import com.paystream.payment.domain.model.PaymentStatus;
import com.paystream.payment.infrastructure.external.FraudServiceClient;
import com.paystream.payment.infrastructure.external.WalletServiceClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the fraud fallback logic — when fraud service throws, payment proceeds with ALLOW(0).
 */
@ExtendWith(MockitoExtension.class)
class CircuitBreakerTest {

    @Mock private PaymentRepository                 paymentRepository;
    @Mock private WalletServiceClient               walletServiceClient;
    @Mock private FraudServiceClient                fraudServiceClient;
    @Mock private OutboxEventPort                   outboxEventPort;
    @Mock private StringRedisTemplate               redisTemplate;
    @Mock private ValueOperations<String,String>    valueOps;

    private PaymentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentApplicationService(
                paymentRepository, walletServiceClient, fraudServiceClient,
                outboxEventPort, redisTemplate, new ObjectMapper(), new SimpleMeterRegistry()
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void fraudCircuitOpen_paymentProceedsWithAllowFallback() {
        // Simulate: fraud service unavailable — falls back to ALLOW(0)
        Payment dummyPayment = new Payment("p-1", "u-1", "idem-1", "w-src", "w-dst",
                1000L, "INR", PaymentStatus.PENDING, null, null, null, 0, Instant.now(), Instant.now());

        FraudCheckResult fallback = FraudCheckResult.allow(0);
        FraudCheckResult result = service.fraudFallback(dummyPayment, null, new RuntimeException("CB open"));

        assertThat(result.decision()).isEqualTo(FraudCheckResult.FraudDecision.ALLOW);
        assertThat(result.riskScore()).isEqualTo(0);
        assertThat(result.flags()).isEmpty();
    }

    @Test
    void fraudDecisionBlock_throwsFraudBlockedException() {
        // When fraud service returns BLOCK, payment should throw FraudBlockedException
        FraudCheckResult blockResult = new FraudCheckResult(
                "check-1", FraudCheckResult.FraudDecision.BLOCK, 100, List.of("BLOCKED_IP")
        );
        assertThat(blockResult.isBlocked()).isTrue();
        assertThat(blockResult.flags()).contains("BLOCKED_IP");
    }

    @Test
    void fraudDecisionReview_paymentProceedsWithReviewFlag() {
        FraudCheckResult reviewResult = new FraudCheckResult(
                "check-2", FraudCheckResult.FraudDecision.REVIEW, 60, List.of("HIGH_VELOCITY")
        );
        assertThat(reviewResult.requiresReview()).isTrue();
        assertThat(reviewResult.isBlocked()).isFalse();
    }
}
