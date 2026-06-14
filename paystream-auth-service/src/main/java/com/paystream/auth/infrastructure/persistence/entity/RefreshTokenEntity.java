package com.paystream.auth.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity for {@code refresh_tokens} table.
 * Only the SHA-256 hash of the opaque refresh token is stored — never the raw value.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    @Column(name = "id", length = 26, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", length = 26, nullable = false, updatable = false)
    private String userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshTokenEntity() {}

    public RefreshTokenEntity(String id, String userId, String tokenHash, Instant expiresAt) {
        this.id         = id;
        this.userId     = userId;
        this.tokenHash  = tokenHash;
        this.expiresAt  = expiresAt;
        this.revoked    = false;
        this.createdAt  = Instant.now();
    }

    public String getId()         { return id; }
    public String getUserId()     { return userId; }
    public String getTokenHash()  { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked()    { return revoked; }
    public Instant getCreatedAt() { return createdAt; }

    public void revoke() { this.revoked = true; }
}
