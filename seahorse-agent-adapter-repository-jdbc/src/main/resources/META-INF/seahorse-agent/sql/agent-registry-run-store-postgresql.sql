CREATE TABLE IF NOT EXISTS sa_secret_ref (
  pk_id BIGSERIAL PRIMARY KEY,
  secret_ref VARCHAR(128) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  encrypted_value TEXT NOT NULL,
  metadata_json TEXT,
  created_at TIMESTAMP NOT NULL,
  rotated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sa_secret_ref_tenant
  ON sa_secret_ref(tenant_id, created_at);

CREATE TABLE IF NOT EXISTS sa_quota_policy (
  pk_id BIGSERIAL PRIMARY KEY,
  policy_id VARCHAR(64) NOT NULL UNIQUE,
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
  pk_id BIGSERIAL PRIMARY KEY,
  usage_id VARCHAR(64) NOT NULL UNIQUE,
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
