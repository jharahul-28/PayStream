package com.paystream.auth.infrastructure.persistence.adapter;

import com.paystream.auth.application.port.out.RefreshTokenRepository;
import com.paystream.auth.infrastructure.persistence.entity.RefreshTokenEntity;
import com.paystream.auth.infrastructure.persistence.repository.RefreshTokenJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;

    public RefreshTokenPersistenceAdapter(RefreshTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public RefreshTokenEntity save(RefreshTokenEntity token) {
        return jpaRepository.save(token);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshTokenEntity> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHashAndRevokedFalse(tokenHash);
    }

    @Override
    @Transactional
    public void revokeAllByUserId(String userId) {
        jpaRepository.revokeAllByUserId(userId);
    }
}
