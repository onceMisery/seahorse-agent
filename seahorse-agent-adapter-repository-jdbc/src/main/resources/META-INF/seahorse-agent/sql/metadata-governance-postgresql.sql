ALTER TABLE t_knowledge_document
ADD COLUMN IF NOT EXISTS metadata_json JSONB;

ALTER TABLE t_knowledge_chunk
ADD COLUMN IF NOT EXISTS metadata_json JSONB;

ALTER TABLE t_knowledge_chunk
ADD COLUMN IF NOT EXISTS search_text TSVECTOR;

COMMENT ON COLUMN t_knowledge_document.metadata_json IS '文档业务元数据 JSON，由 Metadata Schema 管理可过滤字段';
COMMENT ON COLUMN t_knowledge_chunk.metadata_json IS '分块业务元数据 JSON，由 Metadata Schema 管理可过滤字段';
COMMENT ON COLUMN t_knowledge_chunk.search_text IS 'PostgreSQL 全文检索向量，用于轻量关键词检索 fallback';

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_search_text
ON t_knowledge_chunk USING GIN (search_text);

CREATE TABLE IF NOT EXISTS t_metadata_field_schema (
    id                VARCHAR(32) PRIMARY KEY,
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

CREATE UNIQUE INDEX IF NOT EXISTS uk_metadata_schema_field
ON t_metadata_field_schema (tenant_id, kb_id, field_key)
WHERE deleted = 0;

COMMENT ON TABLE t_metadata_field_schema IS '检索元数据字段 Schema 表';
COMMENT ON COLUMN t_metadata_field_schema.id IS '主键 ID';
COMMENT ON COLUMN t_metadata_field_schema.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_field_schema.kb_id IS '知识库 ID，空值表示租户级通用字段';
COMMENT ON COLUMN t_metadata_field_schema.field_key IS '业务元数据字段逻辑名';
COMMENT ON COLUMN t_metadata_field_schema.display_name IS '字段展示名称';
COMMENT ON COLUMN t_metadata_field_schema.value_type IS '字段值类型：STRING/NUMBER/BOOLEAN/DATE_TIME/STRING_ARRAY/NUMBER_ARRAY/ENUM';
COMMENT ON COLUMN t_metadata_field_schema.allowed_ops IS '允许的过滤操作符集合 JSON';
COMMENT ON COLUMN t_metadata_field_schema.required IS '是否必填，0 表示否，1 表示是';
COMMENT ON COLUMN t_metadata_field_schema.filterable IS '是否允许作为检索过滤条件';
COMMENT ON COLUMN t_metadata_field_schema.sortable IS '是否允许排序';
COMMENT ON COLUMN t_metadata_field_schema.facetable IS '是否允许聚合筛选';
COMMENT ON COLUMN t_metadata_field_schema.indexed IS '是否已建立或要求建立索引';
COMMENT ON COLUMN t_metadata_field_schema.index_policy IS '索引策略：NONE/JSON_GIN/EXPRESSION_INDEX/SEARCH_KEYWORD/SEARCH_TEXT/MILVUS_JSON/MILVUS_SCALAR';
COMMENT ON COLUMN t_metadata_field_schema.min_confidence IS '字段自动通过所需最低置信度';
COMMENT ON COLUMN t_metadata_field_schema.trusted_sources IS '可信抽取来源集合 JSON';
COMMENT ON COLUMN t_metadata_field_schema.extraction_hints IS '抽取提示 JSON，如 sourceKeys、ruleRegex、pathRegex、dictionaryCode';
COMMENT ON COLUMN t_metadata_field_schema.backend_mapping IS '后端字段映射配置 JSON';
COMMENT ON COLUMN t_metadata_field_schema.schema_version IS 'Schema 版本号';
COMMENT ON COLUMN t_metadata_field_schema.create_time IS '创建时间';
COMMENT ON COLUMN t_metadata_field_schema.update_time IS '更新时间';
COMMENT ON COLUMN t_metadata_field_schema.deleted IS '是否删除，0 表示正常，1 表示删除';

CREATE TABLE IF NOT EXISTS t_metadata_extraction_job (
    id                  VARCHAR(64) PRIMARY KEY,
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

COMMENT ON TABLE t_metadata_extraction_job IS '元数据抽取与历史回填任务表';
COMMENT ON COLUMN t_metadata_extraction_job.id IS '回填任务 ID';
COMMENT ON COLUMN t_metadata_extraction_job.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_extraction_job.kb_id IS '知识库 ID';
COMMENT ON COLUMN t_metadata_extraction_job.pipeline_id IS '回填使用的入库流水线 ID，为空时使用文档自身流水线';
COMMENT ON COLUMN t_metadata_extraction_job.status IS '任务状态：PENDING/RUNNING/PAUSED/CANCELLED/COMPLETED/FAILED';
COMMENT ON COLUMN t_metadata_extraction_job.current_page IS '当前分页游标，从 1 开始';
COMMENT ON COLUMN t_metadata_extraction_job.checkpoint_json IS '断点续跑游标 JSON，记录当前页和最后处理文档 ID';
COMMENT ON COLUMN t_metadata_extraction_job.batch_size IS '每批扫描的文档数量';
COMMENT ON COLUMN t_metadata_extraction_job.processed_count IS '已扫描处理的文档数量';
COMMENT ON COLUMN t_metadata_extraction_job.success_count IS '回填流水线执行成功的文档数量';
COMMENT ON COLUMN t_metadata_extraction_job.failed_count IS '回填流水线执行失败的文档数量';
COMMENT ON COLUMN t_metadata_extraction_job.skipped_count IS '因禁用、运行中或缺少必要信息而跳过的文档数量';
COMMENT ON COLUMN t_metadata_extraction_job.review_count IS '进入人工复核的文档数量';
COMMENT ON COLUMN t_metadata_extraction_job.quarantine_count IS '进入隔离区的文档数量';
COMMENT ON COLUMN t_metadata_extraction_job.failure_summary IS '失败文档摘要 JSON';
COMMENT ON COLUMN t_metadata_extraction_job.operator IS '最近一次操作人';
COMMENT ON COLUMN t_metadata_extraction_job.create_time IS '创建时间';
COMMENT ON COLUMN t_metadata_extraction_job.update_time IS '更新时间';

CREATE TABLE IF NOT EXISTS t_metadata_extraction_result (
    id                    VARCHAR(64) PRIMARY KEY,
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

COMMENT ON TABLE t_metadata_extraction_result IS '文档元数据抽取结果表';
COMMENT ON COLUMN t_metadata_extraction_result.id IS '抽取结果 ID';
COMMENT ON COLUMN t_metadata_extraction_result.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_extraction_result.kb_id IS '知识库 ID';
COMMENT ON COLUMN t_metadata_extraction_result.doc_id IS '文档 ID';
COMMENT ON COLUMN t_metadata_extraction_result.job_id IS '来源抽取任务 ID';
COMMENT ON COLUMN t_metadata_extraction_result.schema_version IS '抽取结果对应的 Metadata Schema 版本';
COMMENT ON COLUMN t_metadata_extraction_result.extractor_version IS '抽取结果对应的抽取器版本';
COMMENT ON COLUMN t_metadata_extraction_result.status IS '结果状态：ACCEPT/REVIEW_REQUIRED/QUARANTINE';
COMMENT ON COLUMN t_metadata_extraction_result.normalized_metadata IS '标准化后的元数据 JSON';
COMMENT ON COLUMN t_metadata_extraction_result.raw_candidates IS '原始字段候选值、来源、证据和置信度 JSON';
COMMENT ON COLUMN t_metadata_extraction_result.field_quality IS '字段级质量信息 JSON';
COMMENT ON COLUMN t_metadata_extraction_result.validation_issues IS '校验问题 JSON';
COMMENT ON COLUMN t_metadata_extraction_result.approved_metadata IS '人工审核后确认或自动通过的元数据 JSON';
COMMENT ON COLUMN t_metadata_extraction_result.approved_by IS '审核人 ID';
COMMENT ON COLUMN t_metadata_extraction_result.approved_time IS '审核时间';
COMMENT ON COLUMN t_metadata_extraction_result.create_time IS '创建时间';
COMMENT ON COLUMN t_metadata_extraction_result.update_time IS '更新时间';

CREATE TABLE IF NOT EXISTS t_metadata_review_item (
    id                   VARCHAR(64) PRIMARY KEY,
    tenant_id            VARCHAR(64) NOT NULL,
    kb_id                VARCHAR(64),
    doc_id               VARCHAR(64) NOT NULL,
    result_id            VARCHAR(64),
    review_status        VARCHAR(32) NOT NULL,
    priority             INTEGER NOT NULL DEFAULT 0,
    reason_code          VARCHAR(64),
    reason_message       VARCHAR(512),
    suggested_metadata   JSONB,
    corrected_metadata   JSONB,
    reviewer_id          VARCHAR(64),
    review_comment       VARCHAR(1024),
    create_time          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_metadata_review_item IS '元数据人工复核项表';
COMMENT ON COLUMN t_metadata_review_item.id IS '复核项 ID';
COMMENT ON COLUMN t_metadata_review_item.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_review_item.kb_id IS '知识库 ID';
COMMENT ON COLUMN t_metadata_review_item.doc_id IS '文档 ID';
COMMENT ON COLUMN t_metadata_review_item.result_id IS '关联的抽取结果 ID';
COMMENT ON COLUMN t_metadata_review_item.review_status IS '复核状态：PENDING/APPROVED/CORRECTED/REJECTED/QUARANTINED';
COMMENT ON COLUMN t_metadata_review_item.priority IS '复核优先级，数值越大优先级越高';
COMMENT ON COLUMN t_metadata_review_item.reason_code IS '进入复核的原因编码';
COMMENT ON COLUMN t_metadata_review_item.reason_message IS '进入复核的原因说明';
COMMENT ON COLUMN t_metadata_review_item.suggested_metadata IS '系统建议的标准化元数据 JSON';
COMMENT ON COLUMN t_metadata_review_item.corrected_metadata IS '人工修正后的元数据 JSON';
COMMENT ON COLUMN t_metadata_review_item.reviewer_id IS '复核人 ID';
COMMENT ON COLUMN t_metadata_review_item.review_comment IS '复核备注';
COMMENT ON COLUMN t_metadata_review_item.create_time IS '创建时间';
COMMENT ON COLUMN t_metadata_review_item.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_metadata_review_status
ON t_metadata_review_item (tenant_id, kb_id, review_status, priority, update_time);

CREATE INDEX IF NOT EXISTS idx_metadata_review_doc
ON t_metadata_review_item (doc_id);

CREATE TABLE IF NOT EXISTS t_metadata_review_audit (
    id                   VARCHAR(64) PRIMARY KEY,
    review_item_id       VARCHAR(64) NOT NULL,
    tenant_id            VARCHAR(64) NOT NULL,
    kb_id                VARCHAR(64),
    doc_id               VARCHAR(64) NOT NULL,
    result_id            VARCHAR(64),
    from_status          VARCHAR(32),
    to_status            VARCHAR(32) NOT NULL,
    reviewer_id          VARCHAR(64),
    review_comment       VARCHAR(1024),
    decision_metadata    JSONB,
    create_time          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE t_metadata_review_audit IS '元数据人工复核决策审计表';
COMMENT ON COLUMN t_metadata_review_audit.id IS '审计记录 ID';
COMMENT ON COLUMN t_metadata_review_audit.review_item_id IS '关联的复核项 ID';
COMMENT ON COLUMN t_metadata_review_audit.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_review_audit.kb_id IS '知识库 ID';
COMMENT ON COLUMN t_metadata_review_audit.doc_id IS '文档 ID';
COMMENT ON COLUMN t_metadata_review_audit.result_id IS '关联的抽取结果 ID';
COMMENT ON COLUMN t_metadata_review_audit.from_status IS '决策前复核状态';
COMMENT ON COLUMN t_metadata_review_audit.to_status IS '决策后复核状态';
COMMENT ON COLUMN t_metadata_review_audit.reviewer_id IS '复核人 ID';
COMMENT ON COLUMN t_metadata_review_audit.review_comment IS '复核备注';
COMMENT ON COLUMN t_metadata_review_audit.decision_metadata IS '本次决策采纳或修正的元数据 JSON';
COMMENT ON COLUMN t_metadata_review_audit.create_time IS '审计记录创建时间';

CREATE INDEX IF NOT EXISTS idx_metadata_review_audit_item
ON t_metadata_review_audit (review_item_id, create_time);

CREATE INDEX IF NOT EXISTS idx_metadata_review_audit_doc
ON t_metadata_review_audit (tenant_id, kb_id, doc_id, create_time);

CREATE TABLE IF NOT EXISTS t_metadata_quarantine_item (
    id                VARCHAR(64) PRIMARY KEY,
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

COMMENT ON TABLE t_metadata_quarantine_item IS '元数据抽取隔离项表';
COMMENT ON COLUMN t_metadata_quarantine_item.id IS '隔离项 ID';
COMMENT ON COLUMN t_metadata_quarantine_item.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_quarantine_item.kb_id IS '知识库 ID';
COMMENT ON COLUMN t_metadata_quarantine_item.doc_id IS '文档 ID';
COMMENT ON COLUMN t_metadata_quarantine_item.job_id IS '来源抽取任务 ID';
COMMENT ON COLUMN t_metadata_quarantine_item.stage IS '失败阶段：FETCH/PARSE/EXTRACT/NORMALIZE/VALIDATE/INDEX';
COMMENT ON COLUMN t_metadata_quarantine_item.reason_code IS '隔离原因编码';
COMMENT ON COLUMN t_metadata_quarantine_item.reason_message IS '隔离原因说明';
COMMENT ON COLUMN t_metadata_quarantine_item.source_snapshot IS '隔离时的来源、解析结果、候选值等快照 JSON';
COMMENT ON COLUMN t_metadata_quarantine_item.retry_count IS '已重试次数';
COMMENT ON COLUMN t_metadata_quarantine_item.next_retry_time IS '下一次允许重试时间';
COMMENT ON COLUMN t_metadata_quarantine_item.resolved IS '是否已处理，0 表示未处理，1 表示已处理';
COMMENT ON COLUMN t_metadata_quarantine_item.resolved_by IS '处理人 ID';
COMMENT ON COLUMN t_metadata_quarantine_item.resolved_time IS '处理时间';
COMMENT ON COLUMN t_metadata_quarantine_item.create_time IS '创建时间';
COMMENT ON COLUMN t_metadata_quarantine_item.update_time IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_metadata_quarantine_status
ON t_metadata_quarantine_item (tenant_id, kb_id, resolved, next_retry_time);

CREATE INDEX IF NOT EXISTS idx_metadata_quarantine_doc
ON t_metadata_quarantine_item (doc_id);

CREATE TABLE IF NOT EXISTS t_metadata_dictionary_item (
    id                VARCHAR(64) PRIMARY KEY,
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

COMMENT ON TABLE t_metadata_dictionary_item IS '元数据标准化字典项表';
COMMENT ON COLUMN t_metadata_dictionary_item.id IS '字典项 ID';
COMMENT ON COLUMN t_metadata_dictionary_item.tenant_id IS '租户 ID';
COMMENT ON COLUMN t_metadata_dictionary_item.dict_code IS '字典编码，如 department、security_level';
COMMENT ON COLUMN t_metadata_dictionary_item.raw_value IS '原始值或别名';
COMMENT ON COLUMN t_metadata_dictionary_item.canonical_value IS '标准值';
COMMENT ON COLUMN t_metadata_dictionary_item.display_name IS '展示名称';
COMMENT ON COLUMN t_metadata_dictionary_item.enabled IS '是否启用，0 表示禁用，1 表示启用';
COMMENT ON COLUMN t_metadata_dictionary_item.create_time IS '创建时间';
COMMENT ON COLUMN t_metadata_dictionary_item.update_time IS '更新时间';
