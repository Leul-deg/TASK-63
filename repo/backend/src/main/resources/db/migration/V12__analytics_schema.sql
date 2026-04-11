-- ============================================================
-- V12__analytics_schema.sql
-- Stores pre-computed analytics snapshots refreshed every 15 min
-- by AnalyticsComputeService.  One row per metric_name.
-- ============================================================

CREATE TABLE analytics_snapshots (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_name  VARCHAR(100) NOT NULL UNIQUE,
    value        TEXT         NOT NULL,
    computed_at  TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analytics_snapshots_metric ON analytics_snapshots(metric_name);
