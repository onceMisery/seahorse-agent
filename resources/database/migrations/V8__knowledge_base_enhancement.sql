-- =============================================================================
-- V8: Knowledge Base Enhancement (Sprint 5-6 Module 06)
-- Date: 2026-06-05
-- Scope: Knowledge base versioning, permission control, external sharing
-- =============================================================================

-- Phase 1: Knowledge base version snapshots
CREATE TABLE IF NOT EXISTS t_knowledge_base_version (
    id                 BIGINT         NOT NULL PRIMARY KEY,
    kb_id              BIGINT         NOT NULL,
    tenant_id          VARCHAR(64)    NOT NULL,
    version_number     INTEGER        NOT NULL,
    snapshot_json      JSONB          NOT NULL,
    created_by         VARCHAR(128)   NOT NULL,
    created_at         TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    change_description VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_kb_version_kb_id ON t_knowledge_base_version (kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_version_tenant ON t_knowledge_base_version (tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_kb_version_unique ON t_knowledge_base_version (kb_id, version_number);
CREATE INDEX IF NOT EXISTS idx_kb_version_created_at ON t_knowledge_base_version (created_at);

-- Phase 2: Knowledge base permission control
CREATE TABLE IF NOT EXISTS t_knowledge_base_permission (
    id          BIGINT         NOT NULL PRIMARY KEY,
    kb_id       BIGINT         NOT NULL,
    tenant_id   VARCHAR(64)    NOT NULL,
    user_id     BIGINT         NOT NULL,
    permission  VARCHAR(32)    NOT NULL,
    granted_at  TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kb_permission_kb_id ON t_knowledge_base_permission (kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_permission_tenant ON t_knowledge_base_permission (tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_kb_permission_user ON t_knowledge_base_permission (kb_id, user_id);
CREATE INDEX IF NOT EXISTS idx_kb_permission_granted ON t_knowledge_base_permission (granted_at);

-- Phase 3: Knowledge base external sharing
CREATE TABLE IF NOT EXISTS t_knowledge_base_share (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    kb_id               BIGINT         NOT NULL,
    tenant_id           VARCHAR(64)    NOT NULL,
    share_token         VARCHAR(64)    NOT NULL,
    password_hash       VARCHAR(128),
    expires_at          TIMESTAMP,
    max_access_count    INTEGER        NOT NULL DEFAULT 0,
    current_access_count INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_kb_share_token ON t_knowledge_base_share (share_token);
CREATE INDEX IF NOT EXISTS idx_kb_share_kb_id ON t_knowledge_base_share (kb_id);
CREATE INDEX IF NOT EXISTS idx_kb_share_tenant ON t_knowledge_base_share (tenant_id);
CREATE INDEX IF NOT EXISTS idx_kb_share_expires ON t_knowledge_base_share (expires_at);

-- Phase 4: Knowledge base share access log
CREATE TABLE IF NOT EXISTS t_knowledge_base_share_access_log (
    id          BIGINT         NOT NULL PRIMARY KEY,
    share_id    BIGINT         NOT NULL,
    accessed_at TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    ip_address  VARCHAR(64),
    user_agent  VARCHAR(512),
    referrer    VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_kb_share_log_share ON t_knowledge_base_share_access_log (share_id);
CREATE INDEX IF NOT EXISTS idx_kb_share_log_accessed ON t_knowledge_base_share_access_log (accessed_at);
