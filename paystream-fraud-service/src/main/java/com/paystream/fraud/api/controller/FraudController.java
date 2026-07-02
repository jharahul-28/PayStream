package com.paystream.fraud.api.controller;

import com.paystream.common.api.ApiResponse;
import com.paystream.common.constant.RedisKeys;
import com.paystream.common.fraud.FraudCheckResult;
import com.paystream.fraud.api.dto.request.FraudCheckRequest;
import com.paystream.fraud.api.dto.response.FraudCheckResponse;
import com.paystream.fraud.application.command.EvaluateFraudCommand;
import com.paystream.fraud.application.port.in.FraudEvaluationUseCase;
import com.paystream.fraud.application.port.out.FraudCheckRepository;
import com.paystream.fraud.domain.model.FraudCheck;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/fraud")
public class FraudController {

    private static final Logger log = LoggerFactory.getLogger(FraudController.class);

    private final FraudEvaluationUseCase fraudEvaluationUseCase;
    private final FraudCheckRepository   fraudCheckRepository;
    private final StringRedisTemplate    redisTemplate;

    public FraudController(FraudEvaluationUseCase fraudEvaluationUseCase,
                           FraudCheckRepository fraudCheckRepository,
                           StringRedisTemplate redisTemplate) {
        this.fraudEvaluationUseCase = fraudEvaluationUseCase;
        this.fraudCheckRepository   = fraudCheckRepository;
        this.redisTemplate          = redisTemplate;
    }

    /**
     * Stage 1 sync fraud check — called by payment-service.
     * Requires X-Internal-Service-Key header (validated by security filter).
     * SLA target: < 10ms.
     */
    @PostMapping("/check")
    public ResponseEntity<ApiResponse<FraudCheckResult>> check(
            @Valid @RequestBody FraudCheckRequest request) {
        log.info("Fraud check requested paymentId={} userId={} amount={} correlationId={}",
                request.paymentId(), request.userId(), request.amount(), MDC.get("correlationId"));

        EvaluateFraudCommand cmd = new EvaluateFraudCommand(
                request.paymentId(), request.userId(), request.amount(), request.currency(),
                request.sourceWalletId(), request.destinationWalletId(),
                request.deviceId(), request.ipAddress()
        );

        FraudCheckResult result = fraudEvaluationUseCase.evaluate(cmd);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** Full analysis including AI narrative — FRAUD_ANALYST only. */
    @GetMapping("/payments/{paymentId}")
    @PreAuthorize("hasAnyRole('FRAUD_ANALYST', 'ADMIN')")
    public ResponseEntity<ApiResponse<FraudCheckResponse>> getByPayment(@PathVariable String paymentId) {
        FraudCheck fc = fraudCheckRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new com.paystream.common.exception.ResourceNotFoundException("FraudCheck", paymentId));
        return ResponseEntity.ok(ApiResponse.success(toResponse(fc)));
    }

    /** Fraud history for a user — FRAUD_ANALYST only. */
    @GetMapping("/users/{userId}/history")
    @PreAuthorize("hasAnyRole('FRAUD_ANALYST', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<FraudCheckResponse>>> getUserHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "20") int limit) {
        List<FraudCheckResponse> history = fraudCheckRepository.findByUserId(userId, Math.min(limit, 100))
                .stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    /** Block a user — FRAUD_ANALYST can block, ADMIN can unblock. */
    @PutMapping("/users/{userId}/block")
    @PreAuthorize("hasAnyRole('FRAUD_ANALYST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> blockUser(@PathVariable String userId) {
        redisTemplate.opsForValue().set(RedisKeys.userBlocked(userId), "1", 30, TimeUnit.DAYS);
        log.warn("User blocked userId={} by actor={} correlationId={}", userId, "system", MDC.get("correlationId"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** Unblock a user — ADMIN only. */
    @DeleteMapping("/users/{userId}/block")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> unblockUser(@PathVariable String userId) {
        redisTemplate.delete(RedisKeys.userBlocked(userId));
        log.info("User unblocked userId={} correlationId={}", userId, MDC.get("correlationId"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private FraudCheckResponse toResponse(FraudCheck fc) {
        return new FraudCheckResponse(
                fc.getId(), fc.getPaymentId(), fc.getUserId(),
                fc.getRiskScore(), fc.getDecision().name(), fc.getFlags(),
                fc.getRuleVersion(), fc.getProcessingTimeMs(),
                fc.getAiNarrative(), fc.getAiRiskScore(), fc.getAiConfidence(),
                fc.isAiProcessed(), fc.getCreatedAt()
        );
    }
}
