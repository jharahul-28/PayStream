CREATE TABLE notifications (
    id                VARCHAR(26)  PRIMARY KEY,
    user_id           VARCHAR(26)  NOT NULL,
    payment_id        VARCHAR(26),
    type              VARCHAR(30)  NOT NULL,    -- EMAIL, SMS, PUSH
    channel           VARCHAR(50)  NOT NULL,    -- PAYMENT_SUCCESS, PAYMENT_FAILED
    status            VARCHAR(20)  NOT NULL,    -- PENDING, SENT, FAILED
    recipient         VARCHAR(255) NOT NULL,
    subject           VARCHAR(500),
    body              TEXT         NOT NULL,
    attempt_count     INT          NOT NULL DEFAULT 0,
    last_attempted_at TIMESTAMPTZ,
    sent_at           TIMESTAMPTZ,
    error_message     VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user    ON notifications(user_id);
CREATE INDEX idx_notifications_status  ON notifications(status);
CREATE INDEX idx_notifications_payment ON notifications(payment_id);

-- Idempotency table for Kafka consumers
CREATE TABLE notification_processed_events (
    event_id     VARCHAR(26)  PRIMARY KEY,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
