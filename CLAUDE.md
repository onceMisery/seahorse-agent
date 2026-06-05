# Seahorse Agent 项目指南

## 项目概述

Seahorse Agent 是一个基于 Spring Boot 3.5.7 的 RAG（检索增强生成）智能体平台，采用六边形架构（端口-适配器模式）。

## 模块结构

```
seahorse-agent-kernel/              # 领域核心（L2）
seahorse-agent-adapter-web/         # Web 适配器（L3）
seahorse-agent-adapter-*/           # 各类适配器实现（L3）
seahorse-agent-spring-boot-starter/ # 自动配置（L3）
seahorse-agent-bootstrap/           # 启动入口
seahorse-agent-tests/               # 集成测试
```

## 自动配置架构

所有自动配置类注册在 `AutoConfiguration.imports` 中，分 13 层：

0. **Layer 0 — Multi-tenancy**（after DataSource）：租户隔离、RLS、TenantContext
1. **Layer 1 — Adapters**（20 个，after DataSource）：向量、缓存、存储、MQ、搜索等
2. **Layer 2 — Outbox relay**（after MQ + Ops）
3. **Layer 3 — Native aggregator**（after DataSource）
4. **Layer 4 — Kernel auth**（after Auth adapter）
5. **Layer 5 — Kernel main**（after Native aggregator）
6. **Layer 6 — Kernel sub-configs**（16 个，after Kernel main + 各自依赖的 adapter）
7. **Layer 7 — Runtime guards**（after kernel sub-configs）
8. **Layer 8 — Alert notification**（after runtime guards）：DingTalk 告警
9. **Layer 9 — Registration + Security**：用户注册、BCrypt、安全加固
10. **Layer 10 — Billing**：计费、订阅、配额强制
11. **Layer 11 — RAG + Workflow**：高级 RAG、工作流可视化
12. **Layer 12 — KB + Marketplace + Admin**：知识库增强、Agent 市场、管理后台
13. **Layer 13 — AOP + Health**：权限切面、试用拦截器、Redis 健康检查

**关键规则：** kernel 子配置必须在 `@AutoConfigureAfter` 中声明所有产生 `@ConditionalOnBean` 依赖的 adapter/repository 配置。

## Controller 模式

所有 Controller 使用 `ObjectProvider<T>` 懒加载：

```java
private final ObjectProvider<SomePort> somePortProvider;

public SomeController(ObjectProvider<SomePort> somePortProvider) {
    this.somePortProvider = somePortProvider;  // 不要在这里调用 getIfAvailable()
}

@GetMapping("/api")
public Map<String, Object> api() {
    return Map.of("code", "0", "data", somePortProvider.getIfAvailable().query());
}
```

## 部署

### 全量部署（所有中间件）

```bash
docker compose -f docker-compose.full.yml up -d --build
```

包含：PostgreSQL + Redis + Milvus + Pulsar + Elasticsearch + MinIO

### 本地构建 + Docker 打包（推荐，避免 Maven 代理问题）

```bash
./mvnw package -B -pl seahorse-agent-bootstrap -am -DskipTests -Dspotless.check.skip=true
docker build -t seahorse-agent-backend:latest -f - . <<'EOF'
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY seahorse-agent-bootstrap/target/seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF
docker compose -f docker-compose.full.yml up -d backend
```

### SaaS 模块

| 模块 | 核心类 | 说明 |
|------|--------|------|
| 多租户 | `TenantContext`, `TenantInterceptor`, `JdbcTenantSupport` | ThreadLocal 租户上下文 + RLS 防御 |
| 安全加固 | `SandboxPathValidator`, `SecretRotationService`, `ForbiddenException` | 沙箱/密钥/ACL 强制阻断 |
| 用户体系 | `KernelRegistrationService`, `KernelTrialService`, `BCryptPasswordHasherAdapter` | 注册/试用/密码 |
| 计费 | `KernelSubscriptionService`, `KernelPaymentService`, `QuotaEnforcementService` | 套餐/支付/配额 |
| 监控 | `KernelAlertEvaluationService`, `DingTalkAlertNotifierAdapter` | Actuator + 告警 |
| 知识库 | `KernelKnowledgeBaseVersionService`, `KnowledgeBaseShareService` | 版本/权限/分享 |
| Agent 市场 | `KernelAgentMarketplaceService`, `RevenueService` | 发布/订阅/收益 |
| 工作流 | `KernelWorkflowVisualizationService`, `WorkflowEventPublisher` | DAG + SSE |
| 高级 RAG | `JinaRerankModelAdapter`, `CachedRetrievalEngine` | Reranker + 缓存 |
| 管理后台 | `KernelAdminTenantService`, `KernelAuditLogService` | 租户/审计 |

## 数据库迁移

全量 SQL：`resources/database/seahorse_init.sql`（包含 V2-V13 所有迁移）
增量迁移：`resources/database/migrations/V2__` 到 `V13__`
应用启动时 `JdbcTenantSchemaUpgrade` + `JdbcChatSchemaUpgrade` 幂等执行。

## 适配器配置（环境变量）

| 适配器 | 环境变量前缀 | 类型选项 |
|--------|-------------|---------|
| 向量库 | `SEAHORSE_AGENT_ADAPTERS_VECTOR_` | milvus, pgvector, noop |
| 缓存 | `SEAHORSE_AGENT_ADAPTERS_CACHE_` | redis, local |
| 存储 | `SEAHORSE_AGENT_ADAPTERS_STORAGE_` | s3, local |
| 消息队列 | `SEAHORSE_AGENT_ADAPTERS_MQ_` | pulsar, direct |
| 搜索 | `SEAHORSE_AGENT_ADAPTERS_SEARCH_` | elasticsearch, lucene |
| AI 模型 | `SEAHORSE_AGENT_ADAPTERS_AI_` | openai-compatible |
| 监控 | `SEAHORSE_AGENT_OBSERVABILITY_` | micrometer, noop |

## 已知问题

详见 `.claude/projects/D--code-seahorse-agent/memory/project_autoconfig_fix.md`
