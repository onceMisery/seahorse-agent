-- V35: Seahorse Task Facade — 用户任务表
CREATE TABLE IF NOT EXISTS sa_task (
    task_id         VARCHAR(64)  PRIMARY KEY,
    type            VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    user_id         VARCHAR(64)  NOT NULL,
    conversation_id VARCHAR(64),
    run_id          VARCHAR(64),
    agent_id        VARCHAR(64),
    title           VARCHAR(512),
    question        TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sa_task_user    ON sa_task(user_id);
CREATE INDEX IF NOT EXISTS idx_sa_task_status  ON sa_task(status);
CREATE INDEX IF NOT EXISTS idx_sa_task_conv    ON sa_task(conversation_id);
