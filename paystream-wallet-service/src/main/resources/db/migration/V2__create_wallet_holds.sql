-- Wallet holds represent reserved (locked) funds for pending payments.
-- Holds are released either on payment completion or expiry.
-- released flag is never deleted — holds are append-only for auditability.
CREATE TABLE wallet_holds (
    id          VARCHAR(26)  PRIMARY KEY,
    wallet_id   VARCHAR(26)  NOT NULL REFERENCES wallets(id),
    amount      BIGINT       NOT NULL,
    reason      VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    released    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wallet_holds_wallet_id ON wallet_holds(wallet_id, released);
CREATE INDEX idx_wallet_holds_expires ON wallet_holds(expires_at) WHERE released = FALSE;
