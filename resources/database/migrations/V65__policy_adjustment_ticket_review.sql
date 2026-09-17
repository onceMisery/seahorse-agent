-- 自学习闭环的回访记录：已落地（APPLIED）工单的"上一次改动到底有没有用"。
--
-- 没有这张表之前，回访结论只活在当次 LearningLoopReport 里，算完就丢：
-- 下一次运行不知道这张工单上次回访的结论是什么，也无法判断"是不是刚回访过"。
-- 工单落到 APPLIED 之后就停在那里，闭环事实上是断的。
--
-- 设计要点：
--   1. 只追加（append-only）。一张工单在其生命周期里会被回访多次，
--      历史结论不能互相覆盖，否则无法回答"上一次是被判定为改善还是回归"。
--   2. (ticket_id, batch_id) 唯一索引是幂等的最后一道防线。
--      应用层的"先查再写"在并发下拦不住重复，必须靠数据库约束兜底，
--      写入语句统一用 ON CONFLICT DO NOTHING。
--   3. outcome 存枚举名而非数值：这张表主要是给人看的。

CREATE TABLE IF NOT EXISTS sa_policy_adjustment_ticket_review (
    pk_id              BIGSERIAL PRIMARY KEY,
    review_id          VARCHAR(64)  NOT NULL,
    ticket_id          VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(64)  NOT NULL DEFAULT 'default',
    batch_id           VARCHAR(64)  NOT NULL,
    outcome            VARCHAR(32)  NOT NULL,
    detail             TEXT,
    baseline_pass_rate DOUBLE PRECISION,
    current_pass_rate  DOUBLE PRECISION,
    source             VARCHAR(64),
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sa_policy_review_id UNIQUE (review_id),
    CONSTRAINT uk_sa_policy_review_batch UNIQUE (ticket_id, batch_id)
);

CREATE INDEX IF NOT EXISTS idx_sa_policy_review_ticket
  ON sa_policy_adjustment_ticket_review(ticket_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_sa_policy_review_tenant
  ON sa_policy_adjustment_ticket_review(tenant_id, create_time DESC);
