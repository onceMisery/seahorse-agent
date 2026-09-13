-- Agent 协作授权策略（Multi-Agent A2A 设计 §5.3 / §11）
-- 显式 source→target 授权规则；无策略时的放行/拒绝行为由端口实现声明
CREATE TABLE IF NOT EXISTS sa_agent_collaboration_policy (
    policy_id       VARCHAR(64)  PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    source_agent_id VARCHAR(64)  NOT NULL,
    target_agent_id VARCHAR(64)  NOT NULL,
    allowed         BOOLEAN      NOT NULL,
    max_depth       INTEGER,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_collab_policy_pair
    ON sa_agent_collaboration_policy (tenant_id, source_agent_id, target_agent_id);
