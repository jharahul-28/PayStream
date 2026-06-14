-- Refresh tokens — only the SHA-256 hash is stored, never the raw token
CREATE TABLE refresh_tokens (
    id          VARCHAR(26)  NOT NULL,
    user_id     VARCHAR(26)  NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Covering index for token validation queries (user_id + active status + expiry)
CREATE INDEX idx_rt_user_active ON refresh_tokens (user_id, revoked, expires_at);
