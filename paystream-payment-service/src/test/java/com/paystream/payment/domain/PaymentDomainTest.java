package com.paystream.payment.domain;

import com.paystream.common.exception.DomainException;
import com.paystream.payment.domain.model.Payment;
import com.paystream.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Payment domain entity state machine.
 * No Spring context — pure Java.
 */
@DisplayName("Payment Domain State Machine")
class PaymentDomainTest {

    private Payment payment;

    @BeforeEach
    void setUp() {
        payment = new Payment(
                "PAYMENT01", "USER01", "IDEM-001",
                "SRC-WALLET", "DST-WALLET",
                100000L, "INR", PaymentStatus.PENDING,
                null, null, "Test payment",
                0, Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("PENDING -> PROCESSING is allowed")
    void pendingToProcessing_allowed() {
        assertThatCode(() -> payment.transitionTo(PaymentStatus.PROCESSING))
                .doesNotThrowAnyException();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    @DisplayName("PENDING -> COMPLETED is rejected")
    void pendingToCompleted_rejected() {
        assertThatThrownBy(() -> payment.transitionTo(PaymentStatus.COMPLETED))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot transition payment from PENDING to COMPLETED");
    }

    @Test
    @DisplayName("PROCESSING -> COMPLETED is allowed")
    void processingToCompleted_allowed() {
        payment.transitionTo(PaymentStatus.PROCESSING);
        assertThatCode(() -> payment.transitionTo(PaymentStatus.COMPLETED))
                .doesNotThrowAnyException();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("PROCESSING -> FAILED is allowed")
    void processingToFailed_allowed() {
        payment.transitionTo(PaymentStatus.PROCESSING);
        assertThatCode(() -> payment.transitionTo(PaymentStatus.FAILED))
                .doesNotThrowAnyException();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("COMPLETED -> REFUNDED is allowed")
    void completedToRefunded_allowed() {
        payment.transitionTo(PaymentStatus.PROCESSING);
        payment.transitionTo(PaymentStatus.COMPLETED);
        assertThatCode(() -> payment.transitionTo(PaymentStatus.REFUNDED))
                .doesNotThrowAnyException();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("PENDING -> CANCELLED is allowed")
    void pendingToCancelled_allowed() {
        assertThatCode(() -> payment.transitionTo(PaymentStatus.CANCELLED))
                .doesNotThrowAnyException();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    @DisplayName("FAILED -> any state is rejected")
    void failedIsTerminal() {
        payment.transitionTo(PaymentStatus.PROCESSING);
        payment.transitionTo(PaymentStatus.FAILED);
        assertThatThrownBy(() -> payment.transitionTo(PaymentStatus.PENDING))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("markFailed sets failure fields")
    void markFailed_setsFields() {
        payment.transitionTo(PaymentStatus.PROCESSING);
        payment.markFailed("Insufficient funds", "PS-2001");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Insufficient funds");
        assertThat(payment.getFailureCode()).isEqualTo("PS-2001");
    }

    @Test
    @DisplayName("Version increments on each transition")
    void versionIncrements() {
        int initial = payment.getVersion();
        payment.transitionTo(PaymentStatus.PROCESSING);
        assertThat(payment.getVersion()).isEqualTo(initial + 1);
        payment.transitionTo(PaymentStatus.COMPLETED);
        assertThat(payment.getVersion()).isEqualTo(initial + 2);
    }
}
