package com.paystream.wallet.application;

import com.paystream.common.exception.DomainException;
import com.paystream.common.exception.InsufficientFundsException;
import com.paystream.wallet.api.dto.request.CreateWalletRequest;
import com.paystream.wallet.api.dto.request.DebitCreditRequest;
import com.paystream.wallet.api.dto.response.WalletResponse;
import com.paystream.wallet.application.port.out.WalletRepository;
import com.paystream.wallet.application.service.WalletApplicationService;
import com.paystream.wallet.domain.exception.WalletAlreadyExistsException;
import com.paystream.wallet.domain.model.Wallet;
import com.paystream.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletApplicationService")
class WalletApplicationServiceTest {

    @Mock
    private WalletRepository walletRepository;

    private WalletApplicationService service;

    @BeforeEach
    void setUp() {
        service = new WalletApplicationService(walletRepository);
    }

    @Test
    @DisplayName("createWallet — success creates wallet with ACTIVE status and zero balance")
    void createWallet_success() {
        when(walletRepository.existsByUserIdAndCurrency("U01", "INR")).thenReturn(false);
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletResponse response = service.createWallet("U01", new CreateWalletRequest("INR"));

        assertThat(response.userId()).isEqualTo("U01");
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.balance()).isZero();
        assertThat(response.status()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    @DisplayName("createWallet — duplicate throws WalletAlreadyExistsException")
    void createWallet_duplicate_throws() {
        when(walletRepository.existsByUserIdAndCurrency("U01", "INR")).thenReturn(true);
        assertThatThrownBy(() -> service.createWallet("U01", new CreateWalletRequest("INR")))
                .isInstanceOf(WalletAlreadyExistsException.class);
    }

    @Test
    @DisplayName("debit — sufficient balance succeeds")
    void debit_sufficientBalance() {
        Wallet wallet = activeWallet(100000L);
        when(walletRepository.findById("W01")).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletResponse response = service.debit("W01", new DebitCreditRequest(30000L, "INR", "test"));

        assertThat(response.balance()).isEqualTo(70000L);
    }

    @Test
    @DisplayName("debit — insufficient balance throws InsufficientFundsException")
    void debit_insufficientBalance_throws() {
        Wallet wallet = activeWallet(10000L);
        when(walletRepository.findById("W01")).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.debit("W01", new DebitCreditRequest(50000L, "INR", "test")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("debit — FROZEN wallet throws DomainException")
    void debit_frozenWallet_throws() {
        Wallet wallet = frozenWallet();
        when(walletRepository.findById("W01")).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.debit("W01", new DebitCreditRequest(1000L, "INR", "test")))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("credit — success increases balance")
    void credit_success() {
        Wallet wallet = activeWallet(50000L);
        when(walletRepository.findById("W01")).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WalletResponse response = service.credit("W01", new DebitCreditRequest(25000L, "INR", "top-up"));

        assertThat(response.balance()).isEqualTo(75000L);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Wallet activeWallet(long balance) {
        return new Wallet("W01", "U01", balance, "INR", WalletStatus.ACTIVE, 0, Instant.now(), Instant.now());
    }

    private Wallet frozenWallet() {
        return new Wallet("W01", "U01", 100000L, "INR", WalletStatus.FROZEN, 0, Instant.now(), Instant.now());
    }
}
