# Seahorse Agent 功能实战案例

**目标**: 通过实际案例讲解Seahorse项目工作原理  
**场景**: 创建一个关于"Seahorse架构"的知识库，演示完整的RAG流程

---

## 案例1: 知识库管理 - 创建架构知识库

### 功能说明
知识库是Seahorse的核心功能，支持文档向量化、语义检索和RAG增强回答。

### API端点
- 创建: `POST /knowledge-base`
- 上传文档: `POST /knowledge-base/{id}/documents`
- 查询: `GET /knowledge-base`

### 实战步骤

#### 1. 创建知识库
```bash
curl -X POST http://localhost:9090/knowledge-base \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Seahorse架构知识库",
    "embeddingModel": "nomic-embed-text"
  }'
```

**预期返回**:
```json
{
  "code": "0",
  "data": {
    "id": "...",
    "name": "Seahorse架构知识库",
    "embeddingModel": "nomic-embed-text"
  }
}
```

#### 2. 上传文档
```bash
curl -X POST http://localhost:9090/knowledge-base/{kbId}/documents \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@seahorse-architecture.md"
```

#### 3. 查询知识库
```bash
curl -X GET http://localhost:9090/knowledge-base/{kbId} \
  -H "Authorization: Bearer $TOKEN"
```

### 工作原理
1. **文档解析**: Tika adapter解析文档（支持PDF、Word、Markdown等）
2. **文本分块**: 将文档切分为chunk（默认500 tokens）
3. **向量化**: Ollama nomic-embed-text生成768维向量
4. **存储**: 向量存入PostgreSQL pgvector，文本存入t_knowledge_chunk
5. **索引**: HNSW索引支持快速语义检索

---

## 案例2: 对话管理 - 创建带知识库的会话

### 功能说明
对话功能支持多轮记忆、知识库检索和Agent工具调用。

### API端点
- 创建会话: `POST /conversations`
- 发送消息: `POST /chat`
- 查询历史: `GET /conversations/{id}/messages`

### 实战步骤

#### 1. 创建会话
```bash
curl -X POST http://localhost:9090/conversations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Seahorse架构讨论",
    "knowledgeBaseIds": ["'${KB_ID}'"]
  }'
```

#### 2. 发送消息（流式响应）
```bash
curl -X POST http://localhost:9090/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "'${CONV_ID}'",
    "content": "Seahorse使用什么架构模式？",
    "stream": true
  }'
```

### 工作原理（RAG流程）
1. **用户提问**: "Seahorse使用什么架构模式？"
2. **向量检索**: 
   - Ollama生成问题向量（768维）
   - pgvector HNSW索引查询top-k相似chunk
3. **上下文增强**:
   - 检索到: "Seahorse采用六边形架构（端口-适配器模式）..."
   - 组装prompt: 系统提示 + 知识库上下文 + 用户问题
4. **LLM生成**:
   - 调用OpenAI-compatible API（配置为Ollama qwen2.5:7b）
   - 流式返回答案
5. **记忆存储**:
   - 用户消息和AI回复存入t_message
   - conversation_id关联多轮对话

---

## 案例3: Agent工具调用 - 图表生成

### 功能说明
Seahorse内置多个Generation Tools，支持图表、PPT、图片等内容生成。

### API端点
- 工具列表: `GET /tools`
- 调用工具: 通过chat自动触发

### 实战步骤

#### 1. 查询可用工具
```bash
curl -X GET http://localhost:9090/tools \
  -H "Authorization: Bearer $TOKEN"
```

#### 2. 触发图表生成
```bash
curl -X POST http://localhost:9090/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "'${CONV_ID}'",
    "content": "请用饼图展示Seahorse的模块分布：kernel 40%、adapters 35%、web 15%、tests 10%"
  }'
```

### 工作原理
1. **意图识别**: LLM识别需要调用chart-visualization工具
2. **工具调用**:
   - 工具ID: `chart-visualization`
   - 参数: `{"type":"pie", "data":[...]}`
3. **Artifact生成**:
   - ChartVisualizationToolPortAdapter生成ECharts配置
   - 返回artifact_id和content
4. **前端渲染**:
   - ArtifactInspectorTab接收artifact
   - 使用ECharts渲染交互式图表

---

## 案例4: 用户权限管理

### 功能说明
支持多租户隔离、角色权限控制（admin/user）。

### API端点
- 创建用户: `POST /users`
- 查询用户: `GET /users`
- 删除用户: `DELETE /users/{id}`

### 实战步骤

#### 1. 创建普通用户
```bash
curl -X POST http://localhost:9090/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "demo_user",
    "password": "demo123",
    "role": "user"
  }'
```

#### 2. 测试用户权限
```bash
# 普通用户登录
USER_TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo_user","password":"demo123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

# 尝试访问管理功能（应该403）
curl -X GET http://localhost:9090/users \
  -H "Authorization: Bearer $USER_TOKEN"
```

### 工作原理
1. **认证**: sa-token验证Bearer token
2. **租户隔离**:
   - TenantContext从session提取tenant_id
   - PostgreSQL RLS强制行级安全
3. **权限检查**:
   - SeahorseSecurityWebMvcConfiguration拦截器
   - ADMIN_PATH_PREFIXES（/users, /admin等）需要admin角色
4. **数据隔离**:
   - t_knowledge_base.tenant_id过滤
   - JdbcTenantSupport自动注入WHERE条件

---

## 案例5: Agent Run监控

### 功能说明
查看Agent执行详情、成本统计、事件时间线。

### API端点
- Run详情: `GET /agent-runs/{runId}`
- 成本统计: `GET /agent-runs/{runId}/cost`
- 事件列表: `GET /agent-runs/{runId}/events`

### 实战步骤

#### 1. 触发Agent Run
```bash
RUN_RESPONSE=$(curl -s -X POST http://localhost:9090/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "'${CONV_ID}'",
    "content": "总结Seahorse的核心优势"
  }')

RUN_ID=$(echo "$RUN_RESPONSE" | grep -o '"runId":"[^"]*' | cut -d'"' -f4)
```

#### 2. 查询Run详情
```bash
curl -X GET "http://localhost:9090/agent-runs/$RUN_ID" \
  -H "Authorization: Bearer $TOKEN"
```

#### 3. 查看成本
```bash
curl -X GET "http://localhost:9090/agent-runs/$RUN_ID/cost" \
  -H "Authorization: Bearer $TOKEN"
```

### 工作原理
1. **Run创建**: KernelAgentChatService创建AgentRun记录
2. **事件记录**:
   - agent.step.started
   - agent.tool.called
   - agent.source.found
   - agent.artifact.content
3. **成本计算**:
   - 统计input_tokens和output_tokens
   - 根据模型定价计算成本
4. **Snapshot持久化**:
   - 定期保存Run状态到t_agent_run_snapshot
   - 支持断点恢复

---

## 案例6: 知识库检索测试

### 功能说明
直接测试知识库的语义检索能力。

### API端点
- 检索: `POST /knowledge-base/{id}/search`

### 实战步骤

```bash
curl -X POST "http://localhost:9090/knowledge-base/$KB_ID/search" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "六边形架构的核心是什么",
    "topK": 3
  }'
```

**预期返回**:
```json
{
  "code": "0",
  "data": {
    "chunks": [
      {
        "content": "Seahorse采用六边形架构，核心是kernel模块...",
        "score": 0.85
      }
    ]
  }
}
```

### 工作原理
1. **查询向量化**: Ollama生成query的768维向量
2. **相似度计算**: pgvector执行cosine distance查询
3. **HNSW加速**: 索引支持O(log n)复杂度
4. **排序返回**: 按score降序返回top-k结果

---

## 下一步

执行这些案例并验证功能正常性...
