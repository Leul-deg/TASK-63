-- Rename the analytics snapshot row that was previously stored under the
-- misleading key "booking_conversion".  The metric is computed from
-- housing_agreements status counts, not from a booking workflow, so the
-- key is corrected to "agreement_signthrough".
--
-- The UPDATE is idempotent: if the old row was already purged or the service
-- has already written a new row under the correct name, no rows are affected.
UPDATE analytics_snapshots
SET    metric_name = 'agreement_signthrough',
       updated_at  = NOW()
WHERE  metric_name = 'booking_conversion';
