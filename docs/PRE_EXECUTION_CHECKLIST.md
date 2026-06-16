# Seahorse Agent 部署前检查清单

本清单用于本地 Docker 部署、E2E 验证前自检。轻量部署用于页面和基础 API 冒烟；全量部署用于真实 RAG、记忆、用户画像和观测链路。

## 1. 选择部署模式

| 模式 | 命令 | 适用范围 |
|---|---|---|
| 轻量 | `docker compose up -d --build` | PostgreSQL、后端、前端、本地缓存、direct MQ、noop 向量 |
| 全量 | `docker compose -f docker-compose.full.yml up -d --build` | PostgreSQL、Redis、Elasticsearch、MinIO、Ollama、Milvus、Pulsar、Prometheus、Grafana |

全量部署建议 Docker Desktop 至少分配 8 GB 内存。首次拉取镜像和 Ollama 模型会比较慢。

## 2. 构建后端

后端镜像复制已构建的 Spring Boot exec jar，启动 compose 前先打包：

```bash
./mvnw -pl seahorse-agent-bootstrap -am -DskipTests package
```

Windows PowerShell：

```powershell
.\mvnw.cmd -pl seahorse-agent-bootstrap -am -DskipTests package
```

如果只是快速打包并跳过格式检查：

```bash
./mvnw -B -pl seahorse-agent-bootstrap -am -DskipTests -Dspotless.check.skip=true package
```

## 3. 环境变量

轻量部署：

```bash
cp .env.example .env
```

全量部署：

```bash
cp .env.full.example .env
```

至少确认 Chat 模型：

```env
SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL=https://api.openai.com/v1
SEAHORSE_AGENT_ADAPTERS_AI_API_KEY=sk-your-key
SEAHORSE_AGENT_ADAPTERS_AI_CHAT_MODEL=gpt-4o-mini
```

全量 compose 当前将 Embedding 指向容器内 Ollama：

```env
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL=nomic-embed-text
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_BASE_URL=http://ollama:11434/v1
```

向量维度由 Embedding 模型解析，`nomic-embed-text` 对应 768。如果换自定义 Embedding 模型，需要配置 `SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL_DIMENSIONS=模型名=维度`，并重建已有向量索引。

## 4. 启动后检查

```bash
docker compose -f docker-compose.full.yml ps
curl http://localhost:9090/actuator/health
```

预期：

- `seahorse-backend` 为 Up/healthy。
- 前端可打开 `http://localhost`。
- 后端健康接口返回 `UP`。

全量服务额外检查：

```bash
docker exec seahorse-redis redis-cli PING
curl http://localhost:9200/_cluster/health
docker exec seahorse-ollama ollama list
curl http://localhost:11434/api/tags
```

## 5. 登录检查

默认账号：

```text
admin / admin123
```

登录：

```bash
TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.data.token')

echo "$TOKEN"
```

验证 token：

```bash
curl http://localhost:9090/knowledge-base \
  -H "Authorization: Bearer $TOKEN"
```

全量部署下 Redis 应看到 Sa-Token key：

```bash
docker exec seahorse-redis redis-cli KEYS "satoken:*"
```

轻量部署不使用 Redis，不要用 Redis key 数量判断登录是否成功。

## 6. RAG 检查

全量部署的真实 RAG 至少需要：

- Chat 模型配置有效。
- Ollama 有 `nomic-embed-text`。
- Milvus 健康。
- Elasticsearch 健康。
- 知识库文档已完成分块和索引。

检查 Embedding 维度：

```bash
curl -s http://localhost:11434/api/embeddings \
  -H "Content-Type: application/json" \
  -d '{"model":"nomic-embed-text","prompt":"test"}' \
  | jq '.embedding | length'
```

预期为 `768`。

检查知识库数据：

```bash
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT COUNT(*) FROM t_knowledge_chunk;"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT COUNT(*) FROM t_rag_trace_run;"
```

## 7. 记忆检查

全量部署默认开启：

```env
SEAHORSE_AGENT_MEMORY_AGGREGATION_ENABLED=true
```

检查表是否存在：

```bash
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "\dt t_short_term_memory"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "\dt t_user_profile_fact"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "\dt t_memory_outbox"
```

发起一次个人事实对话后，检查：

```bash
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT COUNT(*) FROM t_short_term_memory;"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT user_id, slot_key, slot_value, status FROM t_user_profile_fact ORDER BY update_time DESC LIMIT 20;"
```

## 8. 数据卷安全

旧数据卷不会自动重放 `resources/database/seahorse_init.sql`。如果遇到默认密码、表结构、向量维度或记忆表异常，先判断是否来自旧卷。

高风险命令：

```bash
docker compose -f docker-compose.full.yml down -v
```

只有确认不需要旧数据时才执行。

## 9. E2E 前最后确认

- 前端 `http://localhost` 可登录。
- `admin / admin123` 登录成功。
- 带 `Authorization: Bearer <token>` 的接口不再返回“登录已过期”。
- 全量部署下 `nomic-embed-text` 返回 768 维向量。
- 知识库文档可以上传、分块并产生 chunk。
- `/rag/traces/runs` 能看到 RAG 请求（前端页面路由为 `/admin/traces`）。
- 记忆链路有 `t_short_term_memory` 和 `t_user_profile_fact` 数据。

可用脚本：

```text
scripts/e2e-full-test.sh
scripts/e2e-knowledge-test.sh
scripts/memory-e2e-test.sh
scripts/e2e-backend-smoke.ps1
scripts/e2e-compose-suite.sh          # 四条主闭环统一 E2E 验证
scripts/check-doc-staleness.sh        # 文档过期引用扫描
```

## 季度文档审计模板

每季度至少执行一次以下审计，确保文档与代码保持同步。审计结果记录在本文档末尾。

### 审计步骤

1. **API 路径审计**：
   ```bash
   # 提取所有 Controller 端点
   rg -n "@(Get|Post|Put|Delete|Patch)Mapping|@RequestMapping" \
     seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web
   ```
   对照 `docs/architecture/current-code-architecture.md` Section 4 的路径表，确认新增/删除/变更的端点已同步。

2. **环境变量审计**：
   对照 `.env.full.example` 和实际 `docker-compose.full.yml` 的 environment 段，确认 `.env` 无过期或缺失变量。

3. **前端路由审计**：
   对照 `frontend/src/router.tsx` 和 `frontend/src/pages/admin/AdminLayout.tsx`，确认 `docs/deployment/enterprise-mode.md` 的页面路径表准确。

4. **过期引用扫描**：
   ```bash
   ./scripts/check-doc-staleness.sh
   ```
   确保无旧 compose 文件、quick-start、旧模型名或旧 Trace 路径引用。

5. **Feature Gate 审计**：
   对照 `backend` 中的 feature gate 常量列表，确认 `.env` 和 `.env.full.example` 中的 gate 变量完整。

### 审计记录

| 审计日期 | 审计人 | 发现问题数 | 修复状态 |
|---|---|---|---|
| 2026-06-16 | 初始化 | 0 | 首次审计基线 |
