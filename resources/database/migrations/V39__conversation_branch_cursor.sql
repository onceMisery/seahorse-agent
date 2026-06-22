-- ============================================================================
-- V39: conversation branch cursor
-- ============================================================================

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
