-- ============================================================
-- V4__resident_directory.sql
-- Adds class_year to residents for directory filtering.
-- ============================================================

ALTER TABLE residents ADD COLUMN class_year SMALLINT;

-- Used by the class-year filter dropdown
CREATE INDEX idx_residents_class_year ON residents(class_year)
    WHERE class_year IS NOT NULL;

-- Supports fast building-name enumeration for filter-options endpoint
CREATE INDEX idx_residents_building_name ON residents(building_name)
    WHERE building_name IS NOT NULL AND deleted_at IS NULL;
