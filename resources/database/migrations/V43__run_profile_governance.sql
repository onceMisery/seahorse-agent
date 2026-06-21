-- ============================================================================
-- V43: run profile governance metadata
-- ============================================================================

ALTER TABLE sa_run_profile
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT';

ALTER TABLE sa_run_profile
    ADD COLUMN IF NOT EXISTS approval_operator VARCHAR(64);

ALTER TABLE sa_run_profile
    ADD COLUMN IF NOT EXISTS approval_comment TEXT;

ALTER TABLE sa_run_profile
    ADD COLUMN IF NOT EXISTS approval_time TIMESTAMP;

COMMENT ON COLUMN sa_run_profile.approval_status IS '运行画像审批状态，例如 DRAFT、PENDING_APPROVAL、APPROVED、REJECTED';
COMMENT ON COLUMN sa_run_profile.approval_operator IS '运行画像审批操作人或处理人标识';
COMMENT ON COLUMN sa_run_profile.approval_comment IS '运行画像审批提交、通过或拒绝时填写的说明';
COMMENT ON COLUMN sa_run_profile.approval_time IS '运行画像最近一次审批状态变更时间';

CREATE INDEX IF NOT EXISTS idx_run_profile_approval
    ON sa_run_profile (tenant_id, approval_status, deleted);
