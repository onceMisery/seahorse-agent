-- Agent 团队编排（Multi-Agent A2A 设计 §6 P1 / §11）
-- 团队定义：成员与协作边序列化在 definition_json 中
CREATE TABLE IF NOT EXISTS sa_agent_team (
    team_id              VARCHAR(64)  PRIMARY KEY,
    tenant_id            VARCHAR(64)  NOT NULL,
    name                 VARCHAR(128) NOT NULL,
    mode                 VARCHAR(32)  NOT NULL,
    owner_team           VARCHAR(128) DEFAULT '',
    supervisor_member_id VARCHAR(64),
    status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    definition_json      TEXT         NOT NULL,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_team_tenant ON sa_agent_team (tenant_id);

-- 团队运行：节点运行序列化在 node_runs_json 中；parent_run_id 指向 triggerType=TEAM 的锚定 AgentRun
CREATE TABLE IF NOT EXISTS sa_agent_team_run (
    team_run_id    VARCHAR(64)  PRIMARY KEY,
    team_id        VARCHAR(64)  NOT NULL,
    tenant_id      VARCHAR(64)  NOT NULL,
    user_id        VARCHAR(64),
    mode           VARCHAR(32)  NOT NULL,
    objective      TEXT,
    status         VARCHAR(32)  NOT NULL,
    parent_run_id  VARCHAR(64),
    summary        TEXT,
    error_code     VARCHAR(64),
    error_message  TEXT,
    node_runs_json TEXT,
    started_at     TIMESTAMP    NOT NULL,
    finished_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_team_run_team ON sa_agent_team_run (team_id, started_at);
CREATE INDEX IF NOT EXISTS idx_agent_team_run_tenant ON sa_agent_team_run (tenant_id, started_at);
