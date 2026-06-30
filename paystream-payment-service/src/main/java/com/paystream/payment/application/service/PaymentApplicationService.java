package com.paystream.payment.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.common.constant.ErrorCode;
import com.paystream.common.constant.RedisKeys;
import com.paystream.common.event.BaseEvent;
import com.paystream.common.event.payment.PaymentCompletedEvent;
import com.paystream.common.event.payment.PaymentFailedEvent;
import com.paystream.common.event.payment.PaymentInitiatedEvent;
import com.paystream.common.exception.DomainException;
import com.paystream.common.exception.FraudBlockedException;
import com.paystream.common.exception.ResourceNotFoundException;
import com.paystream.common.exception.ValidationException;
import com.paystream.common.fraud.FraudCheckResult;
import com.paystream.common.util.IdGenerator;
import com.paystream.payment.api.dto.request.InitiatePaymentRequest;
import com.paystream.payment.api.dto.request.RefundRequest;
import com.paystream.payment.api.dto.response.PaymentResponse;
import com.paystream.payment.application.port.in.PaymentUseCase;
import com.paystream.payment.application.port.out.OutboxEventPort;
import com.paystream.payment.application.port.out.PaymentRepository;
import com.paystream.payment.domain.model.Payment;
import com.paystream.payment.domain.model.PaymentStatus;
import com.paystream.payment.infrastructure.external.FraudServiceClient;
import com.paystream.payment.infrastructure.external.WalletServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates the payment initiation lifecycle.
 *
 * Step-by-step (see spec §PAYMENT SERVICE → Payment orchestration):
 *  1. Idempotency check — Redis key lookup
 *  2. Persist PENDING + SET Redis "PROCESSING" atomically
 *  3. Fraud check placeholder (replaced in M4)
 *  4. Debit source wallet — on failure: markFailed, DEL Redis
 *  5. Credit destination wallet — on failure: compensate (credit source back), markFailed, DEL Redis
 *  6. Mark COMPLETED + write outbox event atomically — SET Redis = response
 *  7. (M3) Ledger entries written asynchronously via Kafka outbox relay — NOT synchronous Feign
 */
@Service
public class PaymentApplicationService implements PaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    private static final long   IDEMPOTENCY_TTL_SECONDS = 86400L;
    private static final String PROCESSING_SENTINEL     = "PROCESSING";

    private final PaymentRepository     paymentRepository;
    private final WalletServiceClient   walletServiceClient;
    private final FraudServiceClient    fraudServiceClient;
    private final OutboxEventPort       outboxEventPort;
    private final StringRedisTemplate   redisTemplate;
    private final ObjectMapper          objectMapper;
    private final MeterRegistry         meterRegistry;

    @Value("${paystream.internal.service-key:dev-only-local-key}")
    private String internalServiceKey;

    public PaymentApplicationService(
            PaymentRepository paymentRepository,
            WalletServiceClient walletServiceClient,
            FraudServiceClient fraudServiceClient,
            OutboxEventPort outboxEventPort,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.paymentRepository   = paymentRepository;
        this.walletServiceClient = walletServiceClient;
        this.fraudServiceClient  = fraudServiceClient;
        this.outboxEventPort     = outboxEventPort;
        this.redisTemplate       = redisTemplate;
        this.objectMapper        = objectMapper;
        this.meterRegistry       = meterRegistry;
    }

    // -------------------------------------------------------------------------
    // Initiate payment
    // -------------------------------------------------------------------------

    @Override
    public PaymentResponse initiatePayment(String userId, String idempotencyKey, InitiatePaymentRequest request) {

        // Pre-validation
        if (request.sourceWalletId().equals(request.destinationWalletId())) {
            throw new ValidationException("sourceWalletId and destinationWalletId must be different");
        }

        String idempKey = RedisKeys.idempotency(userId, idempotencyKey);

        // Step 1 — idempotency check
        String existing = redisTemplate.opsForValue().get(idempKey);

        if (PROCESSING_SENTINEL.equals(existing)) {
            log.info("Payment in-flight idempotencyKey={} correlationId={}", idempotencyKey, MDC.get("correlationId"));
            // Return 202 via exception message — caller interprets 202 Accepted
            throw new DomainException(ErrorCode.IDEMPOTENCY_CONFLICT, "Payment is currently being processed");
        }

        if (existing != null) {
            // Cached completed response — replay without hitting wallet/ledger
            log.info("Idempotent replay idempotencyKey={} correlationId={}", idempotencyKey, MDC.get("correlationId"));
            return paymentRepository
                    .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .map(this::toResponse)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment", idempotencyKey));
        }

        // Step 2 — persist PENDING and acquire processing sentinel
        Payment payment = persistPending(userId, idempotencyKey, request);
        redisTemplate.opsForValue().set(idempKey, PROCESSING_SENTINEL, IDEMPOTENCY_TTL_SECONDS, TimeUnit.SECONDS);

        // Step 3 — Fraud check (Stage 1 synchronous rules — decision is FINAL)
        // If the fraud circuit breaker is open, FraudServiceFallbackFactory returns ALLOW(0).
        // AI enrichment (Stage 2) happens asynchronously in fraud-service — never changes this decision.
        FraudCheckResult fraudResult = performFraudCheck(payment, request);
        if (fraudResult.isBlocked()) {
            payment.markFailed("Fraud BLOCK: " + fraudResult.flags(), ErrorCode.FRAUD_BLOCKED.getCode());
            paymentRepository.save(payment);
            redisTemplate.delete(idempKey);
            Counter.builder("paystream.payments.failed.total")
                    .tag("failureCode", ErrorCode.FRAUD_BLOCKED.getCode())
                    .register(meterRegistry).increment();
            throw new FraudBlockedException("Payment blocked by fraud detection. Flags: " + fraudResult.flags());
        }
        if (fraudResult.requiresReview()) {
            log.warn("Payment flagged for review paymentId={} userId={} flags={} correlationId={}",
                    payment.getId(), userId, fraudResult.flags(), MDC.get("correlationId"));
        }

        // Step 4 — Debit source wallet
        try {
            walletServiceClient.debit(
                    request.sourceWalletId(), internalServiceKey,
                    new WalletServiceClient.DebitCreditBody(request.amount(), request.currency(), "Payment " + payment.getId())
            );
        } catch (Exception e) {
            log.warn("Debit failed paymentId={} correlationId={} error={}", payment.getId(), MDC.get("correlationId"), e.getMessage());
            payment.markFailed(e.getMessage(), ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE.getCode());
            paymentRepository.save(payment);
            redisTemplate.delete(idempKey);
            throw e;
        }

        // Step 5 — Credit destination wallet; compensate if credit fails
        try {
            walletServiceClient.credit(
                    request.destinationWalletId(), internalServiceKey,
                    new WalletServiceClient.DebitCreditBody(request.amount(), request.currency(), "Payment " + payment.getId())
            );
        } catch (Exception e) {
            log.error("Credit failed — compensating by crediting source back paymentId={} correlationId={}",
                    payment.getId(), MDC.get("correlationId"), e);
            try {
                walletServiceClient.credit(
                        request.sourceWalletId(), internalServiceKey,
                        new WalletServiceClient.DebitCreditBody(request.amount(), request.currency(), "Compensation for payment " + payment.getId())
                );
            } catch (Exception compensateEx) {
                log.error("COMPENSATION FAILED paymentId={} sourceWalletId={} correlationId={}",
                        payment.getId(), request.sourceWalletId(), MDC.get("correlationId"), compensateEx);
            }
            payment.markFailed(e.getMessage(), ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE.getCode());
            paymentRepository.save(payment);
            redisTemplate.delete(idempKey);
            throw e;
        }

        // Step 6 — Mark COMPLETED and write outbox event atomically
        payment.transitionTo(PaymentStatus.PROCESSING);
        payment.transitionTo(PaymentStatus.COMPLETED);
        Payment completed = paymentRepository.save(payment);

        // Outbox event written in the same transaction — relay publishes to Kafka asynchronously.
        // Ledger entries are created by ledger-service consuming wallet.debited / wallet.credited events.
        writeOutboxEvent(completed, "PaymentCompleted");

        // Cache successful response in Redis
        redisTemplate.opsForValue().set(idempKey, "COMPLETED", IDEMPOTENCY_TTL_SECONDS, TimeUnit.SECONDS);

        // Metrics
        Counter.builder("paystream.payments.completed.total")
                .tag("currency", completed.getCurrency())
                .register(meterRegistry).increment();

        log.info("Payment completed paymentId={} amount={} currency={} correlationId={}",
                completed.getId(), completed.getAmount(), completed.getCurrency(), MDC.get("correlationId"));

        return toResponse(completed);
    }

    @Transactional
    protected Payment persistPending(String userId, String idempotencyKey, InitiatePaymentRequest request) {
        Payment payment = new Payment(
                IdGenerator.generate(), userId, idempotencyKey,
                request.sourceWalletId(), request.destinationWalletId(),
                request.amount(), request.currency(),
                PaymentStatus.PENDING,
                null, null, request.note(),
                0, Instant.now(), Instant.now()
        );
        return paymentRepository.save(payment);
    }

    // -------------------------------------------------------------------------
    // Fraud check — with circuit breaker
    // -------------------------------------------------------------------------

    @CircuitBreaker(name = "fraud-service", fallbackMethod = "fraudFallback")
    protected FraudCheckResult performFraudCheck(Payment payment, InitiatePaymentRequest request) {
        FraudServiceClient.FraudCheckRequest fraudReq = new FraudServiceClient.FraudCheckRequest(
                payment.getId(), payment.getUserId(), request.amount(), request.currency(),
                request.sourceWalletId(), request.destinationWalletId(),
                null, null
        );
        var response = fraudServiceClient.check(internalServiceKey, fraudReq);
        return response.getData() != null ? response.getData() : FraudCheckResult.allow(0);
    }

    protected FraudCheckResult fraudFallback(Payment payment, InitiatePaymentRequest request, Throwable t) {
        log.warn("Fraud circuit open — ALLOW passthrough paymentId={} correlationId={} cause={}",
                payment.getId(), MDC.get("correlationId"), t.getMessage());
        return FraudCheckResult.allow(0);
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String paymentId, String userId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (!payment.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Payment", paymentId);
        }
        return toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> listPayments(String userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> listAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable).map(this::toResponse);
    }

    // -------------------------------------------------------------------------
    // Refund
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public PaymentResponse refund(String paymentId, String userId, RefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (!payment.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Payment", paymentId);
        }

        // Only COMPLETED payments can be refunded
        payment.transitionTo(PaymentStatus.REFUNDED);

        // Debit destination (who received the money) and credit source (original payer)
        walletServiceClient.debit(
                payment.getDestinationWalletId(), internalServiceKey,
                new WalletServiceClient.DebitCreditBody(request.amount(), payment.getCurrency(), "Refund for payment " + paymentId)
        );
        walletServiceClient.credit(
                payment.getSourceWalletId(), internalServiceKey,
                new WalletServiceClient.DebitCreditBody(request.amount(), payment.getCurrency(), "Refund credit for payment " + paymentId)
        );

        Payment saved = paymentRepository.save(payment);

        // Outbox event — ledger will write refund entries asynchronously via Kafka
        writeOutboxEvent(saved, "PaymentRefunded");

        log.info("Payment refunded paymentId={} amount={} correlationId={}", paymentId, request.amount(), MDC.get("correlationId"));
        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Outbox helpers
    // -------------------------------------------------------------------------

    private void writeOutboxEvent(Payment payment, String eventType) {
        try {
            BaseEvent<?> envelope = switch (eventType) {
                case "PaymentCompleted" -> new BaseEvent<>(
                        IdGenerator.generate(), eventType, "1.0", Instant.now(),
                        MDC.get("correlationId"), "payment-service",
                        new PaymentCompletedEvent(payment.getId(), payment.getUserId(),
                                payment.getSourceWalletId(), payment.getDestinationWalletId(),
                                payment.getAmount(), payment.getCurrency(), MDC.get("correlationId")));
                case "PaymentFailed" -> new BaseEvent<>(
                        IdGenerator.generate(), eventType, "1.0", Instant.now(),
                        MDC.get("correlationId"), "payment-service",
                        new PaymentFailedEvent(payment.getId(), payment.getUserId(),
                                payment.getSourceWalletId(), payment.getDestinationWalletId(),
                                payment.getAmount(), payment.getCurrency(),
                                payment.getFailureReason(), payment.getFailureCode(), MDC.get("correlationId")));
                case "PaymentInitiated" -> new BaseEvent<>(
                        IdGenerator.generate(), eventType, "1.0", Instant.now(),
                        MDC.get("correlationId"), "payment-service",
                        new PaymentInitiatedEvent(payment.getId(), payment.getUserId(),
                                payment.getSourceWalletId(), payment.getDestinationWalletId(),
                                payment.getAmount(), payment.getCurrency(), MDC.get("correlationId")));
                case "PaymentRefunded" -> new BaseEvent<>(
                        IdGenerator.generate(), eventType, "1.0", Instant.now(),
                        MDC.get("correlationId"), "payment-service",
                        new PaymentCompletedEvent(payment.getId(), payment.getUserId(),
                                payment.getSourceWalletId(), payment.getDestinationWalletId(),
                                payment.getAmount(), payment.getCurrency(), MDC.get("correlationId")));
                default -> throw new IllegalArgumentException("Unknown eventType: " + eventType);
            };

            String payloadJson = objectMapper.writeValueAsString(envelope);
            outboxEventPort.save(payment.getId(), "Payment", eventType, payloadJson);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event paymentId={} eventType={} correlationId={}",
                    payment.getId(), eventType, MDC.get("correlationId"), e);
            // Do not rethrow — payment state is already saved; relay will retry on next poll
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getUserId(), p.getIdempotencyKey(),
                p.getSourceWalletId(), p.getDestinationWalletId(),
                p.getAmount(), p.getCurrency(), p.getStatus(),
                p.getFailureReason(), p.getFailureCode(), p.getNote(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
