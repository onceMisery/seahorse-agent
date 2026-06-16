ALTER TABLE t_ingestion_task_node ADD COLUMN IF NOT EXISTS input_summary TEXT;
ALTER TABLE t_ingestion_task_node ADD COLUMN IF NOT EXISTS output_summary TEXT;
ALTER TABLE t_ingestion_task_node ADD COLUMN IF NOT EXISTS error_code VARCHAR(128);
ALTER TABLE t_ingestion_task_node ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE t_ingestion_task_node ADD COLUMN IF NOT EXISTS downstream_impact TEXT;

CREATE INDEX IF NOT EXISTS idx_ingestion_task_node_error_code
ON t_ingestion_task_node (error_code);
