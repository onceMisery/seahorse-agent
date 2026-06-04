-- =============================================================================
-- V2: Multi-Tenancy P0 - Add tenant_id to core tables + Enable RLS
-- Date: 2026-06-05
-- Scope: 15 tables get tenant_id added; 18 tables get RLS enabled
-- =============================================================================

-- Phase 1: Add tenant_id column to tables that don't have it
-- All use NOT NULL DEFAULT 'default' for backward compatibility

-- 1. User & Conversation Group
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_conversation ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_conversation_summary ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_message ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE sa_conversation_attachment ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_message_feedback ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

-- 2. Knowledge Base Group
ALTER TABLE t_knowledge_base ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_knowledge_document_chunk_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_knowledge_vector ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

-- 3. RAG Intent & Query Group
ALTER TABLE t_intent_node ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_query_term_mapping ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_rag_trace_run ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_rag_trace_node ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

-- 4. Sample Questions
ALTER TABLE t_sample_question ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

-- Phase 2: Create tenant_id indexes for query performance
CREATE INDEX IF NOT EXISTS idx_t_user_tenant ON t_user (tenant_id);
CREATE INDEX IF NOT EXISTS idx_t_conversation_tenant ON t_conversation (tenant_id, user_id, last_time);
CREATE INDEX IF NOT EXISTS idx_t_conv_summary_tenant ON t_conversation_summary (tenant_id, conversation_id);
CREATE INDEX IF NOT EXISTS idx_t_message_tenant ON t_message (tenant_id, conversation_id, create_time);
CREATE INDEX IF NOT EXISTS idx_sa_conv_attach_tenant ON sa_conversation_attachment (tenant_id, conversation_id);
CREATE INDEX IF NOT EXISTS idx_t_msg_feedback_tenant ON t_message_feedback (tenant_id, conversation_id);
CREATE INDEX IF NOT EXISTS idx_t_kb_tenant ON t_knowledge_base (tenant_id);
CREATE INDEX IF NOT EXISTS idx_t_kb_doc_tenant ON t_knowledge_document (tenant_id, kb_id);
CREATE INDEX IF NOT EXISTS idx_t_kb_chunk_tenant ON t_knowledge_chunk (tenant_id, doc_id);
CREATE INDEX IF NOT EXISTS idx_t_kb_chunk_log_tenant ON t_knowledge_document_chunk_log (tenant_id, doc_id);
CREATE INDEX IF NOT EXISTS idx_t_intent_node_tenant ON t_intent_node (tenant_id, kb_id);
CREATE INDEX IF NOT EXISTS idx_t_qtm_tenant ON t_query_term_mapping (tenant_id, domain);
CREATE INDEX IF NOT EXISTS idx_t_rag_trace_run_tenant ON t_rag_trace_run (tenant_id, trace_id);
CREATE INDEX IF NOT EXISTS idx_t_rag_trace_node_tenant ON t_rag_trace_node (tenant_id, trace_id);
CREATE INDEX IF NOT EXISTS idx_t_sample_q_tenant ON t_sample_question (tenant_id);

-- Phase 3: Enable Row Level Security (RLS) on 18 P0 tables
-- This provides defense-in-depth: even if application layer misses tenant_id filtering,
-- PostgreSQL RLS will block cross-tenant data access.

-- Helper: RLS policy uses current_setting('app.current_tenant_id', true)
-- The TenantConnectionPreparer sets this on each connection checkout.

-- Enable RLS
ALTER TABLE t_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_conversation ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_conversation_summary ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_message ENABLE ROW LEVEL SECURITY;
ALTER TABLE sa_conversation_attachment ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_message_feedback ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_knowledge_base ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_knowledge_document ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_knowledge_chunk ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_knowledge_document_chunk_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_knowledge_vector ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_intent_node ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_query_term_mapping ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_rag_trace_run ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_rag_trace_node ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_sample_question ENABLE ROW LEVEL SECURITY;
ALTER TABLE sa_agent_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE sa_quota_policy ENABLE ROW LEVEL SECURITY;

-- Force RLS for table owners too (otherwise superusers/owners bypass RLS)
ALTER TABLE t_user FORCE ROW LEVEL SECURITY;
ALTER TABLE t_conversation FORCE ROW LEVEL SECURITY;
ALTER TABLE t_conversation_summary FORCE ROW LEVEL SECURITY;
ALTER TABLE t_message FORCE ROW LEVEL SECURITY;
ALTER TABLE sa_conversation_attachment FORCE ROW LEVEL SECURITY;
ALTER TABLE t_message_feedback FORCE ROW LEVEL SECURITY;
ALTER TABLE t_knowledge_base FORCE ROW LEVEL SECURITY;
ALTER TABLE t_knowledge_document FORCE ROW LEVEL SECURITY;
ALTER TABLE t_knowledge_chunk FORCE ROW LEVEL SECURITY;
ALTER TABLE t_knowledge_document_chunk_log FORCE ROW LEVEL SECURITY;
ALTER TABLE t_knowledge_vector FORCE ROW LEVEL SECURITY;
ALTER TABLE t_intent_node FORCE ROW LEVEL SECURITY;
ALTER TABLE t_query_term_mapping FORCE ROW LEVEL SECURITY;
ALTER TABLE t_rag_trace_run FORCE ROW LEVEL SECURITY;
ALTER TABLE t_rag_trace_node FORCE ROW LEVEL SECURITY;
ALTER TABLE t_sample_question FORCE ROW LEVEL SECURITY;
ALTER TABLE sa_agent_definition FORCE ROW LEVEL SECURITY;
ALTER TABLE sa_quota_policy FORCE ROW LEVEL SECURITY;

-- Phase 4: Create RLS policies
-- The USING clause checks tenant_id against the session variable app.current_tenant_id.
-- When the variable is not set (e.g., admin tools, migration scripts), current_setting returns NULL
-- and the policy blocks all rows (safe default).

CREATE POLICY rls_tenant_isolation ON t_user
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_conversation
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_conversation_summary
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_message
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON sa_conversation_attachment
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_message_feedback
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_knowledge_base
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_knowledge_document
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_knowledge_chunk
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_knowledge_document_chunk_log
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_knowledge_vector
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_intent_node
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_query_term_mapping
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_rag_trace_run
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_rag_trace_node
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON t_sample_question
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON sa_agent_definition
    USING (tenant_id = current_setting('app.current_tenant_id', true));
CREATE POLICY rls_tenant_isolation ON sa_quota_policy
    USING (tenant_id = current_setting('app.current_tenant_id', true));
