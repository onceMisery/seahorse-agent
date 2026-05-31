-- AI 模型配置表
CREATE TABLE IF NOT EXISTS sa_ai_model_config (
    id VARCHAR(64) PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    config_type VARCHAR(32) NOT NULL,
    is_encrypted SMALLINT NOT NULL DEFAULT 0,
    description VARCHAR(512),
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_sa_ai_model_config_type
        CHECK (config_type IN ('STRING', 'INTEGER', 'BOOLEAN', 'JSON'))
);

CREATE INDEX IF NOT EXISTS idx_sa_ai_model_config_key
    ON sa_ai_model_config(config_key, deleted);

COMMENT ON TABLE sa_ai_model_config IS 'AI 模型配置表';
COMMENT ON COLUMN sa_ai_model_config.id IS '主键';
COMMENT ON COLUMN sa_ai_model_config.config_key IS '配置键';
COMMENT ON COLUMN sa_ai_model_config.config_value IS '配置值（敏感信息加密存储）';
COMMENT ON COLUMN sa_ai_model_config.config_type IS '配置类型：STRING/INTEGER/BOOLEAN/JSON';
COMMENT ON COLUMN sa_ai_model_config.is_encrypted IS '是否加密：0-否 1-是';
COMMENT ON COLUMN sa_ai_model_config.description IS '配置描述';
COMMENT ON COLUMN sa_ai_model_config.created_by IS '创建人';
COMMENT ON COLUMN sa_ai_model_config.updated_by IS '更新人';
COMMENT ON COLUMN sa_ai_model_config.created_at IS '创建时间';
COMMENT ON COLUMN sa_ai_model_config.updated_at IS '更新时间';
COMMENT ON COLUMN sa_ai_model_config.deleted IS '是否删除：0-否 1-是';

-- 初始化默认配置
INSERT INTO sa_ai_model_config (id, config_key, config_value, config_type, is_encrypted, description, created_by, created_at, updated_at, deleted)
VALUES
    ('ai-config-1', 'ai.base.url', 'https://api.siliconflow.cn/v1', 'STRING', 0, 'AI 服务基础地址', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('ai-config-2', 'ai.api.key', '3JvECMxYoV7DShtYsEhBxA==', 'STRING', 1, 'AI 服务 API 密钥占位值，请通过系统设置界面配置真实密钥', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('ai-config-3', 'ai.chat.model', 'deepseek-ai/DeepSeek-V3.2', 'STRING', 0, '对话模型', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('ai-config-4', 'ai.embedding.model', 'BAAI/bge-m3', 'STRING', 0, '向量化模型', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('ai-config-5', 'ai.rerank.model', 'Qwen/Qwen3-Reranker-8B', 'STRING', 0, '重排序模型', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (config_key) DO NOTHING;
