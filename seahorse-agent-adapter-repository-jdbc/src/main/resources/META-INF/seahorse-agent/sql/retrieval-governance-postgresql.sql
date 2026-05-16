CREATE TABLE IF NOT EXISTS t_retrieval_strategy_template (
    id              VARCHAR(64) PRIMARY KEY,
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

COMMENT ON TABLE t_retrieval_strategy_template IS '知识库检索策略模板覆盖配置表';
COMMENT ON COLUMN t_retrieval_strategy_template.id IS '模板配置 ID';
COMMENT ON COLUMN t_retrieval_strategy_template.kb_id IS '知识库 ID，空值表示全局模板覆盖';
COMMENT ON COLUMN t_retrieval_strategy_template.template_key IS '模板唯一键，同名模板会覆盖内置或全局模板';
COMMENT ON COLUMN t_retrieval_strategy_template.display_name IS '模板展示名称';
COMMENT ON COLUMN t_retrieval_strategy_template.description IS '模板适用场景说明';
COMMENT ON COLUMN t_retrieval_strategy_template.options_json IS '强类型检索参数 JSON，对应 RetrievalOptions';
COMMENT ON COLUMN t_retrieval_strategy_template.sort_order IS '模板展示排序，数值越小越靠前';
COMMENT ON COLUMN t_retrieval_strategy_template.enabled IS '是否启用，0 表示禁用，1 表示启用';
COMMENT ON COLUMN t_retrieval_strategy_template.create_time IS '创建时间';
COMMENT ON COLUMN t_retrieval_strategy_template.update_time IS '更新时间';
COMMENT ON COLUMN t_retrieval_strategy_template.deleted IS '是否删除，0 表示正常，1 表示删除';

CREATE TABLE IF NOT EXISTS t_retrieval_evaluation_dataset (
    id VARCHAR(64) PRIMARY KEY,
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

COMMENT ON TABLE t_retrieval_evaluation_dataset IS '检索质量评测集表';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.id IS '评测集 ID';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.kb_id IS '知识库 ID';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.dataset_name IS '评测集名称';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.description IS '评测集说明';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.cases_json IS '强类型检索评测样本 JSON，对应 RetrievalEvaluationCase 列表';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.enabled IS '是否启用，0 表示禁用，1 表示启用';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.create_time IS '创建时间';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.update_time IS '更新时间';
COMMENT ON COLUMN t_retrieval_evaluation_dataset.deleted IS '是否删除，0 表示正常，1 表示删除';

CREATE TABLE IF NOT EXISTS t_retrieval_evaluation_run (
    id VARCHAR(64) PRIMARY KEY,
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

COMMENT ON TABLE t_retrieval_evaluation_run IS '检索质量评测运行历史表';
COMMENT ON COLUMN t_retrieval_evaluation_run.id IS '评测运行 ID';
COMMENT ON COLUMN t_retrieval_evaluation_run.kb_id IS '知识库 ID';
COMMENT ON COLUMN t_retrieval_evaluation_run.dataset_id IS '评测集 ID';
COMMENT ON COLUMN t_retrieval_evaluation_run.strategy_name IS '检索策略名称';
COMMENT ON COLUMN t_retrieval_evaluation_run.top_k IS '本次评测使用的 TopK';
COMMENT ON COLUMN t_retrieval_evaluation_run.case_count IS '评测样本总数';
COMMENT ON COLUMN t_retrieval_evaluation_run.evaluable_case_count IS '可计算召回指标的样本数';
COMMENT ON COLUMN t_retrieval_evaluation_run.recall_at_k IS 'Recall@K 汇总指标';
COMMENT ON COLUMN t_retrieval_evaluation_run.mrr IS 'MRR 汇总指标';
COMMENT ON COLUMN t_retrieval_evaluation_run.ndcg_at_k IS 'nDCG@K 汇总指标';
COMMENT ON COLUMN t_retrieval_evaluation_run.empty_recall_rate IS '空召回率';
COMMENT ON COLUMN t_retrieval_evaluation_run.avg_latency_ms IS '平均检索耗时毫秒';
COMMENT ON COLUMN t_retrieval_evaluation_run.p95_latency_ms IS 'P95 检索耗时毫秒';
COMMENT ON COLUMN t_retrieval_evaluation_run.report_json IS '完整强类型评测报告 JSON，对应 RetrievalEvaluationReport';
COMMENT ON COLUMN t_retrieval_evaluation_run.create_time IS '创建时间';
