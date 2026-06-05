-- Revenue share table for agent marketplace creator earnings
CREATE TABLE IF NOT EXISTS sa_revenue_share (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    agent_id VARCHAR(64) NOT NULL,
    creator_user_id BIGINT NOT NULL,
    period VARCHAR(7) NOT NULL,  -- yyyy-MM
    gross_revenue DECIMAL(12,2) NOT NULL DEFAULT 0,
    platform_share DECIMAL(12,2) NOT NULL DEFAULT 0,
    creator_share DECIMAL(12,2) NOT NULL DEFAULT 0,
    platform_rate DECIMAL(5,4) NOT NULL DEFAULT 0.2000,  -- 20%
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING, SETTLED, PAID
    settled_at TIMESTAMP,
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_revenue_share_period UNIQUE (agent_id, period)
);
CREATE INDEX IF NOT EXISTS idx_revenue_share_tenant ON sa_revenue_share (tenant_id, period);
CREATE INDEX IF NOT EXISTS idx_revenue_share_creator ON sa_revenue_share (creator_user_id, period);
