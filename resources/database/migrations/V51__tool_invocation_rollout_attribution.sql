ALTER TABLE sa_tool_invocation
  ADD COLUMN IF NOT EXISTS rollout_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_sa_tool_invocation_rollout
  ON sa_tool_invocation(tenant_id, agent_id, rollout_id, started_at);
