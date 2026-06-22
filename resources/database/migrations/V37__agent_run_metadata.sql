ALTER TABLE sa_agent_run
  ADD COLUMN IF NOT EXISTS metadata_json JSONB;
