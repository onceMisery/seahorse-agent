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

所有自动配置类注册在 `AutoConfiguration.imports` 中，分 6 层：

1. **Layer 1 — Adapters**（15 个，after DataSource）：向量、缓存、存储、MQ、搜索等
2. **Layer 2 — Outbox relay**（after MQ + Ops）
3. **Layer 3 — Native aggregator**（after DataSource）
4. **Layer 4 — Kernel auth**（after Auth adapter）
5. **Layer 5 — Kernel main**（after Native aggregator）
6. **Layer 6 — Kernel sub-configs**（11 个，after Kernel main + 各自依赖的 adapter）

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

### 适配器配置（环境变量）

| 适配器 | 环境变量前缀 | 类型选项 |
|--------|-------------|---------|
| 向量库 | `SEAHORSE_AGENT_ADAPTERS_VECTOR_` | milvus, pgvector, noop |
| 缓存 | `SEAHORSE_AGENT_ADAPTERS_CACHE_` | redis, local |
| 存储 | `SEAHORSE_AGENT_ADAPTERS_STORAGE_` | s3, local |
| 消息队列 | `SEAHORSE_AGENT_ADAPTERS_MQ_` | pulsar, direct |
| 搜索 | `SEAHORSE_AGENT_ADAPTERS_SEARCH_` | elasticsearch, lucene |
| AI 模型 | `SEAHORSE_AGENT_ADAPTERS_AI_` | openai-compatible |

## 已知问题

详见 `.claude/projects/D--code-seahorse-agent/memory/project_autoconfig_fix.md`
