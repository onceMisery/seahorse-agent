# Seahorse Agent 后台管理功能使用指南

本指南提供实际可运行的案例，帮助您快速上手Seahorse Agent的后台管理功能。

---

## 前置准备

### 1. 获取管理员Token

```bash
TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.data.token')

echo "Token: $TOKEN"
```

### 2. 配置环境

确保以下服务运行正常：
- Backend: http://localhost:9090 ✅
- PostgreSQL: localhost:5432 ✅
- Redis: localhost:6379 ✅
- Milvus: localhost:19530 ✅
- Ollama: http://localhost:11434 ✅

---

## 功能1: 知识库管理

### 创建知识库

```bash
curl -X POST http://localhost:9090/knowledge-base \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Knowledge Base",
    "collectionName": "my_kb_001",
    "embeddingModel": "nomic-embed-text",
    "description": "My first knowledge base"
  }'
```

**响应示例**:
```json
{
  "code": "0",
  "data": "323449787897925632"
}
```

### 上传文档

```bash
KB_ID="323449787897925632"

curl -X POST "http://localhost:9090/knowledge-base/$KB_ID/docs/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/document.pdf"
```

### 触发文档处理

```bash
DOC_ID="<返回的document_id>"

curl -X POST "http://localhost:9090/knowledge-base/docs/$DOC_ID/chunk" \
  -H "Authorization: Bearer $TOKEN"
```

**⚠️ 已知问题**: 文档处理consumer未实现，status会保持running。临时方案：使用已有知识库(ID: 99999)进行测试。

### 查询知识库

```bash
# 列表查询
curl -X GET "http://localhost:9090/knowledge-base?size=10" \
  -H "Authorization: Bearer $TOKEN"

# 按名称搜索
curl -X GET "http://localhost:9090/knowledge-base?name=My+Knowledge+Base" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 功能2: RAG对话

### 创建会话

```bash
KB_ID="99999"  # 使用已有知识库

CONV_RESP=$(curl -s -X POST "http://localhost:9090/conversations" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"title\": \"My Conversation\",
    \"knowledgeBaseIds\": [\"$KB_ID\"]
  }")

CONV_ID=$(echo "$CONV_RESP" | jq -r '.data')
echo "Conversation ID: $CONV_ID"
```

### 发送消息（流式对话）

```bash
curl -N -X GET "http://localhost:9090/rag/v3/chat" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: text/event-stream" \
  -G \
  --data-urlencode "conversationId=$CONV_ID" \
  --data-urlencode "question=What is Seahorse Agent?" \
  --data-urlencode "knowledgeBaseIds=$KB_ID"
```

**响应格式** (SSE):
```
event:meta
data:{"conversationId":"...","taskId":"...","runId":null}

event:message
data:{"type":"response","delta":"Seahorse"}

event:message
data:{"type":"response","delta":" Agent"}
...
event:done
data:[DONE]
```

### 查询对话历史

```bash
curl -X GET "http://localhost:9090/conversations/$CONV_ID/messages?size=10" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 功能3: Agent工具调用

### 查询可用工具

```bash
curl -X GET "http://localhost:9090/tools" \
  -H "Authorization: Bearer $TOKEN" | jq '.data[] | {id, name}'
```

**可用工具**:
- `chart-visualization`: 图表生成
- `image-generation`: 图片生成
- `ppt-generation`: PPT生成
- `newsletter-generation`: Newsletter生成

### 触发工具（通过对话）

工具由LLM自动识别触发，无需手动调用：

```bash
# 示例：图表生成
curl -N -X GET "http://localhost:9090/rag/v3/chat" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: text/event-stream" \
  -G \
  --data-urlencode "conversationId=$CONV_ID" \
  --data-urlencode "question=Create a pie chart showing: Sales 40%, Marketing 30%, R&D 20%, Other 10%"
```

**工作原理**:
1. LLM识别需要调用图表工具
2. 生成工具参数（type, data等）
3. ChartVisualizationToolPortAdapter执行
4. 返回artifact（ECharts配置）
5. 前端渲染交互式图表

---

## 功能4: 用户权限管理

### 创建普通用户

```bash
curl -X POST "http://localhost:9090/users" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "demo_user",
    "password": "demo123",
    "role": "user"
  }'
```

### 测试用户登录

```bash
USER_TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo_user","password":"demo123"}' \
  | jq -r '.data.token')

echo "User Token: $USER_TOKEN"
```

### 验证权限隔离

```bash
# 普通用户访问管理API（应该失败）
curl -X GET "http://localhost:9090/users" \
  -H "Authorization: Bearer $USER_TOKEN"
# 响应: {"code":"1","message":"无管理员权限"}

# 普通用户访问自己的数据（应该成功）
curl -X GET "http://localhost:9090/knowledge-base" \
  -H "Authorization: Bearer $USER_TOKEN"
# 响应: 返回该用户的知识库列表
```

**权限说明**:
- **Admin**: 可访问所有API，包括用户管理、系统配置等
- **User**: 只能访问自己的数据，受RLS策略保护

---

## 功能5: 对话列表查询

### 查询我的对话

```bash
curl -X GET "http://localhost:9090/conversations?size=10" \
  -H "Authorization: Bearer $TOKEN"
```

### 按标题搜索

```bash
curl -X GET "http://localhost:9090/conversations?title=Seahorse" \
  -H "Authorization: Bearer $TOKEN"
```

### 删除对话

```bash
CONV_ID="<conversation_id>"

curl -X DELETE "http://localhost:9090/conversations/$CONV_ID" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 功能6: Agent Run监控

### 查询Agent Run详情

```bash
RUN_ID="<agent_run_id>"

curl -X GET "http://localhost:9090/agent-runs/$RUN_ID" \
  -H "Authorization: Bearer $TOKEN"
```

**返回内容**:
- runId, status, startTime, endTime
- steps: 执行步骤列表
- sources: 检索的知识源
- artifacts: 生成的内容
- costSummary: 成本统计

### 查询成本统计

```bash
curl -X GET "http://localhost:9090/agent-runs/$RUN_ID/cost" \
  -H "Authorization: Bearer $TOKEN"
```

**返回示例**:
```json
{
  "inputTokens": 1500,
  "outputTokens": 800,
  "totalCost": 0.0025
}
```

---

## Seahorse工作原理

### 1. 六边形架构

```
┌─────────────────────────────────────────┐
│           Inbound Adapters              │
│  (Web API, CLI, gRPC)                   │
└──────────────┬──────────────────────────┘
               │
         Inbound Ports
               │
┌──────────────┴──────────────────────────┐
│           Domain Kernel                  │
│  (Business Logic, Use Cases)             │
└──────────────┬──────────────────────────┘
               │
        Outbound Ports
               │
┌──────────────┴──────────────────────────┐
│          Outbound Adapters               │
│  (AI, Vector, Storage, Cache, MQ)        │
└──────────────────────────────────────────┘
```

**优势**:
- 业务逻辑独立于基础设施
- 易于测试和替换适配器
- 清晰的依赖关系

### 2. RAG流程

```
用户提问
   ↓
生成查询向量 (Ollama 768维)
   ↓
pgvector HNSW检索 top-k相似chunk
   ↓
组装增强prompt:
  - 系统提示
  - 知识库上下文
  - 对话历史
  - 用户问题
   ↓
LLM生成回答 (流式输出)
   ↓
存储对话记忆 (t_message表)
```

**关键技术**:
- 向量模型: nomic-embed-text (768维)
- 相似度算法: cosine distance
- 索引: HNSW (O(log n)查询)
- 数据库: PostgreSQL + pgvector扩展

### 3. 多租户隔离

```sql
-- RLS策略示例
CREATE POLICY tenant_isolation 
ON t_knowledge_base
USING (tenant_id = current_setting('app.current_tenant_id'));
```

**机制**:
1. TenantContext从session提取tenant_id
2. 设置PostgreSQL session变量
3. RLS策略自动过滤查询结果
4. 用户只能看到自己的数据

### 4. 认证授权

```
登录 → sa-token生成UUID → Redis持久化
      ↓
请求携带 Authorization: Bearer <token>
      ↓
拦截器验证token → 提取userId → 加载角色
      ↓
权限检查 → 允许/拒绝
```

---

## 常见问题

### Q1: 文档上传后status一直是running？

**A**: 这是已知问题。MQ consumer未实现，文档处理无法自动完成。

**临时方案**: 使用已有的e2e测试知识库（ID: 99999）进行测试。

### Q2: API返回404？

**A**: 检查路径是否正确：
- Chat: `/rag/v3/chat` (不是`/chat`)
- 文档上传: `/knowledge-base/{kb-id}/docs/upload`

### Q3: 登录后立即显示过期？

**A**: 确认token包含Bearer前缀：
```bash
Authorization: Bearer <token>
```

### Q4: 普通用户无法访问数据？

**A**: 检查RLS策略配置和tenant_id设置。

---

## API快速参考

| 功能 | Method | Path | 说明 |
|------|--------|------|------|
| 登录 | POST | /auth/login | 获取token |
| 创建知识库 | POST | /knowledge-base | Admin/User |
| 上传文档 | POST | /knowledge-base/{kb-id}/docs/upload | 需要collectionName |
| 创建会话 | POST | /conversations | 可绑定知识库 |
| RAG对话 | GET | /rag/v3/chat | SSE流式响应 |
| 查询工具 | GET | /tools | 工具列表 |
| 创建用户 | POST | /users | 仅Admin |
| 查询对话 | GET | /conversations | 分页查询 |

---

## 下一步

1. **修复文档处理**: 实现MQ consumer自动处理文档分块
2. **完善API文档**: 添加更多参数说明和示例
3. **增强监控**: Agent Run事件timeline可视化
4. **优化检索**: 支持混合检索（向量+关键词）

---

**文档版本**: 1.0  
**更新日期**: 2026-06-11  
**反馈**: 如有问题请提issue
