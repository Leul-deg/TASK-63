-- ============================================================
-- V1__initial_schema.sql
-- Initial domain schema for the ResLife portal
-- ============================================================

-- Roles
CREATE TABLE roles (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(50)  NOT NULL UNIQUE,
    description  TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Users
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(100) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- User ↔ Role join (with timestamp)
CREATE TABLE user_roles (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

-- Residents / Students
CREATE TABLE residents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id        VARCHAR(50)  UNIQUE,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    email             VARCHAR(255) NOT NULL UNIQUE,
    phone             VARCHAR(30),
    date_of_birth     DATE,
    enrollment_status VARCHAR(50),
    room_number       VARCHAR(20),
    building_name     VARCHAR(100),
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Emergency Contacts
CREATE TABLE emergency_contacts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resident_id UUID         NOT NULL REFERENCES residents(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    relationship VARCHAR(100),
    phone       VARCHAR(30)  NOT NULL,
    email       VARCHAR(255),
    is_primary  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Move-In Records
CREATE TABLE move_in_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resident_id     UUID        NOT NULL REFERENCES residents(id),
    room_number     VARCHAR(20) NOT NULL,
    building_name   VARCHAR(100) NOT NULL,
    move_in_date    DATE        NOT NULL,
    move_out_date   DATE,
    check_in_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    recorded_by     UUID        REFERENCES users(id),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Housing Agreements
CREATE TABLE housing_agreements (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resident_id    UUID         NOT NULL REFERENCES residents(id),
    agreement_type VARCHAR(100) NOT NULL,
    signed_date    DATE,
    expires_date   DATE,
    status         VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    document_url   TEXT,
    version        VARCHAR(20),
    notes          TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Message Threads
CREATE TABLE message_threads (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject             VARCHAR(255),
    thread_type         VARCHAR(50)  NOT NULL DEFAULT 'DIRECT',
    created_by_user_id  UUID         REFERENCES users(id),
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Thread Participants
CREATE TABLE message_thread_participants (
    thread_id UUID NOT NULL REFERENCES message_threads(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES users(id)           ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (thread_id, user_id)
);

-- Messages
CREATE TABLE messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id  UUID        NOT NULL REFERENCES message_threads(id) ON DELETE CASCADE,
    sender_id  UUID        REFERENCES users(id),
    body       TEXT        NOT NULL,
    is_read    BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Notifications
CREATE TABLE notifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title               VARCHAR(255) NOT NULL,
    body                TEXT,
    type                VARCHAR(50)  NOT NULL DEFAULT 'INFO',
    is_read             BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at             TIMESTAMPTZ,
    related_entity_type VARCHAR(100),
    related_entity_id   UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Configuration Versions
CREATE TABLE configuration_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key                 VARCHAR(255) NOT NULL,
    value               TEXT,
    description         TEXT,
    version             INTEGER      NOT NULL DEFAULT 1,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by_user_id  UUID         REFERENCES users(id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (key, version)
);

-- Audit Logs (append-only, no updated_at)
CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         REFERENCES users(id),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id   UUID,
    old_values  JSONB,
    new_values  JSONB,
    ip_address  VARCHAR(45),
    user_agent  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Indexes
-- ============================================================

CREATE INDEX idx_users_deleted_at           ON users(deleted_at)           WHERE deleted_at IS NULL;
CREATE INDEX idx_residents_student_id       ON residents(student_id);
CREATE INDEX idx_residents_email            ON residents(email);
CREATE INDEX idx_residents_deleted_at       ON residents(deleted_at)       WHERE deleted_at IS NULL;
CREATE INDEX idx_ec_resident_id             ON emergency_contacts(resident_id);
CREATE INDEX idx_mir_resident_id            ON move_in_records(resident_id);
CREATE INDEX idx_ha_resident_id             ON housing_agreements(resident_id);
CREATE INDEX idx_messages_thread_id         ON messages(thread_id);
CREATE INDEX idx_messages_sender_id         ON messages(sender_id);
CREATE INDEX idx_notif_recipient_unread     ON notifications(recipient_id, is_read);
CREATE INDEX idx_config_key_active          ON configuration_versions(key) WHERE is_active = TRUE;
CREATE INDEX idx_audit_entity               ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_user_id              ON audit_logs(user_id);
CREATE INDEX idx_audit_created_at           ON audit_logs(created_at DESC);
