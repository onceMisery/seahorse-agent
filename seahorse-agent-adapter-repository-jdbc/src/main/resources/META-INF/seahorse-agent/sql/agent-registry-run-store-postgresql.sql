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

CREATE TABLE IF NOT EXISTS sa_agent_checkpoint (
  checkpoint_id VARCHAR(64) PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL,
  step_id VARCHAR(64),
  sequence_no BIGINT NOT NULL,
  checkpoint_type VARCHAR(32) NOT NULL,
  state_json TEXT NOT NULL,
  message_history_json TEXT,
  context_pack_id VARCHAR(64),
  pending_tool_call_json TEXT,
  created_at TIMESTAMP NOT NULL,
  UNIQUE(run_id, sequence_no)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_checkpoint_run
  ON sa_agent_checkpoint(run_id, sequence_no);

CREATE TABLE IF NOT EXISTS sa_agent_run_lease (
  run_id VARCHAR(64) PRIMARY KEY,
  worker_id VARCHAR(128) NOT NULL,
  lease_until TIMESTAMP NOT NULL,
  heartbeat_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS sa_tool_catalog (
  tool_id VARCHAR(128) PRIMARY KEY,
  provider VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(1000),
  schema_json TEXT NOT NULL,
  output_schema_json TEXT,
  risk_level VARCHAR(32) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  resource_type VARCHAR(64),
  owner_team VARCHAR(128),
  enabled BOOLEAN NOT NULL,
  requires_approval BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_tool_catalog_resource
  ON sa_tool_catalog(resource_type, enabled);

CREATE TABLE IF NOT EXISTS sa_agent_tool_binding (
  id VARCHAR(64) PRIMARY KEY,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  tool_id VARCHAR(128) NOT NULL,
  max_calls_per_run INT NOT NULL,
  argument_policy_json TEXT,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  UNIQUE(agent_id, version_id, tool_id)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_tool_binding_version
  ON sa_agent_tool_binding(agent_id, version_id);

CREATE TABLE IF NOT EXISTS sa_tool_invocation (
  invocation_id VARCHAR(64) PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL,
  step_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64),
  version_id VARCHAR(64),
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  tool_id VARCHAR(128) NOT NULL,
  idempotency_key VARCHAR(128),
  status VARCHAR(32) NOT NULL,
  policy_decision_id VARCHAR(64),
  arguments_summary TEXT,
  result_summary TEXT,
  error_message VARCHAR(1000),
  started_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sa_tool_invocation_run_tool
  ON sa_tool_invocation(run_id, tool_id, started_at);

CREATE INDEX IF NOT EXISTS idx_sa_tool_invocation_tenant_user
  ON sa_tool_invocation(tenant_id, user_id, started_at);

CREATE TABLE IF NOT EXISTS sa_approval_request (
  approval_id VARCHAR(64) PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL,
  step_id VARCHAR(64),
  tool_invocation_id VARCHAR(64),
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64),
  tool_id VARCHAR(128) NOT NULL,
  approval_type VARCHAR(32) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  summary VARCHAR(1000) NOT NULL,
  arguments_preview_json TEXT,
  status VARCHAR(32) NOT NULL,
  requested_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP,
  decided_by VARCHAR(64),
  decided_at TIMESTAMP,
  decision_comment VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_sa_approval_request_status
  ON sa_approval_request(tenant_id, status, requested_at);

CREATE INDEX IF NOT EXISTS idx_sa_approval_request_run
  ON sa_approval_request(run_id, step_id);

CREATE TABLE IF NOT EXISTS sa_context_pack (
  context_pack_id VARCHAR(64) PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64),
  version_id VARCHAR(64),
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  task_goal VARCHAR(1000) NOT NULL,
  budget_tokens INT NOT NULL,
  item_count INT NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_context_pack_run
  ON sa_context_pack(run_id, created_at);

CREATE TABLE IF NOT EXISTS sa_context_item (
  item_id VARCHAR(64) PRIMARY KEY,
  context_pack_id VARCHAR(64) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id VARCHAR(128) NOT NULL,
  content TEXT NOT NULL,
  summary VARCHAR(1000),
  score DOUBLE PRECISION,
  confidence DOUBLE PRECISION,
  sensitivity VARCHAR(32) NOT NULL,
  acl_decision_id VARCHAR(64) NOT NULL,
  citation_json TEXT NOT NULL,
  estimated_tokens INT NOT NULL,
  expires_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_context_item_pack
  ON sa_context_item(context_pack_id);

CREATE TABLE IF NOT EXISTS sa_access_decision_log (
  decision_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  subject_type VARCHAR(32) NOT NULL,
  subject_id VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(128) NOT NULL,
  effect VARCHAR(32) NOT NULL,
  reason_code VARCHAR(128),
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_access_decision_resource
  ON sa_access_decision_log(tenant_id, resource_type, resource_id, created_at);
