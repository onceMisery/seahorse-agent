CREATE TABLE IF NOT EXISTS sa_sandbox_runtime_profile_policy (
  pk_id BIGSERIAL PRIMARY KEY,
  policy_id VARCHAR(96) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  runtime_type VARCHAR(32) NOT NULL,
  profile_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  session_ttl_seconds BIGINT NOT NULL DEFAULT 3600,
  network_allowed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_sandbox_runtime_profile_policy_runtime
    CHECK (runtime_type IN ('CODE_INTERPRETER', 'FILE_CONVERSION', 'BROWSER_AUTOMATION', 'SHELL')),
  CONSTRAINT chk_sa_sandbox_runtime_profile_policy_status
    CHECK (status IN ('ACTIVE', 'DISABLED')),
  CONSTRAINT chk_sa_sandbox_runtime_profile_policy_ttl
    CHECK (session_ttl_seconds >= 60 AND session_ttl_seconds <= 7200),
  CONSTRAINT chk_sa_sandbox_runtime_profile_policy_network
    CHECK (network_allowed = FALSE OR runtime_type = 'BROWSER_AUTOMATION')
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sa_sandbox_runtime_profile_policy_runtime
  ON sa_sandbox_runtime_profile_policy(tenant_id, runtime_type);

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_runtime_profile_policy_tenant
  ON sa_sandbox_runtime_profile_policy(tenant_id, updated_at DESC, policy_id DESC);
