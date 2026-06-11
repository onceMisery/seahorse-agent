-- V20: 修复向量维度不匹配问题
-- nomic-embed-text模型生成768维向量，但表定义为1024维

-- 修改t_knowledge_vector表的向量维度
ALTER TABLE t_knowledge_vector ALTER COLUMN embedding TYPE vector(768);

-- 注释说明
COMMENT ON COLUMN t_knowledge_vector.embedding IS '768维向量，匹配nomic-embed-text模型';
