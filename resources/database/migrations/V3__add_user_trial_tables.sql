-- =============================================================================
-- V3: User Registration & Trial System (SaaS MVP Tasks 2.1-2.3)
-- Date: 2026-06-05
-- Scope: Add email/status/external_id to t_user; create t_user_trial table
-- =============================================================================

-- Phase 1: Extend t_user with registration fields
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS email       VARCHAR(128);
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS status      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS external_id VARCHAR(128);

-- Unique index on email for registration lookup
CREATE UNIQUE INDEX IF NOT EXISTS idx_t_user_email ON t_user (email) WHERE email IS NOT NULL;

-- Index on external_id for federated identity lookups
CREATE INDEX IF NOT EXISTS idx_t_user_external_id ON t_user (external_id) WHERE external_id IS NOT NULL;

-- Phase 2: Create t_user_trial table
CREATE TABLE IF NOT EXISTS t_user_trial (
    id                BIGINT       NOT NULL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    tenant_id         VARCHAR(64)  NOT NULL,
    plan_code         VARCHAR(32),
    status            VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    token_limit       BIGINT       NOT NULL DEFAULT 0,
    storage_limit_bytes BIGINT     NOT NULL DEFAULT 0,
    concurrency_limit INTEGER      NOT NULL DEFAULT 1,
    started_at        TIMESTAMP,
    expires_at        TIMESTAMP,
    notified_at       TIMESTAMP,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for trial lookups
CREATE INDEX IF NOT EXISTS idx_trial_user_id   ON t_user_trial (user_id);
CREATE INDEX IF NOT EXISTS idx_trial_tenant_id ON t_user_trial (tenant_id);
CREATE INDEX IF NOT EXISTS idx_trial_status    ON t_user_trial (status);
