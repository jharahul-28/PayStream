-- Balance snapshots accelerate balance queries by providing a checkpoint.
-- Instead of summing all ledger entries since the beginning of time,
-- we sum from the last snapshot + all newer entries.
-- Snapshots are updated periodically (batch job in M3+).
CREATE TABLE account_balance_snapshots (
    id           VARCHAR(26)  PRIMARY KEY,
    account_id   VARCHAR(26)  NOT NULL UNIQUE,
    balance      BIGINT       NOT NULL,
    snapshot_at  TIMESTAMPTZ  NOT NULL,
    entry_count  BIGINT       NOT NULL,   -- number of entries included in snapshot (for diagnostics)
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_snapshots_account ON account_balance_snapshots(account_id);
