-- ============================================================================
-- V41: conversation-level run profile binding
-- ============================================================================

ALTER TABLE t_conversation ADD COLUMN IF NOT EXISTS run_profile_id BIGINT;

COMMENT ON COLUMN t_conversation.run_profile_id IS '当前会话应用的运行画像 ID，用于未显式指定 runProfileId 的后续 Chat 或 Agent Run 继承画像配置';

CREATE INDEX IF NOT EXISTS idx_t_conversation_run_profile
    ON t_conversation (tenant_id, user_id, run_profile_id)
    WHERE deleted = 0 AND run_profile_id IS NOT NULL;
