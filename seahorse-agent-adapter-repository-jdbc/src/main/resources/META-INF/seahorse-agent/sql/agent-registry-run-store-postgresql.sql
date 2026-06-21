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

CREATE TABLE IF NOT EXISTS sa_role_card (
  id BIGINT PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
  user_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  definition TEXT NOT NULL,
  avatar_ref VARCHAR(512),
  higher_perm SMALLINT NOT NULL DEFAULT 0,
  enabled SMALLINT NOT NULL DEFAULT 0,
  share_scope VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
  approval_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  published SMALLINT NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0,
  CONSTRAINT chk_sa_role_card_share_scope
    CHECK (share_scope IN ('PRIVATE', 'TEAM', 'ORG')),
  CONSTRAINT chk_sa_role_card_approval_status
    CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED')),
  CONSTRAINT chk_sa_role_card_flags
    CHECK (higher_perm IN (0, 1) AND enabled IN (0, 1) AND published IN (0, 1))
);

ALTER TABLE sa_role_card ADD COLUMN IF NOT EXISTS share_scope VARCHAR(32) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE sa_role_card ADD COLUMN IF NOT EXISTS approval_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE sa_role_card ADD COLUMN IF NOT EXISTS published SMALLINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_sa_role_card_user
  ON sa_role_card(tenant_id, user_id, deleted, update_time DESC, id DESC);

COMMENT ON TABLE sa_role_card IS '角色卡表，记录用户或团队可复用的运行角色设定';
COMMENT ON COLUMN sa_role_card.id IS '角色卡主键 ID';
COMMENT ON COLUMN sa_role_card.tenant_id IS '租户 ID';
COMMENT ON COLUMN sa_role_card.user_id IS '角色卡所属用户 ID';
COMMENT ON COLUMN sa_role_card.name IS '角色卡名称';
COMMENT ON COLUMN sa_role_card.definition IS '角色卡提示词定义';
COMMENT ON COLUMN sa_role_card.avatar_ref IS '角色卡头像或图标引用';
COMMENT ON COLUMN sa_role_card.higher_perm IS '是否高权限角色卡，1 表示需要更严格治理';
COMMENT ON COLUMN sa_role_card.enabled IS '是否作为用户当前默认角色卡启用';
COMMENT ON COLUMN sa_role_card.share_scope IS '共享范围，PRIVATE 私有、TEAM 团队、ORG 组织';
COMMENT ON COLUMN sa_role_card.approval_status IS '审批状态，PENDING 待审批、APPROVED 已通过、REJECTED 已拒绝';
COMMENT ON COLUMN sa_role_card.published IS '是否已发布为可复用资产';
COMMENT ON COLUMN sa_role_card.create_time IS '创建时间';
COMMENT ON COLUMN sa_role_card.update_time IS '更新时间';
COMMENT ON COLUMN sa_role_card.deleted IS '逻辑删除标记，1 表示已删除';

CREATE TABLE IF NOT EXISTS t_run_context_snapshot (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    run_id VARCHAR(64) NOT NULL,
    conversation_id BIGINT,
    branch_leaf_message_id BIGINT,
    role_card_id BIGINT,
    run_profile_id BIGINT,
    executor_engine VARCHAR(32) NOT NULL DEFAULT 'kernel',
    executor_config_json TEXT,
    trace_context_json TEXT,
    snapshot_json TEXT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE t_run_context_snapshot IS '运行上下文快照表，记录每次 Chat 或 Agent Run 启动时实际生效的角色、工具、模型、记忆、安全策略和执行引擎，用于历史复现、审计和评测';
COMMENT ON COLUMN t_run_context_snapshot.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN t_run_context_snapshot.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN t_run_context_snapshot.run_id IS '运行 ID，对应 Chat 请求或 Agent Run 的稳定标识';
COMMENT ON COLUMN t_run_context_snapshot.conversation_id IS '会话 ID，记录本次运行所属会话，可为空';
COMMENT ON COLUMN t_run_context_snapshot.branch_leaf_message_id IS '运行时选中的消息分支叶子节点 ID，用于复现当时的对话路径';
COMMENT ON COLUMN t_run_context_snapshot.role_card_id IS '运行时选中的角色卡 ID，可为空';
COMMENT ON COLUMN t_run_context_snapshot.run_profile_id IS '运行时选中的运行画像 ID，可为空';
COMMENT ON COLUMN t_run_context_snapshot.executor_engine IS '执行引擎标识，例如 kernel 或 agentscope';
COMMENT ON COLUMN t_run_context_snapshot.executor_config_json IS '执行引擎配置快照 JSON，例如 AgentScope 的 Nacos、A2A、Studio 配置摘要';
COMMENT ON COLUMN t_run_context_snapshot.trace_context_json IS '链路追踪上下文 JSON，例如 traceId、spanId、Studio traceId';
COMMENT ON COLUMN t_run_context_snapshot.snapshot_json IS '运行上下文完整快照 JSON，保存角色卡副本、工具集、MCP 工具、模型配置、记忆范围和安全策略';
COMMENT ON COLUMN t_run_context_snapshot.create_time IS '创建时间';
COMMENT ON COLUMN t_run_context_snapshot.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE INDEX IF NOT EXISTS idx_run_context_snapshot_run
    ON t_run_context_snapshot (tenant_id, run_id);

CREATE TABLE IF NOT EXISTS t_conversation_branch_cursor (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    leaf_message_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE t_conversation_branch_cursor IS '会话分支游标表，记录用户在某个会话中最后选中的消息分支叶子节点，避免多个窗口共享全局 active 路径';
COMMENT ON COLUMN t_conversation_branch_cursor.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN t_conversation_branch_cursor.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN t_conversation_branch_cursor.conversation_id IS '会话 ID';
COMMENT ON COLUMN t_conversation_branch_cursor.user_id IS '用户 ID';
COMMENT ON COLUMN t_conversation_branch_cursor.leaf_message_id IS '当前视图选中的分支叶子消息 ID';
COMMENT ON COLUMN t_conversation_branch_cursor.create_time IS '创建时间';
COMMENT ON COLUMN t_conversation_branch_cursor.update_time IS '更新时间';
COMMENT ON COLUMN t_conversation_branch_cursor.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE UNIQUE INDEX IF NOT EXISTS uk_conversation_branch_cursor_user
    ON t_conversation_branch_cursor (tenant_id, conversation_id, user_id)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS sa_run_profile (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    role_card_id BIGINT,
    executor_engine VARCHAR(32) NOT NULL DEFAULT 'kernel',
    executor_config_json TEXT,
    model_config_json TEXT,
    memory_scope_json TEXT,
    guardrail_config_json TEXT,
    approval_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    approval_operator VARCHAR(64),
    approval_comment TEXT,
    approval_time TIMESTAMP,
    enabled SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sa_run_profile IS '运行画像表，保存用户可复用的 Agent 运行配置，包括角色卡、执行引擎、模型配置、记忆范围、安全策略和默认启用状态';
COMMENT ON COLUMN sa_run_profile.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN sa_run_profile.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN sa_run_profile.user_id IS '创建或归属用户 ID';
COMMENT ON COLUMN sa_run_profile.name IS '运行画像名称';
COMMENT ON COLUMN sa_run_profile.description IS '运行画像描述';
COMMENT ON COLUMN sa_run_profile.role_card_id IS '默认绑定的角色卡 ID，可为空';
COMMENT ON COLUMN sa_run_profile.executor_engine IS '默认执行引擎，例如 kernel 或 agentscope';
COMMENT ON COLUMN sa_run_profile.executor_config_json IS '执行引擎配置 JSON，例如 AgentScope 的 Nacos、A2A、Studio 配置';
COMMENT ON COLUMN sa_run_profile.model_config_json IS '模型配置 JSON，例如模型名称、温度、最大输出长度等';
COMMENT ON COLUMN sa_run_profile.memory_scope_json IS '记忆范围 JSON，例如是否启用长期记忆、知识库范围、用户画像范围等';
COMMENT ON COLUMN sa_run_profile.guardrail_config_json IS '安全策略 JSON，例如高风险工具限制、输出过滤、审批策略等';
COMMENT ON COLUMN sa_run_profile.approval_status IS '运行画像审批状态，例如 DRAFT、PENDING_APPROVAL、APPROVED、REJECTED';
COMMENT ON COLUMN sa_run_profile.approval_operator IS '运行画像审批操作人或处理人标识';
COMMENT ON COLUMN sa_run_profile.approval_comment IS '运行画像审批提交、通过或拒绝时填写的说明';
COMMENT ON COLUMN sa_run_profile.approval_time IS '运行画像最近一次审批状态变更时间';
COMMENT ON COLUMN sa_run_profile.enabled IS '是否为当前用户默认启用画像，0 表示否，1 表示是';
COMMENT ON COLUMN sa_run_profile.create_time IS '创建时间';
COMMENT ON COLUMN sa_run_profile.update_time IS '更新时间';
COMMENT ON COLUMN sa_run_profile.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE INDEX IF NOT EXISTS idx_run_profile_user
    ON sa_run_profile (tenant_id, user_id, enabled, deleted);
CREATE INDEX IF NOT EXISTS idx_run_profile_approval
    ON sa_run_profile (tenant_id, approval_status, deleted);

CREATE TABLE IF NOT EXISTS sa_run_profile_tool (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    profile_id BIGINT NOT NULL,
    tool_id VARCHAR(128) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sa_run_profile_tool IS '运行画像工具绑定表，记录某个 Run Profile 可使用的工具目录项，包括内置工具、MCP 工具、OpenAPI 工具和 A2A 远端 Agent 工具';
COMMENT ON COLUMN sa_run_profile_tool.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN sa_run_profile_tool.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN sa_run_profile_tool.profile_id IS '运行画像 ID，对应 sa_run_profile.id';
COMMENT ON COLUMN sa_run_profile_tool.tool_id IS '工具 ID，对应工具目录中的稳定 toolId 或 A2A agentId';
COMMENT ON COLUMN sa_run_profile_tool.provider IS '工具提供方，例如 BUILT_IN、MCP、OPENAPI、A2A';
COMMENT ON COLUMN sa_run_profile_tool.enabled IS '该工具在当前运行画像中是否启用，0 表示禁用，1 表示启用';
COMMENT ON COLUMN sa_run_profile_tool.create_time IS '创建时间';
COMMENT ON COLUMN sa_run_profile_tool.update_time IS '更新时间';
COMMENT ON COLUMN sa_run_profile_tool.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE UNIQUE INDEX IF NOT EXISTS uk_run_profile_tool
    ON sa_run_profile_tool (tenant_id, profile_id, tool_id)
    WHERE deleted = 0;

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
