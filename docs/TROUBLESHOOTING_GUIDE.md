# Seahorse Agent 故障排查指南

本指南按当前本地 Docker 部署整理，优先覆盖登录过期、RAG 无上下文、记忆不召回、向量维度和数据卷问题。

## 快速定位

| 现象 | 第一检查点 |
|---|---|
| 登录后马上“登录已过期” | 请求头是否为 `Authorization: Bearer <token>` |
| 默认账号无法登录 | 当前数据卷是否仍保留旧密码 `admin` |
| 前端正常但直调后端 404 | 直连 `9090` 时不要给基础接口额外加 `/api` |
| RAG 没引用知识库 | 文档是否完成分块和索引，Milvus/Ollama 是否健康 |
| 记忆不召回 | `t_short_term_memory`、`t_user_profile_fact` 是否有当前用户数据 |
| 向量维度报错 | 当前全量默认为 `nomic-embed-text` + 768 维 |
| Grafana 打不开 | 端口是 `13001`，不是 `3001` |

## 1. 认证和登录

### 登录后返回“登录已过期”

先确认登录响应能取到 token：

```bash
TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.data.token')

echo "$TOKEN"
```

再确认请求头格式：

```bash
curl http://localhost:9090/knowledge-base \
  -H "Authorization: Bearer $TOKEN"
```

常见原因：

- 少了 `Bearer ` 前缀。
- 前端代理和后端直连路径混用。前端走 `/api/auth/login`，后端直连走 `/auth/login`。
- 使用旧数据卷，默认账号密码仍不符合当前校验。
- 后端重启后，轻量部署的本地 token 缓存失效。
- 全量部署 Redis 不健康，Sa-Token 无法持久化登录状态。

全量部署下检查 Redis：

```bash
docker compose -f docker-compose.full.yml ps redis
docker exec seahorse-redis redis-cli PING
docker exec seahorse-redis redis-cli KEYS "satoken:*"
```

轻量部署默认没有 Redis，不能用 Redis key 数量判断登录是否正常。

### 默认账号无法登录

当前初始化脚本默认账号为：

```text
admin / admin123
```

如果数据库卷创建于旧版本，可能仍是 `admin / admin`。保留数据时可执行迁移：

```sql
UPDATE t_user
SET password = 'admin123'
WHERE username = 'admin'
  AND password = 'admin';
```

也可以执行仓库迁移脚本：

```text
resources/database/migrations/V17__default_admin_password_validation_alignment.sql
```

确认不需要旧数据时再重建数据卷。

## 2. Docker 启动

查看服务：

```bash
docker compose -f docker-compose.full.yml ps
```

查看后端日志：

```bash
docker logs seahorse-backend --tail 100
```

健康检查：

```bash
curl http://localhost:9090/actuator/health
```

常见端口：

| 服务 | 端口 |
|---|---:|
| 前端 | 80 |
| 后端 | 9090 |
| PostgreSQL | 5432 |
| Redis | 16379 |
| Elasticsearch | 9200 |
| MinIO | 9000 / 9001 |
| Milvus | 19530 / 9091 |
| Attu | 8000 |
| Pulsar | 6650 / 8080 |
| Prometheus | 19090 |
| Grafana | 13001 |

端口冲突时，修改 compose 端口映射后重启对应服务。

## 3. 数据库和数据卷

PostgreSQL 初始化脚本只会在数据卷第一次创建时执行：

```text
resources/database/seahorse_init.sql
```

所以旧数据卷可能带来：

- 默认密码仍是旧值。
- 表结构缺列。
- 向量库或 pgvector 表仍是切换模型前的旧维度。
- 记忆、画像、Trace 或企业能力表不存在。

检查关键表：

```bash
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "\dt t_user"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "\dt t_user_profile_fact"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "\dt t_rag_trace_run"
```

不要在有重要数据的环境直接执行：

```bash
docker compose -f docker-compose.full.yml down -v
```

这会删除数据库、Milvus、Redis、Ollama 等数据卷。

## 4. 向量和 Embedding

全量部署默认：

| 项 | 当前值 |
|---|---|
| Embedding 模型 | `nomic-embed-text` |
| Embedding 服务 | `http://ollama:11434/v1` |
| 宿主机 Ollama | `http://localhost:11434` |
| 向量维度 | 由模型解析，`nomic-embed-text` 为 768 |
| 向量库 | Milvus |
| Milvus collection | `seahorse_default` 或知识库配置的 collection |

验证 Ollama：

```bash
docker exec seahorse-ollama ollama list

curl -s http://localhost:11434/api/embeddings \
  -H "Content-Type: application/json" \
  -d '{"model":"nomic-embed-text","prompt":"test"}' \
  | jq '.embedding | length'
```

预期长度是 `768`。

如果报维度不匹配，优先检查：

- 是否切换过 embedding 模型。
- 自定义 embedding 模型是否配置了 `SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL_DIMENSIONS=模型名=维度`；如必须显式覆盖，使用 `SEAHORSE_AGENT_ADAPTERS_VECTOR_DIMENSION`。旧 `.env` 中的 `MILVUS_DIMENSION` 不再作为应用向量维度事实源。
- 旧数据卷中的 `t_knowledge_vector` 或 Milvus collection 是否仍是旧维度。
- 文档是否需要重新向量化。

仓库已有迁移：

```text
resources/database/migrations/V20__fix_vector_dimension.sql
```

## 5. RAG 没有上下文

先区分部署模式：

- 轻量部署默认 `SEAHORSE_AGENT_ADAPTERS_VECTOR_TYPE=noop`，适合页面/API 冒烟，不代表真实向量检索质量。
- 全量部署才默认启用 Milvus、Ollama、Elasticsearch 和 Pulsar。

检查知识库数据：

```bash
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT COUNT(*) FROM t_knowledge_base;"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT COUNT(*) FROM t_knowledge_document;"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT COUNT(*) FROM t_knowledge_chunk;"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT doc_name, status FROM t_knowledge_document ORDER BY create_time DESC LIMIT 10;"
```

检查检索 Trace：

```bash
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT trace_id, user_question, status, create_time FROM t_rag_trace_run ORDER BY create_time DESC LIMIT 5;"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT trace_id, node_type, status, error_message FROM t_rag_trace_node ORDER BY create_time DESC LIMIT 20;"
```

常见修复：

- 重新上传或重新分块文档。
- 确认 Ollama 模型已拉取。
- 确认 Milvus 健康：`docker compose -f docker-compose.full.yml ps milvus-standalone`。
- 确认 Elasticsearch 健康：`curl http://localhost:9200/_cluster/health`。
- 如果换过 embedding 模型，清理旧 collection 后重建索引。

## 6. 记忆和用户画像不生效

全量部署默认开启：

```env
SEAHORSE_AGENT_MEMORY_AGGREGATION_ENABLED=true
SEAHORSE_AGENT_MEMORY_AGGREGATION_IDLE_FLUSH_MILLIS=30000
SEAHORSE_AGENT_MEMORY_AGGREGATION_MAX_TURNS=5
```

检查关键表：

```bash
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT COUNT(*) FROM t_short_term_memory;"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT COUNT(*) FROM t_memory_aggregation_buffer;"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT COUNT(*) FROM t_memory_outbox;"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT user_id, slot_key, slot_value, status FROM t_user_profile_fact ORDER BY update_time DESC LIMIT 20;"
```

通过 API 检查：

```bash
curl "http://localhost:9090/memories/profile-facts?userId=2001523723396308993&tenantId=default&limit=20" \
  -H "Authorization: Bearer $TOKEN"

curl "http://localhost:9090/memories/health?userId=2001523723396308993&tenantId=default" \
  -H "Authorization: Bearer $TOKEN"
```

常见原因：

- 对话没有落到同一个用户 ID。
- 记忆聚合窗口未到。
- 用户表达过于含糊，无法抽取稳定画像事实。
- Outbox 或派生索引任务失败。
- 新会话没有携带相同登录态。

可以手动触发维护：

```bash
curl -X POST "http://localhost:9090/memories/maintenance/run?reason=manual-check&compaction=true&alias=true&gc=true" \
  -H "Authorization: Bearer $TOKEN"
```

也可以触发治理策略评估（质量快照、清理建议）：

```bash
curl -X POST "http://localhost:9090/memories/governance/run" \
  -H "Authorization: Bearer $TOKEN"
```

两个端点职责不同：
- `/memories/maintenance/run`（SeahorseMemoryMaintenanceController）：触发底层数据整理，包括聚合缓冲刷新、实体别名合并、垃圾回收等。
- `/memories/governance/run`（SeahorseMemoryController）：触发治理策略评估，包括质量快照生成、低价值记忆标记和清理建议。

## 7. 前端路径和 API 路径

前端容器使用 `/api` 代理后端，浏览器里请求看起来可能是：

```text
/api/auth/login
/api/conversations
/api/tools
```

后端直连 `localhost:9090` 时：

```text
/auth/login
/conversations
/rag/v3/chat
/knowledge-base
```

部分企业管理接口本身就是 `/api/...`，例如：

```text
/api/tools
/api/skills
/api/agent-runs
/api/admin/tenants
```

判断标准以 Controller 注解为准。

## 8. 收集诊断信息

```bash
docker compose -f docker-compose.full.yml ps
docker logs seahorse-backend --tail 200
curl http://localhost:9090/actuator/health
docker exec seahorse-ollama ollama list
docker exec seahorse-redis redis-cli INFO keyspace
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT COUNT(*) FROM t_knowledge_chunk;"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT COUNT(*) FROM t_user_profile_fact;"
```

如果要提交问题，附上 compose 状态、后端最近日志、部署模式、是否使用旧数据卷、登录接口响应结构和失败接口的完整路径。
