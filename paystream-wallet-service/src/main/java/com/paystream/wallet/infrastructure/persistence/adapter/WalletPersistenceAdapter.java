package com.paystream.wallet.infrastructure.persistence.adapter;

import com.paystream.wallet.application.port.out.WalletRepository;
import com.paystream.wallet.domain.model.Wallet;
import com.paystream.wallet.domain.model.WalletStatus;
import com.paystream.wallet.infrastructure.persistence.entity.WalletEntity;
import com.paystream.wallet.infrastructure.persistence.repository.WalletJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Translates between the domain {@link Wallet} and the JPA {@link WalletEntity}.
 * The domain model is never exposed to Spring Data directly.
 */
@Component
public class WalletPersistenceAdapter implements WalletRepository {

    private final WalletJpaRepository jpaRepository;

    public WalletPersistenceAdapter(WalletJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Wallet save(Wallet wallet) {
        WalletEntity entity = jpaRepository.findById(wallet.getId())
                .orElseGet(() -> new WalletEntity(
                        wallet.getId(), wallet.getUserId(),
                        wallet.getBalance(), wallet.getCurrency(), wallet.getStatus()));

        entity.setBalance(wallet.getBalance());
        entity.setStatus(wallet.getStatus());

        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Wallet> findById(String walletId) {
        return jpaRepository.findById(walletId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Wallet> findByUserIdAndCurrency(String userId, String currency) {
        return jpaRepository.findByUserIdAndCurrency(userId, currency).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserIdAndCurrency(String userId, String currency) {
        return jpaRepository.existsByUserIdAndCurrency(userId, currency);
    }

    private Wallet toDomain(WalletEntity e) {
        return new Wallet(
                e.getId(), e.getUserId(), e.getBalance(), e.getCurrency(),
                e.getStatus(), e.getVersion() != null ? e.getVersion() : 0,
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
