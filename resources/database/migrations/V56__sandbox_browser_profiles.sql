CREATE TABLE IF NOT EXISTS sa_sandbox_browser_profile (
  pk_id BIGSERIAL PRIMARY KEY,
  profile_id VARCHAR(96) NOT NULL,
  tenant_id VARCHAR(64) NOT NULL,
  name VARCHAR(96) NOT NULL,
  session_state_artifact_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_sandbox_browser_profile_status CHECK (status IN ('ACTIVE', 'DISABLED')),
  CONSTRAINT uk_sa_sandbox_browser_profile_tenant_profile UNIQUE (tenant_id, profile_id)
);

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_browser_profile_tenant
  ON sa_sandbox_browser_profile(tenant_id, updated_at DESC, profile_id ASC);

ALTER TABLE sa_sandbox_browser_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE sa_sandbox_browser_profile FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON sa_sandbox_browser_profile
  USING (tenant_id = current_setting('app.current_tenant_id', true));
