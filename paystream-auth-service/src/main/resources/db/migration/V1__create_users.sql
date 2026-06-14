-- Users table — core identity store for PayStream
-- Primary key is a ULID (VARCHAR 26) — time-sortable, URL-safe, no UUID collision risk
CREATE TABLE users (
    id                   VARCHAR(26)  NOT NULL,
    email                VARCHAR(255) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    full_name            VARCHAR(255) NOT NULL,
    role                 VARCHAR(50)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts INT         NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version              INT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role   CHECK (role   IN ('CUSTOMER','MERCHANT','FRAUD_ANALYST','FINANCE_OPS','ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','SUSPENDED','LOCKED'))
);

CREATE INDEX idx_users_email ON users (email);
