-- V4: Secret ref enhancements and connector credential verification
-- Security Hardening Module (Tasks 2.5-2.6)

-- Secret ref enhancements
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS name VARCHAR(128);
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS secret_type VARCHAR(32);
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS masked_hint VARCHAR(64);
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS rotated_by VARCHAR(64);
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS rotated_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_sa_secret_ref_tenant_status ON sa_secret_ref (tenant_id, status);

-- Connector credential verification
ALTER TABLE sa_connector_credential_binding ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
ALTER TABLE sa_connector_credential_binding ADD COLUMN IF NOT EXISTS last_verified_at TIMESTAMP;
ALTER TABLE sa_connector_credential_binding ADD COLUMN IF NOT EXISTS verify_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED';
