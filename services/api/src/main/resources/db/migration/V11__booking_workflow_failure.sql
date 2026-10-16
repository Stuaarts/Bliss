-- Failed Flow checkpoints are removed; retain a content-free domain marker.
-- Existing bookings remain unmarked; do not infer failures from missing checkpoints.
ALTER TABLE booking ADD COLUMN workflow_failed_at timestamptz;
