-- Transactional outbox for reliable event publishing (Milestone 3 relay).
-- Rows are written inside the same DB transaction as the payment status update,
-- guaranteeing at-least-once delivery without distributed transactions.
-- FOR UPDATE SKIP LOCKED prevents two relay pods from picking the same row.
CREATE TABLE outbox_events (
    id              VARCHAR(26)  PRIMARY KEY,
    aggregate_id    VARCHAR(26)  NOT NULL,
    aggregate_type  VARCHAR(50)  NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Only unpublished events are interesting to the relay poller
CREATE INDEX idx_outbox_unpublished ON outbox_events(published, created_at) WHERE published = FALSE;
