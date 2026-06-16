ALTER TABLE t_ingestion_pipeline ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE t_ingestion_task ADD COLUMN IF NOT EXISTS pipeline_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE t_ingestion_task ADD COLUMN IF NOT EXISTS pipeline_snapshot_json JSONB;
