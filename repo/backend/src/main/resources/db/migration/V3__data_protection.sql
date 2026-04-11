-- ============================================================
-- V3__data_protection.sql
-- Field-level encryption setup:
--   • Sensitive columns changed to TEXT — encrypted values are
--     Base64(12-byte IV ‖ ciphertext ‖ 16-byte GCM tag), which
--     are longer than the original data and have no fixed length.
--   • New roles added for Housing Administrator, Residence Staff,
--     and Student portal access.
-- ============================================================

-- -------------------------------------------------------
-- 1. Resident — date_of_birth (LocalDate → encrypted TEXT)
-- -------------------------------------------------------
ALTER TABLE residents
    ALTER COLUMN date_of_birth TYPE TEXT USING NULL;

-- -------------------------------------------------------
-- 2. Emergency contacts — all PII fields → TEXT
-- -------------------------------------------------------
ALTER TABLE emergency_contacts
    ALTER COLUMN name         TYPE TEXT,
    ALTER COLUMN relationship TYPE TEXT,
    ALTER COLUMN phone        TYPE TEXT,
    ALTER COLUMN email        TYPE TEXT;

-- Drop the NOT NULL constraint on name and phone so that
-- partially-migrated or test rows do not violate the constraint
-- while encrypted values are being written.
-- Re-add application-level validation via @NotBlank on the DTO.
ALTER TABLE emergency_contacts
    ALTER COLUMN name  DROP NOT NULL,
    ALTER COLUMN phone DROP NOT NULL;

-- -------------------------------------------------------
-- 3. Seed new roles
-- -------------------------------------------------------
INSERT INTO roles (id, name, description, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'HOUSING_ADMINISTRATOR',
     'Full access to housing operations and sensitive resident data',
     NOW(), NOW()),
    (gen_random_uuid(), 'RESIDENCE_STAFF',
     'Residence staff — can view resident records including sensitive fields',
     NOW(), NOW()),
    (gen_random_uuid(), 'STUDENT',
     'Student portal — read own housing info, sensitive data of others is redacted',
     NOW(), NOW())
ON CONFLICT (name) DO NOTHING;
