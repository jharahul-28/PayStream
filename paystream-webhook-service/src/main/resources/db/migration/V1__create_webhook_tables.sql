CREATE TABLE webhook_endpoints (
    id          VARCHAR(26)    PRIMARY KEY,
    merchant_id VARCHAR(26)    NOT NULL,
    url         VARCHAR(2000)  NOT NULL,
    secret      VARCHAR(255)   NOT NULL,
    events      TEXT[]         NOT NULL,
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_endpoints_merchant ON webhook_endpoints(merchant_id, active);

CREATE TABLE webhook_deliveries (
    id                   VARCHAR(26)   PRIMARY KEY,
    endpoint_id          VARCHAR(26)   NOT NULL REFERENCES webhook_endpoints(id),
    event_type           VARCHAR(100)  NOT NULL,
    payload              JSONB         NOT NULL,
    status               VARCHAR(20)   NOT NULL,   -- PENDING, DELIVERED, FAILED, EXHAUSTED
    attempt_count        INT           NOT NULL DEFAULT 0,
    last_response_status INT,
    last_response_body   VARCHAR(2000),
    last_error           VARCHAR(500),
    delivered_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_deliveries_endpoint ON webhook_deliveries(endpoint_id, status);
CREATE INDEX idx_webhook_deliveries_status   ON webhook_deliveries(status, created_at);
