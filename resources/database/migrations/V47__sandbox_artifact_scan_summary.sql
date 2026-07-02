ALTER TABLE sa_sandbox_artifact
  ADD COLUMN IF NOT EXISTS scan_summary VARCHAR(256);
