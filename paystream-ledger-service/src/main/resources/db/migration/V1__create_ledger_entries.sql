-- Ledger entries are APPEND-ONLY. No UPDATE. No DELETE. No soft-delete.
-- Every money movement creates exactly two entries (debit + credit) that sum to zero.
-- amount is signed: negative for DEBIT, positive for CREDIT.
--
-- UNIQUE index on (reference_id, account_id, entry_type) provides idempotency —
-- re-inserting the same leg for the same payment is silently ignored.
CREATE TABLE ledger_entries (
    id              VARCHAR(26)  PRIMARY KEY,
    account_id      VARCHAR(26)  NOT NULL,
    entry_type      VARCHAR(6)   NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount          BIGINT       NOT NULL,         -- negative for DEBIT, positive for CREDIT
    currency        CHAR(3)      NOT NULL,
    reference_id    VARCHAR(26)  NOT NULL,         -- paymentId or refundId
    reference_type  VARCHAR(20)  NOT NULL,         -- 'PAYMENT', 'REFUND', 'TOPUP'
    description     VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
    -- NO updated_at, NO deleted_at — immutability is enforced by design
);

-- Idempotency: re-inserting the same leg is a no-op at the DB level
CREATE UNIQUE INDEX idx_ledger_idempotency ON ledger_entries(reference_id, account_id, entry_type);

-- Primary query pattern: entries for an account ordered by time
CREATE INDEX idx_ledger_account_time ON ledger_entries(account_id, created_at DESC);

-- Lookup both legs of a payment/refund
CREATE INDEX idx_ledger_reference ON ledger_entries(reference_id);
