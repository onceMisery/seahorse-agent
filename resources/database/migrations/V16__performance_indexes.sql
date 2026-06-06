-- Performance optimization indexes

-- Conversation queries: tenant + user + ordering.
-- Use a t_conversation-specific name because older init SQL used
-- idx_conversation_user_time on t_message.
CREATE INDEX IF NOT EXISTS idx_t_conversation_tenant_user_time
    ON t_conversation (tenant_id, user_id, last_time DESC)
    WHERE deleted = 0;

-- Message queries: conversation + user + ordering
CREATE INDEX IF NOT EXISTS idx_message_conv_user_time
    ON t_message (conversation_id, user_id, create_time ASC)
    WHERE deleted = 0;

-- Knowledge base queries: tenant + ordering
CREATE INDEX IF NOT EXISTS idx_kb_tenant_created
    ON t_knowledge_base (tenant_id, create_time DESC)
    WHERE deleted = 0;

-- Document queries: knowledge base + status
CREATE INDEX IF NOT EXISTS idx_document_kb_status
    ON t_knowledge_document (kb_id, status, create_time DESC)
    WHERE deleted = 0;

-- Agent definition: tenant + status
CREATE INDEX IF NOT EXISTS idx_agent_def_tenant_active
    ON sa_agent_definition (tenant_id, status, updated_at DESC);

-- Agent run: user + status + ordering
CREATE INDEX IF NOT EXISTS idx_agent_run_user_status
    ON sa_agent_run (tenant_id, user_id, status, started_at DESC);

-- Audit log: tenant + time range queries
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_time
    ON sa_audit_log (tenant_id, created_at DESC);

-- Billing: tenant + period queries
CREATE INDEX IF NOT EXISTS idx_bill_tenant_period
    ON sa_bill (tenant_id, bill_period DESC);

-- Cost usage: tenant + time range
CREATE INDEX IF NOT EXISTS idx_cost_usage_tenant_time
    ON sa_cost_usage_record (tenant_id, created_at DESC);
