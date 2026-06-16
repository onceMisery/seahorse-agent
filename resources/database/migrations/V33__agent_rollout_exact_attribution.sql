ALTER TABLE sa_agent_run
  ADD COLUMN IF NOT EXISTS rollout_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_sa_agent_run_rollout
  ON sa_agent_run(tenant_id, agent_id, rollout_id, started_at);

ALTER TABLE sa_approval_request
  ADD COLUMN IF NOT EXISTS rollout_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_sa_approval_request_rollout
  ON sa_approval_request(tenant_id, agent_id, rollout_id, requested_at);

ALTER TABLE sa_cost_usage_record
  ADD COLUMN IF NOT EXISTS rollout_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_sa_cost_usage_rollout
  ON sa_cost_usage_record(tenant_id, agent_id, rollout_id, created_at);
