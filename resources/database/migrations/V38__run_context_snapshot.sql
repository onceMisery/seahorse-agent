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

ALTER TABLE t_run_context_snapshot ENABLE ROW LEVEL SECURITY;
ALTER TABLE t_run_context_snapshot FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS rls_tenant_isolation ON t_run_context_snapshot;
CREATE POLICY rls_tenant_isolation ON t_run_context_snapshot
    USING (tenant_id = current_setting('app.current_tenant_id', true));
