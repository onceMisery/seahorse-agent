# Skill 语义匹配 - 快速开始指南

## 🚀 5分钟快速启动

### 前置条件

1. ✅ Milvus 已运行（docker-compose）
2. ✅ Embedding 服务已配置（OpenAI API 或其他）
3. ✅ 项目已编译

### 步骤 1：配置 Embedding 服务

编辑 `application.yml`：

```yaml
seahorse:
  agent:
    adapters:
      ai:
        openai:
          api-key: sk-your-openai-api-key  # 或使用环境变量
          base-url: https://api.openai.com/v1
          embedding-model: text-embedding-3-small
```

### 步骤 2：启动应用

```bash
mvn spring-boot:run
```

### 步骤 3：验证功能

查看启动日志，确认以下信息：

```
✅ INFO  SeahorseAgentSkillVectorIndexAutoConfiguration - Configuring Milvus skill vector index repository
✅ INFO  SeahorseAgentSkillVectorIndexAutoConfiguration - Configuring EmbeddingPort adapter
✅ INFO  SkillVectorIndexService - Created Milvus collection: seahorse_skill_vectors (dimension: 1536)
✅ INFO  SeahorseAgentSkillVectorIndexAutoConfiguration - Skill vector index service initialized
```

### 步骤 4：测试语义匹配

```bash
# 发送聊天请求（不指定 Skill）
curl -X POST http://localhost:9090/rag/v3/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "帮我分析销售数据的趋势并生成可视化图表",
    "conversationId": "test-semantic-001",
    "userId": "test-user"
  }'
```

### 步骤 5：检查匹配结果

查看日志：

```
✅ INFO  SkillSemanticMatcher - Semantic skill matching for question '帮我分析销售数据...': [data-analysis] (embedding model: text-embedding-3-small)
✅ INFO  KernelChatInboundService - Smart skill matching triggered: recommendations=[data-analysis]
```

## 🎯 工作原理

```
用户问题："帮我分析销售数据的趋势"
    ↓
[步骤1] 向量化
Embedding API: text → vector[1536]
    ↓
[步骤2] 向量搜索
Milvus: 相似度搜索 → Top 3 相似 Skill
    ↓
[步骤3] 混合评分
向量相似度(70%) + 规则增强(30%)
    ↓
[步骤4] 返回推荐
["data-analysis", "visualization-tool"]
    ↓
[步骤5] 自动加载
注入到 system prompt，LLM 执行
```

## ⚙️ 配置选项

### 禁用语义匹配（回退到规则匹配）

```yaml
seahorse:
  agent:
    skill:
      vector-index:
        enabled: false  # 禁用向量索引
```

### 使用不同的 Embedding 模型

```yaml
# OpenAI（推荐）
seahorse:
  agent:
    adapters:
      ai:
        openai:
          embedding-model: text-embedding-3-large  # 更高质量，3072维

# 通义千问
seahorse:
  agent:
    adapters:
      ai:
        openai:
          base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
          api-key: ${DASHSCOPE_API_KEY}
          embedding-model: text-embedding-v2

# 智谱 AI
seahorse:
  agent:
    adapters:
      ai:
        openai:
          base-url: https://open.bigmodel.cn/api/paas/v4
          api-key: ${ZHIPU_API_KEY}
          embedding-model: embedding-2
```

## 🔍 常见问题

### Q1: 为什么没有使用语义匹配？

**检查清单**：
- [ ] `EmbeddingModelPort` Bean 是否已注册？
- [ ] Embedding API 密钥是否配置正确？
- [ ] 日志中是否有 "Configuring EmbeddingPort adapter"？

### Q2: 向量搜索返回空结果？

**可能原因**：
- Skill 向量索引尚未创建
- Milvus Collection 不存在

**解决方法**：
```bash
# 查看 Milvus Collection
docker exec -it milvus-standalone milvus-cli

# 列出所有 Collection
> list collections

# 检查 seahorse_skill_vectors 是否存在
# 如果不存在，重启应用会自动创建
```

### Q3: 匹配结果不准确？

**优化建议**：
1. 使用更强的 Embedding 模型（text-embedding-3-large）
2. 确保 Skill 的 `description` 和 `tags` 准确描述其功能
3. 调整混合评分权重（需修改代码）

### Q4: 响应速度慢？

**性能优化**：
1. 使用本地部署的 Embedding 模型（避免网络延迟）
2. 启用 Embedding 结果缓存
3. 减少推荐数量（默认 Top 3）

## 📊 监控指标

### Prometheus 指标（建议添加）

```java
// 在 SkillSemanticMatcher 中添加
@Timed(value = "skill.semantic.match", description = "Semantic skill matching duration")
@Counted(value = "skill.semantic.match.total", description = "Total semantic matching attempts")
@Counted(value = "skill.semantic.match.success", description = "Successful matches")
@Counted(value = "skill.semantic.match.fallback", description = "Fallback to rule-based matching")
```

### 日志级别调整

```yaml
logging:
  level:
    com.miracle.ai.seahorse.agent.kernel.application.chat.SkillSemanticMatcher: DEBUG
    com.miracle.ai.seahorse.agent.kernel.application.agent.skill.SkillVectorIndexService: DEBUG
```

## 🛠️ 故障排查

### 问题：EmbeddingPort 为 noop

```
WARN  SeahorseAgentSkillVectorIndexAutoConfiguration - No embedding model available, using noop embedding port
```

**原因**：`EmbeddingModelPort` Bean 未注册

**解决**：
1. 检查 `SeahorseAgentAiAdapterAutoConfiguration` 是否启用
2. 确认 AI 适配器配置正确
3. 查看 `application.yml` 中的 `seahorse.agent.adapters.ai` 配置

### 问题：Milvus 连接失败

```
ERROR MilvusSkillVectorIndexAdapter - Failed to create collection
```

**解决**：
```bash
# 检查 Milvus 状态
docker-compose ps milvus

# 重启 Milvus
docker-compose restart milvus

# 检查连接
telnet localhost 19530
```

### 问题：Skill 索引未自动创建

**手动触发索引**：

```bash
# 方法1：重启应用（会自动初始化）
mvn spring-boot:run

# 方法2：调用管理接口（需要实现）
POST /api/admin/skills/rebuild-index?tenantId=default
```

## 📚 延伸阅读

- [完整实现文档](./SKILL-SEMANTIC-MATCHING-IMPLEMENTATION.md)
- [架构设计说明](./SKILL-REVIEW.md)
- [Milvus 官方文档](https://milvus.io/docs)
- [OpenAI Embeddings API](https://platform.openai.com/docs/guides/embeddings)

## ✅ 成功标志

系统正常工作时，你应该看到：

1. ✅ 启动日志中有 "Skill vector index service initialized"
2. ✅ 聊天时日志有 "Semantic skill matching for question"
3. ✅ Skill 创建后日志有 "Indexed skill: xxx"
4. ✅ 聊天响应中 Agent 使用了推荐的 Skill

## 🎉 总结

语义匹配功能现在已完全集成，具备：

- ✅ **自动化**：无需手动配置，开箱即用
- ✅ **智能化**：深度语义理解，高准确率
- ✅ **可靠性**：自动降级，向量服务故障不影响使用
- ✅ **可扩展**：支持多种 Embedding 模型和向量数据库

如有问题，请查看完整文档或联系开发团队！
