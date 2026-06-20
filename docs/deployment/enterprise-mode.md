# 企业模式部署说明

全量部署默认以 `enterprise-platform` 模式运行，面向本地验证企业治理能力、RAG 评测、记忆治理、Agent/Tool/Skill 管理和可观测性。

## 入口

```bash
cp .env.full.example .env
./mvnw -pl seahorse-agent-bootstrap -am -DskipTests package
docker compose -f docker-compose.full.yml up -d --build
```

前端：

```text
http://localhost
```

默认账号：

```text
admin / admin123
```

## 关键开关

全量部署应保持前后端模式一致：

```env
SEAHORSE_AGENT_PRODUCT_MODE=enterprise-platform
VITE_SEAHORSE_PRODUCT_MODE=enterprise-platform
VITE_SEAHORSE_ENABLE_ADVANCED_ADMIN=true
```

后台模块由 `SEAHORSE_AGENT_ADVANCED_*_ENABLED` 控制。除非明确要隐藏某个模块，本地全量验证建议保持为 `true`。

前端启动后仍会读取：

```text
/api/features
```

Vite 变量只是构建期默认值，后端 capability 响应才是菜单、路由守卫和不可用状态的运行时来源。

## 全量组件

| 能力 | 组件 |
|---|---|
| 关系数据 | PostgreSQL / pgvector |
| Token 和缓存 | Redis |
| 向量检索 | Milvus |
| Embedding | Ollama `nomic-embed-text` |
| 关键词检索 | Elasticsearch |
| 消息队列 | Pulsar |
| 对象存储 | 当前后端使用 local storage；MinIO 已在全量编排中初始化，供后续 S3 切换 |
| 指标 | Prometheus |
| 看板 | Grafana |

## 企业能力入口

| 页面 | 用途 |
|---|---|
| `/admin/dashboard` | 后台概览 |
| `/admin/knowledge` | 知识库 |
| `/admin/traces` | RAG Trace |
| `/admin/model-config` | 模型配置 |
| `/admin/memory-governance` | 记忆治理 |
| `/admin/agents` | Agent 管理 |
| `/admin/agent-runs` | Agent 运行 |
| `/admin/skills` | Skill 管理 |
| `/admin/tools` | Tool 管理 |
| `/admin/security/resource-acl` | 资源 ACL |
| `/admin/security/quotas` | 配额 |
| `/admin/audit`、`/admin/audit-logs` | 审计 |
| `/admin/billing` | 计费 |
| `/admin/cost` | 成本 |
| `/admin/tenants` | 租户 |

## 数据边界

全量部署默认启用租户能力：

```env
SEAHORSE_AGENT_TENANT_ENABLED=true
SEAHORSE_AGENT_BILLING_ENABLED=true
```

初始化数据中的默认租户为 `default`。旧数据库卷可能没有完整租户字段、RLS 策略或企业表，遇到异常先检查迁移是否补齐。

## 验证建议

1. 登录 `admin / admin123`。
2. 打开 `/admin/model-config` 确认 Chat 模型配置。
3. 在 `/admin/knowledge` 上传文档并完成分块。
4. 在 `/chat` 提问，观察 `/rag/traces/runs`（前端页面路径为 `/admin/traces`）。
5. 输入个人事实，等待记忆聚合后检查 `/memories` 和 `/admin/memory-governance`。
6. 打开 Prometheus `http://localhost:19090` 和 Grafana `http://localhost:13001`。

## 常见问题

- 登录过期：先确认 `Authorization: Bearer <token>`，再检查 Redis。
- RAG 无上下文：检查 Ollama、Milvus、Elasticsearch、文档分块和 Trace。
- 记忆不召回：检查 `t_short_term_memory`、`t_user_profile_fact`、`t_memory_outbox`。
- 默认账号不对：旧数据卷可能仍是旧密码，执行 `V17__default_admin_password_validation_alignment.sql` 或重建数据库卷。
