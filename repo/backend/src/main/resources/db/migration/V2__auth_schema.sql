-- ============================================================
-- V2__auth_schema.sql
-- Authentication extensions:
--   • Replaces boolean is_active with account_status enum
--   • Adds scheduled_purge_at for 30-day soft-delete lifecycle
--   • Adds login_attempts for rolling-window lockout tracking
--   • Adds Spring Session JDBC tables (managed by Flyway, not
--     Spring Session auto-init, because ddl-auto=validate)
-- ============================================================

-- ============================================================
-- 1. Extend the users table
-- ============================================================

ALTER TABLE users
    DROP   COLUMN is_active,
    ADD    COLUMN account_status   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    ADD    COLUMN status_reason    TEXT,
    ADD    COLUMN status_changed_by UUID        REFERENCES users(id),
    ADD    COLUMN status_changed_at TIMESTAMPTZ,
    ADD    COLUMN scheduled_purge_at TIMESTAMPTZ;

ALTER TABLE users
    ADD CONSTRAINT chk_users_account_status
    CHECK (account_status IN ('ACTIVE', 'DISABLED', 'FROZEN', 'BLACKLISTED'));

CREATE INDEX idx_users_account_status
    ON users(account_status) WHERE account_status != 'ACTIVE';

CREATE INDEX idx_users_scheduled_purge
    ON users(scheduled_purge_at) WHERE scheduled_purge_at IS NOT NULL;

-- ============================================================
-- 2. Login attempt log (rolling-window lockout + audit trail)
-- ============================================================

CREATE TABLE login_attempts (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- NULL user_id means the username was not found (unknown identifier).
    user_id      UUID        REFERENCES users(id) ON DELETE CASCADE,
    -- Raw identifier the caller supplied (username or email).
    identifier   VARCHAR(255) NOT NULL,
    ip_address   VARCHAR(45),
    user_agent   TEXT,
    succeeded    BOOLEAN     NOT NULL DEFAULT FALSE,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Fast lookup: count recent failures for a specific user
CREATE INDEX idx_login_attempts_user_window
    ON login_attempts(user_id, attempted_at DESC)
    WHERE succeeded = FALSE;

-- Housekeeping: clean up old rows
CREATE INDEX idx_login_attempts_cleanup
    ON login_attempts(attempted_at);

-- ============================================================
-- 3. Spring Session JDBC tables
--    Column types follow the official Spring Session schema for
--    PostgreSQL. Must match the version of spring-session-jdbc
--    pulled in by Spring Boot 3.2.x (spring-session-core 3.2.x).
-- ============================================================

CREATE TABLE spring_session (
    primary_id            CHAR(36)     NOT NULL,
    session_id            CHAR(36)     NOT NULL,
    creation_time         BIGINT       NOT NULL,
    last_access_time      BIGINT       NOT NULL,
    max_inactive_interval INT          NOT NULL,
    expiry_time           BIGINT       NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX        spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX        spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36)     NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
