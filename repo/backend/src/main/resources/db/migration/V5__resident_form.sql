-- V5: Add department field to residents for enrollment/classification tracking

ALTER TABLE residents ADD COLUMN department VARCHAR(100);
