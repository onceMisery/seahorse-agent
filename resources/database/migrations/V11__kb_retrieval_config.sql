-- V11: Add retrieval_config column to t_knowledge_base for configurable retrieval strategies
-- Supports JSONB storage of retrieval parameters (top-k, threshold, re-ranking config, etc.)

ALTER TABLE t_knowledge_base ADD COLUMN IF NOT EXISTS retrieval_config JSONB;

COMMENT ON COLUMN t_knowledge_base.retrieval_config IS
    '检索策略配置（JSONB），包含 top-k、相似度阈值、重排序策略等参数';
