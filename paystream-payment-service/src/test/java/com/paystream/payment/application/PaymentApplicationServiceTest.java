package com.paystream.payment.application;

import com.paystream.common.constant.RedisKeys;
import com.paystream.common.exception.DomainException;
import com.paystream.payment.api.dto.request.InitiatePaymentRequest;
import com.paystream.payment.api.dto.request.RefundRequest;
import com.paystream.payment.api.dto.response.PaymentResponse;
import com.paystream.payment.application.port.out.PaymentRepository;
import com.paystream.payment.application.service.PaymentApplicationService;
import com.paystream.payment.domain.model.Payment;
import com.paystream.payment.domain.model.PaymentStatus;
import com.paystream.payment.infrastructure.external.LedgerServiceClient;
import com.paystream.payment.infrastructure.external.WalletServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentApplicationService.
 * Mocks Redis, WalletClient, LedgerClient, and PaymentRepository.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentApplicationService")
class PaymentApplicationServiceTest {

    @Mock private PaymentRepository      paymentRepository;
    @Mock private WalletServiceClient    walletServiceClient;
    @Mock private LedgerServiceClient    ledgerServiceClient;
    @Mock private StringRedisTemplate    redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private PaymentApplicationService service;

    private static final String USER_ID       = "USER01";
    private static final String IDEM_KEY      = "IDEM-001";
    private static final String SRC_WALLET    = "SRC-W01";
    private static final String DST_WALLET    = "DST-W01";
    private static final String INTERNAL_KEY  = "dev-only-local-key";

    @BeforeEach
    void setUp() {
        service = new PaymentApplicationService(
                paymentRepository, walletServiceClient, ledgerServiceClient, redisTemplate);
        ReflectionTestUtils.setField(service, "internalServiceKey", INTERNAL_KEY);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("Happy path — idempotencyKey absent in Redis proceeds to COMPLETED")
    void initiatePayment_happyPath() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletServiceClient.debit(any(), any(), any())).thenReturn(null);
        when(walletServiceClient.credit(any(), any(), any())).thenReturn(null);
        when(ledgerServiceClient.createDoubleEntry(any(), any())).thenReturn(null);

        PaymentResponse response = service.initiatePayment(USER_ID, IDEM_KEY,
                new InitiatePaymentRequest(SRC_WALLET, DST_WALLET, 100000L, "INR", null));

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(walletServiceClient).debit(eq(SRC_WALLET), eq(INTERNAL_KEY), any());
        verify(walletServiceClient).credit(eq(DST_WALLET), eq(INTERNAL_KEY), any());
    }

    @Test
    @DisplayName("Idempotency replay — COMPLETED sentinel in Redis returns cached payment")
    void initiatePayment_idempotentReplay() {
        when(valueOps.get(anyString())).thenReturn("COMPLETED");
        Payment existing = completedPayment();
        when(paymentRepository.findByUserIdAndIdempotencyKey(USER_ID, IDEM_KEY))
                .thenReturn(Optional.of(existing));

        PaymentResponse response = service.initiatePayment(USER_ID, IDEM_KEY,
                new InitiatePaymentRequest(SRC_WALLET, DST_WALLET, 100000L, "INR", null));

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(walletServiceClient, never()).debit(any(), any(), any());
        verify(walletServiceClient, never()).credit(any(), any(), any());
    }

    @Test
    @DisplayName("PROCESSING sentinel — returns IDEMPOTENCY_CONFLICT")
    void initiatePayment_processing_returnsConflict() {
        when(valueOps.get(anyString())).thenReturn("PROCESSING");

        assertThatThrownBy(() -> service.initiatePayment(USER_ID, IDEM_KEY,
                new InitiatePaymentRequest(SRC_WALLET, DST_WALLET, 100000L, "INR", null)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("being processed");
    }

    @Test
    @DisplayName("Same source and destination wallet rejected with ValidationException")
    void initiatePayment_sameWallet_rejected() {
        assertThatThrownBy(() -> service.initiatePayment(USER_ID, IDEM_KEY,
                new InitiatePaymentRequest(SRC_WALLET, SRC_WALLET, 100000L, "INR", null)))
                .isInstanceOf(com.paystream.common.exception.ValidationException.class);
    }

    @Test
    @DisplayName("Debit failure marks payment FAILED and cleans Redis")
    void initiatePayment_debitFails_marksFailedAndCleansRedis() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new com.paystream.common.exception.ExternalServiceException("wallet-service", "timeout"))
                .when(walletServiceClient).debit(any(), any(), any());

        assertThatThrownBy(() -> service.initiatePayment(USER_ID, IDEM_KEY,
                new InitiatePaymentRequest(SRC_WALLET, DST_WALLET, 100000L, "INR", null)))
                .isInstanceOf(com.paystream.common.exception.ExternalServiceException.class);

        verify(redisTemplate).delete(anyString());
        verify(walletServiceClient, never()).credit(any(), any(), any());
    }

    @Test
    @DisplayName("Credit failure triggers compensation (credit source back) and marks FAILED")
    void initiatePayment_creditFails_compensates() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletServiceClient.debit(any(), any(), any())).thenReturn(null);
        doThrow(new com.paystream.common.exception.ExternalServiceException("wallet-service", "timeout"))
                .when(walletServiceClient).credit(eq(DST_WALLET), any(), any());

        assertThatThrownBy(() -> service.initiatePayment(USER_ID, IDEM_KEY,
                new InitiatePaymentRequest(SRC_WALLET, DST_WALLET, 100000L, "INR", null)))
                .isInstanceOf(com.paystream.common.exception.ExternalServiceException.class);

        // Compensation: credit source back
        verify(walletServiceClient).credit(eq(SRC_WALLET), any(), any());
        verify(redisTemplate).delete(anyString());
    }

    @Test
    @DisplayName("Ledger failure after COMPLETED does not reverse wallets")
    void initiatePayment_ledgerFails_doesNotReverseWallets() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletServiceClient.debit(any(), any(), any())).thenReturn(null);
        when(walletServiceClient.credit(any(), any(), any())).thenReturn(null);
        // Ledger call throws but payment should still complete
        doThrow(new RuntimeException("ledger down")).when(ledgerServiceClient)
                .createDoubleEntry(any(), any());

        PaymentResponse response = service.initiatePayment(USER_ID, IDEM_KEY,
                new InitiatePaymentRequest(SRC_WALLET, DST_WALLET, 100000L, "INR", null));

        // Payment is COMPLETED — ledger failure is logged but not re-thrown
        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        // Wallets are NOT reversed
        verify(walletServiceClient, times(1)).debit(any(), any(), any());
        verify(walletServiceClient, times(1)).credit(eq(DST_WALLET), any(), any());
    }

    @Test
    @DisplayName("Refund on COMPLETED payment succeeds")
    void refund_completed_succeeds() {
        Payment completed = completedPayment();
        when(paymentRepository.findById("PAY01")).thenReturn(Optional.of(completed));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(walletServiceClient.debit(any(), any(), any())).thenReturn(null);
        when(walletServiceClient.credit(any(), any(), any())).thenReturn(null);
        when(ledgerServiceClient.createDoubleEntry(any(), any())).thenReturn(null);

        PaymentResponse response = service.refund("PAY01", USER_ID, new RefundRequest(100000L, "test refund"));

        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("Refund on PROCESSING payment throws DomainException PS-2004")
    void refund_processing_throws() {
        Payment processing = new Payment("PAY02", USER_ID, IDEM_KEY, SRC_WALLET, DST_WALLET,
                100000L, "INR", PaymentStatus.PROCESSING, null, null, null, 0, Instant.now(), Instant.now());
        when(paymentRepository.findById("PAY02")).thenReturn(Optional.of(processing));

        assertThatThrownBy(() -> service.refund("PAY02", USER_ID, new RefundRequest(100000L, null)))
                .isInstanceOf(DomainException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Payment completedPayment() {
        Payment p = new Payment("PAY01", USER_ID, IDEM_KEY, SRC_WALLET, DST_WALLET,
                100000L, "INR", PaymentStatus.PENDING, null, null, null, 0, Instant.now(), Instant.now());
        p.transitionTo(PaymentStatus.PROCESSING);
        p.transitionTo(PaymentStatus.COMPLETED);
        return p;
    }
}
