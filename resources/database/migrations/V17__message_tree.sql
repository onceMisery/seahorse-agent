-- ============================================================================
-- V17: message tree / conversation branches
-- ============================================================================

ALTER TABLE t_message ADD COLUMN IF NOT EXISTS parent_id BIGINT;
ALTER TABLE t_message ADD COLUMN IF NOT EXISTS active SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE t_message ADD COLUMN IF NOT EXISTS branch_root_id BIGINT;
ALTER TABLE t_message ADD COLUMN IF NOT EXISTS sibling_seq INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN t_message.parent_id IS '父消息 ID，用于把线性会话消息组织为可分支的消息树，根消息为空';
COMMENT ON COLUMN t_message.active IS '是否属于当前默认可见路径，0 表示否，1 表示是';
COMMENT ON COLUMN t_message.branch_root_id IS '分支根消息 ID，用于标识一次 fork 或重新生成产生的分支起点';
COMMENT ON COLUMN t_message.sibling_seq IS '同一父消息下的兄弟消息序号，用于稳定排序和分支切换';

-- Existing conversations were written as a linear timeline. Backfill them into
-- a single chain so tree assembly can still return the full historical path.
WITH ordered AS (
    SELECT id,
           LAG(id) OVER (
               PARTITION BY tenant_id, conversation_id, user_id
               ORDER BY create_time ASC, id ASC
           ) AS previous_id
    FROM t_message
    WHERE deleted = 0
      AND parent_id IS NULL
      AND sibling_seq = 0
      AND branch_root_id IS NULL
),
linear AS (
    SELECT id, previous_id
    FROM ordered
    WHERE previous_id IS NOT NULL
)
UPDATE t_message m
SET parent_id = linear.previous_id,
    active = 1,
    sibling_seq = 0
FROM linear
WHERE m.id = linear.id
  AND m.parent_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_t_message_parent
    ON t_message (tenant_id, conversation_id, user_id, parent_id, sibling_seq);

CREATE INDEX IF NOT EXISTS idx_t_message_active
    ON t_message (tenant_id, conversation_id, user_id, active, create_time);

CREATE UNIQUE INDEX IF NOT EXISTS uk_t_message_sibling_seq
    ON t_message (tenant_id, conversation_id, user_id, COALESCE(parent_id, 0), sibling_seq)
    WHERE deleted = 0;
