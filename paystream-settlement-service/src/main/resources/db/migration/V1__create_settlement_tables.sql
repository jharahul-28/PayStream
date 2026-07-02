CREATE TABLE settlement_batches (
    id                    VARCHAR(26)  PRIMARY KEY,
    merchant_id           VARCHAR(26)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,   -- PENDING, PROCESSING, SETTLED, FAILED
    total_payment_count   INT          NOT NULL DEFAULT 0,
    gross_amount          BIGINT       NOT NULL DEFAULT 0,
    fee_amount            BIGINT       NOT NULL DEFAULT 0,
    net_amount            BIGINT       NOT NULL DEFAULT 0,
    currency              CHAR(3)      NOT NULL,
    settlement_date       DATE         NOT NULL,
    processing_started_at TIMESTAMPTZ,
    settled_at            TIMESTAMPTZ,
    failure_reason        VARCHAR(500),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_settlement_batches_merchant ON settlement_batches(merchant_id);
CREATE INDEX idx_settlement_batches_status   ON settlement_batches(status, settlement_date);

CREATE TABLE settlement_items (
    id          VARCHAR(26)  PRIMARY KEY,
    batch_id    VARCHAR(26)  NOT NULL REFERENCES settlement_batches(id),
    payment_id  VARCHAR(26)  NOT NULL,
    amount      BIGINT       NOT NULL,
    fee_amount  BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',   -- PENDING, SETTLED
    settled_at  TIMESTAMPTZ,
    UNIQUE(batch_id, payment_id)
);

CREATE INDEX idx_settlement_items_batch  ON settlement_items(batch_id, status);
CREATE INDEX idx_settlement_items_payment ON settlement_items(payment_id);
