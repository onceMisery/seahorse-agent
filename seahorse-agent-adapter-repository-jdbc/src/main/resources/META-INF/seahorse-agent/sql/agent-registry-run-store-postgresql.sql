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

CREATE INDEX IF NOT EXISTS idx_sa_agent_run_worker_queue
  ON sa_agent_run(tenant_id, status, started_at, run_id);

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

CREATE TABLE IF NOT EXISTS sa_connector (
  connector_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(1000),
  status VARCHAR(32) NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_connector_tenant_status
  ON sa_connector(tenant_id, status);

CREATE TABLE IF NOT EXISTS sa_connector_version (
  connector_version_id VARCHAR(64) PRIMARY KEY,
  connector_id VARCHAR(64) NOT NULL,
  spec_hash VARCHAR(128) NOT NULL,
  spec_json TEXT NOT NULL,
  imported_by VARCHAR(64) NOT NULL,
  imported_at TIMESTAMP NOT NULL,
  UNIQUE(connector_id, spec_hash)
);

CREATE TABLE IF NOT EXISTS sa_connector_operation (
  operation_id VARCHAR(64) PRIMARY KEY,
  connector_id VARCHAR(64) NOT NULL,
  connector_version_id VARCHAR(64) NOT NULL,
  operation_key VARCHAR(256) NOT NULL,
  original_operation_id VARCHAR(128),
  method VARCHAR(16) NOT NULL,
  path VARCHAR(512) NOT NULL,
  summary VARCHAR(256),
  description VARCHAR(1000),
  schema_json TEXT NOT NULL,
  output_schema_json TEXT,
  tool_id VARCHAR(128) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  resource_type VARCHAR(64),
  auth_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
  status VARCHAR(32) NOT NULL,
  requires_approval BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_connector_operation_connector
  ON sa_connector_operation(connector_id, updated_at);

CREATE TABLE IF NOT EXISTS sa_connector_credential_binding (
  binding_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  connector_id VARCHAR(64) NOT NULL,
  operation_id VARCHAR(64) NOT NULL,
  auth_type VARCHAR(32) NOT NULL,
  credential_ref VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  bound_by VARCHAR(64) NOT NULL,
  bound_at TIMESTAMP NOT NULL,
  rotated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sa_connector_credential_binding_operation
  ON sa_connector_credential_binding(tenant_id, connector_id, operation_id, auth_type, status);

CREATE TABLE IF NOT EXISTS sa_agent_template (
  template_id VARCHAR(64) PRIMARY KEY,
  status VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(1000),
  agent_type VARCHAR(32) NOT NULL,
  risk_cap VARCHAR(32) NOT NULL,
  allowed_tool_ids_json TEXT NOT NULL,
  base_instructions TEXT NOT NULL,
  guardrail_config_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_template_status
  ON sa_agent_template(status, template_id);

CREATE TABLE IF NOT EXISTS sa_agent_publish_check (
  check_id VARCHAR(64) PRIMARY KEY,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  result_json TEXT NOT NULL,
  checked_by VARCHAR(64) NOT NULL,
  checked_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_publish_check_agent
  ON sa_agent_publish_check(agent_id, checked_at);

CREATE TABLE IF NOT EXISTS sa_agent_version_activation (
  activation_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  activation_type VARCHAR(32) NOT NULL,
  previous_version_id VARCHAR(64),
  reason_code VARCHAR(64) NOT NULL,
  operator_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_version_activation_active
  ON sa_agent_version_activation(agent_id, created_at DESC, activation_id DESC);

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

CREATE TABLE IF NOT EXISTS sa_secret_ref (
  secret_ref VARCHAR(128) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  encrypted_value TEXT NOT NULL,
  metadata_json TEXT,
  created_at TIMESTAMP NOT NULL,
  rotated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sa_secret_ref_tenant
  ON sa_secret_ref(tenant_id, created_at);

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

CREATE TABLE IF NOT EXISTS sa_resource_acl_rule (
  rule_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  scope VARCHAR(32) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(128) NOT NULL,
  subject_type VARCHAR(32) NOT NULL,
  subject_id VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  effect VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  priority INT NOT NULL,
  expires_at TIMESTAMP,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_resource_acl_scope CHECK (scope IN ('EXACT_RESOURCE', 'RESOURCE_TYPE')),
  CONSTRAINT chk_sa_resource_acl_subject_type CHECK (subject_type IN ('USER', 'AGENT', 'USER_DELEGATED_AGENT')),
  CONSTRAINT chk_sa_resource_acl_action CHECK (action IN ('READ', 'WRITE', 'DELETE', 'EXECUTE')),
  CONSTRAINT chk_sa_resource_acl_effect CHECK (effect IN ('ALLOW', 'DENY')),
  CONSTRAINT chk_sa_resource_acl_status CHECK (status IN ('ENABLED', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_sa_resource_acl_lookup
  ON sa_resource_acl_rule(tenant_id, resource_type, resource_id, subject_type, subject_id, action, status);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sa_resource_acl_active_exact_rule
  ON sa_resource_acl_rule(tenant_id, scope, resource_type, resource_id, subject_type, subject_id, action, effect, priority)
  WHERE status = 'ENABLED';

CREATE TABLE IF NOT EXISTS sa_sandbox_session (
  session_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  runtime_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_session_run
  ON sa_sandbox_session(tenant_id, run_id, created_at);

CREATE TABLE IF NOT EXISTS sa_sandbox_execution (
  execution_id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  runtime_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  result_summary VARCHAR(1000),
  reason_code VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_execution_session
  ON sa_sandbox_execution(session_id, created_at);

CREATE TABLE IF NOT EXISTS sa_sandbox_artifact (
  artifact_id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL,
  execution_id VARCHAR(64) NOT NULL,
  object_uri VARCHAR(1000) NOT NULL,
  media_type VARCHAR(128) NOT NULL,
  scan_status VARCHAR(32) NOT NULL,
  sensitivity VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_artifact_session
  ON sa_sandbox_artifact(session_id, created_at);

CREATE TABLE IF NOT EXISTS sa_audit_event (
  audit_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  actor_type VARCHAR(32) NOT NULL,
  actor_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64),
  agent_id VARCHAR(64),
  resource_type VARCHAR(64),
  resource_id VARCHAR(128),
  redacted_payload TEXT NOT NULL,
  occurred_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_audit_event_tenant_time
  ON sa_audit_event(tenant_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_sa_audit_event_run
  ON sa_audit_event(run_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_sa_audit_event_agent
  ON sa_audit_event(agent_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_sa_audit_event_resource
  ON sa_audit_event(tenant_id, resource_type, resource_id, occurred_at);

CREATE TABLE IF NOT EXISTS sa_production_gate_report (
  report_id VARCHAR(64) PRIMARY KEY,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  result_json TEXT NOT NULL,
  checked_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_production_gate_report_agent
  ON sa_production_gate_report(agent_id, checked_at);

CREATE TABLE IF NOT EXISTS sa_agent_eval_summary (
  summary_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  eval_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  score DOUBLE PRECISION NOT NULL,
  pass_threshold DOUBLE PRECISION NOT NULL,
  warn_threshold DOUBLE PRECISION NOT NULL,
  case_count INT NOT NULL,
  dataset_ref VARCHAR(256),
  eval_run_ref VARCHAR(256),
  evidence_json TEXT NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_agent_eval_summary_type CHECK (eval_type IN ('SAFETY', 'TRAJECTORY', 'RAG', 'MEMORY', 'TOOL_USE')),
  CONSTRAINT chk_sa_agent_eval_summary_status CHECK (status IN ('PASS', 'WARN', 'FAIL', 'STALE')),
  CONSTRAINT chk_sa_agent_eval_summary_score CHECK (score >= 0),
  CONSTRAINT chk_sa_agent_eval_summary_threshold CHECK (pass_threshold >= warn_threshold AND warn_threshold >= 0),
  CONSTRAINT chk_sa_agent_eval_summary_case_count CHECK (case_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_eval_summary_latest
  ON sa_agent_eval_summary(tenant_id, agent_id, version_id, eval_type, created_at DESC, summary_id DESC);

CREATE TABLE IF NOT EXISTS sa_quota_policy (
  policy_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  scope VARCHAR(32) NOT NULL,
  subject_id VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  token_limit BIGINT,
  call_limit BIGINT,
  cost_limit DOUBLE PRECISION,
  warn_ratio DOUBLE PRECISION NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_quota_policy_scope
    CHECK (scope IN ('TENANT', 'AGENT', 'USER', 'TOOL', 'MODEL', 'RUN')),
  CONSTRAINT chk_sa_quota_policy_status
    CHECK (status IN ('ACTIVE', 'DISABLED')),
  CONSTRAINT chk_sa_quota_policy_limit_required
    CHECK (token_limit IS NOT NULL OR call_limit IS NOT NULL OR cost_limit IS NOT NULL),
  CONSTRAINT chk_sa_quota_policy_token_limit
    CHECK (token_limit IS NULL OR token_limit >= 0),
  CONSTRAINT chk_sa_quota_policy_call_limit
    CHECK (call_limit IS NULL OR call_limit >= 0),
  CONSTRAINT chk_sa_quota_policy_cost_limit
    CHECK (cost_limit IS NULL OR cost_limit >= 0),
  CONSTRAINT chk_sa_quota_policy_warn_ratio
    CHECK (warn_ratio > 0 AND warn_ratio <= 1)
);

CREATE INDEX IF NOT EXISTS idx_sa_quota_policy_active
  ON sa_quota_policy(tenant_id, scope, subject_id, status, updated_at DESC, policy_id DESC);

CREATE TABLE IF NOT EXISTS sa_cost_usage_record (
  usage_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64),
  run_id VARCHAR(64),
  user_id VARCHAR(64),
  tool_id VARCHAR(128),
  model_id VARCHAR(128),
  source VARCHAR(32) NOT NULL,
  tokens BIGINT NOT NULL,
  calls BIGINT NOT NULL,
  cost DOUBLE PRECISION NOT NULL,
  reason_ref VARCHAR(256),
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_cost_usage_source
    CHECK (source IN ('MODEL', 'TOOL', 'SANDBOX', 'MANUAL_ADJUSTMENT')),
  CONSTRAINT chk_sa_cost_usage_tokens
    CHECK (tokens >= 0),
  CONSTRAINT chk_sa_cost_usage_calls
    CHECK (calls >= 0),
  CONSTRAINT chk_sa_cost_usage_cost
    CHECK (cost >= 0)
);

CREATE INDEX IF NOT EXISTS idx_sa_cost_usage_aggregate
  ON sa_cost_usage_record(tenant_id, agent_id, run_id, created_at);

CREATE TABLE IF NOT EXISTS sa_agent_version_rollout (
  rollout_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  canary_percent INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  failure_code VARCHAR(64),
  gate_report_id VARCHAR(64),
  started_by VARCHAR(64) NOT NULL,
  started_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP,
  CONSTRAINT chk_sa_agent_version_rollout_percent
    CHECK (canary_percent >= 1 AND canary_percent <= 100),
  CONSTRAINT chk_sa_agent_version_rollout_status
    CHECK (status IN ('CREATED', 'RUNNING', 'PAUSED', 'PROMOTED', 'ROLLED_BACK', 'FAILED')),
  CONSTRAINT chk_sa_agent_version_rollout_failure
    CHECK (
      (status = 'FAILED' AND failure_code IS NOT NULL)
      OR (status <> 'FAILED' AND failure_code IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_version_rollout_latest
  ON sa_agent_version_rollout(tenant_id, agent_id, version_id, updated_at DESC, rollout_id DESC);

CREATE TABLE IF NOT EXISTS sa_enterprise_pilot_readiness_report (
  report_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  check_results_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_enterprise_pilot_readiness_status
    CHECK (status IN ('PASS', 'WARN', 'FAIL'))
);

CREATE INDEX IF NOT EXISTS idx_sa_enterprise_pilot_readiness_latest
  ON sa_enterprise_pilot_readiness_report(tenant_id, agent_id, version_id, created_at DESC, report_id DESC);

CREATE TABLE IF NOT EXISTS sa_agent_handoff (
  handoff_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  parent_run_id VARCHAR(64) NOT NULL,
  child_run_id VARCHAR(64),
  source_agent_id VARCHAR(64) NOT NULL,
  target_agent_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  failure_code VARCHAR(64),
  handoff_reason VARCHAR(1000),
  input_summary_json TEXT NOT NULL,
  context_summary_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP,
  CONSTRAINT chk_sa_agent_handoff_status
    CHECK (status IN ('CREATED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
  CONSTRAINT chk_sa_agent_handoff_failure_code
    CHECK (failure_code IS NULL OR failure_code IN (
      'DEPTH_LIMIT_EXCEEDED',
      'CYCLE_DETECTED',
      'POLICY_DENIED',
      'TARGET_DISABLED',
      'CONTEXT_DENIED',
      'CHILD_RUN_FAILED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_handoff_parent
  ON sa_agent_handoff(tenant_id, parent_run_id, created_at);

CREATE INDEX IF NOT EXISTS idx_sa_agent_handoff_child
  ON sa_agent_handoff(child_run_id);
