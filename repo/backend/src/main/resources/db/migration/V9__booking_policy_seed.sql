-- ============================================================
-- V9__booking_policy_seed.sql
-- Seeds the initial default booking/visit policy configuration.
-- All subsequent changes are managed through the admin API which
-- creates new versions rather than modifying existing rows.
-- ============================================================

INSERT INTO configuration_versions (key, value, version, is_active, description)
VALUES (
    'booking.policy',
    '{
  "windowDays": 14,
  "sameDayCutoffHour": 17,
  "sameDayCutoffMinute": 0,
  "noShowThreshold": 2,
  "noShowWindowDays": 30,
  "canaryEnabled": false,
  "canaryRolloutPercent": 10,
  "canaryBuildingIds": [],
  "holidayBlackoutDates": []
}',
    1,
    TRUE,
    'Initial default booking policy: 14-day window, 5 PM same-day cutoff, 2 no-shows / 30 days, canary rollout disabled'
);
