# Seahorse Agent

Seahorse Agent 是一个面向企业知识问答、RAG 检索增强生成和 Agent 应用治理的工程平台。后端采用 Java 17、Spring Boot 3.5、多模块 Maven 和端口适配器架构；前端采用 React 18、TypeScript、Vite 和 TailwindCSS。项目当前提供聊天入口、知识库管理、文档入库、RAG 评测与 Trace、Agent/Skill/Tool 管理、记忆治理、审计、计费、租户、模型配置和系统设置等后台能力。

## 当前状态

- 默认本地部署方式：`docker-compose.yml`，启动 PostgreSQL/pgvector、后端和前端。
- 默认访问地址：前端 `http://localhost`，后端 `http://localhost:9090`。
- 默认管理员账号：`admin / admin123`，由 `resources/database/seahorse_init.sql` 初始化。
- 前端 Docker 与 Vite 开发服务通过 `/api` 反向代理访问后端，并在代理层去掉 `/api` 前缀；直接调后端 `9090` 端口时使用 Controller 的真实路径。
- 后端入口：`seahorse-agent-bootstrap`。
- 前端入口：`frontend`。
- 数据库初始化脚本：`resources/database/seahorse_init.sql`。
- 增量迁移脚本：`resources/database/migrations/`。

## 架构

后端按六边形架构组织：

- `seahorse-agent-kernel`：领域模型、Inbound/Outbound Port、应用服务、RAG/Agent/Memory/Ingestion 编排。
- `seahorse-agent-adapter-web`：REST/SSE Controller、Sa-Token 登录鉴权、后台管理 API。
- `seahorse-agent-adapter-repository-jdbc`：PostgreSQL 仓储适配器，当前包含 MyBatis Plus mapper、Spring JDBC 兼容实现、租户隔离、RAG Trace、知识库、Agent、审计等表访问。
- `seahorse-agent-adapter-ai-openai-compatible`：OpenAI-compatible Chat/Streaming Chat/Embedding/Rerank 适配器。
- `seahorse-agent-adapter-vector-*`：Milvus、pgvector、noop 向量适配器。
- `seahorse-agent-adapter-search-*`：Elasticsearch、Lucene 关键词检索适配器。
- `seahorse-agent-adapter-cache-*`：local、Redis/Redisson 缓存适配器。
- `seahorse-agent-adapter-mq-*`：direct、Pulsar 消息适配器。
- `seahorse-agent-adapter-parser-tika`：Apache Tika 文档解析适配器。
- `seahorse-agent-adapter-source-feishu`：飞书文档源适配器。
- `seahorse-agent-adapter-storage-*`：local、S3 兼容对象存储适配器。
- `seahorse-agent-adapter-mcp-http`：MCP HTTP 工具适配器。
- `seahorse-agent-adapter-openapi`：OpenAPI 连接器与规范解析适配器。
- `seahorse-agent-mcp-server`：MCP Server 模块。
- `seahorse-agent-spring-boot-starter*`：自动装配与运行时聚合。

Port 接口位于 kernel，外部依赖通过 adapter 实现接入。新增能力应优先保持 Port 契约稳定，在 Adapter、Controller 或配置层扩展。

## 技术栈

后端：

- Java 17
- Spring Boot 3.5.7
- Maven 多模块
- MyBatis Plus 3.5.14 与 Spring JDBC 兼容仓储
- PostgreSQL 16 / pgvector
- Sa-Token 1.43.0
- Resilience4j 2.2.0
- Redisson 4.0.0
- Apache Pulsar 3.1.3
- Apache Tika 3.2.3
- Milvus SDK 2.6.6
- OkHttp 4.12.0

前端：

- React 18.3.1
- TypeScript 5.5.4
- Vite 5.4.x
- TailwindCSS 3.4.x
- Radix UI
- Zustand
- React Router 6
- Axios
- Vitest 4

## 认证与会话

后端认证入口由 `SeahorseAuthController` 提供：

- `POST /auth/login`：用户名密码登录，默认管理员为 `admin / admin123`。
- `POST /auth/logout`：退出登录。
- `POST /auth/refresh`：刷新访问令牌；刷新令牌持久化在 `t_user.refresh_token` 和 `t_user.refresh_token_expires_at`。

`LoginResult` 当前返回 `userId`、`role`、`token`、`avatar`、`tenantId`、`refreshToken` 和 `refreshTokenExpiresAt`。前端当前登录态仍以访问令牌为主，刷新令牌接口已由后端提供，便于后续接入自动续期。

## 模型与凭据管理

当前代码支持按租户维护模型注册表。后台路径：

- 模型配置：`/admin/model-config`
- 供应商凭据：`/admin/secrets`
- 系统设置概览：`/admin/settings`

模型注册表存储在 `sa_ai_model_config` 表中，核心 key 为 `ai.models`。该表包含 `tenant_id`，并使用 `(tenant_id, config_key)` 唯一约束。`ai.models` 的值是 JSON 数组，示例：

```json
[
  {
    "id": "bge-m3-default",
    "capability": "embedding",
    "provider": "ollama",
    "model": "bge-m3",
    "baseUrl": "http://ollama:11434",
    "secretRef": "",
    "dimension": 1024,
    "priority": 1,
    "enabled": true,
    "defaultModel": true
  },
  {
    "id": "qwen-chat-default",
    "capability": "chat",
    "provider": "openai-compatible",
    "model": "qwen-plus",
    "baseUrl": "https://api.openai.com/v1",
    "secretRef": "openai-prod",
    "priority": 2,
    "enabled": true,
    "defaultModel": true
  }
]
```

知识库创建弹窗会优先读取当前租户 `ai.models` 中启用的 `embedding` 模型；如果没有租户模型注册表，再回退到旧的模型配置和 RAG settings。

`.env` 或 Docker 环境变量中的 `SEAHORSE_AGENT_ADAPTERS_AI_*` 仍作为运行时 adapter 的基础配置和兼容回退。生产或多租户场景下，推荐在后台模型配置页维护租户级模型，并用供应商凭据页保存供应商 API Key 或连接器凭据引用，再通过 `secretRef` 绑定到模型配置。

## 快速开始

### 1. 准备环境

需要：

- JDK 17
- Maven 3.9+，或使用仓库内 `mvnw`
- Node.js 20+
- Docker Desktop

### 2. 配置环境变量

```bash
cp .env.example .env
```

按需填写 `.env` 中的模型服务地址和 key。没有外部模型服务时，系统仍可启动，但真实聊天、Embedding、Rerank 会受限。

### 3. 构建后端 jar

`Dockerfile.backend.simplified` 会复制已生成的 Spring Boot exec jar，因此构建后端镜像前需要先打包：

```bash
mvn -pl seahorse-agent-bootstrap -am -DskipTests package
```

### 4. 启动 Docker

```bash
docker compose build backend frontend
docker compose up -d postgres backend frontend
```

健康检查：

```bash
curl http://localhost:9090/actuator/health
```

打开前端：

```text
http://localhost
```

登录：

```text
admin / admin123
```

如果本地 Docker 已经有旧的 PostgreSQL 数据卷，`seahorse_init.sql` 不会自动重新执行。旧库仍是 `admin / admin` 或缺少新字段时，应执行对应迁移脚本，或在确认不需要保留数据后重建数据库数据卷。

## 本地开发

后端本地启动：

```bash
mvn -pl seahorse-agent-bootstrap -am spring-boot:run
```

前端本地启动：

```bash
cd frontend
npm install
npm run dev
```

如需指定 API 地址，可设置：

```bash
VITE_API_BASE_URL=http://localhost:9090
```

## 常用验证命令

后端编译/打包：

```bash
mvn -pl seahorse-agent-bootstrap -am -DskipTests package
```

指定后端测试示例：

```bash
mvn -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseAuthControllerTests" -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl seahorse-agent-adapter-repository-jdbc -am -Dtest=JdbcAiModelConfigRepositoryAdapterTests -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcRefreshTokenRepositoryAdapterTests" -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test
```

前端验证：

```bash
cd frontend
npx tsc --noEmit
npm run build
npx vitest run src/components/admin/CreateKnowledgeBaseDialog.test.ts --pool forks --no-file-parallelism --maxWorkers 1
```

API 冒烟示例：

```bash
curl http://localhost:9090/actuator/health
curl -X POST http://localhost:9090/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'
```

登录后可验证：

- `GET /admin/ai-config?tenantId=default`
- `GET /intent-tree/trees`
- `GET /ingestion/pipelines?page=1&size=10`
- `GET /ingestion/tasks?page=1&size=10`

## 主要功能入口

前台：

- `/login`：登录
- `/register`：注册
- `/chat`：聊天/RAG/Agent 对话
- `/chat/:sessionId`：指定会话对话
- `/memories`：个人记忆中心
- `/marketplace`：Agent 市场

后台：

- `/admin/dashboard`：仪表盘
- `/admin/knowledge`：知识库管理
- `/admin/rag-evaluation`：RAG 评测
- `/admin/rag-strategies`：RAG 策略模板
- `/admin/rag-version-compare`：版本质量对比
- `/admin/traces`：RAG Trace
- `/admin/model-config`：租户模型配置
- `/admin/secrets`：供应商凭据
- `/admin/settings`：系统设置入口
- `/admin/context-packs`：上下文包
- `/admin/task-templates`：任务模板
- `/admin/sample-questions`：样例问题
- `/admin/mappings`：查询术语映射
- `/admin/memory-governance`：记忆治理
- `/admin/metadata-governance`：元数据治理
- `/admin/intent-tree` 与 `/admin/intent-list`：意图树与意图列表
- `/admin/ingestion?tab=pipelines`：流水线管理
- `/admin/ingestion?tab=tasks`：流水线任务
- `/admin/ai-infra` 与 `/admin/agent-inspector`：Agent 控制台与运行检视
- `/admin/agents`：Agent 管理
- `/admin/agent-runs`：Agent 运行管理
- `/admin/approvals`：审批中心
- `/admin/skills`：Skill 管理
- `/admin/tools`：工具目录
- `/admin/tool-invocations`：工具调用审计
- `/admin/security/resource-acl`、`/admin/security/access-decisions`、`/admin/security/quotas`：安全与配额治理
- `/admin/integrations/connectors`：OpenAPI 连接器
- `/admin/plugins`：插件管理
- `/admin/audit`：审计事件
- `/admin/audit-logs`：租户审计日志
- `/admin/billing`：计费管理
- `/admin/cost`：成本分析
- `/admin/sandbox`：沙箱
- `/admin/tenants`：租户管理
- `/admin/users`：用户管理
- `/admin/marketplace-review`：市场审核

## 数据库说明

`resources/database/seahorse_init.sql` 是 Docker 首次初始化数据库时使用的完整脚本。已存在数据库不会自动重新执行该脚本；需要保留数据时，应通过 `resources/database/migrations/` 中的迁移脚本或应用内 schema upgrade 逻辑升级。

当前 README 提到的关键迁移：

- `resources/database/migrations/V17__default_admin_password_validation_alignment.sql`
- `resources/database/migrations/V18__tenant_scoped_ai_model_config.sql`
- `resources/database/migrations/V19__add_refresh_token_columns.sql`

`V17` 将默认管理员密码对齐为 `admin123`；`V18` 为 `sa_ai_model_config` 增加 `tenant_id`，删除旧的全局 `config_key` 唯一约束，并建立 `(tenant_id, config_key)` 唯一约束；`V19` 为 `t_user` 增加 refresh token 字段和索引。应用启动时的 `JdbcTenantSchemaUpgrade` 也会对部分新字段和索引做幂等升级。

## 目录结构

```text
.
|-- frontend/
|-- resources/
|   |-- database/
|   `-- database/migrations/
|-- seahorse-agent-mcp-server/
|-- seahorse-agent-bootstrap/
|-- seahorse-agent-kernel/
|-- seahorse-agent-adapter-web/
|-- seahorse-agent-adapter-repository-jdbc/
|-- seahorse-agent-adapter-ai-openai-compatible/
|-- seahorse-agent-adapter-mcp-http/
|-- seahorse-agent-adapter-openapi/
|-- seahorse-agent-adapter-source-feishu/
|-- seahorse-agent-adapter-vector-milvus/
|-- seahorse-agent-adapter-vector-pgvector/
|-- seahorse-agent-adapter-vector-noop/
|-- seahorse-agent-adapter-search-elasticsearch/
|-- seahorse-agent-adapter-search-lucene/
|-- seahorse-agent-adapter-parser-tika/
|-- seahorse-agent-adapter-cache-local/
|-- seahorse-agent-adapter-cache-redis/
|-- seahorse-agent-adapter-mq-direct/
|-- seahorse-agent-adapter-mq-pulsar/
|-- seahorse-agent-adapter-storage-local/
|-- seahorse-agent-adapter-storage-s3/
|-- seahorse-agent-adapter-observation-noop/
|-- seahorse-agent-adapter-observation-micrometer/
|-- seahorse-agent-spring-boot-starter/
|-- seahorse-agent-spring-boot-starter-core/
|-- seahorse-agent-spring-boot-starter-all/
`-- seahorse-agent-tests/
```

## 参考文档

- `DEPLOY.md`
- `docs/`
- `resources/database/migrations/`
- `frontend/src/services/backendEndpointManifest.ts`

## License

Apache License 2.0，详见 `LICENSE`。
