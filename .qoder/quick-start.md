# Seahorse Agent 快速开始指南

## 默认入口

当前默认交付路径是 Seahorse Agent 原生链路。新部署、默认验证和后续开发应使用：

```powershell
mvn -pl seahorse-agent-bootstrap -am spring-boot:run
```

`seahorse-agent-bootstrap` 默认只扫描 `com.miracle.ai.seahorse.agent`，不依赖旧 `bootstrap`、`seahorse-agent-adapter-legacy` 或 `seahorse-agent-adapter-bridge-legacy`。

旧 `bootstrap`、`framework`、`infra-ai`、legacy adapter/bridge、compat tests 与旧包名 `com.nageoffer.ai.ragent` 生产源码已从主干物理删除；quick start、默认开发和默认部署入口均只使用 Seahorse 原生模块。

## 模块选择

| 场景 | 使用模块 | 说明 |
| --- | --- | --- |
| 默认启动 | `seahorse-agent-bootstrap` | Seahorse 原生可执行启动模块 |
| 默认测试验证 | `seahorse-agent-tests` | 边界、契约与 starter/adapter 组合测试承载模块 |

默认根 reactor 只包含 Seahorse 原生模块、`seahorse-agent-tests` 和 `seahorse-agent-mcp-server`；仓库主干不再提供旧 runtime、legacy adapter、`seahorse-agent` 兼容聚合 artifact 或兼容测试 profile。

## 常用验证

默认主组合回归：

```powershell
mvn -pl seahorse-agent-tests,seahorse-agent-bootstrap -am "-Dmaven.repo.local=.m2\repo" test
```

默认根 reactor 验证：

```powershell
mvn "-Dmaven.repo.local=.m2\repo" validate
```

默认依赖树检查：

```powershell
mvn -pl seahorse-agent-tests -am "-Dmaven.repo.local=.m2\repo" dependency:tree "-Dincludes=com.nageoffer.ai:seahorse-agent,com.nageoffer.ai:seahorse-agent-adapter-legacy,com.nageoffer.ai:seahorse-agent-adapter-bridge-legacy,com.nageoffer.ai:bootstrap"
mvn -pl seahorse-agent-bootstrap -am "-Dmaven.repo.local=.m2\repo" dependency:tree "-Dincludes=com.nageoffer.ai:bootstrap,com.nageoffer.ai:framework,com.nageoffer.ai:infra-ai,com.nageoffer.ai:seahorse-agent-adapter-legacy,com.nageoffer.ai:seahorse-agent-adapter-bridge-legacy"
```

边界扫描：

```powershell
rg "^import .*?(com\.nageoffer\.ai\.ragent|org\.springframework|StpUtil|SaResult|HttpServletRequest|okhttp3|Redisson|Redis|Feishu|Lark)" seahorse-agent-kernel\src\main\java
```

性能门禁：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\perf\compare-rag-perf.ps1 -Baseline docs\performance\rag-baseline.json -After docs\performance\rag-after-docs-api-release.json -MaxRegressionPercent 5
```

## 配置入口

配置统一使用 `seahorse-agent.*` 与原生 adapter 配置。旧 `rag.*`、`ai.*`、`messaging.pulsar.*`、`rustfs.*` 等迁移期配置别名已删除，文档、部署和新增配置样例应只指向 Seahorse 原生模块。

常见原生 adapter：

- 文档来源：local、object storage、Feishu/Lark L3 adapter。
- 模型访问：OpenAI-compatible adapter。
- 向量库：Milvus、pgvector、noop。
- 缓存、锁、限流、Pub/Sub、ID 分配：local 或 Redis adapter。
- 对象存储：local 或 S3 adapter。
- Observation：noop 或 Micrometer adapter。

## API 入口

原生 Web adapter 保持既有路径兼容，前端不需要立即改造路径：

- Auth/User：`/auth/**`、`/user/**`、`/users/**`
- Chat：`/rag/v3/chat`、`/rag/v3/stop`
- Trace：`/rag/traces/runs/**`
- Knowledge：`/knowledge-base/**`
- Ingestion：`/ingestion/pipelines/**`、`/ingestion/tasks/**`
- Memory governance：`/memories/**`
- Plugin 管理：`/agent/plugins/**`
- MCP server：`/mcp`

## 后续开发约束

- L1 kernel 与 ports 不得 import Spring、旧 `com.nageoffer.ai.ragent` 包或基础设施 SDK。
- legacy adapter/bridge legacy、compat tests 和旧 runtime 已物理删除，后续不得作为显式兼容或事故回滚路径新增回流。
- 每个阶段完成后同步 `task_plan.md`、`progress.md` 和 `docs/seahorse-agent-architecture-implementation-review.md`。
