-- ============================================================
-- V7__messaging_enhancements.sql
-- Adds per-session read receipts, staff-block, quick-reply
-- templates, and extends messages with type / image / quick-reply.
-- ============================================================

-- ── Extend messages ───────────────────────────────────────────────────────────
ALTER TABLE messages
    ADD COLUMN message_type    VARCHAR(20)  NOT NULL DEFAULT 'TEXT',
    ADD COLUMN image_filename  VARCHAR(255),
    ADD COLUMN quick_reply_key VARCHAR(100);

-- Relax NOT NULL on body so image-only messages are valid
ALTER TABLE messages ALTER COLUMN body DROP NOT NULL;

-- Add SYSTEM_NOTICE as valid thread_type value (no enum constraint in PG,
-- but document the allowed set here for clarity)

-- ── Per-device / per-session read receipts ────────────────────────────────────
-- Inserted automatically when a recipient opens a thread.
-- session_id is the Spring Session JDBC session ID (max 255 chars).
CREATE TABLE message_read_receipts (
    message_id      UUID         NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    reader_user_id  UUID         NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    session_id      VARCHAR(255) NOT NULL,
    read_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (message_id, reader_user_id, session_id)
);

CREATE INDEX idx_read_receipts_message ON message_read_receipts(message_id);
CREATE INDEX idx_read_receipts_session ON message_read_receipts(reader_user_id, session_id);

-- ── Student can block a staff member from initiating new threads ──────────────
CREATE TABLE staff_blocks (
    student_user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_staff_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (student_user_id, blocked_staff_user_id)
);

CREATE INDEX idx_staff_blocks_staff ON staff_blocks(blocked_staff_user_id);

-- ── Quick-reply templates (pre-seeded, staff-visible) ─────────────────────────
CREATE TABLE quick_reply_templates (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    reply_key  VARCHAR(100) NOT NULL UNIQUE,
    label      VARCHAR(100) NOT NULL,
    body       TEXT         NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INT          NOT NULL DEFAULT 0
);

INSERT INTO quick_reply_templates (reply_key, label, body, sort_order) VALUES
    ('ack',                   'Acknowledged',          'Thank you — I have received your message and will follow up shortly.',                                             1),
    ('more_info',             'Need More Info',         'Could you please provide more details so I can assist you better?',                                               2),
    ('resolved',              'Resolved',               'This matter has been resolved. Please let us know if you need further assistance.',                               3),
    ('maintenance_scheduled', 'Maintenance Scheduled',  'Your maintenance request has been scheduled. A staff member will be in touch to confirm the appointment.',        4),
    ('office_hours',          'Office Hours',           'Please stop by during office hours (Mon–Fri, 9 am–5 pm) or continue this thread with any further questions.',     5);
