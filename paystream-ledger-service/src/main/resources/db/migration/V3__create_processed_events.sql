-- Idempotency table for Kafka consumers.
-- Before processing any wallet event, the consumer checks this table.
-- If event_id already exists, the message is a duplicate and processing is skipped.
-- Stored in the same transaction as the ledger entry — atomically prevents double-processing.
CREATE TABLE processed_events (
    event_id     VARCHAR(26)  PRIMARY KEY,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Allows periodic cleanup of old entries (events older than retention window)
CREATE INDEX idx_pe_age ON processed_events(processed_at);

-- Dead letter events written by @DltHandler when retry is exhausted
CREATE TABLE dead_letter_events (
    id           VARCHAR(26)  PRIMARY KEY,
    event_id     VARCHAR(26),
    event_type   VARCHAR(100),
    topic        VARCHAR(255),
    payload      TEXT,
    error_message VARCHAR(1000),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
