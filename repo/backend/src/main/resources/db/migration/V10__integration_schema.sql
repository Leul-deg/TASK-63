-- V10: Local integration interfaces
--   integration_keys       — API keys issued to on-prem systems
--   webhook_endpoints      — outgoing webhook targets per key
--   integration_audit_logs — append-only record of every inbound/outbound call
--   webhook_deliveries     — per-attempt delivery record for outgoing webhooks

CREATE TABLE integration_keys (
    id                   UUID         PRIMARY KEY,
    key_id               VARCHAR(64)  NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    description          TEXT,
    secret               TEXT         NOT NULL,          -- AES-encrypted HMAC secret
    secret_prefix        VARCHAR(10)  NOT NULL,          -- first 8 plaintext chars, display only
    allowed_events       TEXT,                           -- JSON array; NULL = unrestricted
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    last_used_at         TIMESTAMP WITH TIME ZONE,
    revoked_at           TIMESTAMP WITH TIME ZONE,
    revoked_reason       TEXT,
    created_by_user_id   UUID         REFERENCES users(id),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_integration_keys_key_id UNIQUE (key_id)
);

CREATE TABLE webhook_endpoints (
    id                    UUID         PRIMARY KEY,
    integration_key_id    UUID         NOT NULL REFERENCES integration_keys(id) ON DELETE CASCADE,
    name                  VARCHAR(255) NOT NULL,
    target_url            VARCHAR(500) NOT NULL,         -- must resolve to a private IP
    event_types           TEXT         NOT NULL,         -- JSON array of subscribed event types
    signing_secret        TEXT         NOT NULL,         -- AES-encrypted; used to sign outgoing payloads
    signing_secret_prefix VARCHAR(10)  NOT NULL,
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE integration_audit_logs (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    integration_key_id   UUID         REFERENCES integration_keys(id),
    direction            VARCHAR(20)  NOT NULL CHECK (direction IN ('INBOUND','OUTBOUND')),
    event_type           VARCHAR(100),
    target_url           VARCHAR(500),
    source_ip            VARCHAR(45),
    http_status          INT,
    success              BOOLEAN      NOT NULL,
    error_message        TEXT,
    request_id           VARCHAR(100),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE webhook_deliveries (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_endpoint_id  UUID         NOT NULL REFERENCES webhook_endpoints(id),
    event_type           VARCHAR(100) NOT NULL,
    payload              TEXT         NOT NULL,
    http_status          INT,
    response_body        TEXT,
    attempt_count        INT          NOT NULL DEFAULT 1,
    success              BOOLEAN      NOT NULL DEFAULT FALSE,
    error_message        TEXT,
    delivered_at         TIMESTAMP WITH TIME ZONE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_integration_keys_key_id     ON integration_keys(key_id);
CREATE INDEX idx_integration_keys_active     ON integration_keys(is_active);
CREATE INDEX idx_webhook_endpoints_key       ON webhook_endpoints(integration_key_id);
CREATE INDEX idx_integration_audit_key       ON integration_audit_logs(integration_key_id);
CREATE INDEX idx_integration_audit_created   ON integration_audit_logs(created_at DESC);
CREATE INDEX idx_webhook_deliveries_endpoint ON webhook_deliveries(webhook_endpoint_id);
CREATE INDEX idx_webhook_deliveries_created  ON webhook_deliveries(created_at DESC);
