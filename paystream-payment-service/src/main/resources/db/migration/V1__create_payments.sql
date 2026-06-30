-- Payments table. UNIQUE(user_id, idempotency_key) is the second-layer deduplication guard
-- (Redis is the first layer; DB UNIQUE constraint prevents races where Redis expires before payment completes).
-- metadata is JSONB to allow extensible key-value data without schema changes.
CREATE TABLE payments (
    id                     VARCHAR(26)  PRIMARY KEY,
    user_id                VARCHAR(26)  NOT NULL,
    idempotency_key        VARCHAR(255) NOT NULL,
    source_wallet_id       VARCHAR(26)  NOT NULL,
    destination_wallet_id  VARCHAR(26)  NOT NULL,
    amount                 BIGINT       NOT NULL,
    currency               CHAR(3)      NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    failure_reason         VARCHAR(500),
    failure_code           VARCHAR(50),
    note                   VARCHAR(500),
    metadata               JSONB,
    version                INT          NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT payments_user_idempotency_unique UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_payments_user_time ON payments(user_id, created_at DESC);
CREATE INDEX idx_payments_status ON payments(status);
