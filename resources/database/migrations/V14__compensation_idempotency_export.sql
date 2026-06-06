-- Compensation log for tracking failed operations that need retry
CREATE TABLE IF NOT EXISTS sa_compensation_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    operation_type VARCHAR(64) NOT NULL,     -- e.g. 'TENANT_CREATION', 'PAYMENT', 'NOTIFICATION'
    operation_id VARCHAR(128) NOT NULL,      -- business operation identifier
    payload JSONB,                           -- serialized operation context for retry
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING, RETRYING, SUCCESS, FAILED
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    last_error TEXT,
    next_retry_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT uk_compensation_operation UNIQUE (operation_type, operation_id)
);
CREATE INDEX IF NOT EXISTS idx_compensation_status ON sa_compensation_log (status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_compensation_tenant ON sa_compensation_log (tenant_id, created_at);

-- Idempotency key tracking table
CREATE TABLE IF NOT EXISTS sa_idempotency_key (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    idempotency_key VARCHAR(128) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',  -- PROCESSING, SUCCESS, FAILED
    response_body JSONB,                              -- cached response for replay
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,                    -- auto-expire after 24h
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_idempotency_expires ON sa_idempotency_key (expires_at);

-- Add version column to key legacy tables for optimistic locking.
-- The application entities map to t_user, t_knowledge_base, and t_conversation.
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
ALTER TABLE t_knowledge_base ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
ALTER TABLE t_conversation ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;

-- Export task table (for Plan 18 async export)
CREATE TABLE IF NOT EXISTS sa_export_task (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT NOT NULL,
    export_type VARCHAR(32) NOT NULL,       -- e.g. 'KNOWLEDGE_DOC', 'CONVERSATION', 'AUDIT_LOG'
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSING, COMPLETED, FAILED
    file_name VARCHAR(255),
    file_path VARCHAR(512),
    progress INT NOT NULL DEFAULT 0,
    file_url VARCHAR(512),
    parameters TEXT,
    total_count INT NOT NULL DEFAULT 0,
    processed_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
ALTER TABLE sa_export_task ADD COLUMN IF NOT EXISTS progress INT NOT NULL DEFAULT 0;
ALTER TABLE sa_export_task ADD COLUMN IF NOT EXISTS file_url VARCHAR(512);
ALTER TABLE sa_export_task ADD COLUMN IF NOT EXISTS parameters TEXT;
CREATE INDEX IF NOT EXISTS idx_export_task_user ON sa_export_task (tenant_id, user_id, created_at DESC);
