# Seahorse Agent 用户使用指南

## 快速开始

### 1. 启动服务

使用 Docker Compose 启动完整环境：

```bash
docker compose -f docker-compose.full.yml up -d --build
```

等待所有服务启动完成（约 2-3 分钟），然后检查服务状态：

```bash
docker compose -f docker-compose.full.yml ps
curl http://localhost:9090/actuator/health
```

### 2. 运行 E2E 测试

#### 基础功能测试
```bash
bash seahorse-agent-tests/src/test/scripts/e2e-test.sh
```

验证核心功能：用户认证、知识库管理、对话管理。

#### 完整功能演示
```bash
bash seahorse-agent-tests/src/test/scripts/e2e-complete-demo.sh
```

演示所有 18 个测试用例，覆盖完整的系统工作原理。

#### 扩展功能探索
```bash
bash seahorse-agent-tests/src/test/scripts/e2e-advanced-test.sh
```

探索高级功能接口（部分功能需要特定配置）。

## 核心功能使用

### 用户认证

**登录获取 Token**
```bash
curl -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

返回响应包含 `token` 和 `userId`，后续请求需要携带：
- Header: `Authorization: Bearer {token}`
- Header: `X-User-Id: {userId}`

**查询当前用户信息**
```bash
curl -X GET http://localhost:9090/user/me \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

### 知识库管理

**创建知识库**
```bash
curl -X POST http://localhost:9090/knowledge-base \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}" \
  -d '{
    "name": "我的知识库",
    "embeddingModel": "nomic-embed-text",
    "collectionName": "my_kb_001"
  }'
```

**查询知识库列表**
```bash
curl -X GET "http://localhost:9090/knowledge-base?current=1&size=10" \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

**查询知识库详情**
```bash
curl -X GET http://localhost:9090/knowledge-base/{kb-id} \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

**删除知识库**
```bash
curl -X DELETE http://localhost:9090/knowledge-base/{kb-id} \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

### 对话管理

**创建对话**
```bash
curl -X POST http://localhost:9090/conversations \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

**查询对话列表**
```bash
curl -X GET http://localhost:9090/conversations \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

**查询对话消息**
```bash
curl -X GET http://localhost:9090/conversations/{conversation-id}/messages \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

**重命名对话**
```bash
curl -X PUT http://localhost:9090/conversations/{conversation-id} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}" \
  -d '{"title": "新标题"}'
```

**删除对话**
```bash
curl -X DELETE http://localhost:9090/conversations/{conversation-id} \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

### Memory 系统

**查询 Memory 列表**
```bash
curl -X GET "http://localhost:9090/memories?current=1&size=10" \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

**查询 Memory 追踪**
```bash
curl -X GET "http://localhost:9090/memories/traces?current=1&size=10" \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

### Agent 管理

**查询 Agent 定义**
```bash
curl -X GET "http://localhost:9090/api/agents?current=1&size=10" \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

**查询技能列表**
```bash
curl -X GET "http://localhost:9090/api/skills?current=1&size=10" \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

**查询工具目录**
```bash
curl -X GET "http://localhost:9090/api/tools?current=1&size=10" \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

### 通知与运行记录

**查询通知列表**
```bash
curl -X GET "http://localhost:9090/notifications?current=1&size=10" \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

**查询 Agent 运行记录**
```bash
curl -X GET "http://localhost:9090/agent-runs?current=1&size=10" \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

### 功能特性管理

**查询功能列表**
```bash
curl -X GET http://localhost:9090/api/features \
  -H "Authorization: Bearer {token}" \
  -H "X-User-Id: {userId}"
```

返回所有功能模块的启用状态。

## 系统架构理解

### 六边形架构（端口-适配器模式）

```
用户请求 → Web Adapter (Controller)
           ↓
         Kernel (Application + Domain)
           ↓
         Adapters (Infrastructure)
           ↓
         外部系统 (DB, Cache, MQ, etc.)
```

- **Web Adapter**: 处理 HTTP 请求，参数验证
- **Kernel**: 业务逻辑和领域模型
- **Adapters**: 与外部系统交互（数据库、缓存、向量库等）

### RAG 工作流程

1. **文档摄取**
   - 用户上传文档 → 解析内容 → 分块 → 向量化 → 存储到 Milvus + Elasticsearch

2. **检索增强**
   - 用户提问 → 查询优化 → 多通道检索（向量 + 关键词）→ 融合排序 → 重排序

3. **生成回答**
   - 组装上下文 + 问题 → 调用 LLM → 流式返回答案

### Memory 系统原理

- **捕获**: 对话中自动提取重要信息（人物、事件、偏好等）
- **存储**: 结构化存储（PostgreSQL）+ 向量化（Milvus）
- **检索**: 下次对话时根据上下文智能召回
- **维护**: 定期压缩、去重、垃圾回收

### Agent 工具调用

- Agent 定义包含：目标描述、系统指令、工具绑定
- 运行时 LLM 判断是否需要调用工具
- 支持并行工具调用和多轮交互
- 工具执行结果反馈给 LLM 继续推理

## 常见问题

### 服务无法启动？

检查端口占用：
```bash
netstat -ano | findstr "9090\|5432\|6379\|19530"
```

查看容器日志：
```bash
docker logs seahorse-backend
docker logs seahorse-postgres
```

### API 返回 401 未授权？

确认 Token 是否有效：
```bash
# 重新登录获取新 Token
TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | \
  grep -o '"token":"[^"]*"' | cut -d'"' -f4)

echo $TOKEN
```

### 知识库创建失败？

检查 Milvus 和 Ollama 服务状态：
```bash
docker logs seahorse-milvus
docker logs seahorse-ollama
```

确认 Ollama 已下载向量模型：
```bash
docker exec seahorse-ollama ollama list
```

## 下一步

- 查看 [E2E 测试报告](./E2E-TEST-REPORT.md) 了解详细的系统工作原理
- 探索 [API 文档](http://localhost:9090/swagger-ui.html)（如果启用）
- 查看 [CLAUDE.md](../CLAUDE.md) 了解项目结构和开发指南

## 技术支持

如遇到问题，请：
1. 查看 Docker 容器日志
2. 检查 E2E 测试脚本输出
3. 参考本文档的常见问题部分
