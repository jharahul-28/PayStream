-- Append-only audit log. NO UPDATE. NO DELETE. Partitioned by month for scalability.
CREATE TABLE audit_log (
    id              VARCHAR(26)  PRIMARY KEY,
    event_id        VARCHAR(26)  NOT NULL UNIQUE,
    event_type      VARCHAR(100) NOT NULL,
    entity_id       VARCHAR(26)  NOT NULL,
    entity_type     VARCHAR(50)  NOT NULL,
    actor_id        VARCHAR(26),
    actor_role      VARCHAR(50),
    action          VARCHAR(100) NOT NULL,
    old_state       JSONB,
    new_state       JSONB,
    metadata        JSONB,
    correlation_id  VARCHAR(255),
    source_service  VARCHAR(50)  NOT NULL,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- event_id UNIQUE index ensures idempotent inserts (ON CONFLICT DO NOTHING)
CREATE INDEX idx_audit_entity      ON audit_log(entity_id, entity_type);
CREATE INDEX idx_audit_actor       ON audit_log(actor_id);
CREATE INDEX idx_audit_event_type  ON audit_log(event_type);
CREATE INDEX idx_audit_created_at  ON audit_log(created_at DESC);
CREATE INDEX idx_audit_correlation ON audit_log(correlation_id);
