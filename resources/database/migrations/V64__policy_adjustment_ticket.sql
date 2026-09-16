-- 自学习闭环：为策略调整建议补上"落地工单"载体（持久化 + 审批流）。
--
-- 背景：KernelLearningLoopService 此前只产出内存中的 PolicyAdjustmentProposal，
-- 它能进看板，但没有身份、不能追责、也无法被下一轮闭环回访。本脚本建工单表。
--
-- 为什么独立建表而不复用 sa_approval_request：
--   sa_approval_request 是 run/step 维度、与工具执行强耦合（带 tool_id、run_id、
--   tool_invocation_id），工单是"策略配置"维度，两者生命周期与查询方式都不同。
--   硬塞进同一张表只会让两边都多出一批永远为空的列。
--   只复用它的状态迁移模式（按期望状态做乐观更新）。
--
-- 迁移安全性：
--   1. CREATE TABLE IF NOT EXISTS，可重复执行（幂等）
--   2. 纯新增，不改动任何既有表
--   3. 未配置该端口时内核走 empty() 实现，建表与否都不影响启动

CREATE TABLE IF NOT EXISTS sa_policy_adjustment_ticket (
    pk_id              BIGSERIAL PRIMARY KEY,
    ticket_id          VARCHAR(64)  NOT NULL UNIQUE,
    tenant_id          VARCHAR(64)  NOT NULL DEFAULT 'default',
    target_key         VARCHAR(255) NOT NULL DEFAULT '',
    policy_key         VARCHAR(255) NOT NULL DEFAULT '',
    direction          VARCHAR(32)  NOT NULL,
    suggested_delta    DOUBLE PRECISION NOT NULL DEFAULT 0,
    disapproval_rate   DOUBLE PRECISION NOT NULL DEFAULT 0,
    sample_count       INTEGER      NOT NULL DEFAULT 0,
    rationale          TEXT,
    evidence_ids       TEXT,
    fingerprint        VARCHAR(512) NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    created_by         VARCHAR(128),
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    decided_by         VARCHAR(128),
    decided_at         TIMESTAMP,
    decision_comment   TEXT,
    applied_by         VARCHAR(128),
    applied_at         TIMESTAMP,
    rollback_comment   TEXT
);

-- 运营看板按 租户 + 状态 翻页查待办。
CREATE INDEX IF NOT EXISTS idx_policy_ticket_tenant_status
    ON sa_policy_adjustment_ticket (tenant_id, status, created_at DESC);

-- 闭环每轮都要按指纹去重，否则待办会被同一条建议刷满。
CREATE INDEX IF NOT EXISTS idx_policy_ticket_fingerprint
    ON sa_policy_adjustment_ticket (tenant_id, fingerprint);
