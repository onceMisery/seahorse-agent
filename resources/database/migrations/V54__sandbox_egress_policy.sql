CREATE TABLE IF NOT EXISTS sa_sandbox_egress_policy (
  pk_id BIGSERIAL PRIMARY KEY,
  policy_id VARCHAR(96) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  network_policy VARCHAR(32) NOT NULL DEFAULT 'DENY_ALL',
  allowlisted_hosts TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_sandbox_egress_policy_network
    CHECK (network_policy IN ('DENY_ALL', 'ALLOWLISTED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sa_sandbox_egress_policy_tenant
  ON sa_sandbox_egress_policy(tenant_id);

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_egress_policy_updated
  ON sa_sandbox_egress_policy(tenant_id, updated_at DESC, policy_id DESC);

ALTER TABLE sa_sandbox_egress_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE sa_sandbox_egress_policy FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS rls_tenant_isolation ON sa_sandbox_egress_policy;
CREATE POLICY rls_tenant_isolation ON sa_sandbox_egress_policy
  USING (tenant_id = current_setting('app.current_tenant_id', true));
