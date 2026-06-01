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
    create_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT     DEFAULT 0,
    CONSTRAINT uk_user_username UNIQUE (username)
);
COMMENT ON TABLE t_user IS '绯荤粺鐢ㄦ埛琛?;
COMMENT ON COLUMN t_user.id IS '涓婚敭ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_user.username IS '鐢ㄦ埛鍚嶏紝鍞竴';
COMMENT ON COLUMN t_user.password IS '密码';
COMMENT ON COLUMN t_user.role IS '角色：admin/user';
COMMENT ON COLUMN t_user.avatar IS '用户头像';
COMMENT ON COLUMN t_user.create_time IS '创建时间';
COMMENT ON COLUMN t_user.update_time IS '更新时间';
COMMENT ON COLUMN t_user.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

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
COMMENT ON TABLE t_conversation IS '会话列表';
COMMENT ON COLUMN t_conversation.id IS '涓婚敭ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_conversation.conversation_id IS '浼氳瘽ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_conversation.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_conversation.title IS '会话名称';
COMMENT ON COLUMN t_conversation.last_time IS '鏈€杩戞秷鎭椂闂?;
COMMENT ON COLUMN t_conversation.create_time IS '创建时间';
COMMENT ON COLUMN t_conversation.update_time IS '更新时间';
COMMENT ON COLUMN t_conversation.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

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
COMMENT ON TABLE t_conversation_summary IS '浼氳瘽鎽樿琛紙涓庢秷鎭〃鍒嗙瀛樺偍锛?;
COMMENT ON COLUMN t_conversation_summary.id IS '涓婚敭ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_conversation_summary.conversation_id IS '浼氳瘽ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_conversation_summary.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_conversation_summary.last_message_id IS '鎽樿鏈€鍚庢秷鎭疘D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_conversation_summary.content IS '浼氳瘽鎽樿鍐呭';
COMMENT ON COLUMN t_conversation_summary.create_time IS '创建时间';
COMMENT ON COLUMN t_conversation_summary.update_time IS '更新时间';
COMMENT ON COLUMN t_conversation_summary.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

CREATE TABLE t_message (
    id                BIGINT      NOT NULL PRIMARY KEY,
    conversation_id   BIGINT NOT NULL,
    user_id           BIGINT NOT NULL,
    role              VARCHAR(16) NOT NULL,
    content           TEXT        NOT NULL,
    agent_run_id      BIGINT,
    thinking_content  TEXT,
    thinking_duration INTEGER,
    create_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT    DEFAULT 0
);
CREATE INDEX idx_conversation_user_time ON t_message (conversation_id, user_id, create_time);
CREATE INDEX idx_message_agent_run ON t_message (agent_run_id, user_id, create_time);
COMMENT ON TABLE t_message IS '浼氳瘽娑堟伅璁板綍琛?;
COMMENT ON COLUMN t_message.id IS '涓婚敭ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_message.conversation_id IS '浼氳瘽ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_message.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_message.role IS '角色：user/assistant';
COMMENT ON COLUMN t_message.content IS '娑堟伅鍐呭';
COMMENT ON COLUMN t_message.thinking_content IS '娣卞害鎬濊€冨唴瀹?;
COMMENT ON COLUMN t_message.thinking_duration IS '娣卞害鎬濊€冭€楁椂锛堢锛?;
COMMENT ON COLUMN t_message.create_time IS '创建时间';
COMMENT ON COLUMN t_message.update_time IS '更新时间';
COMMENT ON COLUMN t_message.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

CREATE TABLE sa_conversation_attachment (
    attachment_id     BIGINT   NOT NULL PRIMARY KEY,
    conversation_id   BIGINT   NOT NULL,
    message_id        BIGINT,
    user_id           BIGINT   NOT NULL,
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
COMMENT ON TABLE sa_conversation_attachment IS '浼氳瘽闄勪欢琛?;
COMMENT ON COLUMN sa_conversation_attachment.attachment_id IS '涓婚敭ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

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
COMMENT ON TABLE t_message_feedback IS '浼氳瘽娑堟伅鍙嶉琛?;
COMMENT ON COLUMN t_message_feedback.id IS '涓婚敭ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_message_feedback.message_id IS '娑堟伅ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_message_feedback.conversation_id IS '浼氳瘽ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_message_feedback.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_message_feedback.vote IS '投票 1：赞 -1：踩';
COMMENT ON COLUMN t_message_feedback.reason IS '鍙嶉鍘熷洜';
COMMENT ON COLUMN t_message_feedback.comment IS '鍙嶉璇勮';
COMMENT ON COLUMN t_message_feedback.create_time IS '创建时间';
COMMENT ON COLUMN t_message_feedback.update_time IS '更新时间';
COMMENT ON COLUMN t_message_feedback.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

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
COMMENT ON TABLE t_sample_question IS '绀轰緥闂琛?;
COMMENT ON COLUMN t_sample_question.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_sample_question.title IS '灞曠ず鏍囬';
COMMENT ON COLUMN t_sample_question.description IS '鎻忚堪鎴栨彁绀?;
COMMENT ON COLUMN t_sample_question.question IS '绀轰緥闂鍐呭';
COMMENT ON COLUMN t_sample_question.create_time IS '创建时间';
COMMENT ON COLUMN t_sample_question.update_time IS '更新时间';
COMMENT ON COLUMN t_sample_question.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

-- ============================================
-- Knowledge Base Tables
-- ============================================

CREATE TABLE t_knowledge_base (
    id              BIGINT       NOT NULL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(64)  NOT NULL,
    collection_name VARCHAR(64) NOT NULL,
    created_by      BIGINT  NOT NULL,
    updated_by      BIGINT,
    create_time     TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_collection_name UNIQUE (collection_name)
);
CREATE INDEX idx_kb_name ON t_knowledge_base (name);
COMMENT ON TABLE t_knowledge_base IS '知识库表';
COMMENT ON COLUMN t_knowledge_base.id IS '涓婚敭ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_base.name IS '鐭ヨ瘑搴撳悕绉?;
COMMENT ON COLUMN t_knowledge_base.embedding_model IS '嵌入模型标识';
COMMENT ON COLUMN t_knowledge_base.collection_name IS 'Collection名称';
COMMENT ON COLUMN t_knowledge_base.created_by IS '鍒涘缓浜?;
COMMENT ON COLUMN t_knowledge_base.updated_by IS '淇敼浜?;
COMMENT ON COLUMN t_knowledge_base.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_base.update_time IS '更新时间';
COMMENT ON COLUMN t_knowledge_base.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

CREATE TABLE t_knowledge_document (
    id               BIGINT        NOT NULL PRIMARY KEY,
    kb_id            BIGINT        NOT NULL,
    doc_name         VARCHAR(256)  NOT NULL,
    enabled          SMALLINT      NOT NULL DEFAULT 1,
    chunk_count      INTEGER       DEFAULT 0,
    file_url         VARCHAR(1024) NOT NULL,
    file_type        VARCHAR(16)   NOT NULL,
    file_size        BIGINT,
    process_mode     VARCHAR(16)   DEFAULT 'chunk',
    status           VARCHAR(16)   NOT NULL DEFAULT 'pending',
    source_type      VARCHAR(16),
    source_location  VARCHAR(1024),
    schedule_enabled SMALLINT,
    schedule_cron    VARCHAR(64),
    chunk_strategy   VARCHAR(32),
    chunk_config     JSONB,
    pipeline_id      BIGINT,
    created_by       BIGINT   NOT NULL,
    updated_by       BIGINT,
    create_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_kb_id ON t_knowledge_document (kb_id);
COMMENT ON TABLE t_knowledge_document IS '知识库文档表';
COMMENT ON COLUMN t_knowledge_document.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_document.kb_id IS '鐭ヨ瘑搴揑D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_document.doc_name IS '文档名称';
COMMENT ON COLUMN t_knowledge_document.enabled IS '鏄惁鍚敤 1锛氬惎鐢?0锛氱鐢?;
COMMENT ON COLUMN t_knowledge_document.chunk_count IS '分块数量';
COMMENT ON COLUMN t_knowledge_document.file_url IS '鏂囦欢瀛樺偍璺緞';
COMMENT ON COLUMN t_knowledge_document.file_type IS '文件类型';
COMMENT ON COLUMN t_knowledge_document.file_size IS '文件大小（字节）';
COMMENT ON COLUMN t_knowledge_document.process_mode IS '处理模式：chunk/pipeline';
COMMENT ON COLUMN t_knowledge_document.status IS '鐘舵€侊細pending/running/success/failed';
COMMENT ON COLUMN t_knowledge_document.source_type IS '来源类型：file/url';
COMMENT ON COLUMN t_knowledge_document.source_location IS '来源地址';
COMMENT ON COLUMN t_knowledge_document.schedule_enabled IS '鏄惁鍚敤瀹氭椂鍒锋柊';
COMMENT ON COLUMN t_knowledge_document.schedule_cron IS '瀹氭椂琛ㄨ揪寮?;
COMMENT ON COLUMN t_knowledge_document.chunk_strategy IS '分块策略';
COMMENT ON COLUMN t_knowledge_document.chunk_config IS '分块配置JSON';
COMMENT ON COLUMN t_knowledge_document.pipeline_id IS 'Pipeline ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_document.created_by IS '鍒涘缓浜?;
COMMENT ON COLUMN t_knowledge_document.updated_by IS '淇敼浜?;
COMMENT ON COLUMN t_knowledge_document.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document.update_time IS '更新时间';
COMMENT ON COLUMN t_knowledge_document.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

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
    created_by   BIGINT NOT NULL,
    updated_by   BIGINT,
    create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_doc_id ON t_knowledge_chunk (doc_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_kb_doc ON t_knowledge_chunk (kb_id, doc_id);
COMMENT ON TABLE t_knowledge_chunk IS '知识库文档分块表';
COMMENT ON COLUMN t_knowledge_chunk.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_chunk.kb_id IS '鐭ヨ瘑搴揑D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_chunk.doc_id IS '鏂囨。ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_chunk.chunk_index IS '分块序号';
COMMENT ON COLUMN t_knowledge_chunk.content IS '鍒嗗潡鍐呭';
COMMENT ON COLUMN t_knowledge_chunk.content_hash IS '鍐呭鍝堝笇';
COMMENT ON COLUMN t_knowledge_chunk.char_count IS '瀛楃鏁?;
COMMENT ON COLUMN t_knowledge_chunk.token_count IS 'Token鏁?;
COMMENT ON COLUMN t_knowledge_chunk.enabled IS '鏄惁鍚敤';
COMMENT ON COLUMN t_knowledge_chunk.created_by IS '鍒涘缓浜?;
COMMENT ON COLUMN t_knowledge_chunk.updated_by IS '淇敼浜?;
COMMENT ON COLUMN t_knowledge_chunk.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_chunk.update_time IS '更新时间';
COMMENT ON COLUMN t_knowledge_chunk.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

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
COMMENT ON TABLE t_knowledge_document_chunk_log IS '知识库文档分块日志表';
COMMENT ON COLUMN t_knowledge_document_chunk_log.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_document_chunk_log.doc_id IS '鏂囨。ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_document_chunk_log.status IS '鐘舵€?;
COMMENT ON COLUMN t_knowledge_document_chunk_log.process_mode IS '处理模式';
COMMENT ON COLUMN t_knowledge_document_chunk_log.chunk_strategy IS '分块策略';
COMMENT ON COLUMN t_knowledge_document_chunk_log.pipeline_id IS 'Pipeline ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_document_chunk_log.extract_duration IS '鎻愬彇鑰楁椂锛堟绉掞級';
COMMENT ON COLUMN t_knowledge_document_chunk_log.chunk_duration IS '鍒嗗潡鑰楁椂锛堟绉掞級';
COMMENT ON COLUMN t_knowledge_document_chunk_log.embed_duration IS '鍚戦噺鍖栬€楁椂锛堟绉掞級';
COMMENT ON COLUMN t_knowledge_document_chunk_log.persist_duration IS 'DB鎸佷箙鍖栬€楁椂锛堟绉掞級';
COMMENT ON COLUMN t_knowledge_document_chunk_log.total_duration IS '鎬昏€楁椂锛堟绉掞級';
COMMENT ON COLUMN t_knowledge_document_chunk_log.chunk_count IS '分块数量';
COMMENT ON COLUMN t_knowledge_document_chunk_log.error_message IS '閿欒淇℃伅';
COMMENT ON COLUMN t_knowledge_document_chunk_log.start_time IS '寮€濮嬫椂闂?;
COMMENT ON COLUMN t_knowledge_document_chunk_log.end_time IS '结束时间';
COMMENT ON COLUMN t_knowledge_document_chunk_log.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document_chunk_log.update_time IS '更新时间';

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
COMMENT ON TABLE t_knowledge_document_schedule IS '知识库文档定时刷新任务表';
COMMENT ON COLUMN t_knowledge_document_schedule.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_document_schedule.doc_id IS '鏂囨。ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_document_schedule.kb_id IS '鐭ヨ瘑搴揑D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_document_schedule.cron_expr IS 'Cron琛ㄨ揪寮?;
COMMENT ON COLUMN t_knowledge_document_schedule.enabled IS '鏄惁鍚敤';
COMMENT ON COLUMN t_knowledge_document_schedule.next_run_time IS '涓嬫鎵ц鏃堕棿';
COMMENT ON COLUMN t_knowledge_document_schedule.last_run_time IS '涓婃鎵ц鏃堕棿';
COMMENT ON COLUMN t_knowledge_document_schedule.last_success_time IS '涓婃鎴愬姛鏃堕棿';
COMMENT ON COLUMN t_knowledge_document_schedule.last_status IS '涓婃鐘舵€?;
COMMENT ON COLUMN t_knowledge_document_schedule.last_error IS '涓婃閿欒';
COMMENT ON COLUMN t_knowledge_document_schedule.last_etag IS '涓婃ETag';
COMMENT ON COLUMN t_knowledge_document_schedule.last_modified IS '涓婃淇敼鏃堕棿';
COMMENT ON COLUMN t_knowledge_document_schedule.last_content_hash IS '涓婃鍐呭鍝堝笇';
COMMENT ON COLUMN t_knowledge_document_schedule.lock_owner IS '閿佹寔鏈夎€?;
COMMENT ON COLUMN t_knowledge_document_schedule.lock_until IS '閿佽繃鏈熸椂闂?;
COMMENT ON COLUMN t_knowledge_document_schedule.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document_schedule.update_time IS '更新时间';

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
COMMENT ON TABLE t_knowledge_document_schedule_exec IS '鐭ヨ瘑搴撴枃妗ｅ畾鏃跺埛鏂版墽琛岃褰?;
COMMENT ON COLUMN t_knowledge_document_schedule_exec.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_document_schedule_exec.schedule_id IS '璋冨害ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_document_schedule_exec.doc_id IS '鏂囨。ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_document_schedule_exec.kb_id IS '鐭ヨ瘑搴揑D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_document_schedule_exec.status IS '鐘舵€?;
COMMENT ON COLUMN t_knowledge_document_schedule_exec.message IS '消息';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.start_time IS '寮€濮嬫椂闂?;
COMMENT ON COLUMN t_knowledge_document_schedule_exec.end_time IS '结束时间';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.file_name IS '鏂囦欢鍚?;
COMMENT ON COLUMN t_knowledge_document_schedule_exec.file_size IS '文件大小';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.content_hash IS '鍐呭鍝堝笇';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.etag IS 'ETag';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.last_modified IS '鏈€鍚庝慨鏀规椂闂?;
COMMENT ON COLUMN t_knowledge_document_schedule_exec.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.update_time IS '更新时间';

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
COMMENT ON TABLE t_intent_node IS '鎰忓浘鏍戣妭鐐归厤缃〃';
COMMENT ON COLUMN t_intent_node.id IS '鑷涓婚敭 - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_intent_node.kb_id IS '鐭ヨ瘑搴揑D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_intent_node.intent_code IS '涓氬姟鍞竴鏍囪瘑';
COMMENT ON COLUMN t_intent_node.name IS '展示名称';
COMMENT ON COLUMN t_intent_node.level IS '层级 0：DOMAIN 1：CATEGORY 2：TOPIC';
COMMENT ON COLUMN t_intent_node.parent_code IS '鐖惰妭鐐规爣璇?;
COMMENT ON COLUMN t_intent_node.description IS '璇箟鎻忚堪';
COMMENT ON COLUMN t_intent_node.examples IS '绀轰緥闂';
COMMENT ON COLUMN t_intent_node.collection_name IS '关联的Collection名称';
COMMENT ON COLUMN t_intent_node.top_k IS '鐭ヨ瘑搴撴绱opK';
COMMENT ON COLUMN t_intent_node.mcp_tool_id IS 'MCP工具ID';
COMMENT ON COLUMN t_intent_node.kind IS '绫诲瀷 0锛歊AG鐭ヨ瘑搴撶被 1锛歋YSTEM绯荤粺浜や簰绫?;
COMMENT ON COLUMN t_intent_node.prompt_snippet IS '鎻愮ず璇嶇墖娈?;
COMMENT ON COLUMN t_intent_node.prompt_template IS '鎻愮ず璇嶆ā鏉?;
COMMENT ON COLUMN t_intent_node.param_prompt_template IS '鍙傛暟鎻愬彇鎻愮ず璇嶆ā鏉匡紙MCP妯″紡涓撳睘锛?;
COMMENT ON COLUMN t_intent_node.sort_order IS '鎺掑簭瀛楁';
COMMENT ON COLUMN t_intent_node.enabled IS '鏄惁鍚敤 1锛氬惎鐢?0锛氱鐢?;
COMMENT ON COLUMN t_intent_node.create_by IS '鍒涘缓浜?;
COMMENT ON COLUMN t_intent_node.update_by IS '淇敼浜?;
COMMENT ON COLUMN t_intent_node.create_time IS '创建时间';
COMMENT ON COLUMN t_intent_node.update_time IS '淇敼鏃堕棿';
COMMENT ON COLUMN t_intent_node.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

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
COMMENT ON TABLE t_query_term_mapping IS '鍏抽敭璇嶅綊涓€鍖栨槧灏勮〃';
COMMENT ON COLUMN t_query_term_mapping.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_query_term_mapping.domain IS '领域';
COMMENT ON COLUMN t_query_term_mapping.source_term IS '源词';
COMMENT ON COLUMN t_query_term_mapping.target_term IS '鐩爣璇?;
COMMENT ON COLUMN t_query_term_mapping.match_type IS '鍖归厤绫诲瀷 1锛氱簿纭?2锛氭ā绯?;
COMMENT ON COLUMN t_query_term_mapping.priority IS '浼樺厛绾?;
COMMENT ON COLUMN t_query_term_mapping.enabled IS '鏄惁鍚敤';
COMMENT ON COLUMN t_query_term_mapping.remark IS '备注';
COMMENT ON COLUMN t_query_term_mapping.create_by IS '鍒涘缓浜?;
COMMENT ON COLUMN t_query_term_mapping.update_by IS '淇敼浜?;
COMMENT ON COLUMN t_query_term_mapping.create_time IS '创建时间';
COMMENT ON COLUMN t_query_term_mapping.update_time IS '淇敼鏃堕棿';
COMMENT ON COLUMN t_query_term_mapping.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

CREATE TABLE t_rag_trace_run (
    id              BIGINT           NOT NULL PRIMARY KEY,
    trace_id        BIGINT      NOT NULL,
    trace_name      VARCHAR(128),
    entry_method    VARCHAR(256),
    conversation_id BIGINT,
    task_id         BIGINT,
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
COMMENT ON TABLE t_rag_trace_run IS 'Trace 杩愯璁板綍琛?;
COMMENT ON COLUMN t_rag_trace_run.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_rag_trace_run.trace_id IS '鍏ㄥ眬閾捐矾ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_rag_trace_run.trace_name IS '链路名称';
COMMENT ON COLUMN t_rag_trace_run.entry_method IS '入口方法';
COMMENT ON COLUMN t_rag_trace_run.conversation_id IS '浼氳瘽ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_rag_trace_run.task_id IS '浠诲姟ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_rag_trace_run.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_rag_trace_run.status IS 'RUNNING/SUCCESS/ERROR';
COMMENT ON COLUMN t_rag_trace_run.error_message IS '閿欒淇℃伅';
COMMENT ON COLUMN t_rag_trace_run.start_time IS '寮€濮嬫椂闂?;
COMMENT ON COLUMN t_rag_trace_run.end_time IS '结束时间';
COMMENT ON COLUMN t_rag_trace_run.duration_ms IS '鑰楁椂姣';
COMMENT ON COLUMN t_rag_trace_run.extra_data IS '鎵╁睍瀛楁(JSON)';
COMMENT ON COLUMN t_rag_trace_run.create_time IS '创建时间';
COMMENT ON COLUMN t_rag_trace_run.update_time IS '更新时间';
COMMENT ON COLUMN t_rag_trace_run.deleted IS '鏄惁鍒犻櫎';

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
COMMENT ON TABLE t_rag_trace_node IS 'Trace 鑺傜偣璁板綍琛?;
COMMENT ON COLUMN t_rag_trace_node.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_rag_trace_node.trace_id IS '鎵€灞為摼璺疘D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_rag_trace_node.node_id IS '鑺傜偣ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_rag_trace_node.parent_node_id IS '鐖惰妭鐐笽D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_rag_trace_node.depth IS '节点深度';
COMMENT ON COLUMN t_rag_trace_node.node_type IS '节点类型';
COMMENT ON COLUMN t_rag_trace_node.node_name IS '节点名称';
COMMENT ON COLUMN t_rag_trace_node.class_name IS '类名';
COMMENT ON COLUMN t_rag_trace_node.method_name IS '鏂规硶鍚?;
COMMENT ON COLUMN t_rag_trace_node.status IS 'RUNNING/SUCCESS/ERROR';
COMMENT ON COLUMN t_rag_trace_node.error_message IS '閿欒淇℃伅';
COMMENT ON COLUMN t_rag_trace_node.start_time IS '寮€濮嬫椂闂?;
COMMENT ON COLUMN t_rag_trace_node.end_time IS '结束时间';
COMMENT ON COLUMN t_rag_trace_node.duration_ms IS '鑰楁椂姣';
COMMENT ON COLUMN t_rag_trace_node.extra_data IS '鎵╁睍瀛楁(JSON)';
COMMENT ON COLUMN t_rag_trace_node.create_time IS '创建时间';
COMMENT ON COLUMN t_rag_trace_node.update_time IS '更新时间';
COMMENT ON COLUMN t_rag_trace_node.deleted IS '鏄惁鍒犻櫎';

-- ============================================
-- Ingestion Pipeline Tables
-- ============================================

CREATE TABLE t_ingestion_pipeline (
    id          BIGINT      NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_by  BIGINT DEFAULT 0,
    updated_by  BIGINT DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT uk_ingestion_pipeline_name UNIQUE (name, deleted)
);
COMMENT ON TABLE t_ingestion_pipeline IS '摄取流水线表';
COMMENT ON COLUMN t_ingestion_pipeline.name IS '娴佹按绾垮悕绉?;
COMMENT ON COLUMN t_ingestion_pipeline.description IS '娴佹按绾挎弿杩?;
COMMENT ON COLUMN t_ingestion_pipeline.created_by IS '鍒涘缓浜?;
COMMENT ON COLUMN t_ingestion_pipeline.updated_by IS '鏇存柊浜?;
COMMENT ON COLUMN t_ingestion_pipeline.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_pipeline.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_pipeline.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

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
COMMENT ON TABLE t_ingestion_pipeline_node IS '摄取流水线节点表';
COMMENT ON COLUMN t_ingestion_pipeline_node.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_ingestion_pipeline_node.pipeline_id IS '娴佹按绾縄D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_ingestion_pipeline_node.node_id IS '鑺傜偣鏍囪瘑(鍚屼竴娴佹按绾垮唴鍞竴) - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_ingestion_pipeline_node.node_type IS '节点类型';
COMMENT ON COLUMN t_ingestion_pipeline_node.next_node_id IS '涓嬩竴涓妭鐐笽D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_ingestion_pipeline_node.settings_json IS '节点配置JSON';
COMMENT ON COLUMN t_ingestion_pipeline_node.condition_json IS '条件JSON';
COMMENT ON COLUMN t_ingestion_pipeline_node.created_by IS '鍒涘缓浜?;
COMMENT ON COLUMN t_ingestion_pipeline_node.updated_by IS '鏇存柊浜?;
COMMENT ON COLUMN t_ingestion_pipeline_node.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_pipeline_node.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_pipeline_node.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

CREATE TABLE t_ingestion_task (
    id               BIGINT      NOT NULL PRIMARY KEY,
    pipeline_id      BIGINT      NOT NULL,
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
COMMENT ON TABLE t_ingestion_task IS '鎽勫彇浠诲姟琛?;
COMMENT ON COLUMN t_ingestion_task.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_ingestion_task.pipeline_id IS '娴佹按绾縄D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_ingestion_task.source_type IS '来源类型';
COMMENT ON COLUMN t_ingestion_task.source_location IS '来源地址或URL';
COMMENT ON COLUMN t_ingestion_task.source_file_name IS '鍘熷鏂囦欢鍚?;
COMMENT ON COLUMN t_ingestion_task.status IS '浠诲姟鐘舵€?;
COMMENT ON COLUMN t_ingestion_task.chunk_count IS '分块数量';
COMMENT ON COLUMN t_ingestion_task.error_message IS '閿欒淇℃伅';
COMMENT ON COLUMN t_ingestion_task.logs_json IS '节点日志JSON';
COMMENT ON COLUMN t_ingestion_task.metadata_json IS '扩展元数据JSON';
COMMENT ON COLUMN t_ingestion_task.started_at IS '寮€濮嬫椂闂?;
COMMENT ON COLUMN t_ingestion_task.completed_at IS '完成时间';
COMMENT ON COLUMN t_ingestion_task.created_by IS '鍒涘缓浜?;
COMMENT ON COLUMN t_ingestion_task.updated_by IS '鏇存柊浜?;
COMMENT ON COLUMN t_ingestion_task.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_task.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_task.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

CREATE TABLE t_ingestion_task_node (
    id            BIGINT      NOT NULL PRIMARY KEY,
    task_id       BIGINT      NOT NULL,
    pipeline_id   BIGINT      NOT NULL,
    node_id       BIGINT NOT NULL,
    node_type     VARCHAR(16) NOT NULL,
    node_order    INTEGER     NOT NULL DEFAULT 0,
    status        VARCHAR(16) NOT NULL,
    duration_ms   BIGINT      NOT NULL DEFAULT 0,
    message       TEXT,
    error_message TEXT,
    output_json   TEXT,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX idx_ingestion_task_node_task ON t_ingestion_task_node (task_id);
CREATE INDEX idx_ingestion_task_node_pipeline ON t_ingestion_task_node (pipeline_id);
CREATE INDEX idx_ingestion_task_node_status ON t_ingestion_task_node (status);
COMMENT ON TABLE t_ingestion_task_node IS '鎽勫彇浠诲姟鑺傜偣琛?;
COMMENT ON COLUMN t_ingestion_task_node.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_ingestion_task_node.task_id IS '浠诲姟ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_ingestion_task_node.pipeline_id IS '娴佹按绾縄D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_ingestion_task_node.node_id IS '鑺傜偣鏍囪瘑 - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_ingestion_task_node.node_type IS '节点类型';
COMMENT ON COLUMN t_ingestion_task_node.node_order IS '节点顺序';
COMMENT ON COLUMN t_ingestion_task_node.status IS '鑺傜偣鐘舵€?;
COMMENT ON COLUMN t_ingestion_task_node.duration_ms IS '鎵ц鑰楁椂(姣)';
COMMENT ON COLUMN t_ingestion_task_node.message IS '节点消息';
COMMENT ON COLUMN t_ingestion_task_node.error_message IS '閿欒淇℃伅';
COMMENT ON COLUMN t_ingestion_task_node.output_json IS '节点输出JSON(全量)';
COMMENT ON COLUMN t_ingestion_task_node.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_task_node.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_task_node.deleted IS '鏄惁鍒犻櫎 0锛氭甯?1锛氬垹闄?;

-- ============================================
-- Vector Storage Table (pgvector)
-- ============================================

CREATE TABLE t_knowledge_vector (
    id          BIGINT PRIMARY KEY,
    content     TEXT,
    metadata    JSONB,
    embedding   vector(1536)
);

CREATE INDEX idx_kv_metadata ON t_knowledge_vector USING gin(metadata);
CREATE INDEX idx_kv_embedding ON t_knowledge_vector USING hnsw (embedding vector_cosine_ops);
COMMENT ON TABLE t_knowledge_vector IS '知识库向量存储表';
COMMENT ON COLUMN t_knowledge_vector.id IS '鍒嗗潡ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_knowledge_vector.content IS '鍒嗗潡鏂囨湰鍐呭';
COMMENT ON COLUMN t_knowledge_vector.metadata IS '鍏冩暟鎹?;
COMMENT ON COLUMN t_knowledge_vector.embedding IS '向量';

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
COMMENT ON TABLE t_outbox_event IS '发件箱事件表';
COMMENT ON COLUMN t_outbox_event.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_short_term_memory (
    id BIGINT PRIMARY KEY,
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
    generation_id BIGINT,
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
COMMENT ON TABLE t_short_term_memory IS '鐭湡璁板繂琛?;
COMMENT ON COLUMN t_short_term_memory.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_short_term_memory.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_short_term_memory.conversation_id IS '浼氳瘽ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_short_term_memory.generation_id IS '鐢熸垚ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_long_term_memory (
    id BIGINT PRIMARY KEY,
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
    vector_ref_id BIGINT,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    generation_id BIGINT,
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
COMMENT ON TABLE t_long_term_memory IS '闀挎湡璁板繂琛?;
COMMENT ON COLUMN t_long_term_memory.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_long_term_memory.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_long_term_memory.vector_ref_id IS '鍚戦噺寮曠敤ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_long_term_memory.generation_id IS '鐢熸垚ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_semantic_memory (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) DEFAULT 'default',
    semantic_key VARCHAR(64) NOT NULL,
    semantic_type VARCHAR(32) NOT NULL,
    value_json JSONB NOT NULL,
    confidence_level NUMERIC(4, 3) DEFAULT 0,
    source_memory_ids JSONB,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    generation_id BIGINT,
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
COMMENT ON TABLE t_semantic_memory IS '璇箟璁板繂琛?;
COMMENT ON COLUMN t_semantic_memory.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_semantic_memory.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_semantic_memory.generation_id IS '鐢熸垚ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_memory_operation_log (
    operation_id BIGINT PRIMARY KEY,
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
COMMENT ON TABLE t_memory_operation_log IS '璁板繂鎿嶄綔鏃ュ織琛?;
COMMENT ON COLUMN t_memory_operation_log.operation_id IS '鎿嶄綔ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_operation_log.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_memory_outbox (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    task_type VARCHAR(64) NOT NULL,
    target_id BIGINT,
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
COMMENT ON TABLE t_memory_outbox IS '记忆输出箱表';
COMMENT ON COLUMN t_memory_outbox.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_outbox.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_outbox.target_id IS '鐩爣ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_memory_review_candidate (
    id BIGINT PRIMARY KEY,
    operation_id BIGINT,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    conversation_id BIGINT,
    message_id BIGINT,
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
    reviewer_id BIGINT,
    reviewer_comment TEXT,
    chosen_content TEXT,
    chosen_metadata JSONB,
    reviewed_memory_id BIGINT,
    reviewed_layer VARCHAR(32),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_memory_review_queue
ON t_memory_review_candidate (tenant_id, user_id, review_status, update_time);
CREATE INDEX idx_memory_review_operation
ON t_memory_review_candidate (operation_id);
COMMENT ON TABLE t_memory_review_candidate IS '璁板繂瀹℃煡鍊欓€夎〃';
COMMENT ON COLUMN t_memory_review_candidate.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_review_candidate.operation_id IS '鎿嶄綔ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_review_candidate.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_review_candidate.conversation_id IS '浼氳瘽ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_review_candidate.message_id IS '娑堟伅ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_review_candidate.reviewer_id IS '瀹℃煡鍛業D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_review_candidate.reviewed_memory_id IS '瀹℃煡璁板繂ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_memory_review_feedback_sample (
    id BIGINT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    operation_id BIGINT,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    requested_action VARCHAR(32) NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    reviewer_id BIGINT,
    reviewer_comment TEXT,
    target_layer VARCHAR(32),
    target_kind VARCHAR(64),
    target_key VARCHAR(128),
    rejected_content TEXT,
    chosen_content TEXT,
    rejected_metadata JSONB,
    chosen_metadata JSONB,
    source_message_ids JSONB,
    reviewed_memory_id BIGINT,
    reviewed_layer VARCHAR(32),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_memory_review_feedback_candidate
ON t_memory_review_feedback_sample (candidate_id, create_time);
COMMENT ON TABLE t_memory_review_feedback_sample IS '璁板繂瀹℃煡鍙嶉鏍锋湰琛?;
COMMENT ON COLUMN t_memory_review_feedback_sample.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_review_feedback_sample.candidate_id IS '鍊欓€塈D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_review_feedback_sample.operation_id IS '鎿嶄綔ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_review_feedback_sample.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_review_feedback_sample.reviewer_id IS '瀹℃煡鍛業D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_review_feedback_sample.reviewed_memory_id IS '瀹℃煡璁板繂ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_memory_trace_event (
    id BIGINT PRIMARY KEY,
    trace_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT,
    conversation_id BIGINT,
    session_id VARCHAR(128),
    component VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(32),
    subject_id BIGINT,
    subject_type VARCHAR(64),
    details_json JSONB,
    occurred_at TIMESTAMP NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_memory_trace_recent
ON t_memory_trace_event (occurred_at, create_time);
CREATE INDEX idx_memory_trace_filters
ON t_memory_trace_event (tenant_id, user_id, component, status, occurred_at);
COMMENT ON TABLE t_memory_trace_event IS '璁板繂璺熻釜浜嬩欢琛?;
COMMENT ON COLUMN t_memory_trace_event.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_trace_event.trace_id IS '璺熻釜ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_trace_event.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_trace_event.conversation_id IS '浼氳瘽ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_trace_event.subject_id IS '涓讳綋ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

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
ON t_memory_aggregation_buffer (tenant_id, session_id);
CREATE INDEX idx_memory_aggregation_scan
ON t_memory_aggregation_buffer (last_activity_at, update_time);
COMMENT ON TABLE t_memory_aggregation_buffer IS '璁板繂鑱氬悎缂撳啿琛?;
COMMENT ON COLUMN t_memory_aggregation_buffer.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_aggregation_buffer.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_aggregation_buffer.conversation_id IS '浼氳瘽ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_memory_maintenance_run (
    id BIGINT PRIMARY KEY,
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
COMMENT ON TABLE t_memory_maintenance_run IS '璁板繂缁存姢杩愯琛?;
COMMENT ON COLUMN t_memory_maintenance_run.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_memory_entity_alias (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    alias_text VARCHAR(256) NOT NULL,
    normalized_alias VARCHAR(256) NOT NULL,
    canonical_entity_id BIGINT NOT NULL,
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
COMMENT ON TABLE t_memory_entity_alias IS '璁板繂瀹炰綋鍒悕琛?;
COMMENT ON COLUMN t_memory_entity_alias.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_entity_alias.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_entity_alias.canonical_entity_id IS '瑙勮寖瀹炰綋ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_memory_entity_relation (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    memory_id BIGINT NOT NULL,
    layer_name VARCHAR(32),
    memory_type VARCHAR(64),
    content TEXT,
    source_entity_id BIGINT NOT NULL,
    target_entity_id BIGINT NOT NULL,
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
COMMENT ON TABLE t_memory_entity_relation IS '璁板繂瀹炰綋鍏崇郴琛?;
COMMENT ON COLUMN t_memory_entity_relation.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_entity_relation.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_entity_relation.memory_id IS '璁板繂ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_entity_relation.source_entity_id IS '婧愬疄浣揑D - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_entity_relation.target_entity_id IS '鐩爣瀹炰綋ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_memory_keyword_index (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    memory_id BIGINT NOT NULL,
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
COMMENT ON TABLE t_memory_keyword_index IS '记忆关键词索引表';
COMMENT ON COLUMN t_memory_keyword_index.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_keyword_index.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_keyword_index.memory_id IS '璁板繂ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

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
    generation_id BIGINT,
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
COMMENT ON TABLE t_user_profile_fact IS '鐢ㄦ埛鐢诲儚浜嬪疄琛?;
COMMENT ON COLUMN t_user_profile_fact.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_user_profile_fact.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_user_profile_fact.generation_id IS '鐢熸垚ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

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
    effective_generation_id BIGINT,
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
COMMENT ON TABLE t_memory_correction_ledger IS '璁板繂淇璐︽湰琛?;
COMMENT ON COLUMN t_memory_correction_ledger.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_correction_ledger.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_correction_ledger.effective_generation_id IS '鏈夋晥鐢熸垚ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_memory_conflict_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    memory_id_1 BIGINT NOT NULL,
    memory_id_2 BIGINT NOT NULL,
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
COMMENT ON TABLE t_memory_conflict_log IS '璁板繂鍐茬獊鏃ュ織琛?;
COMMENT ON COLUMN t_memory_conflict_log.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_conflict_log.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_conflict_log.memory_id_1 IS '璁板繂ID 1 - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_conflict_log.memory_id_2 IS '璁板繂ID 2 - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_memory_quality_snapshot (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    snapshot_json JSONB NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_memory_quality_snapshot_user_time ON t_memory_quality_snapshot (user_id, create_time DESC);
COMMENT ON TABLE t_memory_quality_snapshot IS '璁板繂璐ㄩ噺蹇収琛?;
COMMENT ON COLUMN t_memory_quality_snapshot.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_memory_quality_snapshot.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

CREATE TABLE t_long_term_memory_vector (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) DEFAULT 'default',
    content TEXT NOT NULL,
    embedding vector(1536) NOT NULL,
    generation_id BIGINT,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    last_referenced_at TIMESTAMP,
    access_count INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ltm_vector_user ON t_long_term_memory_vector (user_id);
CREATE INDEX idx_ltm_vector_lifecycle ON t_long_term_memory_vector (user_id, tenant_id, status, update_time);
CREATE INDEX idx_ltm_vector_hnsw ON t_long_term_memory_vector USING hnsw (embedding vector_cosine_ops);
COMMENT ON TABLE t_long_term_memory_vector IS '闀挎湡璁板繂鍚戦噺琛?;
COMMENT ON COLUMN t_long_term_memory_vector.id IS 'ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_long_term_memory_vector.user_id IS '鐢ㄦ埛ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;
COMMENT ON COLUMN t_long_term_memory_vector.generation_id IS '鐢熸垚ID - 闆姳绠楁硶鐢熸垚鐨?4浣嶆暣鏁?;

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
  published_by VARCHAR(64) NOT NULL,
  published_at TIMESTAMP NOT NULL,
  change_summary VARCHAR(500) NOT NULL,
  UNIQUE(agent_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_version_agent
  ON sa_agent_version(agent_id, version_no);

CREATE TABLE IF NOT EXISTS sa_agent_run (
  pk_id BIGSERIAL PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL UNIQUE,
  agent_id VARCHAR(64),
  version_id VARCHAR(64),
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

CREATE TABLE IF NOT EXISTS sa_conversation_attachment (
  pk_id BIGSERIAL PRIMARY KEY,
  attachment_id VARCHAR(64) NOT NULL UNIQUE,
  conversation_id VARCHAR(64) NOT NULL,
  message_id VARCHAR(64),
  user_id VARCHAR(64) NOT NULL,
  file_name VARCHAR(256) NOT NULL,
  mime_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT NOT NULL,
  storage_ref VARCHAR(1000) NOT NULL,
  parse_status VARCHAR(32) NOT NULL,
  resource_ref_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  deleted SMALLINT NOT NULL DEFAULT 0,
  CONSTRAINT chk_sa_conversation_attachment_parse_status
    CHECK (parse_status IN ('PENDING', 'PARSED', 'FAILED', 'BLOCKED'))
);

CREATE INDEX IF NOT EXISTS idx_sa_conversation_attachment_user
  ON sa_conversation_attachment(conversation_id, user_id, created_at);

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

COMMENT ON COLUMN t_knowledge_document.metadata_json IS '鏂囨。涓氬姟鍏冩暟鎹?JSON锛岀敱 Metadata Schema 绠＄悊鍙繃婊ゅ瓧娈?;
COMMENT ON COLUMN t_knowledge_chunk.metadata_json IS '鍒嗗潡涓氬姟鍏冩暟鎹?JSON锛岀敱 Metadata Schema 绠＄悊鍙繃婊ゅ瓧娈?;
COMMENT ON COLUMN t_knowledge_chunk.search_text IS 'PostgreSQL 鍏ㄦ枃妫€绱㈠悜閲忥紝鐢ㄤ簬杞婚噺鍏抽敭璇嶆绱?fallback';

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_search_text
ON t_knowledge_chunk USING GIN (search_text);

-- 鍏冩暟鎹不鐞嗗拰鍏抽敭璇嶇储寮曡ˉ鍋挎寜鐭ヨ瘑搴?+ 鏂囨。鏀舵暃鍊欓€夊垎鍧楋紝閮ㄥ垎绱㈠紩閬垮厤宸茶蒋鍒犳暟鎹弬涓庣淮鎶よ矾寰勩€?
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

COMMENT ON TABLE t_metadata_field_schema IS '妫€绱㈠厓鏁版嵁瀛楁 Schema 琛?;
COMMENT ON COLUMN t_metadata_field_schema.id IS '主键 ID';
COMMENT ON COLUMN t_metadata_field_schema.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_field_schema.kb_id IS '鐭ヨ瘑搴?ID锛岀┖鍊艰〃绀虹鎴风骇閫氱敤瀛楁';
COMMENT ON COLUMN t_metadata_field_schema.field_key IS '涓氬姟鍏冩暟鎹瓧娈甸€昏緫鍚?;
COMMENT ON COLUMN t_metadata_field_schema.display_name IS '瀛楁灞曠ず鍚嶇О';
COMMENT ON COLUMN t_metadata_field_schema.value_type IS '瀛楁鍊肩被鍨嬶細STRING/NUMBER/BOOLEAN/DATE_TIME/STRING_ARRAY/NUMBER_ARRAY/ENUM';
COMMENT ON COLUMN t_metadata_field_schema.allowed_ops IS '鍏佽鐨勮繃婊ゆ搷浣滅闆嗗悎 JSON';
COMMENT ON COLUMN t_metadata_field_schema.required IS '鏄惁蹇呭～锛? 琛ㄧず鍚︼紝1 琛ㄧず鏄?;
COMMENT ON COLUMN t_metadata_field_schema.filterable IS '鏄惁鍏佽浣滀负妫€绱㈣繃婊ゆ潯浠?;
COMMENT ON COLUMN t_metadata_field_schema.sortable IS '鏄惁鍏佽鎺掑簭';
COMMENT ON COLUMN t_metadata_field_schema.facetable IS '鏄惁鍏佽鑱氬悎绛涢€?;
COMMENT ON COLUMN t_metadata_field_schema.indexed IS '鏄惁宸插缓绔嬫垨瑕佹眰寤虹珛绱㈠紩';
COMMENT ON COLUMN t_metadata_field_schema.index_policy IS '索引策略：NONE/JSON_GIN/EXPRESSION_INDEX/SEARCH_KEYWORD/SEARCH_TEXT/MILVUS_JSON/MILVUS_SCALAR';
COMMENT ON COLUMN t_metadata_field_schema.min_confidence IS '瀛楁鑷姩閫氳繃鎵€闇€鏈€浣庣疆淇″害';
COMMENT ON COLUMN t_metadata_field_schema.trusted_sources IS '鍙俊鎶藉彇鏉ユ簮闆嗗悎 JSON';
COMMENT ON COLUMN t_metadata_field_schema.extraction_hints IS '鎶藉彇鎻愮ず JSON锛屽 sourceKeys銆乺uleRegex銆乸athRegex銆乨ictionaryCode';
COMMENT ON COLUMN t_metadata_field_schema.backend_mapping IS '鍚庣瀛楁鏄犲皠閰嶇疆 JSON';
COMMENT ON COLUMN t_metadata_field_schema.schema_version IS 'Schema 鐗堟湰鍙?;
COMMENT ON COLUMN t_metadata_field_schema.create_time IS '创建时间';
COMMENT ON COLUMN t_metadata_field_schema.update_time IS '更新时间';
COMMENT ON COLUMN t_metadata_field_schema.deleted IS '鏄惁鍒犻櫎锛? 琛ㄧず姝ｅ父锛? 琛ㄧず鍒犻櫎';

COMMENT ON COLUMN t_metadata_field_schema.last_sync_backend IS '鏈€杩戜竴娆?Schema 绱㈠紩鍚屾鍚庣';
COMMENT ON COLUMN t_metadata_field_schema.last_sync_action IS '鏈€杩戜竴娆?Schema 绱㈠紩鍚屾鍔ㄤ綔';
COMMENT ON COLUMN t_metadata_field_schema.last_sync_outcome IS '鏈€杩戜竴娆?Schema 绱㈠紩鍚屾缁撴灉';
COMMENT ON COLUMN t_metadata_field_schema.last_sync_error_type IS '鏈€杩戜竴娆?Schema 绱㈠紩鍚屾澶辫触绫诲瀷';
COMMENT ON COLUMN t_metadata_field_schema.last_sync_error_message IS '鏈€杩戜竴娆?Schema 绱㈠紩鍚屾澶辫触鎽樿';
COMMENT ON COLUMN t_metadata_field_schema.last_sync_time IS '鏈€杩戜竴娆?Schema 绱㈠紩鍚屾鏃堕棿';

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

COMMENT ON TABLE t_metadata_schema_usage_log IS '妫€绱?Metadata Schema 浣跨敤鎯呭喌浜嬩欢琛?;
COMMENT ON COLUMN t_metadata_schema_usage_log.id IS '事件�?ID';
COMMENT ON COLUMN t_metadata_schema_usage_log.request_id IS '同一次过滤编译或拒绝请求的聚�?ID';
COMMENT ON COLUMN t_metadata_schema_usage_log.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_schema_usage_log.kb_id IS '知识�?ID';
COMMENT ON COLUMN t_metadata_schema_usage_log.schema_version IS '过滤编译时使用的 Schema 版本';
COMMENT ON COLUMN t_metadata_schema_usage_log.field_key IS '鍙備笌杩囨护鐨勫瓧娈甸€昏緫鍚?;
COMMENT ON COLUMN t_metadata_schema_usage_log.event_type IS '事件类型：COMPILED/REJECTED';
COMMENT ON COLUMN t_metadata_schema_usage_log.guard_only IS '鏄惁鍙兘璧?guard 鍚庡鐞嗭紝0 琛ㄧず鍚︼紝1 琛ㄧず鏄?;
COMMENT ON COLUMN t_metadata_schema_usage_log.reject_reason IS '过滤编译拒绝原因编码';
COMMENT ON COLUMN t_metadata_schema_usage_log.create_time IS '事件写入时间';

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

COMMENT ON TABLE t_metadata_extraction_job IS '鍏冩暟鎹娊鍙栦笌鍘嗗彶鍥炲～浠诲姟琛?;
COMMENT ON COLUMN t_metadata_extraction_job.id IS '回填任务 ID';
COMMENT ON COLUMN t_metadata_extraction_job.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_extraction_job.kb_id IS '知识�?ID';
COMMENT ON COLUMN t_metadata_extraction_job.pipeline_id IS '鍥炲～浣跨敤鐨勫叆搴撴祦姘寸嚎 ID锛屼负绌烘椂浣跨敤鏂囨。鑷韩娴佹按绾?;
COMMENT ON COLUMN t_metadata_extraction_job.status IS '浠诲姟鐘舵€侊細PENDING/RUNNING/PAUSED/CANCELLED/COMPLETED/FAILED';
COMMENT ON COLUMN t_metadata_extraction_job.current_page IS '褰撳墠鍒嗛〉娓告爣锛屼粠 1 寮€濮?;
COMMENT ON COLUMN t_metadata_extraction_job.checkpoint_json IS '鏂偣缁窇娓告爣 JSON锛岃褰曞綋鍓嶉〉鍜屾渶鍚庡鐞嗘枃妗?ID';
COMMENT ON COLUMN t_metadata_extraction_job.batch_size IS '姣忔壒鎵弿鐨勬枃妗ｆ暟閲?;
COMMENT ON COLUMN t_metadata_extraction_job.processed_count IS '宸叉壂鎻忓鐞嗙殑鏂囨。鏁伴噺';
COMMENT ON COLUMN t_metadata_extraction_job.success_count IS '回填流水线执行成功的文档数量';
COMMENT ON COLUMN t_metadata_extraction_job.failed_count IS '回填流水线执行失败的文档数量';
COMMENT ON COLUMN t_metadata_extraction_job.skipped_count IS '鍥犵鐢ㄣ€佽繍琛屼腑鎴栫己灏戝繀瑕佷俊鎭€岃烦杩囩殑鏂囨。鏁伴噺';
COMMENT ON COLUMN t_metadata_extraction_job.review_count IS '杩涘叆浜哄伐澶嶆牳鐨勬枃妗ｆ暟閲?;
COMMENT ON COLUMN t_metadata_extraction_job.quarantine_count IS '杩涘叆闅旂鍖虹殑鏂囨。鏁伴噺';
COMMENT ON COLUMN t_metadata_extraction_job.failure_summary IS '澶辫触鏂囨。鎽樿 JSON';
COMMENT ON COLUMN t_metadata_extraction_job.operator IS '鏈€杩戜竴娆℃搷浣滀汉';
COMMENT ON COLUMN t_metadata_extraction_job.create_time IS '创建时间';
COMMENT ON COLUMN t_metadata_extraction_job.update_time IS '更新时间';

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

COMMENT ON TABLE t_metadata_extraction_result IS '鏂囨。鍏冩暟鎹娊鍙栫粨鏋滆〃';
COMMENT ON COLUMN t_metadata_extraction_result.id IS '抽取结果 ID';
COMMENT ON COLUMN t_metadata_extraction_result.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_extraction_result.kb_id IS '知识�?ID';
COMMENT ON COLUMN t_metadata_extraction_result.doc_id IS '文档 ID';
COMMENT ON COLUMN t_metadata_extraction_result.job_id IS '来源抽取任务 ID';
COMMENT ON COLUMN t_metadata_extraction_result.schema_version IS '抽取结果对应�?Metadata Schema 版本';
COMMENT ON COLUMN t_metadata_extraction_result.extractor_version IS '抽取结果对应的抽取器版本';
COMMENT ON COLUMN t_metadata_extraction_result.status IS '缁撴灉鐘舵€侊細ACCEPT/ACCEPTED/REVIEW_REQUIRED/RE_EXTRACTING/QUARANTINE/QUARANTINED/REJECTED';
COMMENT ON COLUMN t_metadata_extraction_result.normalized_metadata IS '标准化后的元数据 JSON';
COMMENT ON COLUMN t_metadata_extraction_result.raw_candidates IS '鍘熷瀛楁鍊欓€夊€笺€佹潵婧愩€佽瘉鎹拰缃俊搴?JSON';
COMMENT ON COLUMN t_metadata_extraction_result.field_quality IS '瀛楁绾ц川閲忎俊鎭?JSON';
COMMENT ON COLUMN t_metadata_extraction_result.validation_issues IS '鏍￠獙闂 JSON';
COMMENT ON COLUMN t_metadata_extraction_result.approved_metadata IS '浜哄伐瀹℃牳鍚庣‘璁ゆ垨鑷姩閫氳繃鐨勫厓鏁版嵁 JSON';
COMMENT ON COLUMN t_metadata_extraction_result.approved_by IS '审核�?ID';
COMMENT ON COLUMN t_metadata_extraction_result.approved_time IS '审核时间';
COMMENT ON COLUMN t_metadata_extraction_result.create_time IS '创建时间';
COMMENT ON COLUMN t_metadata_extraction_result.update_time IS '更新时间';

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

COMMENT ON TABLE t_metadata_review_item IS '鍏冩暟鎹汉宸ュ鏍搁」琛?;
COMMENT ON COLUMN t_metadata_review_item.id IS '复核�?ID';
COMMENT ON COLUMN t_metadata_review_item.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_review_item.kb_id IS '知识�?ID';
COMMENT ON COLUMN t_metadata_review_item.doc_id IS '文档 ID';
COMMENT ON COLUMN t_metadata_review_item.result_id IS '关联的抽取结�?ID';
COMMENT ON COLUMN t_metadata_review_item.review_status IS '澶嶆牳鐘舵€侊細PENDING/APPROVED/CORRECTED/RE_EXTRACTING/REJECTED/QUARANTINED';
COMMENT ON COLUMN t_metadata_review_item.priority IS '澶嶆牳浼樺厛绾э紝鏁板€艰秺澶т紭鍏堢骇瓒婇珮';
COMMENT ON COLUMN t_metadata_review_item.reason_code IS '杩涘叆澶嶆牳鐨勫師鍥犵紪鐮?;
COMMENT ON COLUMN t_metadata_review_item.reason_message IS '杩涘叆澶嶆牳鐨勫師鍥犺鏄?;
COMMENT ON COLUMN t_metadata_review_item.suggested_metadata IS '绯荤粺寤鸿鐨勬爣鍑嗗寲鍏冩暟鎹?JSON';
COMMENT ON COLUMN t_metadata_review_item.review_context IS '澶嶆牳涓婁笅鏂?JSON锛屽寘鍚棶棰樸€佸瓧娈佃川閲忋€佸€欓€夎瘉鎹拰琚嫆缁濆瓧娈?;
COMMENT ON COLUMN t_metadata_review_item.corrected_metadata IS '浜哄伐淇鍚庣殑鍏冩暟鎹?JSON';
COMMENT ON COLUMN t_metadata_review_item.reviewer_id IS '复核�?ID';
COMMENT ON COLUMN t_metadata_review_item.review_comment IS '复核备注';
COMMENT ON COLUMN t_metadata_review_item.create_time IS '创建时间';
COMMENT ON COLUMN t_metadata_review_item.update_time IS '更新时间';

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

COMMENT ON TABLE t_metadata_review_audit IS '鍏冩暟鎹汉宸ュ鏍稿喅绛栧璁¤〃';
COMMENT ON COLUMN t_metadata_review_audit.id IS '瀹¤璁板綍 ID';
COMMENT ON COLUMN t_metadata_review_audit.review_item_id IS '鍏宠仈鐨勫鏍搁」 ID';
COMMENT ON COLUMN t_metadata_review_audit.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_review_audit.kb_id IS '知识�?ID';
COMMENT ON COLUMN t_metadata_review_audit.doc_id IS '文档 ID';
COMMENT ON COLUMN t_metadata_review_audit.result_id IS '关联的抽取结�?ID';
COMMENT ON COLUMN t_metadata_review_audit.from_status IS '鍐崇瓥鍓嶅鏍哥姸鎬?;
COMMENT ON COLUMN t_metadata_review_audit.to_status IS '鍐崇瓥鍚庡鏍哥姸鎬?;
COMMENT ON COLUMN t_metadata_review_audit.reviewer_id IS '复核�?ID';
COMMENT ON COLUMN t_metadata_review_audit.review_comment IS '复核备注';
COMMENT ON COLUMN t_metadata_review_audit.previous_metadata IS '澶嶆牳鍐崇瓥鍓嶇殑寤鸿鎴栧凡淇鍏冩暟鎹揩鐓?JSON';
COMMENT ON COLUMN t_metadata_review_audit.updated_metadata IS '澶嶆牳鍐崇瓥鍚庣殑閲囩撼銆佷慨姝ｆ垨璋冨害鍏冩暟鎹揩鐓?JSON';
COMMENT ON COLUMN t_metadata_review_audit.decision_metadata IS '鏈鍐崇瓥閲囩撼鎴栦慨姝ｇ殑鍏冩暟鎹?JSON';
COMMENT ON COLUMN t_metadata_review_audit.create_time IS '瀹¤璁板綍鍒涘缓鏃堕棿';

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

COMMENT ON TABLE t_metadata_quarantine_item IS '鍏冩暟鎹娊鍙栭殧绂婚」琛?;
COMMENT ON COLUMN t_metadata_quarantine_item.id IS '闅旂椤?ID';
COMMENT ON COLUMN t_metadata_quarantine_item.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_quarantine_item.kb_id IS '知识�?ID';
COMMENT ON COLUMN t_metadata_quarantine_item.doc_id IS '文档 ID';
COMMENT ON COLUMN t_metadata_quarantine_item.job_id IS '来源抽取任务 ID';
COMMENT ON COLUMN t_metadata_quarantine_item.stage IS '澶辫触闃舵锛欶ETCH/PARSE/EXTRACT/NORMALIZE/VALIDATE/INDEX';
COMMENT ON COLUMN t_metadata_quarantine_item.reason_code IS '闅旂鍘熷洜缂栫爜';
COMMENT ON COLUMN t_metadata_quarantine_item.reason_message IS '闅旂鍘熷洜璇存槑';
COMMENT ON COLUMN t_metadata_quarantine_item.source_snapshot IS '闅旂鏃剁殑鏉ユ簮銆佽В鏋愮粨鏋溿€佸€欓€夊€肩瓑蹇収 JSON';
COMMENT ON COLUMN t_metadata_quarantine_item.retry_count IS '宸查噸璇曟鏁?;
COMMENT ON COLUMN t_metadata_quarantine_item.next_retry_time IS '涓嬩竴娆″厑璁搁噸璇曟椂闂?;
COMMENT ON COLUMN t_metadata_quarantine_item.resolved IS '鏄惁宸插鐞嗭紝0 琛ㄧず鏈鐞嗭紝1 琛ㄧず宸插鐞?;
COMMENT ON COLUMN t_metadata_quarantine_item.resolved_by IS '处理�?ID';
COMMENT ON COLUMN t_metadata_quarantine_item.resolved_time IS '处理时间';
COMMENT ON COLUMN t_metadata_quarantine_item.create_time IS '创建时间';
COMMENT ON COLUMN t_metadata_quarantine_item.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_metadata_quarantine_status
ON t_metadata_quarantine_item (tenant_id, kb_id, resolved, next_retry_time);

CREATE INDEX IF NOT EXISTS idx_metadata_quarantine_doc
ON t_metadata_quarantine_item (doc_id);

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

COMMENT ON TABLE t_metadata_dictionary_item IS '鍏冩暟鎹爣鍑嗗寲瀛楀吀椤硅〃';
COMMENT ON COLUMN t_metadata_dictionary_item.id IS '字典�?ID';
COMMENT ON COLUMN t_metadata_dictionary_item.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_dictionary_item.dict_code IS '瀛楀吀缂栫爜锛屽 department銆乻ecurity_level';
COMMENT ON COLUMN t_metadata_dictionary_item.raw_value IS '鍘熷鍊兼垨鍒悕';
COMMENT ON COLUMN t_metadata_dictionary_item.canonical_value IS '鏍囧噯鍊?;
COMMENT ON COLUMN t_metadata_dictionary_item.display_name IS '展示名称';
COMMENT ON COLUMN t_metadata_dictionary_item.enabled IS '鏄惁鍚敤锛? 琛ㄧず绂佺敤锛? 琛ㄧず鍚敤';
COMMENT ON COLUMN t_metadata_dictionary_item.create_time IS '创建时间';
COMMENT ON COLUMN t_metadata_dictionary_item.update_time IS '更新时间';

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
    enabled         SMALLINT NOT NULL DEFAULT 1,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_retrieval_strategy_template_scope
ON t_retrieval_strategy_template (COALESCE(kb_id, ''), template_key)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_retrieval_strategy_template_scope
ON t_retrieval_strategy_template (kb_id, enabled, deleted, sort_order);

COMMENT ON TABLE t_retrieval_strategy_template IS '鐭ヨ瘑搴撴绱㈢瓥鐣ユā鏉胯鐩栭厤缃〃';
COMMENT ON COLUMN t_retrieval_strategy_template.id IS '模板配置 ID';
COMMENT ON COLUMN t_retrieval_strategy_template.kb_id IS '鐭ヨ瘑搴?ID锛岀┖鍊艰〃绀哄叏灞€妯℃澘瑕嗙洊';
COMMENT ON COLUMN t_retrieval_strategy_template.template_key IS '妯℃澘鍞竴閿紝鍚屽悕妯℃澘浼氳鐩栧唴缃垨鍏ㄥ眬妯℃澘';
COMMENT ON COLUMN t_retrieval_strategy_template.display_name IS '模板展示名称';
COMMENT ON COLUMN t_retrieval_strategy_template.description IS '模板适用场景说明';
COMMENT ON COLUMN t_retrieval_strategy_template.options_json IS '寮虹被鍨嬫绱㈠弬鏁?JSON锛屽搴?RetrievalOptions';
COMMENT ON COLUMN t_retrieval_strategy_template.sort_order IS '模板展示排序，数值越小越靠前';
COMMENT ON COLUMN t_retrieval_strategy_template.enabled IS '鏄惁鍚敤锛? 琛ㄧず绂佺敤锛? 琛ㄧず鍚敤';
COMMENT ON COLUMN t_retrieval_strategy_template.create_time IS '创建时间';
COMMENT ON COLUMN t_retrieval_strategy_template.update_time IS '更新时间';
COMMENT ON COLUMN t_retrieval_strategy_template.deleted IS '鏄惁鍒犻櫎锛? 琛ㄧず姝ｅ父锛? 琛ㄧず鍒犻櫎';

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

COMMENT ON TABLE t_retrieval_evaluation_dataset IS '妫€绱㈣川閲忚瘎娴嬮泦琛?;
COMMENT ON COLUMN t_retrieval_evaluation_dataset.id IS '评测�?ID';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.kb_id IS '知识�?ID';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.dataset_name IS '璇勬祴闆嗗悕绉?;
COMMENT ON COLUMN t_retrieval_evaluation_dataset.description IS '璇勬祴闆嗚鏄?;
COMMENT ON COLUMN t_retrieval_evaluation_dataset.cases_json IS '寮虹被鍨嬫绱㈣瘎娴嬫牱鏈?JSON锛屽搴?RetrievalEvaluationCase 鍒楄〃';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.enabled IS '鏄惁鍚敤锛? 琛ㄧず绂佺敤锛? 琛ㄧず鍚敤';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.create_time IS '创建时间';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.update_time IS '更新时间';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.deleted IS '鏄惁鍒犻櫎锛? 琛ㄧず姝ｅ父锛? 琛ㄧず鍒犻櫎';

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

COMMENT ON TABLE t_retrieval_evaluation_run IS '妫€绱㈣川閲忚瘎娴嬭繍琛屽巻鍙茶〃';
COMMENT ON COLUMN t_retrieval_evaluation_run.id IS '璇勬祴杩愯 ID';
COMMENT ON COLUMN t_retrieval_evaluation_run.kb_id IS '知识�?ID';
COMMENT ON COLUMN t_retrieval_evaluation_run.dataset_id IS '评测�?ID';
COMMENT ON COLUMN t_retrieval_evaluation_run.strategy_name IS '妫€绱㈢瓥鐣ュ悕绉?;
COMMENT ON COLUMN t_retrieval_evaluation_run.top_k IS '鏈璇勬祴浣跨敤鐨?TopK';
COMMENT ON COLUMN t_retrieval_evaluation_run.case_count IS '评测样本总数';
COMMENT ON COLUMN t_retrieval_evaluation_run.evaluable_case_count IS '鍙绠楀彫鍥炴寚鏍囩殑鏍锋湰鏁?;
COMMENT ON COLUMN t_retrieval_evaluation_run.recall_at_k IS 'Recall@K 姹囨€绘寚鏍?;
COMMENT ON COLUMN t_retrieval_evaluation_run.mrr IS 'MRR 姹囨€绘寚鏍?;
COMMENT ON COLUMN t_retrieval_evaluation_run.ndcg_at_k IS 'nDCG@K 姹囨€绘寚鏍?;
COMMENT ON COLUMN t_retrieval_evaluation_run.empty_recall_rate IS '空召回率';
COMMENT ON COLUMN t_retrieval_evaluation_run.avg_latency_ms IS '骞冲潎妫€绱㈣€楁椂姣';
COMMENT ON COLUMN t_retrieval_evaluation_run.p95_latency_ms IS 'P95 妫€绱㈣€楁椂姣';
COMMENT ON COLUMN t_retrieval_evaluation_run.report_json IS '瀹屾暣寮虹被鍨嬭瘎娴嬫姤鍛?JSON锛屽搴?RetrievalEvaluationReport';
COMMENT ON COLUMN t_retrieval_evaluation_run.create_time IS '创建时间';

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

COMMENT ON TABLE t_retrieval_evaluation_comparison IS '妫€绱㈣川閲忚瘎娴嬪姣斿巻鍙茶〃';
COMMENT ON COLUMN t_retrieval_evaluation_comparison.id IS '瀵规瘮鎵规 ID';
COMMENT ON COLUMN t_retrieval_evaluation_comparison.kb_id IS '知识�?ID';
COMMENT ON COLUMN t_retrieval_evaluation_comparison.dataset_id IS '评测�?ID';
COMMENT ON COLUMN t_retrieval_evaluation_comparison.baseline_strategy_name IS '基线策略名称';
COMMENT ON COLUMN t_retrieval_evaluation_comparison.winner_strategy_name IS '鏈瀵规瘮鑳滃嚭绛栫暐鍚嶇О';
COMMENT ON COLUMN t_retrieval_evaluation_comparison.strategy_count IS '鏈鍙備笌瀵规瘮鐨勭瓥鐣ユ暟閲?;
COMMENT ON COLUMN t_retrieval_evaluation_comparison.case_count IS '鏈瀵规瘮澶嶇敤鐨勮瘎娴嬫牱鏈暟閲?;
COMMENT ON COLUMN t_retrieval_evaluation_comparison.report_json IS '瀹屾暣澶氱瓥鐣ュ姣旀姤鍛?JSON锛屽搴?RetrievalEvaluationComparisonReport';
COMMENT ON COLUMN t_retrieval_evaluation_comparison.create_time IS '创建时间';
-- ============================================
-- AI Model Config
-- ============================================

CREATE TABLE IF NOT EXISTS sa_ai_model_config (
    id BIGINT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL UNIQUE,
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
        CHECK (config_type IN ('STRING', 'INTEGER', 'BOOLEAN', 'JSON'))
);
CREATE INDEX IF NOT EXISTS idx_sa_ai_model_config_key
    ON sa_ai_model_config(config_key, deleted);

-- PostgreSQL Initial Data for Seahorse Agent

INSERT INTO t_user (id, username, password, role, avatar, create_time, update_time, deleted)
VALUES (2001523723396308993, 'admin', 'admin', 'admin', 'https://avatars.githubusercontent.com/u/37446017?v=4', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO NOTHING;
