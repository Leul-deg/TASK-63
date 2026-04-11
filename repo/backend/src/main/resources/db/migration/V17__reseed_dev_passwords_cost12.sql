-- ============================================================
-- V17__reseed_dev_passwords_cost12.sql
--
-- V14 seeded dev accounts with BCrypt cost 10 (gen_salt('bf', 10)).
-- SecurityConfig requires cost 12.  This migration regenerates the
-- three fixed dev-user hashes at the correct cost factor.
--
-- Targets only the three fixed dev UUIDs, so this is a no-op on
-- any database that does not have those rows (e.g. production).
-- Password remains:  password
-- ============================================================

UPDATE users
SET    password_hash = crypt('password', gen_salt('bf', 12)),
       updated_at    = NOW()
WHERE  id IN (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000003'
);
