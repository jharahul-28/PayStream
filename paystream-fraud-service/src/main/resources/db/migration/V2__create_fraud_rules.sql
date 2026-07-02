-- Configurable fraud rules — cached in Redis (TTL 5 min), key: fraud:rules:active
CREATE TABLE fraud_rules (
    id         VARCHAR(26)  PRIMARY KEY,
    rule_code  VARCHAR(50)  NOT NULL UNIQUE,
    rule_name  VARCHAR(255) NOT NULL,
    flag_name  VARCHAR(50)  NOT NULL,
    weight     INT          NOT NULL DEFAULT 10,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    version    VARCHAR(20)  NOT NULL DEFAULT '1.0',
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Seed initial rules as per spec
INSERT INTO fraud_rules (id, rule_code, rule_name, flag_name, weight, enabled, version) VALUES
  ('01HKRULES0000000000000001', 'RULE_HIGH_AMOUNT',    'High Transaction Amount',        'HIGH_AMOUNT',          20, TRUE, '1.0'),
  ('01HKRULES0000000000000002', 'RULE_HIGH_VELOCITY',  'High Transaction Velocity',      'HIGH_VELOCITY',        30, TRUE, '1.0'),
  ('01HKRULES0000000000000003', 'RULE_NEW_DEVICE',     'Transaction from New Device',    'NEW_DEVICE',           15, TRUE, '1.0'),
  ('01HKRULES0000000000000004', 'RULE_ODD_HOUR',       'Transaction at Unusual Hour',    'ODD_HOUR',             10, TRUE, '1.0'),
  ('01HKRULES0000000000000005', 'RULE_HIGH_CHARGEBACK','High Chargeback History',        'HIGH_CHARGEBACK_HIST', 35, TRUE, '1.0'),
  ('01HKRULES0000000000000006', 'RULE_BLOCKED_IP',     'Transaction from Blocked IP',    'BLOCKED_IP',           80, TRUE, '1.0');
