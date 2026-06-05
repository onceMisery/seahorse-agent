-- =============================================================================
-- V10: Audit Log and Admin Operations (Sprint 5-6 Module 10)
-- Date: 2026-06-05
-- Scope: System audit log for compliance and admin operations
-- =============================================================================

-- Phase 1: Audit log table
CREATE TABLE IF NOT EXISTS sa_audit_log (
    id             BIGINT         NOT NULL PRIMARY KEY,
    tenant_id      VARCHAR(64)    NOT NULL,
    operator       VARCHAR(128)   NOT NULL,
    action         VARCHAR(64)    NOT NULL,
    resource_type  VARCHAR(64)    NOT NULL,
    resource_id    VARCHAR(128),
    detail         TEXT,
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(512),
    created_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant ON sa_audit_log (tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON sa_audit_log (action);
CREATE INDEX IF NOT EXISTS idx_audit_log_resource_type ON sa_audit_log (resource_type);
CREATE INDEX IF NOT EXISTS idx_audit_log_operator ON sa_audit_log (operator);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON sa_audit_log (created_at);

-- Composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_action ON sa_audit_log (tenant_id, action);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_created ON sa_audit_log (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_action_created ON sa_audit_log (action, created_at DESC);

-- Index for cleanup jobs
CREATE INDEX IF NOT EXISTS idx_audit_log_created_asc ON sa_audit_log (created_at ASC);
