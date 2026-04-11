-- Add a stable optional resident-to-user link for student self-service.
-- This avoids resolving a student's resident record through mutable email equality.

ALTER TABLE residents
    ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_residents_user_id
    ON residents(user_id)
    WHERE user_id IS NOT NULL;

-- Backfill existing student-linked residents by matching the current email to an
-- active STUDENT user account. Future code paths persist the explicit link.
UPDATE residents r
SET    user_id = u.id
FROM   users u
JOIN   user_roles ur ON ur.user_id = u.id
JOIN   roles ro ON ro.id = ur.role_id
WHERE  r.user_id IS NULL
  AND  LOWER(r.email) = LOWER(u.email)
  AND  ro.name = 'STUDENT';
