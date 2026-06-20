# Seahorse Agent 使用指南

本指南面向已经完成本地部署的用户，给出当前源码和 Docker 编排下可操作的前端入口、API 示例和闭环验证路径。

架构边界和路线图请先看 `docs/architecture/current-code-architecture.md` 与 `docs/roadmap/architecture-roadmap-and-vision.md`。本文只保留可直接执行的使用路径。

## 前置条件

推荐用全量部署验证 RAG、记忆和用户画像：

```bash
docker compose -f docker-compose.full.yml up -d --build
```

基础页面和 API 冒烟可以使用轻量部署：

```bash
docker compose up -d --build
```

默认入口：

| 项目 | 地址 |
|---|---|
| 前端 | `http://localhost` |
| 后端 | `http://localhost:9090` |
| Actuator | `http://localhost:9090/actuator/health` |
| Milvus Attu | `http://localhost:8000` |
| Prometheus | `http://localhost:19090` |
| Grafana | `http://localhost:13001` |

默认账号：

```text
admin / admin123
```

## 登录和 Token

后端登录接口：

```bash
TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.data.token')

echo "$TOKEN"
```

后续请求需要携带：

```http
Authorization: Bearer <token>
```

前端通过 `/api` 代理访问后端，代理层会去掉 `/api` 前缀。直接访问 `localhost:9090` 时使用后端真实路径，例如 `/auth/login`、`/knowledge-base`、`/rag/v3/chat`。

## 前端常用页面

| 页面 | 路径 | 用途 |
|---|---|---|
| 登录 | `/login` | 获取会话 |
| 聊天 | `/chat` | RAG、Agent、流式对话 |
| 记忆中心 | `/memories` | 查看和管理个人记忆 |
| 知识库 | `/admin/knowledge` | 创建知识库、上传文档 |
| RAG Trace | `/admin/traces` | 查看检索和生成链路 |
| 模型配置 | `/admin/model-config` | 检查租户模型配置 |
| 记忆治理 | `/admin/memory-governance` | 记忆健康、维护和评估 |
| Agent 管理 | `/admin/agents` | Agent 定义和版本 |
| 工具目录 | `/admin/tools` | Tool 管理 |
| Skill 管理 | `/admin/skills` | Skill 管理 |

## 知识库和 RAG

创建知识库：

```bash
KB_ID=$(curl -s -X POST http://localhost:9090/knowledge-base \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "demo-kb",
    "collectionName": "demo_kb",
    "embeddingModel": "nomic-embed-text"
  }' | jq -r '.data')

echo "$KB_ID"
```

上传文档：

```bash
DOC_ID=$(curl -s -X POST "http://localhost:9090/knowledge-base/$KB_ID/docs/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/document.pdf" \
  -F "processMode=pipeline" \
  | jq -r '.data.id')

echo "$DOC_ID"
```

触发分块：

```bash
curl -X POST "http://localhost:9090/knowledge-base/docs/$DOC_ID/chunk" \
  -H "Authorization: Bearer $TOKEN"
```

查看文档和分块日志：

```bash
curl "http://localhost:9090/knowledge-base/docs/$DOC_ID" \
  -H "Authorization: Bearer $TOKEN"

curl "http://localhost:9090/knowledge-base/docs/$DOC_ID/chunk-logs" \
  -H "Authorization: Bearer $TOKEN"
```

发送流式 RAG 请求：

```bash
CONV_ID=$(curl -s -X POST "http://localhost:9090/conversations" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.data')

curl -N -G "http://localhost:9090/rag/v3/chat" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: text/event-stream" \
  --data-urlencode "conversationId=$CONV_ID" \
  --data-urlencode "question=请根据知识库回答这个项目的核心能力是什么"
```

SSE 响应会包含 `meta`、`message`、`done` 等事件。若缺少 `Authorization: Bearer <token>`，会返回未登录或登录已过期。

## 记忆和用户画像

全量部署默认开启记忆聚合：

```env
SEAHORSE_AGENT_MEMORY_AGGREGATION_ENABLED=true
SEAHORSE_AGENT_MEMORY_AGGREGATION_IDLE_FLUSH_MILLIS=30000
SEAHORSE_AGENT_MEMORY_AGGREGATION_MAX_TURNS=5
```

建议验证步骤：

1. 在 `/chat` 中输入明确个人事实，例如“我的职业是平台可靠性后端工程师，我偏好极简中文回答”。
2. 等待聚合窗口，或触发一次维护：

```bash
curl -X POST "http://localhost:9090/memories/maintenance/run?reason=manual-check&compaction=true&alias=true&gc=true" \
  -H "Authorization: Bearer $TOKEN"
```

3. 查看记忆和画像：

```bash
curl "http://localhost:9090/memories?userId=2001523723396308993&layer=short_term&limit=20" \
  -H "Authorization: Bearer $TOKEN"

curl "http://localhost:9090/memories/profile-facts?userId=2001523723396308993&tenantId=default&limit=20" \
  -H "Authorization: Bearer $TOKEN"

curl "http://localhost:9090/memories/health?userId=2001523723396308993&tenantId=default" \
  -H "Authorization: Bearer $TOKEN"
```

4. 开新会话提问“你记得我的职业和回答偏好吗”，确认记忆召回影响回答。

关键表：

| 表 | 作用 |
|---|---|
| `t_short_term_memory` | 对话轮次和短期记忆 |
| `t_memory_aggregation_buffer` | 聚合缓冲 |
| `t_memory_outbox` | 派生索引任务 |
| `t_user_profile_fact` | 用户画像事实 |
| `t_memory_keyword_index` | 记忆关键词索引 |
| `t_memory_entity_alias`、`t_memory_entity_relation` | 记忆图谱索引 |
| `t_memory_trace_event` | 记忆链路追踪 |

## Agent、Tool 和 Skill

部分企业能力接口使用 `/api/...` 路径。直接调后端时也保留 `/api`：

```bash
curl "http://localhost:9090/api/tools" \
  -H "Authorization: Bearer $TOKEN"

curl "http://localhost:9090/api/skills" \
  -H "Authorization: Bearer $TOKEN"

curl "http://localhost:9090/api/agent-runs" \
  -H "Authorization: Bearer $TOKEN"
```

Agent 模式对话可以通过 `chatMode=AGENT`、`agentId`、`versionId` 或受控任务模板触发：

```bash
curl -N -G "http://localhost:9090/rag/v3/chat" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: text/event-stream" \
  --data-urlencode "conversationId=$CONV_ID" \
  --data-urlencode "chatMode=AGENT" \
  --data-urlencode "question=帮我分析这个知识库的核心主题"
```

## 常见检查

登录后接口返回“登录已过期”时，先确认：

```bash
curl http://localhost:9090/knowledge-base \
  -H "Authorization: Bearer $TOKEN"
```

全量部署下还可以看 Redis token：

```bash
docker exec seahorse-redis redis-cli KEYS "satoken:*"
```

RAG 没有上下文时，先确认：

- 文档已经完成分块。
- `t_knowledge_chunk` 有数据。
- Milvus collection 存在且维度为 768。
- Ollama `nomic-embed-text` 可以返回 768 维向量。
- `/rag/traces/runs` 能看到 retrieval 节点（前端页面路径为 `/admin/traces`）。

记忆没有召回时，先确认：

- 对话使用同一个真实用户。
- `t_short_term_memory` 有数据。
- `t_user_profile_fact` 有 active 画像事实。
- `t_memory_outbox` 没有持续失败任务。

## API 快速参考

| 功能 | Method | Path |
|---|---|---|
| 登录 | `POST` | `/auth/login` |
| 刷新 token | `POST` | `/auth/refresh` |
| 退出 | `POST` | `/auth/logout` |
| 创建知识库 | `POST` | `/knowledge-base` |
| 上传文档 | `POST` | `/knowledge-base/{kb-id}/docs/upload` |
| 触发分块 | `POST` | `/knowledge-base/docs/{doc-id}/chunk` |
| 创建会话 | `POST` | `/conversations` 或 `/api/conversations` |
| 查询会话 | `GET` | `/conversations` 或 `/api/conversations` |
| 流式聊天 | `GET` | `/rag/v3/chat` |
| 停止流式任务 | `POST` | `/rag/v3/stop` |
| 记忆列表 | `GET` | `/memories` |
| 用户画像 | `GET` | `/memories/profile-facts` |
| 记忆维护 | `POST` | `/memories/maintenance/run` |
| 工具目录 | `GET` | `/api/tools` |
| Skill 列表 | `GET` | `/api/skills` |

## 版本说明

本文档按当前源码、`docker-compose*.yml` 和 `resources/database/seahorse_init.sql` 更新。旧数据卷不会自动重放初始化 SQL，如默认密码、表结构或向量维度异常，请先看 `docs/TROUBLESHOOTING_GUIDE.md`。
