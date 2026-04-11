-- ============================================================
-- V13__dev_seed_data.sql
-- DEV / DEVELOPMENT SEED DATA — safe to run in production
-- (all inserts use ON CONFLICT DO NOTHING and fixed UUIDs).
-- These records exist so the analytics dashboard renders
-- meaningful charts without manual data entry.
-- All seed users have account_status = DISABLED (cannot log in).
-- ============================================================

-- ── Dev users (recipients for notifications) ──────────────────────────────
-- Password hashes are non-functional placeholders; accounts are DISABLED.

INSERT INTO users (id, username, email, password_hash, first_name, last_name, account_status, created_at, updated_at)
VALUES
  ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380001', 'dev.alice',   'dev.alice@reslife.dev',   '$2a$12$devSeedHashPlaceholder.XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXx', 'Alice',   'Morgan',  'DISABLED', NOW() - INTERVAL '180 days', NOW()),
  ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380002', 'dev.ben',     'dev.ben@reslife.dev',     '$2a$12$devSeedHashPlaceholder.XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXx', 'Ben',     'Turner',  'DISABLED', NOW() - INTERVAL '150 days', NOW()),
  ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380003', 'dev.carol',   'dev.carol@reslife.dev',   '$2a$12$devSeedHashPlaceholder.XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXx', 'Carol',   'Singh',   'DISABLED', NOW() - INTERVAL '120 days', NOW()),
  ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380004', 'dev.david',   'dev.david@reslife.dev',   '$2a$12$devSeedHashPlaceholder.XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXx', 'David',   'Kim',     'DISABLED', NOW() - INTERVAL '90 days',  NOW()),
  ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380005', 'dev.eve',     'dev.eve@reslife.dev',     '$2a$12$devSeedHashPlaceholder.XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXx', 'Eve',     'Patel',   'DISABLED', NOW() - INTERVAL '60 days',  NOW())
ON CONFLICT (id) DO NOTHING;

-- ── Residents ─────────────────────────────────────────────────────────────
-- 12 residents spread across three buildings.
-- date_of_birth is omitted (encrypted TEXT column, nullable).

INSERT INTO residents (id, first_name, last_name, email, enrollment_status, room_number, building_name, created_at, updated_at)
VALUES
  -- Whitmore Hall
  ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380001', 'James',    'Okafor',    'james.okafor@reslife.dev',    'ACTIVE', '101', 'Whitmore Hall', NOW() - INTERVAL '180 days', NOW()),
  ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380002', 'Priya',    'Nair',      'priya.nair@reslife.dev',      'ACTIVE', '102', 'Whitmore Hall', NOW() - INTERVAL '150 days', NOW()),
  ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380003', 'Marcus',   'Chen',      'marcus.chen@reslife.dev',     'ACTIVE', '103', 'Whitmore Hall', NOW() - INTERVAL '120 days', NOW()),
  ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380004', 'Sofia',    'Rivera',    'sofia.rivera@reslife.dev',    'ACTIVE', '104', 'Whitmore Hall', NOW() - INTERVAL '90 days',  NOW()),
  -- Packer Hall
  ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380005', 'Tariq',    'Williams',  'tariq.williams@reslife.dev',  'ACTIVE', '201', 'Packer Hall',   NOW() - INTERVAL '180 days', NOW()),
  ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380006', 'Hannah',   'Lee',       'hannah.lee@reslife.dev',      'ACTIVE', '202', 'Packer Hall',   NOW() - INTERVAL '150 days', NOW()),
  ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380007', 'Diego',    'Santos',    'diego.santos@reslife.dev',    'ACTIVE', '203', 'Packer Hall',   NOW() - INTERVAL '120 days', NOW()),
  ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380008', 'Amara',    'Mensah',    'amara.mensah@reslife.dev',    'ACTIVE', '204', 'Packer Hall',   NOW() - INTERVAL '90 days',  NOW()),
  -- Parker Hall
  ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380009', 'Lena',     'Hoffman',   'lena.hoffman@reslife.dev',    'ACTIVE', '301', 'Parker Hall',   NOW() - INTERVAL '180 days', NOW()),
  ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380010', 'Kofi',     'Asante',    'kofi.asante@reslife.dev',     'ACTIVE', '302', 'Parker Hall',   NOW() - INTERVAL '150 days', NOW()),
  ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380011', 'Yuki',     'Tanaka',    'yuki.tanaka@reslife.dev',     'ACTIVE', '303', 'Parker Hall',   NOW() - INTERVAL '120 days', NOW()),
  ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380012', 'Fatima',   'Al-Hassan', 'fatima.al-hassan@reslife.dev','ACTIVE', '304', 'Parker Hall',   NOW() - INTERVAL '90 days',  NOW())
ON CONFLICT (id) DO NOTHING;

-- ── Housing Agreements ────────────────────────────────────────────────────
-- 25 agreements: 14 SIGNED, 5 EXPIRED, 3 CANCELLED, 3 PENDING.
-- created_at spread over last 6 months so monthly trend shows real data.

INSERT INTO housing_agreements (id, resident_id, agreement_type, status, signed_date, expires_date, created_at, updated_at)
VALUES
  -- Month -5: 3 SIGNED
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380001', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380001', 'STANDARD', 'SIGNED',    CURRENT_DATE - 155, CURRENT_DATE + 210, NOW() - INTERVAL '155 days', NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380002', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380005', 'STANDARD', 'SIGNED',    CURRENT_DATE - 152, CURRENT_DATE + 213, NOW() - INTERVAL '152 days', NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380003', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380009', 'SUMMER',   'SIGNED',    CURRENT_DATE - 148, CURRENT_DATE + 90,  NOW() - INTERVAL '148 days', NOW()),
  -- Month -5: 1 EXPIRED
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380004', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380002', 'STANDARD', 'EXPIRED',   NULL,               CURRENT_DATE - 10,  NOW() - INTERVAL '145 days', NOW()),
  -- Month -4: 3 SIGNED
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380005', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380003', 'STANDARD', 'SIGNED',    CURRENT_DATE - 118, CURRENT_DATE + 247, NOW() - INTERVAL '118 days', NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380006', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380006', 'STANDARD', 'SIGNED',    CURRENT_DATE - 115, CURRENT_DATE + 250, NOW() - INTERVAL '115 days', NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380007', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380010', 'STANDARD', 'SIGNED',    CURRENT_DATE - 112, CURRENT_DATE + 253, NOW() - INTERVAL '112 days', NOW()),
  -- Month -4: 1 EXPIRED, 1 CANCELLED
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380008', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380004', 'SUMMER',   'EXPIRED',   NULL,               CURRENT_DATE - 5,   NOW() - INTERVAL '110 days', NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380009', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380007', 'STANDARD', 'CANCELLED', NULL,               NULL,               NOW() - INTERVAL '105 days', NOW()),
  -- Month -3: 3 SIGNED
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380010', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380011', 'STANDARD', 'SIGNED',    CURRENT_DATE - 82,  CURRENT_DATE + 283, NOW() - INTERVAL '82 days',  NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380011', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380008', 'STANDARD', 'SIGNED',    CURRENT_DATE - 80,  CURRENT_DATE + 285, NOW() - INTERVAL '80 days',  NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380012', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380012', 'STANDARD', 'SIGNED',    CURRENT_DATE - 78,  CURRENT_DATE + 287, NOW() - INTERVAL '78 days',  NOW()),
  -- Month -3: 1 EXPIRED
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380013', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380001', 'SUMMER',   'EXPIRED',   NULL,               CURRENT_DATE - 2,   NOW() - INTERVAL '75 days',  NOW()),
  -- Month -2: 2 SIGNED
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380014', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380002', 'STANDARD', 'SIGNED',    CURRENT_DATE - 50,  CURRENT_DATE + 315, NOW() - INTERVAL '50 days',  NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380015', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380005', 'STANDARD', 'SIGNED',    CURRENT_DATE - 48,  CURRENT_DATE + 317, NOW() - INTERVAL '48 days',  NOW()),
  -- Month -2: 1 EXPIRED, 1 CANCELLED
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380016', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380009', 'SUMMER',   'EXPIRED',   NULL,               NULL,               NOW() - INTERVAL '45 days',  NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380017', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380003', 'STANDARD', 'CANCELLED', NULL,               NULL,               NOW() - INTERVAL '42 days',  NOW()),
  -- Month -1: 2 SIGNED
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380018', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380006', 'STANDARD', 'SIGNED',    CURRENT_DATE - 22,  CURRENT_DATE + 343, NOW() - INTERVAL '22 days',  NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380019', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380010', 'STANDARD', 'SIGNED',    CURRENT_DATE - 20,  CURRENT_DATE + 345, NOW() - INTERVAL '20 days',  NOW()),
  -- Month -1: 1 CANCELLED
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380020', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380004', 'SUMMER',   'CANCELLED', NULL,               NULL,               NOW() - INTERVAL '18 days',  NOW()),
  -- This month: 2 SIGNED, 3 PENDING
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380021', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380007', 'STANDARD', 'SIGNED',    CURRENT_DATE - 5,   CURRENT_DATE + 360, NOW() - INTERVAL '5 days',   NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380022', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380011', 'STANDARD', 'SIGNED',    CURRENT_DATE - 3,   CURRENT_DATE + 362, NOW() - INTERVAL '3 days',   NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380023', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380008', 'STANDARD', 'PENDING',   NULL,               NULL,               NOW() - INTERVAL '2 days',   NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380024', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380012', 'STANDARD', 'PENDING',   NULL,               NULL,               NOW() - INTERVAL '1 day',    NOW()),
  ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380025', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380002', 'SUMMER',   'PENDING',   NULL,               NULL,               NOW(),                       NOW())
ON CONFLICT (id) DO NOTHING;

-- ── Move-In Records ───────────────────────────────────────────────────────
-- 15 records across 3 buildings.
-- Utilization: Whitmore 3/4 rooms, Packer 3/4, Parker 3/4 → ~75% each.

INSERT INTO move_in_records (id, resident_id, room_number, building_name, move_in_date, move_out_date, check_in_status, created_at, updated_at)
VALUES
  -- Whitmore Hall
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380001', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380001', '101', 'Whitmore Hall', CURRENT_DATE - 155, NULL,               'CHECKED_IN',  NOW() - INTERVAL '155 days', NOW()),
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380002', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380002', '102', 'Whitmore Hall', CURRENT_DATE - 118, NULL,               'CHECKED_IN',  NOW() - INTERVAL '118 days', NOW()),
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380003', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380003', '103', 'Whitmore Hall', CURRENT_DATE - 82,  NULL,               'CHECKED_IN',  NOW() - INTERVAL '82 days',  NOW()),
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380004', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380004', '104', 'Whitmore Hall', CURRENT_DATE - 50,  NULL,               'NO_SHOW',     NOW() - INTERVAL '50 days',  NOW()),
  -- Packer Hall
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380005', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380005', '201', 'Packer Hall',   CURRENT_DATE - 155, NULL,               'CHECKED_IN',  NOW() - INTERVAL '155 days', NOW()),
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380006', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380006', '202', 'Packer Hall',   CURRENT_DATE - 118, NULL,               'CHECKED_IN',  NOW() - INTERVAL '118 days', NOW()),
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380007', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380007', '203', 'Packer Hall',   CURRENT_DATE - 82,  CURRENT_DATE - 10, 'CHECKED_OUT', NOW() - INTERVAL '82 days',  NOW()),
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380008', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380008', '204', 'Packer Hall',   CURRENT_DATE - 50,  NULL,               'NO_SHOW',     NOW() - INTERVAL '50 days',  NOW()),
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380009', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380006', '205', 'Packer Hall',   CURRENT_DATE - 22,  NULL,               'CHECKED_IN',  NOW() - INTERVAL '22 days',  NOW()),
  -- Parker Hall
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380010', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380009', '301', 'Parker Hall',   CURRENT_DATE - 155, NULL,               'CHECKED_IN',  NOW() - INTERVAL '155 days', NOW()),
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380011', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380010', '302', 'Parker Hall',   CURRENT_DATE - 118, NULL,               'CHECKED_IN',  NOW() - INTERVAL '118 days', NOW()),
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380012', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380011', '303', 'Parker Hall',   CURRENT_DATE - 82,  NULL,               'CHECKED_IN',  NOW() - INTERVAL '82 days',  NOW()),
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380013', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380012', '304', 'Parker Hall',   CURRENT_DATE - 50,  NULL,               'NO_SHOW',     NOW() - INTERVAL '50 days',  NOW()),
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380014', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380009', '305', 'Parker Hall',   CURRENT_DATE - 22,  NULL,               'PENDING',     NOW() - INTERVAL '22 days',  NOW()),
  ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380015', 'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380001', '106', 'Whitmore Hall', CURRENT_DATE - 5,   NULL,               'CANCELLED',   NOW() - INTERVAL '5 days',   NOW())
ON CONFLICT (id) DO NOTHING;

-- ── Settlement & Arbitration Notifications ────────────────────────────────
-- 8 SETTLEMENT (5 acknowledged), 6 ARBITRATION (3 acknowledged).
-- Completion rate: 8/14 ≈ 57%.

INSERT INTO notifications (id, recipient_id, title, body, type, category, requires_acknowledgment, acknowledged_at, is_read, created_at, updated_at)
VALUES
  -- SETTLEMENT: 5 acknowledged
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380001', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380001', 'Settlement Outcome — Case S-2024-001', 'A settlement has been reached.', 'INFO', 'SETTLEMENT', TRUE,  NOW() - INTERVAL '140 days', TRUE, NOW() - INTERVAL '145 days', NOW()),
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380002', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380002', 'Settlement Outcome — Case S-2024-002', 'A settlement has been reached.', 'INFO', 'SETTLEMENT', TRUE,  NOW() - INTERVAL '110 days', TRUE, NOW() - INTERVAL '115 days', NOW()),
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380003', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380003', 'Settlement Outcome — Case S-2024-003', 'A settlement has been reached.', 'INFO', 'SETTLEMENT', TRUE,  NOW() - INTERVAL '75 days',  TRUE, NOW() - INTERVAL '78 days',  NOW()),
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380004', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380004', 'Settlement Outcome — Case S-2024-004', 'A settlement has been reached.', 'INFO', 'SETTLEMENT', TRUE,  NOW() - INTERVAL '42 days',  TRUE, NOW() - INTERVAL '45 days',  NOW()),
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380005', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380001', 'Settlement Outcome — Case S-2024-005', 'A settlement has been reached.', 'INFO', 'SETTLEMENT', TRUE,  NOW() - INTERVAL '10 days',  TRUE, NOW() - INTERVAL '12 days',  NOW()),
  -- SETTLEMENT: 3 not yet acknowledged
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380006', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380005', 'Settlement Outcome — Case S-2024-006', 'A settlement has been reached.', 'INFO', 'SETTLEMENT', TRUE,  NULL,                        FALSE, NOW() - INTERVAL '30 days',  NOW()),
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380007', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380002', 'Settlement Outcome — Case S-2024-007', 'A settlement has been reached.', 'INFO', 'SETTLEMENT', TRUE,  NULL,                        FALSE, NOW() - INTERVAL '15 days',  NOW()),
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380008', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380003', 'Settlement Outcome — Case S-2024-008', 'A settlement has been reached.', 'INFO', 'SETTLEMENT', TRUE,  NULL,                        FALSE, NOW() - INTERVAL '5 days',   NOW()),
  -- ARBITRATION: 3 acknowledged
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380009', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380001', 'Arbitration Decision — Case A-2024-001', 'An arbitration decision has been issued.', 'ALERT', 'ARBITRATION', TRUE, NOW() - INTERVAL '130 days', TRUE, NOW() - INTERVAL '135 days', NOW()),
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380010', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380004', 'Arbitration Decision — Case A-2024-002', 'An arbitration decision has been issued.', 'ALERT', 'ARBITRATION', TRUE, NOW() - INTERVAL '68 days',  TRUE, NOW() - INTERVAL '70 days',  NOW()),
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380011', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380005', 'Arbitration Decision — Case A-2024-003', 'An arbitration decision has been issued.', 'ALERT', 'ARBITRATION', TRUE, NOW() - INTERVAL '20 days',  TRUE, NOW() - INTERVAL '22 days',  NOW()),
  -- ARBITRATION: 3 not acknowledged
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380012', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380002', 'Arbitration Decision — Case A-2024-004', 'An arbitration decision has been issued.', 'ALERT', 'ARBITRATION', TRUE, NULL,                        FALSE, NOW() - INTERVAL '40 days',  NOW()),
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380013', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380003', 'Arbitration Decision — Case A-2024-005', 'An arbitration decision has been issued.', 'ALERT', 'ARBITRATION', TRUE, NULL,                        FALSE, NOW() - INTERVAL '12 days',  NOW()),
  ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380014', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380001', 'Arbitration Decision — Case A-2024-006', 'An arbitration decision has been issued.', 'ALERT', 'ARBITRATION', TRUE, NULL,                        FALSE, NOW() - INTERVAL '2 days',   NOW())
ON CONFLICT (id) DO NOTHING;
