package com.paystream.wallet.application.port.out;

import com.paystream.wallet.domain.model.Wallet;

import java.util.Optional;

/** Output port — persistence contract for wallets. */
public interface WalletRepository {

    Wallet save(Wallet wallet);

    Optional<Wallet> findById(String walletId);

    Optional<Wallet> findByUserIdAndCurrency(String userId, String currency);

    boolean existsByUserIdAndCurrency(String userId, String currency);
}
