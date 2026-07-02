-- Stores the result of each fraud evaluation (Stage 1 deterministic + Stage 2 AI)
CREATE TABLE fraud_checks (
    id                  VARCHAR(26)   PRIMARY KEY,
    payment_id          VARCHAR(26)   NOT NULL UNIQUE,
    user_id             VARCHAR(26)   NOT NULL,
    risk_score          SMALLINT      NOT NULL,
    decision            VARCHAR(10)   NOT NULL,   -- ALLOW, BLOCK, REVIEW
    flags               TEXT[]        NOT NULL DEFAULT '{}',
    rule_version        VARCHAR(20)   NOT NULL,
    ai_narrative        TEXT,
    ai_risk_score       SMALLINT,
    ai_confidence       DECIMAL(3,2),
    ai_processed        BOOLEAN       NOT NULL DEFAULT FALSE,
    ai_processing_error VARCHAR(500),
    processing_time_ms  BIGINT,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fraud_checks_user_id   ON fraud_checks(user_id);
CREATE INDEX idx_fraud_checks_decision  ON fraud_checks(decision);
CREATE INDEX idx_fraud_checks_ai_pending ON fraud_checks(ai_processed) WHERE ai_processed = FALSE;
