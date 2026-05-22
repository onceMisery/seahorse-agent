CREATE TABLE IF NOT EXISTS sa_agent_definition (
  agent_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(500),
  owner_user_id VARCHAR(64) NOT NULL,
  owner_team VARCHAR(128),
  agent_type VARCHAR(32) NOT NULL,
  base_agent_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  latest_version_id VARCHAR(64),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_definition_tenant_status
  ON sa_agent_definition(tenant_id, status);

CREATE TABLE IF NOT EXISTS sa_agent_version (
  version_id VARCHAR(64) PRIMARY KEY,
  agent_id VARCHAR(64) NOT NULL,
  version_no BIGINT NOT NULL,
  instructions TEXT NOT NULL,
  tool_set_json TEXT NOT NULL,
  model_config_json TEXT NOT NULL,
  memory_config_json TEXT NOT NULL,
  guardrail_config_json TEXT NOT NULL,
  published_by VARCHAR(64) NOT NULL,
  published_at TIMESTAMP NOT NULL,
  change_summary VARCHAR(500) NOT NULL,
  UNIQUE(agent_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_version_agent
  ON sa_agent_version(agent_id, version_no);

CREATE TABLE IF NOT EXISTS sa_agent_run (
  run_id VARCHAR(64) PRIMARY KEY,
  agent_id VARCHAR(64),
  version_id VARCHAR(64),
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(64),
  trigger_type VARCHAR(32) NOT NULL,
  input_summary VARCHAR(1000),
  status VARCHAR(32) NOT NULL,
  trace_id VARCHAR(64),
  token_input BIGINT NOT NULL DEFAULT 0,
  token_output BIGINT NOT NULL DEFAULT 0,
  cost_total DECIMAL(18,6) NOT NULL DEFAULT 0,
  error_code VARCHAR(128),
  error_message VARCHAR(1000),
  started_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_run_agent_status
  ON sa_agent_run(agent_id, status, started_at);

CREATE INDEX IF NOT EXISTS idx_sa_agent_run_user
  ON sa_agent_run(tenant_id, user_id, started_at);

CREATE TABLE IF NOT EXISTS sa_agent_step (
  step_id VARCHAR(64) PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL,
  step_no INT NOT NULL,
  step_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  input_json TEXT,
  output_json TEXT,
  error_code VARCHAR(128),
  error_message VARCHAR(1000),
  started_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP,
  UNIQUE(run_id, step_no)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_step_run
  ON sa_agent_step(run_id, step_no);
