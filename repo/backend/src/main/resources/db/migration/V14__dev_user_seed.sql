-- ============================================================
-- V14__dev_user_seed.sql
-- Development-only seed: login accounts, sample messages and
-- notifications.  All inserts are idempotent (ON CONFLICT DO NOTHING).
-- Password for every account below is:  password
-- ============================================================

-- pgcrypto gives us crypt() for bcrypt hashing
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ── Dev users ────────────────────────────────────────────────────────────
-- Fixed UUIDs make it easy to reference these in tests and other seeds.
-- account_status = ACTIVE so they can log in immediately.

INSERT INTO users (id, username, email, password_hash, first_name, last_name, account_status, created_at, updated_at)
VALUES
  -- admin / password
  ('00000000-0000-0000-0000-000000000001',
   'admin', 'admin@reslife.local',
   crypt('password', gen_salt('bf', 10)),
   'System', 'Administrator', 'ACTIVE', NOW(), NOW()),
  -- staff / password
  ('00000000-0000-0000-0000-000000000002',
   'staff', 'staff@reslife.local',
   crypt('password', gen_salt('bf', 10)),
   'Jane', 'Smith', 'ACTIVE', NOW(), NOW()),
  -- student / password
  ('00000000-0000-0000-0000-000000000003',
   'student', 'student@reslife.local',
   crypt('password', gen_salt('bf', 10)),
   'Alex', 'Chen', 'ACTIVE', NOW(), NOW())
ON CONFLICT DO NOTHING;

-- ── Role assignments ──────────────────────────────────────────────────────
-- Roles are seeded in V1 and V3 using gen_random_uuid(), so we look them
-- up by name.  NOT EXISTS guards prevent duplicate rows on re-run.

INSERT INTO user_roles (user_id, role_id, assigned_at)
SELECT '00000000-0000-0000-0000-000000000001', r.id, NOW()
FROM   roles r
WHERE  r.name = 'ADMIN'
  AND  NOT EXISTS (
    SELECT 1 FROM user_roles WHERE user_id = '00000000-0000-0000-0000-000000000001');

INSERT INTO user_roles (user_id, role_id, assigned_at)
SELECT '00000000-0000-0000-0000-000000000002', r.id, NOW()
FROM   roles r
WHERE  r.name = 'RESIDENCE_STAFF'
  AND  NOT EXISTS (
    SELECT 1 FROM user_roles WHERE user_id = '00000000-0000-0000-0000-000000000002');

INSERT INTO user_roles (user_id, role_id, assigned_at)
SELECT '00000000-0000-0000-0000-000000000003', r.id, NOW()
FROM   roles r
WHERE  r.name = 'STUDENT'
  AND  NOT EXISTS (
    SELECT 1 FROM user_roles WHERE user_id = '00000000-0000-0000-0000-000000000003');

-- ── Message threads ───────────────────────────────────────────────────────

INSERT INTO message_threads (id, subject, thread_type, created_by_user_id, created_at, updated_at)
VALUES
  ('10000000-0000-0000-0000-000000000001',
   'Welcome to the Team', 'DIRECT',
   '00000000-0000-0000-0000-000000000001', NOW() - INTERVAL '5 days', NOW()),
  ('10000000-0000-0000-0000-000000000002',
   'Move-in Information — Parker Hall 301', 'SUPPORT',
   '00000000-0000-0000-0000-000000000002', NOW() - INTERVAL '3 days', NOW())
ON CONFLICT (id) DO NOTHING;

-- Thread participants

INSERT INTO message_thread_participants (thread_id, user_id, joined_at)
VALUES
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', NOW() - INTERVAL '5 days'),
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', NOW() - INTERVAL '5 days'),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', NOW() - INTERVAL '3 days'),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', NOW() - INTERVAL '3 days')
ON CONFLICT DO NOTHING;

-- ── Messages ──────────────────────────────────────────────────────────────
-- message_type was added in V7 with NOT NULL DEFAULT 'TEXT'.

INSERT INTO messages (id, thread_id, sender_id, body, message_type, is_read, created_at, updated_at)
VALUES
  -- Thread 1: admin ↔ staff
  ('20000000-0000-0000-0000-000000000001',
   '10000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000001',
   'Welcome Jane! Your staff account is now active. Let me know if you need help getting started.',
   'TEXT', TRUE, NOW() - INTERVAL '5 days', NOW()),

  ('20000000-0000-0000-0000-000000000002',
   '10000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000002',
   'Thank you! Really excited to be here. Should I begin resident check-ins today?',
   'TEXT', TRUE, NOW() - INTERVAL '5 days' + INTERVAL '10 minutes', NOW()),

  ('20000000-0000-0000-0000-000000000003',
   '10000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000001',
   'Yes — please start with Whitmore Hall, rooms 101–110. There are 3 pending check-ins.',
   'TEXT', TRUE, NOW() - INTERVAL '5 days' + INTERVAL '15 minutes', NOW()),

  ('20000000-0000-0000-0000-000000000004',
   '10000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000002',
   'Got it. I will also review the housing agreements before I go.',
   'TEXT', TRUE, NOW() - INTERVAL '4 days', NOW()),

  ('20000000-0000-0000-0000-000000000005',
   '10000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000001',
   'Perfect. Ping me if anything needs escalation.',
   'TEXT', FALSE, NOW() - INTERVAL '4 days' + INTERVAL '5 minutes', NOW()),

  -- Thread 2: staff ↔ student
  ('20000000-0000-0000-0000-000000000006',
   '10000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000002',
   'Hi Alex! Just confirming your move-in for Room 301, Parker Hall. Are you still arriving Friday?',
   'TEXT', TRUE, NOW() - INTERVAL '3 days', NOW()),

  ('20000000-0000-0000-0000-000000000007',
   '10000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000003',
   'Yes, planning to arrive around 2 PM. Is that okay?',
   'TEXT', TRUE, NOW() - INTERVAL '3 days' + INTERVAL '30 minutes', NOW()),

  ('20000000-0000-0000-0000-000000000008',
   '10000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000002',
   'Perfect! Check-in desk is open 10 AM – 6 PM. Bring your housing agreement and student ID.',
   'TEXT', TRUE, NOW() - INTERVAL '2 days', NOW()),

  ('20000000-0000-0000-0000-000000000009',
   '10000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000003',
   'Will do. Is there a parking lot nearby?',
   'TEXT', TRUE, NOW() - INTERVAL '2 days' + INTERVAL '20 minutes', NOW()),

  ('20000000-0000-0000-0000-000000000010',
   '10000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000002',
   'Use Lot C on the east side of Parker Hall — free on weekends. See you Friday!',
   'TEXT', FALSE, NOW() - INTERVAL '1 day', NOW())
ON CONFLICT (id) DO NOTHING;

-- ── Notifications ─────────────────────────────────────────────────────────
-- Covers: GENERAL, ONBOARDING, APPOINTMENT, SETTLEMENT categories.
-- Mixes read/unread and acknowledged/pending to give the dashboard life.

INSERT INTO notifications (id, recipient_id, title, body, type, priority, category,
                            requires_acknowledgment, acknowledged_at, is_read, created_at, updated_at)
VALUES
  -- Admin notifications
  ('30000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000001',
   'New staff member onboarded: Jane Smith',
   'Jane Smith (staff@reslife.local) was successfully onboarded and assigned the RESIDENCE_STAFF role.',
   'SUCCESS', 'NORMAL', 'ONBOARDING', FALSE, NULL, TRUE,
   NOW() - INTERVAL '5 days', NOW()),

  ('30000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000001',
   'Settlement Outcome — Case S-2024-009',
   'A settlement has been reached in case S-2024-009. Your acknowledgment is required.',
   'INFO', 'HIGH', 'SETTLEMENT', TRUE, NOW() - INTERVAL '2 days', TRUE,
   NOW() - INTERVAL '3 days', NOW()),

  ('30000000-0000-0000-0000-000000000003',
   '00000000-0000-0000-0000-000000000001',
   'Analytics refresh completed',
   'All four analytics metrics have been computed and are now available on the dashboard.',
   'INFO', 'LOW', 'GENERAL', FALSE, NULL, FALSE,
   NOW() - INTERVAL '1 hour', NOW()),

  ('30000000-0000-0000-0000-000000000004',
   '00000000-0000-0000-0000-000000000001',
   'Arbitration Decision — Case A-2024-007',
   'A binding arbitration decision has been issued for case A-2024-007. Acknowledgment required.',
   'ALERT', 'CRITICAL', 'ARBITRATION', TRUE, NULL, FALSE,
   NOW() - INTERVAL '30 minutes', NOW()),

  -- Staff notifications
  ('30000000-0000-0000-0000-000000000005',
   '00000000-0000-0000-0000-000000000002',
   'Welcome to ResLife Staff Team',
   'Your ResLife staff account is now active. Please review the onboarding checklist.',
   'INFO', 'NORMAL', 'ONBOARDING', FALSE, NULL, TRUE,
   NOW() - INTERVAL '5 days', NOW()),

  ('30000000-0000-0000-0000-000000000006',
   '00000000-0000-0000-0000-000000000002',
   'Appointment Rescheduled: Move-in Orientation',
   'The move-in orientation has been rescheduled from Monday to Wednesday at 10 AM.',
   'WARNING', 'HIGH', 'APPOINTMENT', TRUE, NOW() - INTERVAL '1 day', TRUE,
   NOW() - INTERVAL '2 days', NOW()),

  ('30000000-0000-0000-0000-000000000007',
   '00000000-0000-0000-0000-000000000002',
   '3 residents checking in today',
   'Parker Hall rooms 301, 302, 303 are scheduled for check-in today. Pending move-in records updated.',
   'INFO', 'NORMAL', 'GENERAL', FALSE, NULL, FALSE,
   NOW() - INTERVAL '4 hours', NOW()),

  -- Student notifications
  ('30000000-0000-0000-0000-000000000008',
   '00000000-0000-0000-0000-000000000003',
   'Welcome to ResLife Portal',
   'Your student portal account is active. You can view your housing assignment and send messages to staff.',
   'SUCCESS', 'NORMAL', 'ONBOARDING', FALSE, NULL, TRUE,
   NOW() - INTERVAL '3 days', NOW()),

  ('30000000-0000-0000-0000-000000000009',
   '00000000-0000-0000-0000-000000000003',
   'Room Assignment Confirmed: Parker Hall 301',
   'Your room assignment (Parker Hall, Room 301) has been confirmed for the upcoming semester.',
   'SUCCESS', 'NORMAL', 'GENERAL', FALSE, NULL, TRUE,
   NOW() - INTERVAL '3 days', NOW()),

  ('30000000-0000-0000-0000-000000000010',
   '00000000-0000-0000-0000-000000000003',
   'Reminder: Check-in is Friday 2 PM – 6 PM',
   'Your scheduled check-in is this Friday. Please bring your housing agreement and student ID.',
   'INFO', 'HIGH', 'APPOINTMENT', FALSE, NULL, FALSE,
   NOW() - INTERVAL '1 day', NOW())
ON CONFLICT (id) DO NOTHING;
