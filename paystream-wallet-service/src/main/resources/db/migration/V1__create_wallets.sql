-- Wallet table — one wallet per user per currency.
-- UNIQUE(user_id, currency) enforces the one-wallet-per-currency rule at the DB level.
-- balance is stored in minor units (e.g. paise for INR) to avoid floating-point imprecision.
-- balance_non_negative CHECK prevents the balance from going negative even if application bugs slip through.
CREATE TABLE wallets (
    id          VARCHAR(26)  PRIMARY KEY,
    user_id     VARCHAR(26)  NOT NULL,
    balance     BIGINT       NOT NULL DEFAULT 0,
    currency    CHAR(3)      NOT NULL DEFAULT 'INR',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version     INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT balance_non_negative CHECK (balance >= 0),
    CONSTRAINT wallets_user_currency_unique UNIQUE (user_id, currency)
);

CREATE INDEX idx_wallets_user_id ON wallets(user_id);
