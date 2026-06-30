package com.paystream.wallet.domain;

import com.paystream.common.exception.DomainException;
import com.paystream.common.exception.InsufficientFundsException;
import com.paystream.wallet.domain.model.Wallet;
import com.paystream.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Wallet domain entity.
 * No Spring context — pure Java.
 */
@DisplayName("Wallet Domain")
class WalletDomainTest {

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet("W01", "U01", 100000L, "INR",
                WalletStatus.ACTIVE, 0, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("Debit reduces balance and increments version")
    void debit_reducesBalance() {
        wallet.debit(30000L);
        assertThat(wallet.getBalance()).isEqualTo(70000L);
        assertThat(wallet.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("Credit increases balance and increments version")
    void credit_increasesBalance() {
        wallet.credit(50000L);
        assertThat(wallet.getBalance()).isEqualTo(150000L);
        assertThat(wallet.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("Debit throws InsufficientFundsException when balance < amount")
    void debit_insufficientFunds() {
        assertThatThrownBy(() -> wallet.debit(200000L))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("Debit throws DomainException when amount <= 0")
    void debit_zeroAmount() {
        assertThatThrownBy(() -> wallet.debit(0L))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("Debit on FROZEN wallet throws DomainException")
    void debit_frozenWallet() {
        wallet.freeze();
        assertThatThrownBy(() -> wallet.debit(1000L))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("FROZEN");
    }

    @Test
    @DisplayName("Credit on FROZEN wallet throws DomainException")
    void credit_frozenWallet() {
        wallet.freeze();
        assertThatThrownBy(() -> wallet.credit(1000L))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("Freeze sets status to FROZEN")
    void freeze_setsStatus() {
        wallet.freeze();
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.FROZEN);
        assertThat(wallet.isActive()).isFalse();
    }

    @Test
    @DisplayName("Unfreeze restores ACTIVE status")
    void unfreeze_restoresActive() {
        wallet.freeze();
        wallet.unfreeze();
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(wallet.isActive()).isTrue();
    }

    @Test
    @DisplayName("Unfreeze on ACTIVE wallet throws DomainException")
    void unfreeze_alreadyActive() {
        assertThatThrownBy(wallet::unfreeze)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("FROZEN");
    }

    @Test
    @DisplayName("Full debit to zero is allowed")
    void debit_toZero_allowed() {
        assertThatCode(() -> wallet.debit(100000L)).doesNotThrowAnyException();
        assertThat(wallet.getBalance()).isZero();
    }
}
