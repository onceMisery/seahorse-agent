-- PostgreSQL Schema for Seahorse Agent - Optimized with BIGINT Primary Keys
-- Converted from MySQL schema_table.sql
-- Performance Optimization: Changed VARCHAR primary keys to BIGINT for Snowflake ID support

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================
-- User & Conversation Tables
-- ============================================

CREATE TABLE t_user (
    id           BIGINT  NOT NULL PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL,
    password     VARCHAR(128) NOT NULL,
    role         VARCHAR(32)  NOT NULL,
    avatar       VARCHAR(128),
    refresh_token VARCHAR(255),
    refresh_token_expires_at TIMESTAMP,
    create_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT     DEFAULT 0,
    CONSTRAINT uk_user_username UNIQUE (username)
);
CREATE INDEX idx_user_refresh_token ON t_user (refresh_token);

CREATE TABLE t_conversation (
    id              BIGINT NOT NULL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    title           VARCHAR(128) NOT NULL,
    last_time       TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT    DEFAULT 0,
    CONSTRAINT uk_conversation_user UNIQUE (conversation_id, user_id)
);
CREATE INDEX idx_user_time ON t_conversation (user_id, last_time);

CREATE TABLE t_conversation_summary (
    id              BIGINT      NOT NULL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    last_message_id BIGINT NOT NULL,
    content         TEXT        NOT NULL,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT    DEFAULT 0
);
CREATE INDEX idx_conv_user ON t_conversation_summary (conversation_id, user_id);

CREATE TABLE t_message (
    id                BIGINT      NOT NULL PRIMARY KEY,
    conversation_id   BIGINT NOT NULL,
    user_id           BIGINT NOT NULL,
    role              VARCHAR(16) NOT NULL,
    content           TEXT        NOT NULL,
    agent_run_id      VARCHAR(64),
    thinking_content  TEXT,
    thinking_duration INTEGER,
    create_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT    DEFAULT 0
);
CREATE INDEX idx_conversation_user_time ON t_message (conversation_id, user_id, create_time);
CREATE INDEX idx_message_agent_run ON t_message (agent_run_id, user_id, create_time);

CREATE TABLE sa_conversation_attachment (
    pk_id             BIGSERIAL PRIMARY KEY,
    attachment_id     VARCHAR(64)   NOT NULL UNIQUE,
    conversation_id   VARCHAR(64)   NOT NULL,
    message_id        VARCHAR(64),
    user_id           VARCHAR(64)   NOT NULL,
    file_name         VARCHAR(256)  NOT NULL,
    mime_type         VARCHAR(128)  NOT NULL,
    size_bytes        BIGINT        NOT NULL,
    storage_ref       VARCHAR(1000) NOT NULL,
    parse_status      VARCHAR(32)   NOT NULL,
    resource_ref_json TEXT          NOT NULL,
    created_at        TIMESTAMP     NOT NULL,
    deleted           SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_sa_conversation_attachment_parse_status
        CHECK (parse_status IN ('PENDING', 'PARSED', 'FAILED', 'BLOCKED'))
);
CREATE INDEX idx_sa_conversation_attachment_user
    ON sa_conversation_attachment (conversation_id, user_id, created_at);

CREATE TABLE t_message_feedback (
    id              BIGINT       NOT NULL PRIMARY KEY,
    message_id      BIGINT       NOT NULL,
    conversation_id BIGINT  NOT NULL,
    user_id         BIGINT  NOT NULL,
    vote            SMALLINT     NOT NULL,
    reason          VARCHAR(255),
    comment         VARCHAR(1024),
    create_time     TIMESTAMP  NOT NULL,
    update_time     TIMESTAMP  NOT NULL,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_msg_user UNIQUE (message_id, user_id)
);
CREATE INDEX idx_conversation_id ON t_message_feedback (conversation_id);
CREATE INDEX idx_user_id ON t_message_feedback (user_id);

CREATE TABLE t_sample_question (
    id          BIGINT        NOT NULL PRIMARY KEY,
    title       VARCHAR(64),
    description VARCHAR(255),
    question    VARCHAR(255) NOT NULL,
    create_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT      DEFAULT 0
);
CREATE INDEX idx_sample_question_deleted ON t_sample_question (deleted);

-- ============================================
-- Knowledge Base Tables
-- ============================================

CREATE TABLE t_knowledge_base (
    id              BIGINT       NOT NULL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(64)  NOT NULL,
    collection_name VARCHAR(64) NOT NULL,
    created_by      BIGINT  DEFAULT 0,
    updated_by      BIGINT,
    create_time     TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_collection_name UNIQUE (collection_name)
);
CREATE INDEX idx_kb_name ON t_knowledge_base (name);

CREATE TABLE t_knowledge_document (
    id               BIGINT        NOT NULL PRIMARY KEY,
    kb_id            BIGINT        NOT NULL,
    doc_name         VARCHAR(256)  NOT NULL,
    enabled          SMALLINT      NOT NULL DEFAULT 1,
    chunk_count      INTEGER       DEFAULT 0,
    file_url         VARCHAR(1024) NOT NULL,
    file_type        VARCHAR(64)  NOT NULL,
    file_size        BIGINT,
    process_mode     VARCHAR(32)  DEFAULT 'chunk',
    status           VARCHAR(32)  NOT NULL DEFAULT 'pending',
    source_type      VARCHAR(16),
    source_location  VARCHAR(1024),
    schedule_enabled SMALLINT,
    schedule_cron    VARCHAR(64),
    chunk_strategy   VARCHAR(32),
    chunk_config     JSONB,
    pipeline_id      BIGINT,
    created_by       BIGINT   DEFAULT 0,
    updated_by       BIGINT,
    create_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_kb_id ON t_knowledge_document (kb_id);

CREATE TABLE t_knowledge_chunk (
    id           BIGINT      NOT NULL PRIMARY KEY,
    kb_id        BIGINT      NOT NULL,
    doc_id       BIGINT      NOT NULL,
    chunk_index  INTEGER     NOT NULL,
    content      TEXT        NOT NULL,
    content_hash VARCHAR(64),
    char_count   INTEGER,
    token_count  INTEGER,
    enabled      SMALLINT    NOT NULL DEFAULT 1,
    created_by   BIGINT DEFAULT 0,
    updated_by   BIGINT,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_doc_id ON t_knowledge_chunk (doc_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_kb_doc ON t_knowledge_chunk (kb_id, doc_id);

CREATE TABLE t_knowledge_document_chunk_log (
    id                 BIGINT      NOT NULL PRIMARY KEY,
    doc_id             BIGINT      NOT NULL,
    status             VARCHAR(16)      NOT NULL,
    process_mode       VARCHAR(16),
    chunk_strategy     VARCHAR(16),
    pipeline_id        BIGINT,
    extract_duration   BIGINT,
    chunk_duration     BIGINT,
    embed_duration     BIGINT,
    persist_duration   BIGINT,
    total_duration     BIGINT,
    chunk_count        INTEGER,
    error_message      TEXT,
    start_time         TIMESTAMP,
    end_time           TIMESTAMP,
    create_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_doc_id_log ON t_knowledge_document_chunk_log (doc_id);

CREATE TABLE t_knowledge_document_schedule (
    id                BIGINT       NOT NULL PRIMARY KEY,
    doc_id            BIGINT       NOT NULL,
    kb_id             BIGINT       NOT NULL,
    cron_expr         VARCHAR(64),
    enabled           SMALLINT     DEFAULT 0,
    next_run_time     TIMESTAMP,
    last_run_time     TIMESTAMP,
    last_success_time TIMESTAMP,
    last_status       VARCHAR(16),
    last_error        VARCHAR(512),
    last_etag         VARCHAR(256),
    last_modified     VARCHAR(256),
    last_content_hash VARCHAR(128),
    lock_owner        VARCHAR(128),
    lock_until        TIMESTAMP,
    create_time       TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_doc_id UNIQUE (doc_id)
);
CREATE INDEX idx_next_run ON t_knowledge_document_schedule (next_run_time);
CREATE INDEX idx_lock_until ON t_knowledge_document_schedule (lock_until);

CREATE TABLE t_knowledge_document_schedule_exec (
    id            BIGINT       NOT NULL PRIMARY KEY,
    schedule_id   BIGINT       NOT NULL,
    doc_id        BIGINT       NOT NULL,
    kb_id         BIGINT       NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    message       VARCHAR(512),
    start_time    TIMESTAMP,
    end_time      TIMESTAMP,
    file_name     VARCHAR(512),
    file_size     BIGINT,
    content_hash  VARCHAR(128),
    etag          VARCHAR(256),
    last_modified VARCHAR(256),
    create_time   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_schedule_time ON t_knowledge_document_schedule_exec (schedule_id, start_time);
CREATE INDEX idx_doc_id_exec ON t_knowledge_document_schedule_exec (doc_id);

-- ============================================
-- RAG Intent & Query Tables
-- ============================================

CREATE TABLE t_intent_node (
    id                    BIGINT       NOT NULL PRIMARY KEY,
    kb_id                 BIGINT,
    intent_code           VARCHAR(64)  NOT NULL,
    name                  VARCHAR(64)  NOT NULL,
    level                 SMALLINT     NOT NULL,
    parent_code           VARCHAR(64),
    description           VARCHAR(512),
    examples              TEXT,
    collection_name       VARCHAR(128),
    top_k                 INTEGER,
    mcp_tool_id           VARCHAR(128),
    kind                  SMALLINT     NOT NULL DEFAULT 0,
    prompt_snippet        TEXT,
    prompt_template       TEXT,
    param_prompt_template TEXT,
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    enabled               SMALLINT     NOT NULL DEFAULT 1,
    create_by             BIGINT,
    update_by             BIGINT,
    create_time           TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0
);

CREATE TABLE t_query_term_mapping (
    id          BIGINT       NOT NULL PRIMARY KEY,
    domain      VARCHAR(64),
    source_term VARCHAR(128) NOT NULL,
    target_term VARCHAR(128) NOT NULL,
    match_type  SMALLINT     NOT NULL DEFAULT 1,
    priority    INTEGER      NOT NULL DEFAULT 100,
    enabled     SMALLINT     NOT NULL DEFAULT 1,
    remark      VARCHAR(255),
    create_by   BIGINT,
    update_by   BIGINT,
    create_time TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_domain ON t_query_term_mapping (domain);
CREATE INDEX idx_source ON t_query_term_mapping (source_term);

CREATE TABLE t_rag_trace_run (
    id              BIGINT           NOT NULL PRIMARY KEY,
    trace_id        BIGINT      NOT NULL,
    trace_name      VARCHAR(128),
    entry_method    VARCHAR(256),
    conversation_id VARCHAR(64),
    task_id         VARCHAR(64),
    user_id         BIGINT,
    status          VARCHAR(16)      NOT NULL DEFAULT 'RUNNING',
    error_message   VARCHAR(1000),
    start_time      TIMESTAMP(3),
    end_time        TIMESTAMP(3),
    duration_ms     BIGINT,
    extra_data      TEXT,
    create_time     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT         DEFAULT 0,
    CONSTRAINT uk_run_id UNIQUE (trace_id)
);
CREATE INDEX idx_task_id ON t_rag_trace_run (task_id);
CREATE INDEX idx_user_id_trace ON t_rag_trace_run (user_id);

CREATE TABLE t_rag_trace_node (
    id             BIGINT           NOT NULL PRIMARY KEY,
    trace_id       BIGINT      NOT NULL,
    node_id        BIGINT      NOT NULL,
    parent_node_id BIGINT,
    depth          INTEGER          DEFAULT 0,
    node_type      VARCHAR(64),
    node_name      VARCHAR(128),
    class_name     VARCHAR(256),
    method_name    VARCHAR(128),
    status         VARCHAR(16)      NOT NULL DEFAULT 'RUNNING',
    error_message  VARCHAR(1000),
    start_time     TIMESTAMP(3),
    end_time       TIMESTAMP(3),
    duration_ms    BIGINT,
    extra_data     TEXT,
    create_time    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT         DEFAULT 0,
    CONSTRAINT uk_run_node UNIQUE (trace_id, node_id)
);

-- ============================================
-- Ingestion Pipeline Tables
-- ============================================

CREATE TABLE t_ingestion_pipeline (
    id          BIGINT      NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    version     INTEGER     NOT NULL DEFAULT 1,
    created_by  BIGINT DEFAULT 0,
    updated_by  BIGINT DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT uk_ingestion_pipeline_name UNIQUE (name, deleted)
);

CREATE TABLE t_ingestion_pipeline_node (
    id             BIGINT      NOT NULL PRIMARY KEY,
    pipeline_id    BIGINT      NOT NULL,
    node_id        BIGINT NOT NULL,
    node_type      VARCHAR(16) NOT NULL,
    next_node_id   BIGINT,
    settings_json  JSONB,
    condition_json JSONB,
    created_by     BIGINT DEFAULT 0,
    updated_by     BIGINT DEFAULT 0,
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT uk_ingestion_pipeline_node UNIQUE (pipeline_id, node_id, deleted)
);
CREATE INDEX idx_ingestion_pipeline_node_pipeline ON t_ingestion_pipeline_node (pipeline_id);

CREATE TABLE t_ingestion_task (
    id               BIGINT      NOT NULL PRIMARY KEY,
    pipeline_id      BIGINT      NOT NULL,
    pipeline_version INTEGER     NOT NULL DEFAULT 0,
    pipeline_snapshot_json JSONB,
    source_type      VARCHAR(20) NOT NULL,
    source_location  TEXT,
    source_file_name VARCHAR(255),
    status           VARCHAR(16) NOT NULL,
    chunk_count      INTEGER     DEFAULT 0,
    error_message    TEXT,
    logs_json        JSONB,
    metadata_json    JSONB,
    started_at       TIMESTAMP,
    completed_at     TIMESTAMP,
    created_by       BIGINT DEFAULT 0,
    updated_by       BIGINT DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_ingestion_task_pipeline ON t_ingestion_task (pipeline_id);
CREATE INDEX idx_ingestion_task_status ON t_ingestion_task (status);

CREATE TABLE t_ingestion_task_node (
    id            BIGINT      NOT NULL PRIMARY KEY,
    task_id       BIGINT      NOT NULL,
    pipeline_id   BIGINT      NOT NULL,
    node_id       BIGINT NOT NULL,
    node_type     VARCHAR(16) NOT NULL,
    node_order    INTEGER     NOT NULL DEFAULT 0,
    status        VARCHAR(16) NOT NULL,
    duration_ms   BIGINT      NOT NULL DEFAULT 0,
    input_summary TEXT,
    output_summary TEXT,
    error_code    VARCHAR(128),
    message       TEXT,
    error_message TEXT,
    retry_count   INTEGER     NOT NULL DEFAULT 0,
    downstream_impact TEXT,
    output_json   TEXT,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_ingestion_task_node_task ON t_ingestion_task_node (task_id);
CREATE INDEX idx_ingestion_task_node_pipeline ON t_ingestion_task_node (pipeline_id);
CREATE INDEX idx_ingestion_task_node_status ON t_ingestion_task_node (status);
CREATE INDEX idx_ingestion_task_node_error_code ON t_ingestion_task_node (error_code);

-- ============================================
-- Vector Storage Table (pgvector)
-- ============================================

CREATE TABLE t_knowledge_vector (
    id          VARCHAR(128) PRIMARY KEY,
    content     TEXT NOT NULL,
    metadata    JSONB NOT NULL,
    embedding   vector(768) NOT NULL  -- 匹配nomic-embed-text维度
);

CREATE INDEX idx_kv_metadata ON t_knowledge_vector USING gin(metadata);
CREATE INDEX idx_kv_embedding ON t_knowledge_vector USING hnsw (embedding vector_cosine_ops);

-- ============================================
-- Memory & Messaging Tables
-- ============================================

CREATE TABLE t_outbox_event (
    id BIGINT PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    message_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(16) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_time TIMESTAMP,
    last_error TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_outbox_status_retry ON t_outbox_event (status, next_retry_time, create_time);

CREATE TABLE t_short_term_memory (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) DEFAULT 'default',
    conversation_id BIGINT,
    memory_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    metadata_json JSONB,
    source_message_ids JSONB,
    importance_score NUMERIC(4, 3) DEFAULT 0,
    access_count INTEGER DEFAULT 0,
    last_access_time TIMESTAMP,
    decay_score NUMERIC(4, 3) DEFAULT 0,
    expires_time TIMESTAMP,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    generation_id VARCHAR(64),
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    last_referenced_at TIMESTAMP,
    schema_version VARCHAR(32),
    policy_version VARCHAR(64),
    sensitivity_level VARCHAR(32),
    obsolete_reason TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_stm_user_conv_time ON t_short_term_memory (user_id, conversation_id, create_time DESC);
CREATE INDEX idx_stm_user_type_decay ON t_short_term_memory (user_id, memory_type, decay_score DESC);
CREATE INDEX idx_stm_lifecycle_user_status ON t_short_term_memory (user_id, tenant_id, status, update_time);
CREATE INDEX idx_stm_metadata_gin ON t_short_term_memory USING GIN (metadata_json);

CREATE TABLE t_long_term_memory (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) DEFAULT 'default',
    memory_category VARCHAR(32) NOT NULL,
    title VARCHAR(256),
    content TEXT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_ids JSONB,
    tags JSONB,
    importance_score NUMERIC(4, 3) DEFAULT 0,
    confidence_level NUMERIC(4, 3) DEFAULT 0,
    embedding_model VARCHAR(64),
    vector_ref_id VARCHAR(64),
    status VARCHAR(32) DEFAULT 'ACTIVE',
    generation_id VARCHAR(64),
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    last_referenced_at TIMESTAMP,
    schema_version VARCHAR(32),
    policy_version VARCHAR(64),
    sensitivity_level VARCHAR(32),
    obsolete_reason TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_ltm_user_category_importance ON t_long_term_memory (user_id, memory_category, importance_score DESC);
CREATE INDEX idx_ltm_lifecycle_user_status ON t_long_term_memory (user_id, tenant_id, status, update_time);
CREATE INDEX idx_ltm_tags_gin ON t_long_term_memory USING GIN (tags);

CREATE TABLE t_semantic_memory (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) DEFAULT 'default',
    semantic_key VARCHAR(64) NOT NULL,
    semantic_type VARCHAR(32) NOT NULL,
    value_json JSONB NOT NULL,
    confidence_level NUMERIC(4, 3) DEFAULT 0,
    source_memory_ids JSONB,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    generation_id VARCHAR(64),
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    last_referenced_at TIMESTAMP,
    schema_version VARCHAR(32),
    policy_version VARCHAR(64),
    sensitivity_level VARCHAR(32),
    obsolete_reason TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0,
    CONSTRAINT uk_semantic_memory UNIQUE (user_id, semantic_key, semantic_type)
);
CREATE INDEX idx_sem_user_type ON t_semantic_memory (user_id, semantic_type);
CREATE INDEX idx_sem_lifecycle_user_status ON t_semantic_memory (user_id, tenant_id, status, update_time);

CREATE TABLE t_memory_operation_log (
    pk_id BIGSERIAL PRIMARY KEY,
    operation_id VARCHAR(128) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    operation_type VARCHAR(32) NOT NULL,
    target_kind VARCHAR(32) NOT NULL,
    target_key VARCHAR(128),
    request_json JSONB NOT NULL,
    decision_json JSONB,
    status VARCHAR(32) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    error_message TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_memory_operation_user_time
ON t_memory_operation_log (user_id, tenant_id, create_time);

CREATE TABLE t_memory_outbox (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(128) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    task_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64),
    payload_json JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    next_retry_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_memory_outbox_status
ON t_memory_outbox (status, next_retry_time, create_time);

CREATE TABLE t_memory_review_candidate (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(128) NOT NULL UNIQUE,
    operation_id VARCHAR(128),
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    conversation_id VARCHAR(64),
    message_id VARCHAR(64),
    requested_action VARCHAR(32) NOT NULL,
    target_layer VARCHAR(32) NOT NULL,
    target_kind VARCHAR(64),
    target_key VARCHAR(128),
    candidate_content TEXT NOT NULL,
    confidence_level NUMERIC(5, 4) DEFAULT 0,
    importance_score NUMERIC(5, 4) DEFAULT 0,
    value_score NUMERIC(5, 4) DEFAULT 0,
    risk_score NUMERIC(5, 4) DEFAULT 0,
    reason TEXT,
    source_message_ids JSONB,
    candidate_metadata JSONB,
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reviewer_id VARCHAR(64),
    reviewer_comment TEXT,
    chosen_content TEXT,
    chosen_metadata JSONB,
    reviewed_memory_id VARCHAR(128),
    reviewed_layer VARCHAR(32),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_memory_review_queue
ON t_memory_review_candidate (tenant_id, user_id, review_status, update_time);
CREATE INDEX idx_memory_review_operation
ON t_memory_review_candidate (operation_id);

CREATE TABLE t_memory_review_feedback_sample (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(128) NOT NULL UNIQUE,
    candidate_id VARCHAR(128) NOT NULL,
    operation_id VARCHAR(128),
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    requested_action VARCHAR(32) NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    reviewer_id VARCHAR(64),
    reviewer_comment TEXT,
    target_layer VARCHAR(32),
    target_kind VARCHAR(64),
    target_key VARCHAR(128),
    rejected_content TEXT,
    chosen_content TEXT,
    rejected_metadata JSONB,
    chosen_metadata JSONB,
    source_message_ids JSONB,
    reviewed_memory_id VARCHAR(128),
    reviewed_layer VARCHAR(32),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_memory_review_feedback_candidate
ON t_memory_review_feedback_sample (candidate_id, create_time);

CREATE TABLE t_memory_trace_event (
    id BIGINT PRIMARY KEY,
    trace_id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT,
    conversation_id BIGINT,
    session_id VARCHAR(128),
    component VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(32),
    subject_id VARCHAR(128),
    subject_type VARCHAR(64),
    details_json JSONB,
    occurred_at TIMESTAMP NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_memory_trace_recent
ON t_memory_trace_event (occurred_at, create_time);
CREATE INDEX idx_memory_trace_filters
ON t_memory_trace_event (tenant_id, user_id, component, status, occurred_at);

CREATE TABLE t_memory_aggregation_buffer (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    turn_count INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    turns_json JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    first_activity_at TIMESTAMP NOT NULL,
    last_activity_at TIMESTAMP NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_memory_aggregation_session
ON t_memory_aggregation_buffer (tenant_id, user_id, session_id);
CREATE INDEX idx_memory_aggregation_scan
ON t_memory_aggregation_buffer (last_activity_at, update_time);

CREATE TABLE t_memory_maintenance_run (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(128) NOT NULL UNIQUE,
    reason VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    compaction_requested SMALLINT NOT NULL DEFAULT 0,
    alias_requested SMALLINT NOT NULL DEFAULT 0,
    gc_requested SMALLINT NOT NULL DEFAULT 0,
    compaction_scanned_count INTEGER NOT NULL DEFAULT 0,
    compaction_group_count INTEGER NOT NULL DEFAULT 0,
    compaction_fragment_count INTEGER NOT NULL DEFAULT 0,
    alias_scanned_count INTEGER NOT NULL DEFAULT 0,
    alias_normalized_count INTEGER NOT NULL DEFAULT 0,
    alias_dictionary_match_count INTEGER NOT NULL DEFAULT 0,
    alias_skipped_count INTEGER NOT NULL DEFAULT 0,
    gc_scanned_count INTEGER NOT NULL DEFAULT 0,
    gc_enqueued_count INTEGER NOT NULL DEFAULT 0,
    gc_marked_count INTEGER NOT NULL DEFAULT 0,
    gc_dry_run SMALLINT NOT NULL DEFAULT 0,
    skipped_tasks TEXT,
    errors TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_memory_maintenance_run_status_time
ON t_memory_maintenance_run (status, update_time);

CREATE TABLE t_memory_entity_alias (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(128) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    alias_text VARCHAR(256) NOT NULL,
    normalized_alias VARCHAR(256) NOT NULL,
    canonical_entity_id VARCHAR(128) NOT NULL,
    canonical_name VARCHAR(256) NOT NULL,
    entity_type VARCHAR(64) NOT NULL DEFAULT 'ENTITY',
    confidence_level NUMERIC(4, 3) DEFAULT 0,
    source_type VARCHAR(64),
    source_memory_ids JSONB,
    metadata_json JSONB,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_memory_alias_lookup
ON t_memory_entity_alias (user_id, tenant_id, normalized_alias, status);

CREATE TABLE t_memory_entity_relation (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(128) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    memory_id VARCHAR(64) NOT NULL,
    layer_name VARCHAR(32),
    memory_type VARCHAR(64),
    content TEXT,
    source_entity_id VARCHAR(128) NOT NULL,
    target_entity_id VARCHAR(128) NOT NULL,
    relation_type VARCHAR(64) NOT NULL DEFAULT 'MENTIONS',
    weight NUMERIC(6, 4) DEFAULT 1,
    confidence_level NUMERIC(4, 3) DEFAULT 1,
    metadata_json JSONB,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_memory_relation_source
ON t_memory_entity_relation (user_id, tenant_id, source_entity_id, status);
CREATE INDEX idx_memory_relation_target
ON t_memory_entity_relation (user_id, tenant_id, target_entity_id, status);
CREATE INDEX idx_memory_relation_memory
ON t_memory_entity_relation (user_id, tenant_id, memory_id, status);

CREATE TABLE t_memory_keyword_index (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    memory_id VARCHAR(64) NOT NULL,
    layer_name VARCHAR(32),
    memory_type VARCHAR(64),
    content TEXT,
    metadata_json JSONB,
    source_update_time TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE UNIQUE INDEX uk_memory_keyword_memory
ON t_memory_keyword_index (user_id, tenant_id, memory_id);
CREATE INDEX idx_memory_keyword_lookup
ON t_memory_keyword_index (user_id, tenant_id, status, update_time);

CREATE TABLE t_user_profile_fact (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    slot_key VARCHAR(128) NOT NULL,
    value_text TEXT NOT NULL,
    value_json JSONB,
    confidence_level NUMERIC(4, 3) DEFAULT 0,
    source_type VARCHAR(64),
    source_ids JSONB,
    generation_id VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 1,
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    last_referenced_at TIMESTAMP,
    access_count INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_user_profile_active ON t_user_profile_fact (user_id, tenant_id, status, slot_key);
CREATE UNIQUE INDEX uk_user_profile_active_slot ON t_user_profile_fact (user_id, tenant_id, slot_key)
WHERE status = 'ACTIVE' AND deleted = 0;

CREATE TABLE t_memory_correction_ledger (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    correction_type VARCHAR(32) NOT NULL,
    target_kind VARCHAR(32) NOT NULL,
    target_key VARCHAR(128) NOT NULL,
    incorrect_value TEXT,
    correct_value TEXT,
    rule_text TEXT NOT NULL,
    priority VARCHAR(32) NOT NULL DEFAULT 'HARD_RULE',
    source_message_ids JSONB,
    effective_generation_id VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_memory_correction_active
ON t_memory_correction_ledger (user_id, tenant_id, status, target_kind, target_key);
CREATE UNIQUE INDEX uk_memory_correction_active_target
ON t_memory_correction_ledger (user_id, tenant_id, target_kind, target_key)
WHERE status = 'ACTIVE' AND deleted = 0;

CREATE TABLE t_memory_conflict_log (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(128) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    memory_id_1 VARCHAR(64) NOT NULL,
    memory_id_2 VARCHAR(64) NOT NULL,
    conflict_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    resolution_status VARCHAR(16) NOT NULL,
    resolution_action VARCHAR(32),
    resolved_by VARCHAR(32),
    resolved_at TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_memory_conflict_user_status ON t_memory_conflict_log (user_id, resolution_status, create_time DESC);

CREATE TABLE t_memory_quality_snapshot (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(128) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    snapshot_json JSONB NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_memory_quality_snapshot_user_time ON t_memory_quality_snapshot (user_id, create_time DESC);

CREATE TABLE t_long_term_memory_vector (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) DEFAULT 'default',
    content TEXT NOT NULL,
    embedding vector(1536) NOT NULL,
    generation_id VARCHAR(64),
    status VARCHAR(32) DEFAULT 'ACTIVE',
    last_referenced_at TIMESTAMP,
    access_count INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ltm_vector_user ON t_long_term_memory_vector (user_id);
CREATE INDEX idx_ltm_vector_lifecycle ON t_long_term_memory_vector (user_id, tenant_id, status, update_time);
CREATE INDEX idx_ltm_vector_hnsw ON t_long_term_memory_vector USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS t_agent_extension_status (
    id BIGSERIAL PRIMARY KEY,
    extension_name VARCHAR(128) NOT NULL,
    port_type VARCHAR(256) NOT NULL,
    feature_type VARCHAR(64) NOT NULL,
    version VARCHAR(64),
    enabled BOOLEAN NOT NULL,
    healthy BOOLEAN NOT NULL,
    capabilities_json TEXT,
    message TEXT,
    last_error TEXT,
    details_json TEXT,
    updated_by VARCHAR(128),
    update_time TIMESTAMP,
    deleted SMALLINT DEFAULT 0,
    CONSTRAINT uk_agent_extension_status UNIQUE (extension_name, port_type)
);
CREATE INDEX IF NOT EXISTS idx_agent_extension_status_port
    ON t_agent_extension_status(port_type, deleted);

-- ============================================
-- Advanced Platform Tables
-- Physical primary keys are BIGSERIAL. Business identifiers remain varchar with UNIQUE constraints.
-- ============================================

-- Source merged from: seahorse-agent-adapter-repository-jdbc\src\main\resources\META-INF\seahorse-agent\sql\agent-registry-run-store-postgresql.sql
CREATE TABLE IF NOT EXISTS sa_agent_definition (
  pk_id BIGSERIAL PRIMARY KEY,
  agent_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(500),
  owner_user_id VARCHAR(64) NOT NULL,
  owner_team VARCHAR(128),
  agent_type VARCHAR(32) NOT NULL,
  base_agent_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  latest_version_id VARCHAR(64),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_definition_tenant_status
  ON sa_agent_definition(tenant_id, status);

CREATE TABLE IF NOT EXISTS sa_agent_version (
  pk_id BIGSERIAL PRIMARY KEY,
  version_id VARCHAR(64) NOT NULL UNIQUE,
  agent_id VARCHAR(64) NOT NULL,
  version_no BIGINT NOT NULL,
  instructions TEXT NOT NULL,
  tool_set_json TEXT NOT NULL,
  model_config_json TEXT NOT NULL,
  memory_config_json TEXT NOT NULL,
  guardrail_config_json TEXT NOT NULL,
  skill_set_json TEXT NOT NULL DEFAULT '{}',
  published_by VARCHAR(64) NOT NULL,
  published_at TIMESTAMP NOT NULL,
  change_summary VARCHAR(500) NOT NULL,
  UNIQUE(agent_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_version_agent
  ON sa_agent_version(agent_id, version_no);

CREATE TABLE IF NOT EXISTS sa_agent_skill (
  pk_id BIGSERIAL PRIMARY KEY,
  skill_name VARCHAR(128) NOT NULL,
  tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
  category VARCHAR(32) NOT NULL,
  source VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  enabled SMALLINT NOT NULL DEFAULT 1,
  latest_revision_id VARCHAR(128),
  description TEXT NOT NULL,
  tags_json TEXT NOT NULL DEFAULT '[]',
  allowed_tools_json TEXT NOT NULL DEFAULT '[]',
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  deleted SMALLINT NOT NULL DEFAULT 0,
  UNIQUE(tenant_id, skill_name)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_skill_tenant_status
  ON sa_agent_skill(tenant_id, status, enabled);

CREATE TABLE IF NOT EXISTS sa_agent_skill_revision (
  pk_id BIGSERIAL PRIMARY KEY,
  revision_id VARCHAR(128) NOT NULL UNIQUE,
  skill_name VARCHAR(128) NOT NULL,
  tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
  revision_no BIGINT NOT NULL,
  content_hash VARCHAR(128) NOT NULL,
  content TEXT NOT NULL,
  frontmatter_json TEXT NOT NULL,
  scan_decision VARCHAR(32) NOT NULL,
  scan_result_json TEXT NOT NULL,
  created_by VARCHAR(64),
  created_at TIMESTAMP NOT NULL,
  deleted SMALLINT NOT NULL DEFAULT 0,
  UNIQUE(tenant_id, skill_name, revision_no)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_skill_revision_skill
  ON sa_agent_skill_revision(tenant_id, skill_name, revision_no);

CREATE TABLE IF NOT EXISTS sa_agent_skill_binding (
  pk_id BIGSERIAL PRIMARY KEY,
  agent_id VARCHAR(64) NOT NULL,
  tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
  skill_name VARCHAR(128) NOT NULL,
  revision_id VARCHAR(128) NOT NULL,
  inject_mode VARCHAR(32) NOT NULL DEFAULT 'METADATA_AND_BODY',
  created_by VARCHAR(64),
  created_at TIMESTAMP NOT NULL,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_skill_binding_agent
  ON sa_agent_skill_binding(tenant_id, agent_id, deleted);

CREATE TABLE IF NOT EXISTS sa_agent_run (
  pk_id BIGSERIAL PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL UNIQUE,
  agent_id VARCHAR(64),
  version_id VARCHAR(64),
  rollout_id VARCHAR(64),
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(64),
  trigger_type VARCHAR(32) NOT NULL,
  input_summary VARCHAR(1000),
  status VARCHAR(32) NOT NULL,
  trace_id VARCHAR(64),
  token_input BIGINT NOT NULL DEFAULT 0,
  token_output BIGINT NOT NULL DEFAULT 0,
  cost_total DECIMAL(18,6) NOT NULL DEFAULT 0,
  error_code VARCHAR(128),
  error_message VARCHAR(1000),
  started_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_run_agent_status
  ON sa_agent_run(agent_id, status, started_at);

CREATE INDEX IF NOT EXISTS idx_sa_agent_run_user
  ON sa_agent_run(tenant_id, user_id, started_at);

CREATE INDEX IF NOT EXISTS idx_sa_agent_run_worker_queue
  ON sa_agent_run(tenant_id, status, started_at, run_id);

CREATE INDEX IF NOT EXISTS idx_sa_agent_run_rollout
  ON sa_agent_run(tenant_id, agent_id, rollout_id, started_at);

CREATE TABLE IF NOT EXISTS sa_agent_artifact (
  pk_id BIGSERIAL PRIMARY KEY,
  artifact_id VARCHAR(64) NOT NULL UNIQUE,
  run_id VARCHAR(64) NOT NULL,
  message_id VARCHAR(64),
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  artifact_type VARCHAR(32) NOT NULL,
  title VARCHAR(256) NOT NULL,
  mime_type VARCHAR(128) NOT NULL,
  storage_ref VARCHAR(1000) NOT NULL,
  preview_text TEXT,
  provenance_json TEXT,
  scan_status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_agent_artifact_type
    CHECK (artifact_type IN ('REPORT', 'TABLE', 'CHART', 'HTML', 'IMAGE', 'FILE', 'MARKDOWN')),
  CONSTRAINT chk_sa_agent_artifact_scan_status
    CHECK (scan_status IN ('PENDING', 'CLEAN', 'BLOCKED'))
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_artifact_run
  ON sa_agent_artifact(run_id, created_at, artifact_id);

CREATE INDEX IF NOT EXISTS idx_sa_agent_artifact_user
  ON sa_agent_artifact(tenant_id, user_id, created_at);

CREATE TABLE IF NOT EXISTS sa_agent_step (
  pk_id BIGSERIAL PRIMARY KEY,
  step_id VARCHAR(64) NOT NULL UNIQUE,
  run_id VARCHAR(64) NOT NULL,
  step_no INT NOT NULL,
  step_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  input_json TEXT,
  output_json TEXT,
  error_code VARCHAR(128),
  error_message VARCHAR(1000),
  started_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP,
  UNIQUE(run_id, step_no)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_step_run
  ON sa_agent_step(run_id, step_no);

CREATE TABLE IF NOT EXISTS sa_agent_checkpoint (
  pk_id BIGSERIAL PRIMARY KEY,
  checkpoint_id VARCHAR(64) NOT NULL UNIQUE,
  run_id VARCHAR(64) NOT NULL,
  step_id VARCHAR(64),
  sequence_no BIGINT NOT NULL,
  checkpoint_type VARCHAR(32) NOT NULL,
  state_json TEXT NOT NULL,
  message_history_json TEXT,
  context_pack_id VARCHAR(64),
  pending_tool_call_json TEXT,
  created_at TIMESTAMP NOT NULL,
  UNIQUE(run_id, sequence_no)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_checkpoint_run
  ON sa_agent_checkpoint(run_id, sequence_no);

CREATE TABLE IF NOT EXISTS sa_agent_run_lease (
  pk_id BIGSERIAL PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL UNIQUE,
  worker_id VARCHAR(128) NOT NULL,
  lease_until TIMESTAMP NOT NULL,
  heartbeat_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS sa_tool_catalog (
  pk_id BIGSERIAL PRIMARY KEY,
  tool_id VARCHAR(128) NOT NULL UNIQUE,
  provider VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(1000),
  schema_json TEXT NOT NULL,
  output_schema_json TEXT,
  risk_level VARCHAR(32) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  resource_type VARCHAR(64),
  owner_team VARCHAR(128),
  enabled BOOLEAN NOT NULL,
  requires_approval BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_tool_catalog_resource
  ON sa_tool_catalog(resource_type, enabled);

CREATE TABLE IF NOT EXISTS sa_connector (
  pk_id BIGSERIAL PRIMARY KEY,
  connector_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(1000),
  status VARCHAR(32) NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_connector_tenant_status
  ON sa_connector(tenant_id, status);

CREATE TABLE IF NOT EXISTS sa_connector_version (
  pk_id BIGSERIAL PRIMARY KEY,
  connector_version_id VARCHAR(64) NOT NULL UNIQUE,
  connector_id VARCHAR(64) NOT NULL,
  spec_hash VARCHAR(128) NOT NULL,
  spec_json TEXT NOT NULL,
  imported_by VARCHAR(64) NOT NULL,
  imported_at TIMESTAMP NOT NULL,
  UNIQUE(connector_id, spec_hash)
);

CREATE TABLE IF NOT EXISTS sa_connector_operation (
  pk_id BIGSERIAL PRIMARY KEY,
  operation_id VARCHAR(64) NOT NULL UNIQUE,
  connector_id VARCHAR(64) NOT NULL,
  connector_version_id VARCHAR(64) NOT NULL,
  operation_key VARCHAR(256) NOT NULL,
  original_operation_id VARCHAR(128),
  method VARCHAR(16) NOT NULL,
  path VARCHAR(512) NOT NULL,
  summary VARCHAR(256),
  description VARCHAR(1000),
  schema_json TEXT NOT NULL,
  output_schema_json TEXT,
  tool_id VARCHAR(128) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  resource_type VARCHAR(64),
  auth_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
  status VARCHAR(32) NOT NULL,
  requires_approval BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_connector_operation_connector
  ON sa_connector_operation(connector_id, updated_at);

CREATE TABLE IF NOT EXISTS sa_connector_credential_binding (
  pk_id BIGSERIAL PRIMARY KEY,
  binding_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  connector_id VARCHAR(64) NOT NULL,
  operation_id VARCHAR(64) NOT NULL,
  auth_type VARCHAR(32) NOT NULL,
  credential_ref VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  bound_by VARCHAR(64) NOT NULL,
  bound_at TIMESTAMP NOT NULL,
  rotated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sa_connector_credential_binding_operation
  ON sa_connector_credential_binding(tenant_id, connector_id, operation_id, auth_type, status);

CREATE TABLE IF NOT EXISTS sa_agent_template (
  pk_id BIGSERIAL PRIMARY KEY,
  template_id VARCHAR(64) NOT NULL UNIQUE,
  status VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(1000),
  agent_type VARCHAR(32) NOT NULL,
  risk_cap VARCHAR(32) NOT NULL,
  allowed_tool_ids_json TEXT NOT NULL,
  base_instructions TEXT NOT NULL,
  guardrail_config_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_template_status
  ON sa_agent_template(status, template_id);

INSERT INTO sa_agent_template (
    template_id,
    status,
    name,
    description,
    agent_type,
    risk_cap,
    allowed_tool_ids_json,
    base_instructions,
    guardrail_config_json,
    created_at,
    updated_at
)
VALUES (
    'github-visual-project-intro',
    'ENABLED',
    'GitHub 项目图文介绍',
    '读取 GitHub 仓库和公开补充资料，生成包含图文、架构图、流程图和关键文件证据的中文项目介绍。',
    'ASSISTANT',
    'HIGH',
    '["github_repository_reader","web_fetch","chart_visualization","newsletter_generation","ppt_generation","frontend_design"]',
    $instructions$
你是“GitHub 项目图文介绍生成 Agent”。用户会提供一个 GitHub 仓库链接，你需要读取项目公开资料并生成中文 Markdown 项目介绍。

工作要求：
1. 先调用 github_repository_reader 读取 README、docs、关键源码和配置文件。第一次读取成功后进入分析阶段；如果材料不足，最多再读取一次，并调整参数。
2. 必须基于读取到的文件证据总结，不编造仓库不存在的模块、架构或能力。
3. 必须按需调用 web_fetch 获取公开补充资料。
4. 必须至少输出一个 Mermaid 架构图或流程图；如需辅助生成图表草案，优先使用 chart_visualization。
5. 输出中文 Markdown，包含项目概览、架构设计、核心流程、关键文件证据、重点特性、适用场景、Mermaid 图表和后续建议。
6. 如果仓库读取或图表生成失败，说明失败原因，并给出用户可重试的建议。
    $instructions$,
    '{"requireEvidence":true,"maxRepositoryReadCalls":2}',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (template_id) DO UPDATE
SET status = EXCLUDED.status,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    agent_type = EXCLUDED.agent_type,
    risk_cap = EXCLUDED.risk_cap,
    allowed_tool_ids_json = EXCLUDED.allowed_tool_ids_json,
    base_instructions = EXCLUDED.base_instructions,
    guardrail_config_json = EXCLUDED.guardrail_config_json,
    updated_at = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS sa_agent_publish_check (
  pk_id BIGSERIAL PRIMARY KEY,
  check_id VARCHAR(64) NOT NULL UNIQUE,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  result_json TEXT NOT NULL,
  checked_by VARCHAR(64) NOT NULL,
  checked_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_publish_check_agent
  ON sa_agent_publish_check(agent_id, checked_at);

CREATE TABLE IF NOT EXISTS sa_agent_version_activation (
  pk_id BIGSERIAL PRIMARY KEY,
  activation_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  activation_type VARCHAR(32) NOT NULL,
  previous_version_id VARCHAR(64),
  reason_code VARCHAR(64) NOT NULL,
  operator_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_version_activation_active
  ON sa_agent_version_activation(agent_id, created_at DESC, activation_id DESC);

CREATE TABLE IF NOT EXISTS sa_agent_tool_binding (
  pk_id BIGSERIAL PRIMARY KEY,
  id VARCHAR(64) NOT NULL UNIQUE,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  tool_id VARCHAR(128) NOT NULL,
  max_calls_per_run INT NOT NULL,
  argument_policy_json TEXT,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  UNIQUE(agent_id, version_id, tool_id)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_tool_binding_version
  ON sa_agent_tool_binding(agent_id, version_id);

CREATE TABLE IF NOT EXISTS sa_tool_invocation (
  pk_id BIGSERIAL PRIMARY KEY,
  invocation_id VARCHAR(64) NOT NULL UNIQUE,
  run_id VARCHAR(64) NOT NULL,
  step_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64),
  version_id VARCHAR(64),
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  tool_id VARCHAR(128) NOT NULL,
  idempotency_key VARCHAR(128),
  status VARCHAR(32) NOT NULL,
  policy_decision_id VARCHAR(64),
  arguments_summary TEXT,
  result_summary TEXT,
  error_message VARCHAR(1000),
  started_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sa_tool_invocation_run_tool
  ON sa_tool_invocation(run_id, tool_id, started_at);

CREATE INDEX IF NOT EXISTS idx_sa_tool_invocation_tenant_user
  ON sa_tool_invocation(tenant_id, user_id, started_at);

CREATE TABLE IF NOT EXISTS sa_approval_request (
  pk_id BIGSERIAL PRIMARY KEY,
  approval_id VARCHAR(64) NOT NULL UNIQUE,
  run_id VARCHAR(64) NOT NULL,
  step_id VARCHAR(64),
  tool_invocation_id VARCHAR(64),
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64),
  rollout_id VARCHAR(64),
  tool_id VARCHAR(128) NOT NULL,
  approval_type VARCHAR(32) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  summary VARCHAR(1000) NOT NULL,
  arguments_preview_json TEXT,
  status VARCHAR(32) NOT NULL,
  requested_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP,
  decided_by VARCHAR(64),
  decided_at TIMESTAMP,
  decision_comment VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_sa_approval_request_status
  ON sa_approval_request(tenant_id, status, requested_at);

CREATE INDEX IF NOT EXISTS idx_sa_approval_request_run
  ON sa_approval_request(run_id, step_id);

CREATE INDEX IF NOT EXISTS idx_sa_approval_request_rollout
  ON sa_approval_request(tenant_id, agent_id, rollout_id, requested_at);

CREATE TABLE IF NOT EXISTS sa_secret_ref (
  pk_id BIGSERIAL PRIMARY KEY,
  secret_ref VARCHAR(128) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  encrypted_value TEXT NOT NULL,
  metadata_json TEXT,
  created_at TIMESTAMP NOT NULL,
  rotated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sa_secret_ref_tenant
  ON sa_secret_ref(tenant_id, created_at);

CREATE TABLE IF NOT EXISTS sa_context_pack (
  pk_id BIGSERIAL PRIMARY KEY,
  context_pack_id VARCHAR(64) NOT NULL UNIQUE,
  run_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64),
  version_id VARCHAR(64),
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  task_goal VARCHAR(1000) NOT NULL,
  budget_tokens INT NOT NULL,
  item_count INT NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_context_pack_run
  ON sa_context_pack(run_id, created_at);

CREATE TABLE IF NOT EXISTS sa_context_item (
  pk_id BIGSERIAL PRIMARY KEY,
  item_id VARCHAR(64) NOT NULL UNIQUE,
  context_pack_id VARCHAR(64) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id VARCHAR(128) NOT NULL,
  content TEXT NOT NULL,
  summary VARCHAR(1000),
  score DOUBLE PRECISION,
  confidence DOUBLE PRECISION,
  sensitivity VARCHAR(32) NOT NULL,
  acl_decision_id VARCHAR(64) NOT NULL,
  citation_json TEXT NOT NULL,
  estimated_tokens INT NOT NULL,
  expires_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_context_item_pack
  ON sa_context_item(context_pack_id);

CREATE TABLE IF NOT EXISTS sa_access_decision_log (
  pk_id BIGSERIAL PRIMARY KEY,
  decision_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  subject_type VARCHAR(32) NOT NULL,
  subject_id VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(128) NOT NULL,
  effect VARCHAR(32) NOT NULL,
  reason_code VARCHAR(128),
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_access_decision_resource
  ON sa_access_decision_log(tenant_id, resource_type, resource_id, created_at);

CREATE TABLE IF NOT EXISTS sa_resource_acl_rule (
  pk_id BIGSERIAL PRIMARY KEY,
  rule_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  scope VARCHAR(32) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(128) NOT NULL,
  subject_type VARCHAR(32) NOT NULL,
  subject_id VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  effect VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  priority INT NOT NULL,
  expires_at TIMESTAMP,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_resource_acl_scope CHECK (scope IN ('EXACT_RESOURCE', 'RESOURCE_TYPE')),
  CONSTRAINT chk_sa_resource_acl_subject_type CHECK (subject_type IN ('USER', 'AGENT', 'USER_DELEGATED_AGENT')),
  CONSTRAINT chk_sa_resource_acl_action CHECK (action IN ('READ', 'WRITE', 'DELETE', 'EXECUTE')),
  CONSTRAINT chk_sa_resource_acl_effect CHECK (effect IN ('ALLOW', 'DENY')),
  CONSTRAINT chk_sa_resource_acl_status CHECK (status IN ('ENABLED', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_sa_resource_acl_lookup
  ON sa_resource_acl_rule(tenant_id, resource_type, resource_id, subject_type, subject_id, action, status);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sa_resource_acl_active_exact_rule
  ON sa_resource_acl_rule(tenant_id, scope, resource_type, resource_id, subject_type, subject_id, action, effect, priority)
  WHERE status = 'ENABLED';

CREATE TABLE IF NOT EXISTS sa_sandbox_session (
  pk_id BIGSERIAL PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  runtime_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_session_run
  ON sa_sandbox_session(tenant_id, run_id, created_at);

CREATE TABLE IF NOT EXISTS sa_sandbox_execution (
  pk_id BIGSERIAL PRIMARY KEY,
  execution_id VARCHAR(64) NOT NULL UNIQUE,
  session_id VARCHAR(64) NOT NULL,
  runtime_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  result_summary VARCHAR(1000),
  reason_code VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_execution_session
  ON sa_sandbox_execution(session_id, created_at);

CREATE TABLE IF NOT EXISTS sa_sandbox_artifact (
  pk_id BIGSERIAL PRIMARY KEY,
  artifact_id VARCHAR(64) NOT NULL UNIQUE,
  session_id VARCHAR(64) NOT NULL,
  execution_id VARCHAR(64) NOT NULL,
  object_uri VARCHAR(1000) NOT NULL,
  media_type VARCHAR(128) NOT NULL,
  scan_status VARCHAR(32) NOT NULL,
  sensitivity VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_sandbox_artifact_session
  ON sa_sandbox_artifact(session_id, created_at);

CREATE TABLE IF NOT EXISTS sa_audit_event (
  pk_id BIGSERIAL PRIMARY KEY,
  audit_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  actor_type VARCHAR(32) NOT NULL,
  actor_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64),
  agent_id VARCHAR(64),
  resource_type VARCHAR(64),
  resource_id VARCHAR(128),
  redacted_payload TEXT NOT NULL,
  occurred_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_audit_event_tenant_time
  ON sa_audit_event(tenant_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_sa_audit_event_run
  ON sa_audit_event(run_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_sa_audit_event_agent
  ON sa_audit_event(agent_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_sa_audit_event_resource
  ON sa_audit_event(tenant_id, resource_type, resource_id, occurred_at);

CREATE TABLE IF NOT EXISTS sa_production_gate_report (
  pk_id BIGSERIAL PRIMARY KEY,
  report_id VARCHAR(64) NOT NULL UNIQUE,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  result_json TEXT NOT NULL,
  checked_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sa_production_gate_report_agent
  ON sa_production_gate_report(agent_id, checked_at);

CREATE TABLE IF NOT EXISTS sa_agent_eval_summary (
  pk_id BIGSERIAL PRIMARY KEY,
  summary_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  eval_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  score DOUBLE PRECISION NOT NULL,
  pass_threshold DOUBLE PRECISION NOT NULL,
  warn_threshold DOUBLE PRECISION NOT NULL,
  case_count INT NOT NULL,
  dataset_ref VARCHAR(256),
  eval_run_ref VARCHAR(256),
  evidence_json TEXT NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_agent_eval_summary_type CHECK (eval_type IN ('SAFETY', 'TRAJECTORY', 'RAG', 'MEMORY', 'TOOL_USE')),
  CONSTRAINT chk_sa_agent_eval_summary_status CHECK (status IN ('PASS', 'WARN', 'FAIL', 'STALE')),
  CONSTRAINT chk_sa_agent_eval_summary_score CHECK (score >= 0),
  CONSTRAINT chk_sa_agent_eval_summary_threshold CHECK (pass_threshold >= warn_threshold AND warn_threshold >= 0),
  CONSTRAINT chk_sa_agent_eval_summary_case_count CHECK (case_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_eval_summary_latest
  ON sa_agent_eval_summary(tenant_id, agent_id, version_id, eval_type, created_at DESC, summary_id DESC);

CREATE TABLE IF NOT EXISTS sa_quota_policy (
  pk_id BIGSERIAL PRIMARY KEY,
  policy_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  scope VARCHAR(32) NOT NULL,
  subject_id VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  token_limit BIGINT,
  call_limit BIGINT,
  cost_limit DOUBLE PRECISION,
  warn_ratio DOUBLE PRECISION NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_quota_policy_scope
    CHECK (scope IN ('TENANT', 'AGENT', 'USER', 'TOOL', 'MODEL', 'RUN')),
  CONSTRAINT chk_sa_quota_policy_status
    CHECK (status IN ('ACTIVE', 'DISABLED')),
  CONSTRAINT chk_sa_quota_policy_limit_required
    CHECK (token_limit IS NOT NULL OR call_limit IS NOT NULL OR cost_limit IS NOT NULL),
  CONSTRAINT chk_sa_quota_policy_token_limit
    CHECK (token_limit IS NULL OR token_limit >= 0),
  CONSTRAINT chk_sa_quota_policy_call_limit
    CHECK (call_limit IS NULL OR call_limit >= 0),
  CONSTRAINT chk_sa_quota_policy_cost_limit
    CHECK (cost_limit IS NULL OR cost_limit >= 0),
  CONSTRAINT chk_sa_quota_policy_warn_ratio
    CHECK (warn_ratio > 0 AND warn_ratio <= 1)
);

CREATE INDEX IF NOT EXISTS idx_sa_quota_policy_active
  ON sa_quota_policy(tenant_id, scope, subject_id, status, updated_at DESC, policy_id DESC);

CREATE TABLE IF NOT EXISTS sa_cost_usage_record (
  pk_id BIGSERIAL PRIMARY KEY,
  usage_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64),
  run_id VARCHAR(64),
  rollout_id VARCHAR(64),
  user_id VARCHAR(64),
  tool_id VARCHAR(128),
  model_id VARCHAR(128),
  source VARCHAR(32) NOT NULL,
  tokens BIGINT NOT NULL,
  calls BIGINT NOT NULL,
  cost DOUBLE PRECISION NOT NULL,
  reason_ref VARCHAR(256),
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_cost_usage_source
    CHECK (source IN ('MODEL', 'TOOL', 'SANDBOX', 'MANUAL_ADJUSTMENT')),
  CONSTRAINT chk_sa_cost_usage_tokens
    CHECK (tokens >= 0),
  CONSTRAINT chk_sa_cost_usage_calls
    CHECK (calls >= 0),
  CONSTRAINT chk_sa_cost_usage_cost
    CHECK (cost >= 0)
);

CREATE INDEX IF NOT EXISTS idx_sa_cost_usage_aggregate
  ON sa_cost_usage_record(tenant_id, agent_id, run_id, created_at);

CREATE INDEX IF NOT EXISTS idx_sa_cost_usage_rollout
  ON sa_cost_usage_record(tenant_id, agent_id, rollout_id, created_at);

CREATE TABLE IF NOT EXISTS sa_agent_version_rollout (
  pk_id BIGSERIAL PRIMARY KEY,
  rollout_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  canary_percent INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  failure_code VARCHAR(64),
  gate_report_id VARCHAR(64),
  started_by VARCHAR(64) NOT NULL,
  started_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP,
  CONSTRAINT chk_sa_agent_version_rollout_percent
    CHECK (canary_percent >= 1 AND canary_percent <= 100),
  CONSTRAINT chk_sa_agent_version_rollout_status
    CHECK (status IN ('CREATED', 'RUNNING', 'PAUSED', 'PROMOTED', 'ROLLED_BACK', 'FAILED')),
  CONSTRAINT chk_sa_agent_version_rollout_failure
    CHECK (
      (status = 'FAILED' AND failure_code IS NOT NULL)
      OR (status <> 'FAILED' AND failure_code IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_version_rollout_latest
  ON sa_agent_version_rollout(tenant_id, agent_id, version_id, updated_at DESC, rollout_id DESC);

CREATE TABLE IF NOT EXISTS sa_enterprise_pilot_readiness_report (
  pk_id BIGSERIAL PRIMARY KEY,
  report_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  check_results_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_enterprise_pilot_readiness_status
    CHECK (status IN ('PASS', 'WARN', 'FAIL'))
);

CREATE INDEX IF NOT EXISTS idx_sa_enterprise_pilot_readiness_latest
  ON sa_enterprise_pilot_readiness_report(tenant_id, agent_id, version_id, created_at DESC, report_id DESC);

CREATE TABLE IF NOT EXISTS sa_agent_handoff (
  pk_id BIGSERIAL PRIMARY KEY,
  handoff_id VARCHAR(64) NOT NULL UNIQUE,
  tenant_id VARCHAR(64) NOT NULL,
  parent_run_id VARCHAR(64) NOT NULL,
  child_run_id VARCHAR(64),
  source_agent_id VARCHAR(64) NOT NULL,
  target_agent_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  failure_code VARCHAR(64),
  handoff_reason VARCHAR(1000),
  input_summary_json TEXT NOT NULL,
  context_summary_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP,
  CONSTRAINT chk_sa_agent_handoff_status
    CHECK (status IN ('CREATED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
  CONSTRAINT chk_sa_agent_handoff_failure_code
    CHECK (failure_code IS NULL OR failure_code IN (
      'DEPTH_LIMIT_EXCEEDED',
      'CYCLE_DETECTED',
      'POLICY_DENIED',
      'TARGET_DISABLED',
      'CONTEXT_DENIED',
      'CHILD_RUN_FAILED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_handoff_parent
  ON sa_agent_handoff(tenant_id, parent_run_id, created_at);

CREATE INDEX IF NOT EXISTS idx_sa_agent_handoff_child
  ON sa_agent_handoff(child_run_id);

CREATE TABLE IF NOT EXISTS sa_agent_run_event_buffer (
  id          BIGSERIAL PRIMARY KEY,
  run_id      VARCHAR(64) NOT NULL,
  event_seq   BIGINT NOT NULL,
  event_id    VARCHAR(64) NOT NULL,
  event_type  VARCHAR(64) NOT NULL,
  step_id     VARCHAR(64),
  payload     JSONB NOT NULL DEFAULT '{}',
  created_at  TIMESTAMP NOT NULL DEFAULT now(),
  UNIQUE (run_id, event_seq)
);

CREATE INDEX IF NOT EXISTS idx_sa_event_buffer_run_seq
  ON sa_agent_run_event_buffer(run_id, event_seq);

CREATE INDEX IF NOT EXISTS idx_sa_event_buffer_created
  ON sa_agent_run_event_buffer(created_at);

CREATE TABLE IF NOT EXISTS sa_durable_task_queue (
  pk_id BIGSERIAL PRIMARY KEY,
  task_id VARCHAR(64) NOT NULL UNIQUE,
  run_id        VARCHAR(64) NOT NULL,
  step_type     VARCHAR(32) NOT NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  worker_id     VARCHAR(64),
  payload_json  TEXT,
  last_error    TEXT,
  retry_at      TIMESTAMP,
  created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
  claimed_at    TIMESTAMP,
  completed_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sa_dtq_status_retry
  ON sa_durable_task_queue(status, retry_at);

CREATE INDEX IF NOT EXISTS idx_sa_dtq_run_id
  ON sa_durable_task_queue(run_id);

-- Source merged from: seahorse-agent-adapter-repository-jdbc\src\main\resources\META-INF\seahorse-agent\sql\eval-dataset-postgresql.sql
CREATE TABLE IF NOT EXISTS sa_eval_candidate (
  pk_id BIGSERIAL PRIMARY KEY,
  candidate_id VARCHAR(64) NOT NULL UNIQUE,
  run_id VARCHAR(64) NOT NULL,
  message_id VARCHAR(64),
  user_query TEXT NOT NULL,
  assistant_response TEXT NOT NULL,
  feedback_reason VARCHAR(1000),
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  reviewer_note VARCHAR(1000),
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  decided_at TIMESTAMP,
  CONSTRAINT chk_sa_eval_candidate_status
    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_sa_eval_candidate_status
  ON sa_eval_candidate(status, created_at);

CREATE TABLE IF NOT EXISTS sa_eval_sample (
  pk_id BIGSERIAL PRIMARY KEY,
  sample_id VARCHAR(64) NOT NULL UNIQUE,
  dataset_id VARCHAR(64) NOT NULL,
  user_query TEXT NOT NULL,
  expected_response TEXT NOT NULL,
  feedback_reason VARCHAR(1000),
  source_run_id VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sa_eval_sample_dataset
  ON sa_eval_sample(dataset_id, created_at);

-- Source merged from: seahorse-agent-adapter-repository-jdbc\src\main\resources\META-INF\seahorse-agent\sql\metadata-governance-postgresql.sql
ALTER TABLE t_knowledge_document
ADD COLUMN IF NOT EXISTS metadata_json JSONB;

ALTER TABLE t_knowledge_chunk
ADD COLUMN IF NOT EXISTS metadata_json JSONB;

ALTER TABLE t_knowledge_chunk
ADD COLUMN IF NOT EXISTS search_text TSVECTOR;


CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_search_text
ON t_knowledge_chunk USING GIN (search_text);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_kb_doc_alive
ON t_knowledge_chunk (kb_id, doc_id)
WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS t_metadata_field_schema (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(32) NOT NULL UNIQUE,
    tenant_id         VARCHAR(64) NOT NULL,
    kb_id             VARCHAR(64),
    field_key         VARCHAR(128) NOT NULL,
    display_name      VARCHAR(128),
    value_type        VARCHAR(32) NOT NULL,
    allowed_ops       JSONB NOT NULL,
    required          SMALLINT NOT NULL DEFAULT 0,
    filterable        SMALLINT NOT NULL DEFAULT 0,
    sortable          SMALLINT NOT NULL DEFAULT 0,
    facetable         SMALLINT NOT NULL DEFAULT 0,
    indexed           SMALLINT NOT NULL DEFAULT 0,
    index_policy      VARCHAR(32) NOT NULL DEFAULT 'NONE',
    min_confidence    NUMERIC(5,4) NOT NULL DEFAULT 0.8000,
    trusted_sources   JSONB,
    extraction_hints  JSONB,
    backend_mapping   JSONB,
    schema_version    INTEGER NOT NULL DEFAULT 1,
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT NOT NULL DEFAULT 0
);

ALTER TABLE t_metadata_field_schema
ADD COLUMN IF NOT EXISTS last_sync_backend VARCHAR(32);

ALTER TABLE t_metadata_field_schema
ADD COLUMN IF NOT EXISTS last_sync_action VARCHAR(32);

ALTER TABLE t_metadata_field_schema
ADD COLUMN IF NOT EXISTS last_sync_outcome VARCHAR(32);

ALTER TABLE t_metadata_field_schema
ADD COLUMN IF NOT EXISTS last_sync_error_type VARCHAR(64);

ALTER TABLE t_metadata_field_schema
ADD COLUMN IF NOT EXISTS last_sync_error_message VARCHAR(1024);

ALTER TABLE t_metadata_field_schema
ADD COLUMN IF NOT EXISTS last_sync_time TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uk_metadata_schema_field
ON t_metadata_field_schema (tenant_id, kb_id, field_key)
WHERE deleted = 0;



CREATE TABLE IF NOT EXISTS t_metadata_schema_usage_log (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    request_id        VARCHAR(64) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    kb_id             VARCHAR(64) NOT NULL,
    schema_version    INTEGER NOT NULL DEFAULT 1,
    field_key         VARCHAR(128) NOT NULL,
    event_type        VARCHAR(32) NOT NULL,
    guard_only        SMALLINT NOT NULL DEFAULT 0,
    reject_reason     VARCHAR(64),
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_metadata_schema_usage_scope
ON t_metadata_schema_usage_log (tenant_id, kb_id, schema_version, event_type, create_time);

CREATE INDEX IF NOT EXISTS idx_metadata_schema_usage_request
ON t_metadata_schema_usage_log (request_id);


CREATE TABLE IF NOT EXISTS t_metadata_extraction_job (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    tenant_id           VARCHAR(64) NOT NULL,
    kb_id               VARCHAR(64) NOT NULL,
    pipeline_id         VARCHAR(64),
    status              VARCHAR(32) NOT NULL,
    current_page        BIGINT NOT NULL DEFAULT 1,
    checkpoint_json     JSONB,
    batch_size          INTEGER NOT NULL DEFAULT 50,
    processed_count     INTEGER NOT NULL DEFAULT 0,
    success_count       INTEGER NOT NULL DEFAULT 0,
    failed_count        INTEGER NOT NULL DEFAULT 0,
    skipped_count       INTEGER NOT NULL DEFAULT 0,
    review_count        INTEGER NOT NULL DEFAULT 0,
    quarantine_count    INTEGER NOT NULL DEFAULT 0,
    failure_summary     JSONB,
    operator            VARCHAR(64),
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_metadata_extraction_job_scope
ON t_metadata_extraction_job (tenant_id, kb_id, status, update_time);


CREATE TABLE IF NOT EXISTS t_metadata_extraction_result (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    tenant_id             VARCHAR(64) NOT NULL,
    kb_id                 VARCHAR(64),
    doc_id                VARCHAR(64) NOT NULL,
    job_id                VARCHAR(64),
    schema_version        INTEGER NOT NULL DEFAULT 1,
    extractor_version     VARCHAR(64),
    status                VARCHAR(32) NOT NULL,
    normalized_metadata   JSONB,
    raw_candidates        JSONB,
    field_quality         JSONB,
    validation_issues     JSONB,
    approved_metadata     JSONB,
    approved_by           VARCHAR(64),
    approved_time         TIMESTAMP,
    create_time           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE IF NOT EXISTS t_metadata_review_item (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    tenant_id            VARCHAR(64) NOT NULL,
    kb_id                VARCHAR(64),
    doc_id               VARCHAR(64) NOT NULL,
    result_id            VARCHAR(64),
    review_status        VARCHAR(32) NOT NULL,
    priority             INTEGER NOT NULL DEFAULT 0,
    reason_code          VARCHAR(64),
    reason_message       VARCHAR(512),
    suggested_metadata   JSONB,
    review_context       JSONB,
    corrected_metadata   JSONB,
    reviewer_id          VARCHAR(64),
    review_comment       VARCHAR(1024),
    create_time          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE t_metadata_review_item
ADD COLUMN IF NOT EXISTS review_context JSONB;


CREATE INDEX IF NOT EXISTS idx_metadata_review_status
ON t_metadata_review_item (tenant_id, kb_id, review_status, priority, update_time);

CREATE INDEX IF NOT EXISTS idx_metadata_review_doc
ON t_metadata_review_item (doc_id);

CREATE TABLE IF NOT EXISTS t_metadata_review_audit (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    review_item_id       VARCHAR(64) NOT NULL,
    tenant_id            VARCHAR(64) NOT NULL,
    kb_id                VARCHAR(64),
    doc_id               VARCHAR(64) NOT NULL,
    result_id            VARCHAR(64),
    from_status          VARCHAR(32),
    to_status            VARCHAR(32) NOT NULL,
    reviewer_id          VARCHAR(64),
    review_comment       VARCHAR(1024),
    previous_metadata    JSONB,
    updated_metadata     JSONB,
    decision_metadata    JSONB,
    create_time          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE t_metadata_review_audit
ADD COLUMN IF NOT EXISTS previous_metadata JSONB;

ALTER TABLE t_metadata_review_audit
ADD COLUMN IF NOT EXISTS updated_metadata JSONB;


CREATE INDEX IF NOT EXISTS idx_metadata_review_audit_item
ON t_metadata_review_audit (review_item_id, create_time);

CREATE INDEX IF NOT EXISTS idx_metadata_review_audit_doc
ON t_metadata_review_audit (tenant_id, kb_id, doc_id, create_time);

CREATE TABLE IF NOT EXISTS t_metadata_quarantine_item (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    tenant_id         VARCHAR(64) NOT NULL,
    kb_id             VARCHAR(64),
    doc_id            VARCHAR(64),
    job_id            VARCHAR(64),
    stage             VARCHAR(32) NOT NULL,
    reason_code       VARCHAR(64),
    reason_message    VARCHAR(512),
    source_snapshot   JSONB,
    retry_count       INTEGER NOT NULL DEFAULT 0,
    next_retry_time   TIMESTAMP,
    resolved          SMALLINT NOT NULL DEFAULT 0,
    resolved_by       VARCHAR(64),
    resolved_time     TIMESTAMP,
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


CREATE INDEX IF NOT EXISTS idx_metadata_quarantine_status
ON t_metadata_quarantine_item (tenant_id, kb_id, resolved, next_retry_time);

CREATE INDEX IF NOT EXISTS idx_metadata_quarantine_doc
ON t_metadata_quarantine_item (doc_id);

CREATE INDEX IF NOT EXISTS idx_metadata_quarantine_job
ON t_metadata_quarantine_item (job_id, resolved);

CREATE TABLE IF NOT EXISTS t_metadata_dictionary_item (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    tenant_id         VARCHAR(64) NOT NULL,
    dict_code         VARCHAR(128) NOT NULL,
    raw_value         VARCHAR(256) NOT NULL,
    canonical_value   VARCHAR(256) NOT NULL,
    display_name      VARCHAR(256),
    enabled           SMALLINT NOT NULL DEFAULT 1,
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_metadata_dictionary_lookup
ON t_metadata_dictionary_item (tenant_id, dict_code, raw_value, enabled);


-- Source merged from: seahorse-agent-adapter-repository-jdbc\src\main\resources\META-INF\seahorse-agent\sql\retrieval-governance-postgresql.sql
CREATE TABLE IF NOT EXISTS t_retrieval_strategy_template (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    kb_id           VARCHAR(64),
    template_key    VARCHAR(128) NOT NULL,
    display_name    VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    options_json    JSONB NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    recommended     SMALLINT NOT NULL DEFAULT 0,
    enabled         SMALLINT NOT NULL DEFAULT 1,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_retrieval_strategy_template_scope
ON t_retrieval_strategy_template (COALESCE(kb_id, ''), template_key)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_retrieval_strategy_template_scope
ON t_retrieval_strategy_template (kb_id, enabled, deleted, recommended, sort_order);

CREATE UNIQUE INDEX IF NOT EXISTS uk_retrieval_strategy_template_recommended
ON t_retrieval_strategy_template (COALESCE(kb_id, ''))
WHERE deleted = 0 AND recommended = 1;


CREATE TABLE IF NOT EXISTS t_retrieval_evaluation_dataset (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    kb_id VARCHAR(64) NOT NULL,
    dataset_name VARCHAR(128) NOT NULL,
    description VARCHAR(1024),
    cases_json JSONB NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_retrieval_evaluation_dataset_kb
ON t_retrieval_evaluation_dataset (kb_id, enabled, deleted, update_time);


CREATE TABLE IF NOT EXISTS t_retrieval_evaluation_run (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    kb_id VARCHAR(64) NOT NULL,
    dataset_id VARCHAR(64) NOT NULL,
    strategy_name VARCHAR(128) NOT NULL,
    top_k INTEGER NOT NULL DEFAULT 0,
    case_count INTEGER NOT NULL DEFAULT 0,
    evaluable_case_count INTEGER NOT NULL DEFAULT 0,
    recall_at_k DOUBLE PRECISION NOT NULL DEFAULT 0,
    mrr DOUBLE PRECISION NOT NULL DEFAULT 0,
    ndcg_at_k DOUBLE PRECISION NOT NULL DEFAULT 0,
    empty_recall_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
    avg_latency_ms DOUBLE PRECISION NOT NULL DEFAULT 0,
    p95_latency_ms DOUBLE PRECISION NOT NULL DEFAULT 0,
    report_json JSONB NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_retrieval_evaluation_run_dataset
ON t_retrieval_evaluation_run (kb_id, dataset_id, create_time);


CREATE TABLE IF NOT EXISTS t_retrieval_evaluation_comparison (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    kb_id VARCHAR(64) NOT NULL,
    dataset_id VARCHAR(64) NOT NULL,
    baseline_strategy_name VARCHAR(128) NOT NULL,
    winner_strategy_name VARCHAR(128) NOT NULL,
    strategy_count INTEGER NOT NULL DEFAULT 0,
    case_count INTEGER NOT NULL DEFAULT 0,
    report_json JSONB NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_retrieval_evaluation_comparison_dataset
ON t_retrieval_evaluation_comparison (kb_id, dataset_id, create_time);

-- ============================================
-- AI Model Config
-- ============================================

CREATE TABLE IF NOT EXISTS sa_ai_model_config (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT NOT NULL,
    config_type VARCHAR(32) NOT NULL,
    is_encrypted SMALLINT NOT NULL DEFAULT 0,
    description VARCHAR(512),
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_sa_ai_model_config_type
        CHECK (config_type IN ('STRING', 'INTEGER', 'BOOLEAN', 'JSON')),
    CONSTRAINT uk_sa_ai_model_config_tenant_key UNIQUE (tenant_id, config_key)
);
CREATE INDEX IF NOT EXISTS idx_sa_ai_model_config_key
    ON sa_ai_model_config(tenant_id, config_key, deleted);

-- PostgreSQL Initial Data for Seahorse Agent
-- NOTE: Admin user seed moved to end of file (after SaaS MVP extensions add tenant_id/email columns)

-- ============================================
-- SaaS MVP Extensions (V2-V13)
-- Merged from resources/database/migrations/
-- ============================================

-- ---- V2: Multi-Tenancy ----
-- Add tenant_id to core tables
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_conversation ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_conversation_summary ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_message ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE sa_conversation_attachment ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_message_feedback ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_knowledge_base ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_knowledge_document ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_knowledge_chunk ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_knowledge_document_chunk_log ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_knowledge_vector ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_intent_node ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_query_term_mapping ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_rag_trace_run ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_rag_trace_node ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE t_sample_question ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

-- Tenant indexes
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

-- Enable Row Level Security
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

-- Force RLS for table owners
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

-- RLS policies
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

-- ---- V3: User Trial ----
-- Extend t_user with registration fields
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS email       VARCHAR(128);
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS status      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS external_id VARCHAR(128);
CREATE UNIQUE INDEX IF NOT EXISTS idx_t_user_email ON t_user (email) WHERE email IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_t_user_external_id ON t_user (external_id) WHERE external_id IS NOT NULL;

-- User trial table
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
CREATE INDEX IF NOT EXISTS idx_trial_user_id   ON t_user_trial (user_id);
CREATE INDEX IF NOT EXISTS idx_trial_tenant_id ON t_user_trial (tenant_id);
CREATE INDEX IF NOT EXISTS idx_trial_status    ON t_user_trial (status);

-- ---- V4: Secret & Credential Enhancement ----
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS name VARCHAR(128);
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS secret_type VARCHAR(32);
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS masked_hint VARCHAR(64);
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS rotated_by VARCHAR(64);
ALTER TABLE sa_secret_ref ADD COLUMN IF NOT EXISTS rotated_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_sa_secret_ref_tenant_status ON sa_secret_ref (tenant_id, status);

ALTER TABLE sa_connector_credential_binding ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
ALTER TABLE sa_connector_credential_binding ADD COLUMN IF NOT EXISTS last_verified_at TIMESTAMP;
ALTER TABLE sa_connector_credential_binding ADD COLUMN IF NOT EXISTS verify_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED';

-- ---- V5: Billing Tables ----
-- Subscription plan definitions
CREATE TABLE IF NOT EXISTS sa_subscription_plan (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    code                VARCHAR(32)    NOT NULL,
    name                VARCHAR(128)   NOT NULL,
    description         VARCHAR(512),
    monthly_price       DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    yearly_price        DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    token_limit         BIGINT         NOT NULL DEFAULT 0,
    storage_limit_bytes BIGINT         NOT NULL DEFAULT 0,
    concurrency_limit   INTEGER        NOT NULL DEFAULT 1,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_plan_code ON sa_subscription_plan (code);

-- Seed default plans
INSERT INTO sa_subscription_plan (id, code, name, description, monthly_price, yearly_price, token_limit, storage_limit_bytes, concurrency_limit, active)
VALUES
    (1, 'FREE_TRIAL', 'Free Trial', 'Free trial with limited quota', 0.00, 0.00, 100000, 1073741824, 1, TRUE),
    (2, 'BASIC', 'Basic', 'Basic plan for small teams', 29.99, 299.99, 1000000, 10737418240, 5, TRUE),
    (3, 'PRO', 'Pro', 'Professional plan for growing teams', 99.99, 999.99, 10000000, 107374182400, 20, TRUE),
    (4, 'ENTERPRISE', 'Enterprise', 'Enterprise plan with unlimited quota', 499.99, 4999.99, 100000000, 1099511627776, 100, TRUE)
ON CONFLICT DO NOTHING;

-- Tenant subscriptions
CREATE TABLE IF NOT EXISTS sa_subscription (
    id                  BIGINT         NOT NULL PRIMARY KEY,
    tenant_id           VARCHAR(64)    NOT NULL,
    plan_code           VARCHAR(32)    NOT NULL,
    status              VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',
    started_at          TIMESTAMP,
    expires_at          TIMESTAMP,
    token_limit         BIGINT         NOT NULL DEFAULT 0,
    storage_limit_bytes BIGINT         NOT NULL DEFAULT 0,
    concurrency_limit   INTEGER        NOT NULL DEFAULT 1,
    created_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_subscription_tenant ON sa_subscription (tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscription_status ON sa_subscription (status);
CREATE INDEX IF NOT EXISTS idx_subscription_tenant_status ON sa_subscription (tenant_id, status);

-- Payment orders
CREATE TABLE IF NOT EXISTS sa_payment_order (
    id                BIGINT         NOT NULL PRIMARY KEY,
    order_no          VARCHAR(64)    NOT NULL,
    tenant_id         VARCHAR(64)    NOT NULL,
    plan_code         VARCHAR(32)    NOT NULL,
    payment_channel   VARCHAR(32)    NOT NULL,
    status            VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    amount            DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    channel_trade_no  VARCHAR(128),
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    paid_at           TIMESTAMP,
    updated_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_order_no ON sa_payment_order (order_no);
CREATE INDEX IF NOT EXISTS idx_payment_order_tenant ON sa_payment_order (tenant_id);
CREATE INDEX IF NOT EXISTS idx_payment_order_status ON sa_payment_order (status);
CREATE INDEX IF NOT EXISTS idx_payment_order_channel_trade ON sa_payment_order (channel_trade_no) WHERE channel_trade_no IS NOT NULL;

-- Payment callback log (idempotency)
CREATE TABLE IF NOT EXISTS sa_payment_callback_log (
    id                BIGINT         NOT NULL PRIMARY KEY,
    channel           VARCHAR(32)    NOT NULL,
    channel_trade_no  VARCHAR(128)   NOT NULL,
    order_no          VARCHAR(64)    NOT NULL,
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_callback_channel_trade ON sa_payment_callback_log (channel, channel_trade_no);
CREATE INDEX IF NOT EXISTS idx_callback_order_no ON sa_payment_callback_log (order_no);

-- Usage rollup
CREATE TABLE IF NOT EXISTS sa_usage_rollup (
    id                BIGINT         NOT NULL PRIMARY KEY,
    tenant_id         VARCHAR(64)    NOT NULL,
    period            VARCHAR(7)     NOT NULL,
    token_used        BIGINT         NOT NULL DEFAULT 0,
    call_count        BIGINT         NOT NULL DEFAULT 0,
    storage_bytes     BIGINT         NOT NULL DEFAULT 0,
    cost_amount       DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_usage_rollup_tenant_period ON sa_usage_rollup (tenant_id, period);
CREATE INDEX IF NOT EXISTS idx_usage_rollup_period ON sa_usage_rollup (period);

-- Bills
CREATE TABLE IF NOT EXISTS sa_bill (
    id                BIGINT         NOT NULL PRIMARY KEY,
    bill_no           VARCHAR(64)    NOT NULL,
    tenant_id         VARCHAR(64)    NOT NULL,
    bill_period       VARCHAR(7)     NOT NULL,
    total_amount      DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    status            VARCHAR(32)    NOT NULL DEFAULT 'GENERATED',
    generated_at      TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    due_at            TIMESTAMP,
    updated_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_bill_no ON sa_bill (bill_no);
CREATE INDEX IF NOT EXISTS idx_bill_tenant ON sa_bill (tenant_id);
CREATE INDEX IF NOT EXISTS idx_bill_period ON sa_bill (bill_period);
CREATE INDEX IF NOT EXISTS idx_bill_tenant_period ON sa_bill (tenant_id, bill_period);
CREATE INDEX IF NOT EXISTS idx_bill_status ON sa_bill (status);

-- Bill line items
CREATE TABLE IF NOT EXISTS sa_bill_line_item (
    id                BIGINT         NOT NULL PRIMARY KEY,
    bill_id           BIGINT         NOT NULL,
    item_type         VARCHAR(32)    NOT NULL,
    description       VARCHAR(256),
    amount            DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    quantity          BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_bill_line_item_bill ON sa_bill_line_item (bill_id);
CREATE INDEX IF NOT EXISTS idx_bill_line_item_type ON sa_bill_line_item (item_type);

-- ---- V6: Query Rewrite Log ----
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

-- ---- V7: Workflow Execution Steps ----
CREATE TABLE IF NOT EXISTS t_agent_execution_steps (
    step_id        VARCHAR(64)    NOT NULL PRIMARY KEY,
    run_id         VARCHAR(64)    NOT NULL,
    step_type      VARCHAR(32)    NOT NULL,
    status         VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    started_at     TIMESTAMP,
    completed_at   TIMESTAMP,
    duration_ms    BIGINT,
    result_data    TEXT,
    position_x     INTEGER,
    position_y     INTEGER,
    created_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_exec_steps_run ON t_agent_execution_steps (run_id);
CREATE INDEX IF NOT EXISTS idx_exec_steps_status ON t_agent_execution_steps (status);
CREATE INDEX IF NOT EXISTS idx_exec_steps_run_started ON t_agent_execution_steps (run_id, started_at);

CREATE TABLE IF NOT EXISTS t_agent_execution_step_edges (
    id               BIGSERIAL      PRIMARY KEY,
    source_step_id   VARCHAR(64)    NOT NULL,
    target_step_id   VARCHAR(64)    NOT NULL,
    edge_type        VARCHAR(32)    NOT NULL DEFAULT 'SEQUENTIAL',
    created_at       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_exec_edges_source ON t_agent_execution_step_edges (source_step_id);
CREATE INDEX IF NOT EXISTS idx_exec_edges_target ON t_agent_execution_step_edges (target_step_id);
CREATE INDEX IF NOT EXISTS idx_exec_edges_source_target ON t_agent_execution_step_edges (source_step_id, target_step_id);

-- ---- V8: Knowledge Base Enhancement ----
-- Knowledge base version snapshots
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

-- Knowledge base permission control
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

-- Knowledge base external sharing
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

-- Knowledge base share access log
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

-- ---- V9: Agent Marketplace ----
-- Add marketplace columns to agent definition
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS visibility VARCHAR(32) DEFAULT 'PRIVATE';
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS category VARCHAR(64);
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS tags VARCHAR(512);
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS pricing_type VARCHAR(32) DEFAULT 'FREE';
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS price DECIMAL(10,2) DEFAULT 0.00;
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS review_status VARCHAR(32) DEFAULT 'NOT_SUBMITTED';
CREATE INDEX IF NOT EXISTS idx_agent_visibility ON sa_agent_definition (visibility);
CREATE INDEX IF NOT EXISTS idx_agent_category ON sa_agent_definition (category);
CREATE INDEX IF NOT EXISTS idx_agent_review_status ON sa_agent_definition (review_status);

-- Agent publish review
CREATE TABLE IF NOT EXISTS sa_agent_publish_review (
    id              BIGINT         NOT NULL PRIMARY KEY,
    agent_id        VARCHAR(64)    NOT NULL,
    tenant_id       VARCHAR(64)    NOT NULL,
    submitted_by    VARCHAR(128)   NOT NULL,
    status          VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    review_comment  VARCHAR(1024),
    reviewed_by     VARCHAR(128),
    submitted_at    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    reviewed_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_review_agent ON sa_agent_publish_review (agent_id);
CREATE INDEX IF NOT EXISTS idx_review_tenant ON sa_agent_publish_review (tenant_id);
CREATE INDEX IF NOT EXISTS idx_review_status ON sa_agent_publish_review (status);
CREATE INDEX IF NOT EXISTS idx_review_submitted ON sa_agent_publish_review (submitted_at);

-- Agent subscription
CREATE TABLE IF NOT EXISTS sa_agent_subscription (
    id              BIGINT         NOT NULL PRIMARY KEY,
    agent_id        VARCHAR(64)    NOT NULL,
    user_id         BIGINT         NOT NULL,
    tenant_id       VARCHAR(64)    NOT NULL,
    subscribed_at   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    active          BOOLEAN        NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_subscription_agent ON sa_agent_subscription (agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_subscription_user ON sa_agent_subscription (user_id);
CREATE INDEX IF NOT EXISTS idx_agent_subscription_tenant ON sa_agent_subscription (tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_subscription_unique ON sa_agent_subscription (agent_id, user_id);
CREATE INDEX IF NOT EXISTS idx_agent_subscription_active ON sa_agent_subscription (active);

-- Agent rating
CREATE TABLE IF NOT EXISTS sa_agent_rating (
    id          BIGINT         NOT NULL PRIMARY KEY,
    agent_id    VARCHAR(64)    NOT NULL,
    user_id     BIGINT         NOT NULL,
    rating      INTEGER        NOT NULL,
    comment     VARCHAR(1024),
    created_at  TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_rating_agent ON sa_agent_rating (agent_id);
CREATE INDEX IF NOT EXISTS idx_rating_user ON sa_agent_rating (user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_rating_unique ON sa_agent_rating (agent_id, user_id);
CREATE INDEX IF NOT EXISTS idx_rating_value ON sa_agent_rating (rating);

-- Agent rating summary (cached aggregation)
CREATE TABLE IF NOT EXISTS sa_agent_rating_summary (
    agent_id        VARCHAR(64)    NOT NULL PRIMARY KEY,
    average_rating  DECIMAL(3,2)   NOT NULL DEFAULT 0.00,
    rating_count    INTEGER        NOT NULL DEFAULT 0,
    rating_1_count  INTEGER        NOT NULL DEFAULT 0,
    rating_2_count  INTEGER        NOT NULL DEFAULT 0,
    rating_3_count  INTEGER        NOT NULL DEFAULT 0,
    rating_4_count  INTEGER        NOT NULL DEFAULT 0,
    rating_5_count  INTEGER        NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

-- Agent popularity score
CREATE TABLE IF NOT EXISTS sa_agent_popularity (
    agent_id            VARCHAR(64)    NOT NULL PRIMARY KEY,
    subscription_count  BIGINT         NOT NULL DEFAULT 0,
    average_rating      DECIMAL(3,2)   NOT NULL DEFAULT 0.00,
    rating_count        INTEGER        NOT NULL DEFAULT 0,
    activity_score      DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    popularity_score    DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    rank_position       INTEGER,
    updated_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_popularity_score ON sa_agent_popularity (popularity_score DESC);
CREATE INDEX IF NOT EXISTS idx_popularity_rank ON sa_agent_popularity (rank_position);

-- ---- V10: Audit Log ----
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
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant ON sa_audit_log (tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON sa_audit_log (action);
CREATE INDEX IF NOT EXISTS idx_audit_log_resource_type ON sa_audit_log (resource_type);
CREATE INDEX IF NOT EXISTS idx_audit_log_operator ON sa_audit_log (operator);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON sa_audit_log (created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_action ON sa_audit_log (tenant_id, action);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_created ON sa_audit_log (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_action_created ON sa_audit_log (action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_asc ON sa_audit_log (created_at ASC);

-- ---- V11: KB Retrieval Config ----
ALTER TABLE t_knowledge_base ADD COLUMN IF NOT EXISTS retrieval_config JSONB;
COMMENT ON COLUMN t_knowledge_base.retrieval_config IS
    '检索策略配置（JSONB），包含 top-k、相似度阈值、重排序策略等参数';

-- ---- V12: Login History ----
CREATE TABLE IF NOT EXISTS t_login_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    login_type VARCHAR(32) NOT NULL DEFAULT 'PASSWORD',
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    device_info VARCHAR(256),
    status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    failure_reason VARCHAR(256),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_login_history_user ON t_login_history (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_history_tenant ON t_login_history (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_history_ip ON t_login_history (ip_address, created_at DESC);

-- ---- V13: Revenue Share ----
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

-- ---- V14: Compensation, idempotency, export, optimistic locking ----
CREATE TABLE IF NOT EXISTS sa_compensation_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    operation_type VARCHAR(64) NOT NULL,
    operation_id VARCHAR(128) NOT NULL,
    payload JSONB,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
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

CREATE TABLE IF NOT EXISTS sa_idempotency_key (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    idempotency_key VARCHAR(128) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    response_body JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_idempotency_expires ON sa_idempotency_key (expires_at);

ALTER TABLE t_user ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
ALTER TABLE t_knowledge_base ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
ALTER TABLE t_conversation ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS sa_export_task (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT NOT NULL,
    export_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
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
CREATE INDEX IF NOT EXISTS idx_export_task_user ON sa_export_task (tenant_id, user_id, created_at DESC);

-- ---- V15: Notification center ----
CREATE TABLE IF NOT EXISTS sa_notification (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    link VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notification_user ON sa_notification (tenant_id, user_id, is_read, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_expires ON sa_notification (expires_at) WHERE expires_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS sa_notification_template (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    template_code VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'IN_APP',
    title_template VARCHAR(255) NOT NULL,
    body_template TEXT NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'zh_CN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_template UNIQUE (template_code, channel, locale)
);

CREATE TABLE IF NOT EXISTS sa_notification_preference (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT NOT NULL,
    channel VARCHAR(16) NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_pref UNIQUE (user_id, channel, notification_type)
);

CREATE TABLE IF NOT EXISTS sa_webhook (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    name VARCHAR(128) NOT NULL,
    url VARCHAR(512) NOT NULL,
    secret VARCHAR(256) NOT NULL,
    events TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_webhook_tenant ON sa_webhook (tenant_id, is_active);

CREATE TABLE IF NOT EXISTS sa_webhook_log (
    id BIGSERIAL PRIMARY KEY,
    webhook_id BIGINT NOT NULL REFERENCES sa_webhook(id),
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    response_status INT,
    response_body TEXT,
    attempt INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    next_retry_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_webhook_log_status ON sa_webhook_log (status, next_retry_at);

-- ---- V16: Performance indexes ----
CREATE INDEX IF NOT EXISTS idx_t_conversation_tenant_user_time
    ON t_conversation (tenant_id, user_id, last_time DESC)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_message_conv_user_time
    ON t_message (conversation_id, user_id, create_time ASC)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_kb_tenant_created
    ON t_knowledge_base (tenant_id, create_time DESC)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_document_kb_status
    ON t_knowledge_document (kb_id, status, create_time DESC)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_agent_def_tenant_active
    ON sa_agent_definition (tenant_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_run_user_status
    ON sa_agent_run (tenant_id, user_id, status, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_time
    ON sa_audit_log (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_bill_tenant_period
    ON sa_bill (tenant_id, bill_period DESC);
CREATE INDEX IF NOT EXISTS idx_cost_usage_tenant_time
    ON sa_cost_usage_record (tenant_id, created_at DESC);

-- ============================================
-- Seed Data (after all schema extensions)
-- ============================================

-- Default admin user (includes tenant_id and email from V2/V3).
-- The password must satisfy the web login validation rule (6-128 characters).
INSERT INTO t_user (id, username, password, role, avatar, tenant_id, email, status, create_time, update_time, deleted)
VALUES (2001523723396308993, 'admin', 'admin123', 'admin', 'https://avatars.githubusercontent.com/u/37446017?v=4',
        'default', 'admin@seahorse.local', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO NOTHING;

SELECT set_config('app.current_tenant_id', 'default', false);

INSERT INTO sa_agent_definition (
    agent_id,
    tenant_id,
    name,
    description,
    owner_user_id,
    owner_team,
    agent_type,
    base_agent_id,
    status,
    risk_level,
    latest_version_id,
    created_at,
    updated_at
)
VALUES (
    'github-visual-project-intro-agent',
    'default',
    $agent_name$GitHub 项目图文介绍生成 Agent$agent_name$,
    $agent_description$基于用户提供的 GitHub 链接读取项目文档和代码，生成包含架构设计、架构图、流程图、核心逻辑、重点特性的图文并茂项目介绍。$agent_description$,
    'system',
    'platform',
    'ASSISTANT',
    NULL,
    'PUBLISHED',
    'HIGH',
    'github-visual-project-intro-agent-v1',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;

INSERT INTO sa_agent_version (
    version_id,
    agent_id,
    version_no,
    instructions,
    tool_set_json,
    model_config_json,
    memory_config_json,
    guardrail_config_json,
    skill_set_json,
    published_by,
    published_at,
    change_summary
)
VALUES (
    'github-visual-project-intro-agent-v1',
    'github-visual-project-intro-agent',
    1,
    $instructions$
你是“GitHub 项目图文介绍生成 Agent”。用户会提供一个 GitHub 仓库链接，你必须读取该项目的公开文档和代码，并生成中文 Markdown 项目介绍。

硬性执行契约 (GitHub visual project intro execution contract)：
1. 先调用 github_repository_reader 读取仓库 README、docs、关键源码和配置文件。第一次调用成功返回 files 后，必须停止继续读取仓库并进入下一阶段。
2. 如果第一次仓库读取缺少关键材料，最多允许第二次调用 github_repository_reader，但第二次必须使用不同参数，例如提高 maxFiles、提高 maxCharsPerFile 或指定 branch。禁止连续重复使用相同参数调用 github_repository_reader。
3. 成功读取仓库后必须停止使用 github_repository_reader，不要为了“继续了解项目”反复调用它。
4. 读取仓库后，必须按顺序推进：web_fetch -> chart_visualization -> newsletter_generation -> ppt_generation -> frontend_design。
5. web_fetch 用于获取项目官网、README 中的公开文档链接或 GitHub 项目页补充信息；不要把 web_fetch 跳过。
6. chart_visualization 至少生成一次架构图或流程图草案，优先输出 Mermaid。
7. newsletter_generation 至少生成一次中文长文 Markdown 草稿，材料必须来自仓库读取和 web_fetch 结果。
8. ppt_generation 至少生成一次演示文稿结构，包含每页标题、要点、讲稿和视觉提示。
9. frontend_design 至少生成一次图文版式草案，用于承载项目介绍内容。
10. 每个硬性工具至少成功调用一次后，才能输出最终 Markdown。硬性工具包括 github_repository_reader、web_fetch、chart_visualization、newsletter_generation、ppt_generation、frontend_design。
11. 如果任一硬性工具失败，先用修正后的参数重试一次；仍失败时继续保留已完成材料，并在最终回答末尾简要说明失败原因。不要因为某个生成工具失败而重新读取仓库。

内容质量要求：
1. 必须基于读取到的文件证据总结，不要编造仓库不存在的模块、架构或能力。
2. 输出必须是中文 Markdown，包含：项目概览、架构设计、架构图、流程图、核心逻辑、重点特性、关键文件证据、Mermaid 图表说明、生成稿件和版式产物摘要。
3. 最终 Markdown 必须使用固定大纲并逐节输出，至少包含这些二级标题，且不要合并或改名：## 一、项目概览、## 二、架构设计、## 三、架构图、## 四、流程图、## 五、核心逻辑、## 六、重点特性、## 七、关键文件证据表、## 八、Mermaid 图表说明、## 九、生成稿件和版式产物摘要、## 十、总结。
4. “2.1 整体架构分层”必须输出标准 Mermaid flowchart，禁止使用 ASCII 文本框图；必须包含独立的 ```mermaid 代码块，并能被 Mermaid 渲染器直接渲染。
5. “流程图”必须是独立章节，不能合并到“核心逻辑”；该章节至少包含一个 Mermaid sequenceDiagram 或 flowchart，描述用户请求到仓库读取、工具生成、最终 Markdown 输出的流程，或描述该项目的核心执行流程。
6. 架构图和流程图必须优先用 Mermaid 代码块表达，不要调用图片生成工具，也不要要求图片 URL。
7. 关键文件证据需要列出文件路径和对应用途，说明结论来自哪些 README、docs 或源码文件。
8. 第八章必须解释每个 Mermaid 图表达的架构、流程或模块关系，且图表应能被前端 Mermaid 渲染器直接渲染。
9. 第九章必须总结 newsletter_generation、ppt_generation、frontend_design 的实际产物，至少包含“长文稿件摘要”“演示文稿摘要”“Web 版式预览摘要”三个小节；不得只写章节标题或空泛一句话。
10. 在第九章后输出一个 HTML 预览 artifact，格式必须严格为：<artifact language="html" title="项目介绍 Web 预览.html"> 换行，完整 HTML 片段，换行 </artifact>。该 artifact 用于 Web 端完整阅读预览，不替代最终 Markdown 正文。HTML 预览 artifact 必须覆盖整篇项目介绍文档，包含所有章节、Mermaid 图说明、关键文件证据和第九章产物摘要，而不是只渲染第九章或局部摘要。
11. 最终回答正文必须保持流式可读的 Markdown；完整 Markdown 文档会由系统以 Markdown artifact 形式提供复制和下载，所以正文不要输出本地保存路径，也不要要求用户手工复制文件。
12. 最终回答必须是可直接渲染的 Markdown；标题、段落、表格、列表、分隔线和代码块前后必须保留换行，禁止把多个 Markdown 块压缩到同一行。
13. 每个 Mermaid 图必须独立成块：第一行只能是 ```mermaid，第二行才开始 graph、flowchart 或 sequenceDiagram，最后单独一行 ```；禁止输出 ```mermaidgraph、```mermaidflowchart、```mermaidsequenceDiagram。
14. 同一个 Mermaid 图内节点 ID 必须唯一，禁止重复使用同一个 ID 表示不同节点；不要使用不稳定或 beta Mermaid 语法。
15. 输出前必须自检：硬性工具调用是否满足清单；所有关键结论是否能在 README、docs、源码或 web_fetch 材料中找到；是否包含可渲染 Mermaid 图、关键文件证据表和第九章产物摘要。

严格产物要求（用于真实 E2E 验证）：
1. newsletter_generation、ppt_generation、frontend_design 的工具 observation 是真实产物来源；最终第九章必须基于这些实际 observation 写摘要，不得只声称“已生成”。
2. newsletter_generation 的真实输出会由系统保存为 newsletter.md；ppt_generation 的真实输出会由系统保存为 presentation.md；frontend_design 的真实输出会由系统保存为 frontend-design-tool-output.html。
3. 你仍然必须在最终回答末尾输出整篇项目介绍的 HTML 预览 artifact：<artifact language="html" title="project-intro-web-preview.html"> ... </artifact>。这个 HTML 预览用于整篇文档的 Web 端复制、预览和下载。
4. 如果任一生成工具没有成功返回内容，最终回答必须明确标注该产物缺失；不得把摘要、计划或占位文本当成已生成产物。
    $instructions$,
    $toolset${"tools":["github_repository_reader","web_fetch","chart_visualization","newsletter_generation","ppt_generation","frontend_design"]}$toolset$,
    $model${"temperature":0.3,"maxTokens":12000,"thinking":true}$model$,
    '{}',
    '{}',
    '{}',
    'system',
    CURRENT_TIMESTAMP,
    'Seed GitHub visual project introduction agent'
)
ON CONFLICT DO NOTHING;

UPDATE sa_agent_definition
SET latest_version_id = 'github-visual-project-intro-agent-v1',
    status = 'PUBLISHED',
    risk_level = 'HIGH',
    updated_at = CURRENT_TIMESTAMP
WHERE agent_id = 'github-visual-project-intro-agent'
  AND (latest_version_id IS NULL OR latest_version_id = 'github-visual-project-intro-agent-v1');

INSERT INTO sa_agent_tool_binding (
    id,
    agent_id,
    version_id,
    tool_id,
    max_calls_per_run,
    argument_policy_json,
    created_by,
    created_at
)
VALUES
    ('bind-github-visual-reader', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'github_repository_reader', 2, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-web', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'web_fetch', 10, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-newsletter', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'newsletter_generation', 2, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-ppt', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'ppt_generation', 2, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-chart', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'chart_visualization', 4, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-frontend', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'frontend_design', 2, '{}', 'system', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
