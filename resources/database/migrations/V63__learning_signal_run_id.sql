-- 自学习闭环：为反馈与复核补上共有维度，使两类信号能按同一维度聚合。
--
-- 背景：反馈按 reason 聚合、复核按 targetKey 聚合，两者此前只有 userId 能对应上，
-- 粒度无法对齐。本脚本补两列：
--   session_id —— 会话维度（取值即 conversationId），两侧同源，是真正的共有维度
--   run_id     —— 运行维度，留痕用；复核发生在人工治理链路、捕获点拿不到 runId，故留 NULL
--
-- 迁移安全性：
--   1. 所有列均可空，ADD COLUMN 对已有部署只改元数据、不重写表
--   2. ADD COLUMN IF NOT EXISTS 可重复执行（幂等）
--   3. 回填语句带 IS NULL 条件，同样幂等
--   4. 回填失败不影响表结构，聚合侧显式跳过空值，不会把空值聚成一个桶

ALTER TABLE t_message_feedback              ADD COLUMN IF NOT EXISTS run_id     VARCHAR(64);
ALTER TABLE t_memory_review_feedback_sample ADD COLUMN IF NOT EXISTS session_id VARCHAR(64);
ALTER TABLE t_memory_review_feedback_sample ADD COLUMN IF NOT EXISTS run_id     VARCHAR(64);

-- 历史反馈回填运行标识：反馈表自身不掌握 runId，需从消息表反查。
UPDATE t_message_feedback f
SET run_id = m.agent_run_id
FROM t_message m
WHERE f.message_id = m.id
  AND f.run_id IS NULL
  AND m.agent_run_id IS NOT NULL;

-- 历史复核样本回填会话维度：候选表已保存 conversation_id，按 candidate_id 回补。
UPDATE t_memory_review_feedback_sample s
SET session_id = c.conversation_id
FROM t_memory_review_candidate c
WHERE s.candidate_id = c.id
  AND s.session_id IS NULL
  AND c.conversation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_message_feedback_run  ON t_message_feedback (run_id);
CREATE INDEX IF NOT EXISTS idx_memory_review_sample_session ON t_memory_review_feedback_sample (session_id);
CREATE INDEX IF NOT EXISTS idx_memory_review_sample_run     ON t_memory_review_feedback_sample (run_id);
