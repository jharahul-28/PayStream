package com.paystream.wallet.infrastructure.persistence.repository;

import com.paystream.wallet.infrastructure.persistence.entity.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data JPA interface for wallet persistence. */
public interface WalletJpaRepository extends JpaRepository<WalletEntity, String> {

    Optional<WalletEntity> findByUserIdAndCurrency(String userId, String currency);

    boolean existsByUserIdAndCurrency(String userId, String currency);
}
