CREATE INDEX IF NOT EXISTS idx_metadata_quarantine_job
ON t_metadata_quarantine_item (job_id, resolved);
