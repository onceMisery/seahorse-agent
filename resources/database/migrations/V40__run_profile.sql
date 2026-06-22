-- ============================================================================
-- V40: run profile
-- ============================================================================

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
COMMENT ON COLUMN sa_run_profile.enabled IS '是否为当前用户默认启用画像，0 表示否，1 表示是';
COMMENT ON COLUMN sa_run_profile.create_time IS '创建时间';
COMMENT ON COLUMN sa_run_profile.update_time IS '更新时间';
COMMENT ON COLUMN sa_run_profile.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE INDEX IF NOT EXISTS idx_run_profile_user
    ON sa_run_profile (tenant_id, user_id, enabled, deleted);

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

ALTER TABLE sa_run_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE sa_run_profile FORCE ROW LEVEL SECURITY;
ALTER TABLE sa_run_profile_tool ENABLE ROW LEVEL SECURITY;
ALTER TABLE sa_run_profile_tool FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS rls_tenant_isolation ON sa_run_profile;
CREATE POLICY rls_tenant_isolation ON sa_run_profile
    USING (tenant_id = current_setting('app.current_tenant_id', true));

DROP POLICY IF EXISTS rls_tenant_isolation ON sa_run_profile_tool;
CREATE POLICY rls_tenant_isolation ON sa_run_profile_tool
    USING (tenant_id = current_setting('app.current_tenant_id', true));
