-- Add reconciliation tracking to settlement_batches
ALTER TABLE settlement_batches ADD COLUMN IF NOT EXISTS reconciled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_settlement_batches_reconciled
    ON settlement_batches(reconciled, status) WHERE status = 'SETTLED';

-- Reconciliation alerts — created when amount mismatch detected
CREATE TABLE reconciliation_alerts (
    id               VARCHAR(26)  PRIMARY KEY,
    batch_id         VARCHAR(26)  REFERENCES settlement_batches(id),
    discrepancy_type VARCHAR(50)  NOT NULL,  -- AMOUNT_MISMATCH, ITEM_COUNT_MISMATCH
    expected_amount  BIGINT,
    actual_amount    BIGINT,
    description      TEXT,
    resolved         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reconciliation_alerts_batch    ON reconciliation_alerts(batch_id);
CREATE INDEX idx_reconciliation_alerts_resolved ON reconciliation_alerts(resolved) WHERE resolved = FALSE;
