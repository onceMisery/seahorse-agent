-- ============================================================================
-- V42: run experiment and profile comparison
-- ============================================================================

CREATE TABLE IF NOT EXISTS sa_run_experiment (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    base_leaf_message_id BIGINT,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sa_run_experiment IS '运行实验表，记录基于同一会话分支发起的多个 Run Profile 对比实验';
COMMENT ON COLUMN sa_run_experiment.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN sa_run_experiment.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN sa_run_experiment.user_id IS '发起实验的用户 ID';
COMMENT ON COLUMN sa_run_experiment.conversation_id IS '实验所属会话 ID';
COMMENT ON COLUMN sa_run_experiment.base_leaf_message_id IS '实验基准分支叶子消息 ID';
COMMENT ON COLUMN sa_run_experiment.name IS '实验名称';
COMMENT ON COLUMN sa_run_experiment.status IS '实验状态，例如 PENDING、RUNNING、SUCCEEDED、FAILED、CANCELLED';
COMMENT ON COLUMN sa_run_experiment.create_time IS '创建时间';
COMMENT ON COLUMN sa_run_experiment.update_time IS '更新时间';
COMMENT ON COLUMN sa_run_experiment.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE TABLE IF NOT EXISTS sa_run_experiment_trial (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    experiment_id BIGINT NOT NULL,
    run_profile_id BIGINT NOT NULL,
    run_id VARCHAR(128),
    output_message_id BIGINT,
    score_json TEXT,
    metric_json TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sa_run_experiment_trial IS '运行实验试验项表，记录某个 Run Profile 在一次实验中的运行结果、评分和成本指标';
COMMENT ON COLUMN sa_run_experiment_trial.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN sa_run_experiment_trial.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN sa_run_experiment_trial.experiment_id IS '运行实验 ID，对应 sa_run_experiment.id';
COMMENT ON COLUMN sa_run_experiment_trial.run_profile_id IS '参与实验的运行画像 ID';
COMMENT ON COLUMN sa_run_experiment_trial.run_id IS '本次试验对应的 Agent run ID，用于关联运行上下文快照、Trace、成本和审计';
COMMENT ON COLUMN sa_run_experiment_trial.output_message_id IS '本次试验生成的 assistant 消息 ID，可为空';
COMMENT ON COLUMN sa_run_experiment_trial.score_json IS '实验评分 JSON，例如人工评分、自动评分、偏好标签';
COMMENT ON COLUMN sa_run_experiment_trial.metric_json IS '运行指标 JSON，例如耗时、token、费用、工具调用次数';
COMMENT ON COLUMN sa_run_experiment_trial.status IS '试验项状态，例如 PENDING、RUNNING、SUCCEEDED、FAILED、CANCELLED';
COMMENT ON COLUMN sa_run_experiment_trial.error_message IS '失败原因或错误摘要';
COMMENT ON COLUMN sa_run_experiment_trial.create_time IS '创建时间';
COMMENT ON COLUMN sa_run_experiment_trial.update_time IS '更新时间';
COMMENT ON COLUMN sa_run_experiment_trial.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE INDEX IF NOT EXISTS idx_run_experiment_user_status
    ON sa_run_experiment (tenant_id, user_id, status, update_time DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_run_experiment_conversation
    ON sa_run_experiment (tenant_id, conversation_id, base_leaf_message_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_run_experiment_trial_experiment
    ON sa_run_experiment_trial (tenant_id, experiment_id, id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_run_experiment_trial_profile
    ON sa_run_experiment_trial (tenant_id, run_profile_id, status)
    WHERE deleted = 0;
