package com.paystream.auth.application.port.out;

import com.paystream.auth.infrastructure.persistence.entity.RefreshTokenEntity;

import java.util.Optional;

/** Output port — refresh token persistence. */
public interface RefreshTokenRepository {

    RefreshTokenEntity save(RefreshTokenEntity token);

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    void revokeAllByUserId(String userId);
}
