-- Tenant-scoped AI model configuration.
-- Existing rows are retained under the default tenant.

ALTER TABLE sa_ai_model_config
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

ALTER TABLE sa_ai_model_config
    DROP CONSTRAINT IF EXISTS sa_ai_model_config_config_key_key;

DROP INDEX IF EXISTS idx_sa_ai_model_config_key;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_sa_ai_model_config_tenant_key'
    ) THEN
        ALTER TABLE sa_ai_model_config
            ADD CONSTRAINT uk_sa_ai_model_config_tenant_key UNIQUE (tenant_id, config_key);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_sa_ai_model_config_tenant_key
    ON sa_ai_model_config(tenant_id, config_key, deleted);
