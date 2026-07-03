ALTER TABLE sa_sandbox_artifact
  ADD COLUMN IF NOT EXISTS redaction_summary_json VARCHAR(2048);
