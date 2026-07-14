ALTER TABLE sa_sandbox_session
  ADD COLUMN IF NOT EXISTS runtime_node_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_session_runtime_node
  ON sa_sandbox_session(tenant_id, runtime_node_id, updated_at DESC)
  WHERE runtime_node_id IS NOT NULL;
