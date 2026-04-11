-- ============================================================
-- V15__audit_log_actor_snapshot.sql
--
-- Allows user rows to be hard-purged without violating the
-- audit_logs FK, while keeping actor identity readable.
--
-- Two changes:
--   1. Replace ON DELETE RESTRICT (default) with ON DELETE SET NULL
--      so that purging a user nulls the FK instead of blocking DELETE.
--   2. Add actor_username / actor_email snapshot columns that are
--      populated at write time; these survive the purge intact.
-- ============================================================

-- 1. Swap the FK constraint to SET NULL
ALTER TABLE audit_logs
    DROP CONSTRAINT IF EXISTS audit_logs_user_id_fkey;

ALTER TABLE audit_logs
    ADD CONSTRAINT audit_logs_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

-- 2. Actor snapshot columns (idempotent)
ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS actor_username VARCHAR(100),
    ADD COLUMN IF NOT EXISTS actor_email    VARCHAR(255);
