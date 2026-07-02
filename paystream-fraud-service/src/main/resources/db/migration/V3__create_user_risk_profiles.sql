-- Per-user behavioral baseline used by the rules engine
CREATE TABLE user_risk_profiles (
    user_id                VARCHAR(26) PRIMARY KEY,
    avg_transaction_amount BIGINT      NOT NULL DEFAULT 0,
    typical_hours_start    INT         NOT NULL DEFAULT 8,
    typical_hours_end      INT         NOT NULL DEFAULT 22,
    known_device_ids       TEXT[]      NOT NULL DEFAULT '{}',
    known_ip_prefixes      TEXT[]      NOT NULL DEFAULT '{}',
    chargeback_count_30d   INT         NOT NULL DEFAULT 0,
    transaction_count_30d  INT         NOT NULL DEFAULT 0,
    last_updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
