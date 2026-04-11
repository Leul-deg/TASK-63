-- ============================================================
-- V16__message_delivery_receipts.sql
--
-- Tracks when a message has been delivered to a recipient's
-- device (via background poll) — distinct from being read
-- (thread opened).  Enables the SENT → DELIVERED → READ
-- status progression shown to the sender.
-- ============================================================

CREATE TABLE message_delivery_receipts (
    message_id        UUID        NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    recipient_user_id UUID        NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    delivered_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (message_id, recipient_user_id)
);

CREATE INDEX idx_delivery_receipts_message ON message_delivery_receipts(message_id);
