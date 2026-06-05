-- =============================================================================
-- V6: Query Rewrite Log (SaaS MVP Sprint 4 — Module 09: Advanced RAG)
-- Date: 2026-06-05
-- Scope: Audit trail for query rewriting operations
-- =============================================================================

CREATE TABLE IF NOT EXISTS sa_query_rewrite_log (
    id                BIGSERIAL      PRIMARY KEY,
    tenant_id         VARCHAR(64)    NOT NULL DEFAULT 'default',
    original_query    TEXT           NOT NULL,
    rewritten_queries TEXT           NOT NULL,
    rewrite_method    VARCHAR(64),
    hit_count         INTEGER        DEFAULT 0,
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_qrl_tenant ON sa_query_rewrite_log (tenant_id, created_at);
