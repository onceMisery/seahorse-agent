# Seahorse Agent 全方位架构优化方案

> 日期：2026-05-17
> 状态：待 Review
> 输入依据：`docs/architecture-review-report.md` 校准版及其第 13 节二次 Review 意见
> 执行模式：非 TDD；每个阶段先做最小验证设计，再实现，再运行定向回归

---

## 0. 当前实施状态

> 更新时间：2026-05-17

| 阶段 | 状态 | 已落地内容 | 剩余内容 |
|------|------|------------|----------|
| 0 | 已完成 | `docs/architecture-review-report.md` 已吸收二次 Review 意见，修正 Rerank、记忆写入和衰减优先级 | 无 |
| 1 | 已完成 | 检索通道级 timeout、慢通道降级、超时观测、默认检索线程池 Bean、定向测试 | 底层慢任务取消仍可作为后续增强 |
| 2a | 已完成 | `MemoryCaptureStage` 已在响应完成后触发；`DefaultMemoryEnginePort.writeMemory()` 已支持显式可信用户声明写入短期记忆并过滤普通问题；读取 limit 与捕获开关已配置化 | 摘要/候选抽取增强和观测事件可作为后续质量增强 |
| 2b | 部分完成 | 已新增 `ShortTermMemoryMaintenancePort`；`KernelMemoryGovernanceService.runDecay()` 已调用短期记忆维护端口；JDBC 短期记忆适配器已支持过期/低衰减扫描与批量软删；治理 Job 已能触发实际 decay 链路 | Prompt token budget、复杂衰减分更新、高价值记忆自动晋升策略仍待增强 |
| 3 | 已完成 | RAG Trace sample-rate、TTL 软删端口、JDBC run/node 清理、Spring cleanup job、定向测试 | 异步写入、采样命中率指标和清理指标可作为后续增强 |
| 5 | 已完成 | `t_knowledge_chunk(kb_id, doc_id)` 基础复合索引和 PostgreSQL 软删部分索引已补齐 | 迁移发布顺序和大表在线建索引策略需按部署环境规划 |
| 4 | 部分完成 | OpenAI streaming 已迁移到可注入专用 executor；Milvus content/HNSW/mmap/search ef 已配置化；Milvus 业务 metadata JSON 已统一 Jackson | starter 依赖拆分仍需单独规划发布边界 |
| 6 | 已完成主要闭环 | wrapper passThrough、storage reliableUpload 默认语义、cache-local 命名说明、chatStore 门面式拆分、元数据治理最小 UI 已完成 | chatStore 深层 slice 化和元数据治理高级编辑体验可作为后续增强 |
| 7A | 已完成主要目标 | native 自动配置已按 storage、observation、cache、local、auth、MQ、AI、vector、keyword、knowledge repository、ingestion repository、memory repository、retrieval repository、ops repository、outbox relay、metadata 十六个技术域完成兼容拆分；主配置仍通过 `@Import` 聚合，Bean 名称与条件保持兼容，主 native 配置已降至约 53 行且不再直接声明 `@Bean`；kernel memory、trace、model、auth/user、chat、ops/management、keyword maintenance、document refresh、metadata governance、retrieval orchestration、knowledge/ingestion 与 plugin/feature registration 自动配置已拆出主要职责，少量跨主配置强依赖 Bean 仍保留在主 kernel 配置，主 kernel 配置降至约 175 行 | native 主配置拆分目标已完成，后续重点转向 starter 依赖边界和少量跨域 Bean 的收敛策略 |

---

## 1. 目标

本方案用于把架构 Review 中的候选问题转化为可排期、可验证、可回滚的优化路线。核心目标是：

1. 先处理会影响线上请求稳定性的 P0 问题。
2. 再补齐 Agent Memory、RAG Trace、starter 依赖、适配器配置化等平台化能力。
3. 避免为了减少文件数量而做机械合并，所有重构必须能降低真实维护成本。
4. 保持现有端口适配器边界、Spring 条件装配语义、已有 API 契约和零基础设施开发模式。

---

## 2. 基线与权威参考

| 类型 | 文件 | 用途 |
|------|------|------|
| Review 基线 | `docs/architecture-review-report.md` | 当前问题清单、优先级和不采纳项 |
| 混合检索设计 | `docs/zh/content/架构设计/混合检索与重排完善设计方案.md` | 检索通道、RRF、Rerank、关键词适配器边界 |
| 元数据治理设计 | `docs/zh/content/架构设计/企业级元数据抽取与治理管道设计.md` | metadata schema、Review/Quarantine、索引联动边界 |
| 记忆设计 | `docs/Agent_Memory_系统改进设计方案.md` | 四层记忆、衰减、质量评估、token 预算设计 |
| 查询优化设计 | `docs/memory-query-optimizer-cross-session-design.md` | QueryOptimizer 与跨会话推理背景 |
| 当前计划记录 | `task_plan.md` / `progress.md` / `findings.md` | 已完成能力与后续规划边界 |

---

## 3. 优先级原则

### 3.1 P0 定义

P0 只包含会导致现有请求不可用、明显阻塞、资源耗尽或数据损坏的问题。

当前 P0：

1. 检索通道缺少通道级超时。
2. 检索线程池缺省退化为同步执行。

### 3.2 P1 定义

P1 是核心能力闭环、生产治理或部署维护成本问题，不一定影响当前请求可用性，但会影响企业级交付质量。

当前 P1：

1. 记忆写入闭环与衰减闭环。
2. starter 依赖过重。
3. OpenAI streaming 专用线程池已补齐。
4. Milvus 核心索引参数已配置化，JSON 序列化统一仍保留后续规划。
5. RAG Trace 采样与 TTL 已完成最小治理，后续只保留异步写入和指标增强。
6. 记忆 limit/token budget 配置化。
7. `t_knowledge_chunk(kb_id, doc_id)` 复合索引已补齐，后续关注大表在线建索引发布策略。

### 3.3 P2 定义

P2 是清理、命名、低风险维护性优化或前端体验增强。

当前 P2：

1. wrapper 占位实现清理或标注。
2. Gson 统一 Jackson（Milvus 业务 JSON 已统一到 Jackson，SDK JsonObject 载体保留）。
3. `ObjectStoragePort.reliableUpload` 语义收敛（已完成）。
4. `adapter-cache-local` 命名说明或重命名（已补充说明，暂不重命名）。
5. `chatStore.ts` 拆分（已完成第一步门面式拆分）。
6. 元数据治理 UI（已新增最小管理页）。

---

## 4. 非目标

本轮优化不做以下事情：

1. 不把四层记忆简化为两层。
2. 不按“文件数量”机械合并端口或 JDBC adapter。
3. 不删除 `idx_conv_user` 或其他仍有查询使用的索引。
4. 不把 OpenSearch 作为本轮默认实现，仍保留为后续规划。
5. 不在 P0 阶段重写 `KernelMultiChannelRetrievalEngine` 主流程；P0 只补超时、线程池和观测。
6. 不在 P0 阶段拆 starter 依赖、拆自动配置或做前端重构。
7. 不引入新的外部任务调度或 tracing 框架；优先扩展现有 Spring/Micrometer/RAG Trace 能力。

---

## 5. 总体分阶段路线

| 阶段 | 优先级 | 名称 | 目标 | 预计提交粒度 |
|------|--------|------|------|--------------|
| 0 | 文档治理 | 修正 Review 报告优先级 | 让报告与第 13 节一致，避免误排期 | 1 个 docs 提交 |
| 1 | P0 | 检索稳定性 | 通道级超时 + 默认线程池 + 超时观测 | 1-2 个代码提交 |
| 2a | P1 | 记忆写入闭环 | `MemoryCaptureStage` + 可信候选写入 + 读取 limit 配置 | 1-2 个代码提交 |
| 2b | P1 | 记忆治理与预算 | 短期衰减维护 + token budget + 治理 Job 闭环 | 1-2 个代码提交 |
| 3 | P1 | 生产可观测治理 | RAG Trace 采样/TTL 已完成；关键业务指标后续增强 | 1-2 个代码提交 |
| 4 | P1 | 部署与适配器治理 | starter 依赖拆分策略、OpenAI streaming executor、Milvus 配置 | 2-4 个代码提交 |
| 5 | P1 | 数据库补偿 | `t_knowledge_chunk(kb_id, doc_id)` 索引与迁移说明 | 1 个代码/SQL 提交 |
| 6 | P2 | 清理与前端运营 | wrapper 占位已显式暴露 passThrough；storage 可靠上传默认语义已收敛；cache-local 命名已澄清；chatStore 已完成第一步门面式拆分；元数据治理 UI 已新增最小管理页 | 多个独立提交 |
| 7 | P1/P2 | 架构瘦身与职责治理 | 自动配置已开始按技术域拆分；JDBC 元数据适配器拆分、端口准入规则、聊天/检索阶段边界收敛仍待推进 | 多个独立提交 |

---

## 6. 阶段 0：修正 Review 报告

### 6.1 目标

把第 13 节二次 Review 意见吸收到报告主体，确保后续团队只看到一套一致的优先级。

### 6.2 修改文件

- `docs/architecture-review-report.md`

### 6.3 修改内容

1. 将 `writeMemory()` 从 P0 降为 P1。
2. 将 `executeMemoryDecay()` 从 `P0/P1` 统一为 P1。
3. 将 4.4 节改为“Rerank 超时已完整实现；P0 仅针对检索通道级超时”。
4. 11.1 高优先级表只保留检索通道超时和默认线程池。
5. 11.2 中优先级表加入记忆写入闭环和衰减闭环。
6. 附录补充统计方法，便于后续复核。

### 6.4 验证

```powershell
git diff --check -- docs/architecture-review-report.md
rg -n "P0/P1|记忆写入闭环缺失.*P0|executeMemoryDecay.*P0|Rerank 超时已部分完成" docs/architecture-review-report.md
```

预期：

- `git diff --check` 无输出错误。
- `rg` 无命中。

### 6.5 回滚

只涉及文档，可用单文件反向补丁或 `git restore -- docs/architecture-review-report.md` 回滚本阶段。

---

## 7. 阶段 1：P0 检索稳定性

### 7.1 问题

`KernelMultiChannelRetrievalEngine` 当前对所有启用通道执行 `CompletableFuture.supplyAsync()` 后直接 `join()`。任一通道变慢时，整体检索请求会等待该通道完成。starter 在缺少指定 executor Bean 时使用 `Runnable::run`，实际会退化为同步执行。

### 7.2 目标

1. 每个检索通道都有独立超时边界。
2. 单通道超时只降级该通道，不影响其他通道结果。
3. 默认装配提供真实线程池，不依赖用户手写 executor Bean。
4. 超时事件可观测，可用于排查慢通道。

### 7.3 涉及文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `seahorse-agent-kernel/src/main/java/.../KernelMultiChannelRetrievalEngine.java` | 修改 | 增加通道超时执行与降级 |
| `seahorse-agent-kernel/src/main/java/.../RetrievalOptions.java` | 可能修改 | 如需补默认 timeout 或辅助方法 |
| `seahorse-agent-spring-boot-starter/src/main/java/.../SeahorseAgentKernelAutoConfiguration.java` | 修改 | 增加默认检索 executor Bean |
| `seahorse-agent-tests/src/test/java/.../KernelMultiChannelRetrievalEngineTraceTests.java` | 修改/新增测试 | 覆盖通道超时与 trace 完成 |
| `seahorse-agent-tests/src/test/java/.../KernelRetrievalEngineTests.java` | 修改/新增测试 | 覆盖慢通道不阻塞快通道 |
| `seahorse-agent-tests/src/test/java/.../SeahorseAgentKernelAutoConfigurationTests.java` | 修改/新增测试 | 覆盖默认 executor 装配 |

### 7.4 设计方案

#### 7.4.1 通道超时选择规则

根据 `SearchChannelType` 选择 timeout：

| 通道类型 | timeout 来源 | 默认值 |
|----------|--------------|--------|
| `VECTOR_GLOBAL` | `RetrievalOptions.vectorTimeout` | 5s |
| `INTENT_DIRECTED` | `RetrievalOptions.vectorTimeout` | 5s |
| `KEYWORD_BM25` / `KEYWORD_ES` | `RetrievalOptions.keywordTimeout` | 5s |
| 其他 | 较小安全默认值 | 5s |

如果配置为 `null`、0 或负数，使用默认值，而不是无限等待。

#### 7.4.2 执行模型

通道执行流程：

1. 为每个 channel 创建 future。
2. 使用 `completeOnTimeout(emptyResult(channel, timeoutMs), timeoutMs, MILLISECONDS)` 或等价逻辑。
3. 超时结果需要记录 channelName、channelType、timeoutMs、latencyMs、hitCount=0。
4. 正常异常仍走现有 `executeSingleChannel` 的降级路径。
5. 保证 trace node 在正常、异常、超时路径都只 finish 一次。

复杂并发逻辑需要中文注释，说明：

```java
// 单个检索通道超时只降级当前通道，避免慢后端阻塞整个多通道检索。
```

#### 7.4.3 默认线程池

在 starter 中增加默认 Bean：

| Bean 名 | 用途 | 建议默认 |
|---------|------|----------|
| `ragRetrievalThreadPoolExecutor` | 多通道检索 | core=4, max=16, queue=200 |
| `ragInnerRetrievalThreadPoolExecutor` | 意图定向内部并发 | core=4, max=16, queue=200 |
| `ragContextThreadPoolExecutor` | RAG 上下文组装 | core=2, max=8, queue=100 |

使用 `@ConditionalOnMissingBean(name = "...")`，确保用户自定义 Bean 优先。

配置项可先用 `@Value`：

```properties
seahorse-agent.retrieval.executor.core-size=4
seahorse-agent.retrieval.executor.max-size=16
seahorse-agent.retrieval.executor.queue-capacity=200
seahorse-agent.retrieval.executor.thread-name-prefix=seahorse-rag-retrieval-
```

### 7.5 验证

实施前先确认定向测试类存在，避免 `surefire.failIfNoSpecifiedTests=false` 掩盖拼写错误或测试缺失：

```powershell
rg --files seahorse-agent-tests/src/test/java | rg "KernelMultiChannelRetrievalEngineTraceTests|KernelRetrievalEngineTests|SeahorseAgentKernelAutoConfigurationTests"
```

运行：

```powershell
mvn -pl seahorse-agent-tests,seahorse-agent-spring-boot-starter -am "-Dtest=KernelMultiChannelRetrievalEngineTraceTests,KernelRetrievalEngineTests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
git diff --check
```

验收：

1. 慢 keyword channel 超时后，fast vector channel 结果仍返回。
2. 总耗时不超过 timeout + 合理调度误差。
3. 超时通道有观测事件或 trace 标记。
4. 无用户自定义 executor 时，Spring context 中存在默认 executor Bean。
5. 已有检索测试不回退。
6. 上述 3 个定向测试类必须实际存在；若新增/改名测试类，验证命令同步更新。

### 7.6 回滚

保留原 `executeSingleChannel` 逻辑。若超时逻辑引入问题，可临时通过配置放大 timeout，但不建议恢复无限等待。

---

## 8. 阶段 2：P1 记忆闭环

### 8.1 问题

四层记忆读链路已经接入 `KernelChatPipeline.activateMemory()`。当前 2a/2b 已补齐最小写入和衰减维护闭环，后续重点转为质量增强：

- `DefaultMemoryEnginePort.writeMemory()` 已支持显式可信用户声明写入短期记忆，并支持关闭捕获。
- `KernelMemoryGovernanceService.runDecay()` 已通过 `ShortTermMemoryMaintenancePort` 扫描和软删过期/低衰减短期记忆。
- 短期/长期/语义读取 limit 已配置化。
- token budget 未实现。

这些问题不阻断当前对话功能，但会让 Agent Memory 无法持续积累、筛选和控制 prompt 成本。

### 8.2 目标

1. 对话结束后只写入可信摘要、事实、偏好或画像，不写原始问题噪声。
2. 记忆读取 limit 配置化。
3. 短期记忆支持过期和衰减维护。
4. Prompt 记忆注入支持 token budget，避免记忆挤占 RAG 和回答上下文。

### 8.3 拆批边界

阶段 2 拆成两个可独立回滚的子阶段：

| 子阶段 | 范围 | 不包含 |
|--------|------|--------|
| 2a | `MemoryCaptureStage`、`writeMemory()` 可信候选写入、读取 limit 配置化 | 衰减扫描、晋升策略、token budget |
| 2b | 短期记忆过期/衰减维护、治理 Job 实际闭环、Prompt token budget | 主链路写入触发点重构 |

当前实现状态：2a 已完成；2b 已完成短期过期/低衰减扫描、软删和治理 Job 调用链路，Prompt token budget 仍未实现。

2a 先让系统开始积累可信短期记忆；2b 再治理存量增长和 Prompt 成本。2a 不依赖 2b 的维护端口，出现问题时可以只关闭捕获阶段或写入开关。

### 8.4 涉及文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `KernelChatPipeline.java` | 修改 | 增加响应完成后的记忆捕获触发点 |
| `MemoryCaptureStage.java` / 轻量阶段接口 | 新增 | 非阻塞捕获可信记忆候选 |
| `DefaultMemoryEnginePort.java` | 修改 | 写入、limit 配置、budget 入口 |
| `MemoryEnginePort.java` | 可能不改 | 优先复用现有契约 |
| `ShortTermMemoryPort.java` / 新维护端口 | 修改/新增 | 扫描过期/低衰减短期记忆 |
| `JdbcShortTermMemoryRepositoryAdapter.java` | 修改 | 实现维护查询 |
| `KernelMemoryGovernanceService.java` | 修改 | 调用维护端口和衰减逻辑 |
| `SeahorseAgentKernelAutoConfiguration.java` | 修改 | 注入 memory limit / budget 配置 |
| `SeahorseMemoryGovernanceJob.java` | 修改 | 调用实际维护链路 |
| `DefaultMemoryEnginePortTests.java` | 修改/新增 | 覆盖写入、limit、budget |
| `KernelMemoryGovernanceServiceTests.java` | 修改/新增 | 覆盖衰减维护 |

### 8.5 写入策略

不直接写入完整用户消息。写入来源必须满足以下之一：

| 来源 | 可写入内容 | 默认处理 |
|------|------------|----------|
| 对话摘要 | 简短摘要、明确事实 | 写短期记忆 |
| LLM/规则抽取 | PROFILE、PREFERENCE、FACT | 写短期，治理后晋升 |
| 用户显式保存 | 偏好、个人信息、任务约束 | 写短期或长期 |
| 系统事件 | 审核通过的记忆候选 | 按治理规则写入 |

写入前必须具备：

- `userId`
- `conversationId`
- `type`
- `content`
- `confidence`
- `sourceIds`

### 8.6 写入触发点

写入由 `MemoryCaptureStage` 在 `ResponseStreamingStage` 完成后异步触发。主链路顺序建议为：

```text
loadMemory -> activateMemory -> optimizeQuery -> rewriteQuery -> resolveIntents -> retrieve -> streamResponse -> captureMemory
```

约束：

1. `MemoryCaptureStage` 只负责捕获、抽取和调用 `writeMemory()`，不做 promote、decay 或 inference。
2. 捕获失败不阻塞回答完成，但必须记录 trace/metric，至少包含 `userId`、`conversationId`、失败类型和候选数量。
3. 捕获阶段必须有开关，默认可以 fail-open；回滚时关闭捕获开关即可保留读链路。
4. 对流式响应，只有在响应正常完成或已产生可用答案摘要时才触发写入，取消/异常链路不写入。

### 8.7 衰减策略

短期记忆维护分三类：

1. 已过期：`expires_time < now`，直接删除或标记无效。
2. 低衰减分：`decay_score < threshold`，标记待清理。
3. 高价值记忆：根据 importance/confidence/access_count 晋升长期记忆。

第一版不做复杂冲突检测，只实现过期清理和基础衰减分更新。

### 8.8 Token budget

默认分配：

| 上下文来源 | 默认比例 |
|------------|----------|
| 工作记忆/当前会话 | 40% |
| 短期记忆 | 25% |
| 长期记忆 | 20% |
| 语义记忆 | 15% |

如果缺少精确 token 计数，先使用已有 `TokenCounterPort.approximate()`。后续可替换模型精确 tokenizer。

### 8.9 验证

```powershell
mvn -pl seahorse-agent-tests,seahorse-agent-spring-boot-starter,seahorse-agent-adapter-repository-jdbc -am "-Dtest=DefaultMemoryEnginePortTests,KernelMemoryGovernanceServiceTests,Jdbc*Memory*Tests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
git diff --check
```

验收：

1. `writeMemory()` 能保存可信摘要到短期记忆。
2. 原始用户问题不会被无条件保存为记忆。
3. limit 配置生效。
4. 过期短期记忆可被治理 Job 清理。
5. 现有 `activateMemory()` 读链路不受影响。
6. `MemoryCaptureStage` 失败不影响流式响应完成，并能留下 trace/metric 证据。

---

## 9. 阶段 3：P1 可观测与生产治理

### 9.1 RAG Trace 采样与 TTL

#### 目标

避免生产环境 trace 表无限增长，同时保留排障价值。当前已完成最小治理闭环。

#### 涉及文件

- `KernelRagTraceRecorder.java`：已支持采样率，采样只在 run 入口判定一次。
- RAG Trace repository port：已新增 `deleteRunsBefore(Instant before, int limit)` 默认端口。
- `JdbcRagTraceRepositoryAdapter.java`：已实现过期 run/node 同步软删。
- `SeahorseAgentKernelAutoConfiguration.java`：已装配 sample-rate 和 cleanup job。
- `SeahorseRagTraceCleanupJob.java`：已新增 Spring 定时清理任务，使用分布式锁避免多实例重复清理。

#### 配置建议

```properties
seahorse-agent.rag-trace.enabled=true
seahorse-agent.rag-trace.sample-rate=1.0
seahorse-agent.rag-trace.ttl-days=30
seahorse-agent.rag-trace.cleanup.enabled=true
seahorse-agent.rag-trace.cleanup-batch-size=1000
seahorse-agent.rag-trace.cleanup-cron=0 20 3 * * ?
```

#### 验证

```powershell
mvn -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc,seahorse-agent-spring-boot-starter -am "-Dtest=KernelRagTraceRecorderTests,JdbcRagTraceRepositoryAdapterTests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

验收：

1. sample-rate=0 时不写 trace。
2. sample-rate=1 时保持现有行为。
3. TTL 清理只删除过期 run/node，不影响未过期 trace。
4. cleanup job 默认装配，可通过 `seahorse-agent.rag-trace.cleanup.enabled=false` 关闭。

### 9.2 Micrometer 业务指标

#### 目标

把通用 observation event 转换成易于监控平台消费的业务指标。

#### 指标建议

| 指标 | 类型 | 标签 |
|------|------|------|
| `seahorse.retrieval.channel.duration` | timer | channelType, channelName, outcome |
| `seahorse.retrieval.channel.timeout` | counter | channelType, channelName |
| `seahorse.rerank.duration` | timer | model, outcome |
| `seahorse.outbox.pending` | gauge | topic |
| `seahorse.metadata.backfill.running` | gauge | tenantId/kbId 可选 |
| `seahorse.memory.governance.duration` | timer | outcome |

#### 验证

```powershell
mvn -pl seahorse-agent-adapter-observation-micrometer,seahorse-agent-tests -am "-Dtest=*Observation*Tests,*Retrieval*Tests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

---

## 10. 阶段 4：P1 部署与适配器治理

### 10.1 Starter 依赖治理

当前状态：已完成第一阶段落地。`seahorse-agent-spring-boot-starter` 已将 OpenAI-compatible、MCP HTTP、Tika、Feishu、Milvus、PgVector、Redis、S3、Micrometer、Elasticsearch、Lucene、Pulsar 等重型适配器依赖改为 `optional`；同时新增 `seahorse-agent-spring-boot-starter-all` 聚合模块承载全量官方适配器依赖，`seahorse-agent-bootstrap` 与测试模块暂切到 `starter-all`，以保留现有默认运行路径。native 子自动配置中混合的重型 adapter 装配已下沉到受 `@ConditionalOnClass(name = "...")` 保护的子配置，最小 classpath 场景下不会再因为缺失类型导致类加载失败。

#### 问题

当前 `seahorse-agent-spring-boot-starter` 直接依赖大量适配器，最小部署也会携带 Milvus、Pulsar、S3、Tika、Elasticsearch、Lucene 等 SDK。

#### 当前风险判断

不能直接把现有 adapter 依赖批量标记为 `optional`。原因是 `SeahorseAgentNativeAdapterAutoConfiguration` 单个自动配置类直接 import 并在 `@Bean` 方法签名里引用了几乎所有 adapter 类型。若消费端只引入 starter 而未显式引入某个 optional adapter，Spring Boot 在解析该自动配置类时可能因为缺少方法签名类型而发生类加载失败，而不是按 `@ConditionalOnClass` 优雅跳过。

因此 starter 依赖治理必须先拆自动配置类，再调整 Maven 依赖传递性。

#### 建议拆分顺序

1. 先把 native adapter 自动配置按技术域拆成多个小配置类，并在每个配置类上使用 `@ConditionalOnClass` 保护外部 SDK 和 adapter 类型：

| 新配置类 | 覆盖范围 | 条件保护 |
|---------|----------|----------|
| `SeahorseNativeCoreAdapterAutoConfiguration` | local/noop/direct/JDBC 基础能力 | kernel + JDBC/local 类型 |
| `SeahorseNativeAiAutoConfiguration` | OpenAI-compatible、query optimizer | `OpenAiCompatibleModelAdapter`、`OkHttpClient` |
| `SeahorseNativeVectorAutoConfiguration` | Milvus/PGVector/Noop vector | `MilvusClientV2`、`PgVectorAdapter` |
| `SeahorseNativeSearchAutoConfiguration` | Elasticsearch/Lucene keyword 与 metadata index | ES/Lucene adapter 类型 |
| `SeahorseNativeStorageAutoConfiguration` | Local/S3 object storage | `S3Client` |
| `SeahorseNativeMqAutoConfiguration` | Direct/Pulsar/Reliable outbox | `PulsarClient` |
| `SeahorseNativeSourceParserAutoConfiguration` | Tika/Feishu | Tika/Feishu adapter 类型 |

2. 保持旧 `SeahorseAgentNativeAdapterAutoConfiguration` 一轮兼容：可作为空壳或导入类，避免自动配置导入文件一次性大改导致使用方升级风险。

3. 拆完自动配置后再调整依赖：

| 依赖类别 | 处理策略 |
|---------|----------|
| kernel、JDBC、local/noop/direct | 可继续作为核心 starter 默认依赖 |
| Milvus、Elasticsearch、Pulsar、S3、Feishu、Tika | 移入能力 starter 或标记 optional，并要求部署工程显式选择 |
| Lucene | 可作为轻量搜索能力 starter，默认不强绑到 core starter |

4. 最后补一个最小依赖启动测试：只引入 core starter，不引入 Milvus/ES/Pulsar/S3/Tika 时，Spring context 仍可启动且不会解析缺失 adapter 类型。

#### 推荐路线

分三步做，避免 optional 化直接改变现有部署的传递依赖行为。

第一步：新增轻量 starter，不改变现有聚合 starter 行为。

| 新 starter | 包含 |
|------------|------|
| `seahorse-agent-spring-boot-starter-core` | 已落地；当前作为精简 starter 坐标别名，承载 kernel + web + jdbc + direct/local/noop + optional 扩展依赖边界 |
| `seahorse-agent-spring-boot-starter-all` | 已落地；显式聚合所有官方适配器，承载原有全量 starter 行为 |

第二步：迁移 bootstrap 到 `starter-core` + 显式适配器。

- bootstrap 需要哪些适配器，就在 `seahorse-agent-bootstrap/pom.xml` 中显式引入。
- 迁移期间当前聚合 starter 行为保持不变，避免影响已有使用方。

第三步：拆能力 starter，并 optional 化旧聚合 starter 的非核心依赖。

| 新 starter | 包含 |
|------------|------|
| `seahorse-agent-starter-vector-milvus` | Milvus |
| `seahorse-agent-starter-search-elasticsearch` | Elasticsearch |
| `seahorse-agent-starter-search-lucene` | Lucene |
| `seahorse-agent-starter-storage-s3` | S3 |
| `seahorse-agent-starter-mq-pulsar` | Pulsar |

当前实现采用兼容落地：保留现有 artifactId `seahorse-agent-spring-boot-starter` 作为稳定实现载体，同时新增 `starter-core` 作为精简坐标别名、`starter-all` 作为全量聚合入口。`bootstrap` 已切到 `starter-core + starter-all` 组合，后续若需要进一步清理命名，可再把自动配置源码整体迁移到 `starter-core`。

#### 验证

```powershell
mvn -pl seahorse-agent-spring-boot-starter,seahorse-agent-spring-boot-starter-all,seahorse-agent-bootstrap,seahorse-agent-tests -am "-Dtest=SeahorseAgentNativeAdapterAutoConfigurationTests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl seahorse-agent-spring-boot-starter -am dependency:tree
```

### 10.2 OpenAI streaming executor

当前状态：已完成最小治理。`OpenAiCompatibleModelAdapter` 支持注入 `Executor`，`SeahorseAgentNativeAdapterAutoConfiguration` 在选择 `openai-compatible` 时默认装配 `openAiStreamingExecutor`，避免 SSE 阻塞读取占用公共 ForkJoinPool。

#### 目标

避免阻塞 SSE 读取占用 `ForkJoinPool.commonPool()`。

#### 涉及文件

- `OpenAiCompatibleModelAdapter.java`
- `OpenAiCompatibleModelProperties.java`
- `SeahorseAgentNativeAdapterAutoConfiguration.java`
- OpenAI adapter tests

#### 方案

1. `OpenAiCompatibleModelAdapter` 构造函数接收可选 `Executor`。
2. `streamChat()` 使用专用 executor。
3. starter 提供默认 `seahorseOpenAiStreamingExecutor`。
4. cancellation 仍调用 OkHttp `Call.cancel()`。

#### 验证

```powershell
mvn -pl seahorse-agent-adapter-ai-openai-compatible,seahorse-agent-spring-boot-starter -am "-Dtest=*OpenAi*Tests,*NativeAdapterAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

### 10.3 Milvus 配置化

当前状态：已完成核心参数配置化。`MilvusVectorProperties` 已承载 `contentMaxLength`、HNSW `M/efConstruction`、`mmapEnabled` 和 `searchEf`，starter 可通过 `seahorse-agent.adapters.vector.milvus.*` 注入。业务侧 metadata 序列化/反序列化已统一使用 Jackson；Milvus SDK 行数据仍保留 `JsonObject/JsonArray` 作为边界载体。

#### 目标

HNSW 参数、内容最大长度、mmap、JSON 序列化统一。

#### 配置建议

```properties
seahorse-agent.adapters.vector.milvus.content-max-length=65535
seahorse-agent.adapters.vector.milvus.hnsw.m=48
seahorse-agent.adapters.vector.milvus.hnsw.ef-construction=200
seahorse-agent.adapters.vector.milvus.mmap-enabled=false
seahorse-agent.adapters.vector.milvus.search-ef=128
```

#### 验证

```powershell
mvn -pl seahorse-agent-adapter-vector-milvus,seahorse-agent-spring-boot-starter -am "-Dtest=*Milvus*Tests,*NativeAdapterAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

---

## 11. 阶段 5：P1 数据库补偿

### 11.1 `t_knowledge_chunk(kb_id, doc_id)` 复合索引

#### 问题

当前 `t_knowledge_chunk` 只有 `doc_id` 索引，但关键词索引维护、文档分块查询和部分 metadata 写回路径会按 `kb_id + doc_id` 操作。

#### 当前状态

已完成：

- `resources/database/seahorse_init.sql` 新增 `idx_knowledge_chunk_kb_doc` 普通复合索引，兼容基础初始化和非 PostgreSQL 方言。
- `metadata-governance-postgresql.sql` 新增 `idx_knowledge_chunk_kb_doc_alive` 部分索引，过滤 `deleted = 0` 的维护路径。

发布注意：如果生产表数据量较大，应按目标数据库能力选择在线建索引或低峰窗口执行，避免 DDL 阻塞写入。

#### 涉及文件

- `resources/database/seahorse_init.sql`
- `seahorse-agent-adapter-repository-jdbc/src/main/resources/META-INF/seahorse-agent/sql/metadata-governance-postgresql.sql` 或新增 migration SQL

#### SQL 建议

```sql
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_kb_doc_alive
ON t_knowledge_chunk (kb_id, doc_id)
WHERE deleted = 0;
```

如果目标数据库版本或方言不支持 partial index，应使用普通复合索引：

```sql
CREATE INDEX idx_knowledge_chunk_kb_doc
ON t_knowledge_chunk (kb_id, doc_id);
```

#### 验证

```powershell
mvn -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcKnowledgeChunkRepositoryAdapterTests,JdbcKeywordIndexAdapterTests,JdbcMetadataGovernanceRepositoryAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
git diff --check
```

---

## 12. 阶段 6：P2 清理与前端运营

### 12.1 Wrapper 占位实现

当前状态：已完成最小语义澄清。`PortWrapper` 增加 `passThrough()`，五个占位 wrapper 在快照中显式标记为透传，避免被误读为真实审计、限流、熔断、重试或观测实现。

方案二选一：

1. 删除 `AuditPortWrapper`、`CircuitBreakerPortWrapper`、`RateLimitPortWrapper`、`RetryPortWrapper`、`ObservationPortWrapper`。
2. 保留但重命名/注释为 `Noop*Wrapper`，避免误导。

建议优先删除未被使用的占位 wrapper。删除前先搜索引用：

```powershell
rg -n "AuditPortWrapper|CircuitBreakerPortWrapper|RateLimitPortWrapper|RetryPortWrapper|ObservationPortWrapper" .
```

### 12.2 `ObjectStoragePort.reliableUpload`

当前状态：已完成兼容性语义收敛。`reliableUpload()` 保留为端口默认方法并委托 `upload()`；Local/S3 adapter 删除重复覆盖。后续只有适配器真正提供幂等、重试、断点续传、内容校验或半成品清理能力时，才允许覆盖该方法。

如需升级为真实可靠上传，必须定义：

- 是否幂等。
- 是否重试。
- 是否断点续传。
- 失败后是否清理半成品。
- 是否校验内容摘要或对象长度。

### 12.3 `adapter-cache-local` 命名

当前状态：已补充模块 README 和 POM 描述，明确该模块是“本地内存缓存 + 单 JVM 协调”适配器，不只是缓存适配器。

短期结论：不直接重命名 artifact。`seahorse-agent-adapter-cache-local` 已被 starter 和文档引用，直接改名会破坏 Maven 坐标、自动配置依赖和用户侧显式依赖。

长期方案：如后续确实需要职责拆分，可新增 `seahorse-agent-adapter-coordination-local`，先迁移 `DistributedLockPort`、`DistributedSemaphorePort`、`RateLimiterPort`、`PubSubPort` 等协调类能力，并提供兼容迁移期；当前 artifact 保留 `KeyValueCachePort` 或作为兼容聚合包。

### 12.4 `chatStore.ts` 拆分

当前状态：已完成第一步低风险拆分。`chatStore.ts` 保留 `useChatStore` 门面，新增 `chatStoreTypes.ts`、`chatSessionUtils.ts`、`chatStreamUtils.ts` 承担状态契约、会话列表合并/反馈映射、流式基础配置与思考耗时计算，现有组件导入路径不变。

建议拆分为：

| 文件 | 职责 |
|------|------|
| `sessionStore.ts` | 会话列表、选择、创建、重命名、删除 |
| `messageStore.ts` | 消息列表、反馈、历史加载 |
| `streamStore.ts` | SSE 状态、取消、thinking、delta 追加 |
| `chatStore.ts` | 向后兼容门面，逐步收敛 |

验证：

```powershell
cd frontend
npm run typecheck
npm run build
```

### 12.5 元数据治理 UI

当前状态：已新增 `/admin/metadata-governance` 最小管理页和 `metadataGovernanceService.ts`，接入 Schema 字段、Review 队列、Quarantine 隔离区、Quality 报表四类后端 API。页面采用租户/知识库筛选、分段视图、表格和操作按钮，先满足治理闭环可见与基本操作。

按业务价值排序：

1. Schema 字段管理页。
2. Review 待审核列表与详情页。
3. Quarantine 隔离列表、重试、解决页。
4. Quality 报表页。
5. Backfill 任务列表与创建页。

验收：

- 页面只消费现有 Web API，不绕过后端治理规则。
- 动态 metadata 字段展示必须基于 Schema，不允许前端任意拼过滤字段。

---

## 13. 跨阶段兼容性边界

1. 所有新增配置必须有默认值，旧配置不改即可启动。
2. Spring Bean 名称保持兼容，用户自定义 Bean 优先。
3. 检索通道超时只影响慢通道，不改变正常通道排序语义。
4. 记忆写入不保存原始用户问题，避免隐私和噪声风险。
5. 数据库索引新增必须可重复执行。
6. starter 依赖治理不能破坏 bootstrap 当前默认可运行能力。
7. 前端 store 拆分必须保持现有页面路由和 API 请求行为。

---

## 14. 回滚策略

| 阶段 | 回滚方式 |
|------|----------|
| 阶段 0 | 单文件回滚 review 报告 |
| 阶段 1 | 调大 timeout 或禁用默认线程池 Bean；保留通道异常降级 |
| 阶段 2 | 关闭 memory 写入/治理 scheduler，保留读取链路 |
| 阶段 3 | 设置 trace sample-rate=0 或 `seahorse-agent.rag-trace.cleanup.enabled=false` |
| 阶段 4 | 保持原 starter 依赖或由 bootstrap 显式引入适配器 |
| 阶段 5 | 新增索引可保留；如影响写入性能再单独 DROP |
| 阶段 6 | 前端门面 `chatStore.ts` 保留期间可回退到旧调用方式 |
| 阶段 7 | 保留旧 Bean 名称和旧自动配置导入一轮；拆分后的配置类可按域单独禁用或回退 |

---

## 15. 建议排期

### 第 1 批：稳定性闭环

1. 阶段 0：报告修正。
2. 阶段 1：检索通道超时 + 默认线程池。

交付标准：

- 检索 P0 关闭。
- Review 报告优先级一致。
- 定向测试通过。

### 第 2 批：记忆与可观测

1. 阶段 2a：记忆写入触发、可信候选写入、limit 配置。
2. 阶段 2b：短期记忆衰减维护、token budget、治理 Job 闭环。
3. 阶段 3：RAG Trace 采样/TTL 已完成，后续补关键 Micrometer 指标。

交付标准：

- 记忆开始自动积累可信内容。
- 记忆捕获失败不阻塞流式响应，并有 trace/metric 证据。
- trace 表可控增长。
- 检索/记忆/回填关键路径可监控。

### 第 3 批：部署治理与适配器稳定性

1. 阶段 4：starter 依赖治理、OpenAI streaming executor、Milvus 配置化。
2. 阶段 5：数据库复合索引。

交付标准：

- 最小部署依赖减少。
- 高并发 streaming 不占 common pool。
- Milvus 参数可按部署调优。

### 第 4 批：清理与运营体验

1. 阶段 6：wrapper/storage/命名清理。
2. 元数据治理 UI。
3. 前端 store 拆分。

交付标准：

- 占位和重复语义减少。
- 管理员可在 UI 操作元数据治理闭环。
- 前端聊天状态边界清晰。

### 第 5 批：架构瘦身与职责治理

1. 阶段 7A：拆分 Spring 自动配置和 starter 依赖边界。
2. 阶段 7B：梳理 metadata 事务边界并拆分 JDBC 元数据治理适配器，保留端口 Bean 兼容。
3. 阶段 7C：收敛聊天/检索/记忆阶段职责，补充端口准入规则并固化为 ADR。

交付标准：

- 新增检索通道、存储适配器或记忆策略时，不再需要修改千行级自动配置类。
- JDBC 元数据治理改动可以按 schema、dictionary、review、quarantine、backfill、quality 独立测试。
- 端口数量增长有准入标准，DTO 文件增长不再被误判为架构端口膨胀。
- metadata 跨端口流程明确使用同事务、domain event 还是 outbox。

---

## 16. 验证总表

| 领域 | 命令 |
|------|------|
| 文档 | `git diff --check -- docs/architecture-review-report.md docs/architecture-optimization-plan.md` |
| 测试存在性 | `rg --files seahorse-agent-tests/src/test/java | rg "KernelMultiChannelRetrievalEngineTraceTests|KernelRetrievalEngineTests|SeahorseAgentKernelAutoConfigurationTests"` |
| 检索 | `mvn -pl seahorse-agent-tests,seahorse-agent-spring-boot-starter -am "-Dtest=KernelMultiChannelRetrievalEngineTraceTests,KernelRetrievalEngineTests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` |
| 记忆 | `mvn -pl seahorse-agent-tests,seahorse-agent-spring-boot-starter,seahorse-agent-adapter-repository-jdbc -am "-Dtest=DefaultMemoryEnginePortTests,KernelMemoryGovernanceServiceTests,Jdbc*Memory*Tests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` |
| 可观测 | `mvn -pl seahorse-agent-adapter-observation-micrometer,seahorse-agent-tests -am "-Dtest=*Observation*Tests,*Retrieval*Tests" "-Dsurefire.failIfNoSpecifiedTests=false" test` |
| 适配器 | `mvn -pl seahorse-agent-adapter-ai-openai-compatible,seahorse-agent-adapter-vector-milvus,seahorse-agent-spring-boot-starter -am test` |
| JDBC | `mvn -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcKnowledgeChunkRepositoryAdapterTests,JdbcKeywordIndexAdapterTests,JdbcMetadataGovernanceRepositoryAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` |
| 架构度量 | `powershell -NoProfile -Command "Get-ChildItem -Recurse -Filter *.java | ? { $_.FullName -notmatch '\\target\\|\\src\\test\\|\\.claude\\' } | % { $c=(Get-Content -LiteralPath $_.FullName).Count; if($c -ge 500){ \"$c`t$($_.FullName)\" } }"` |
| 前端 | `cd frontend && npm run typecheck && npm run build` |
| 全局空白 | `git diff --check` |

---

## 17. 决策记录

1. 采纳第 13 节二次 Review：记忆写入和衰减降为 P1。
2. P0 只保留会影响请求稳定性的检索问题。
3. Rerank 超时视为已完成，不纳入 P0。
4. 端口/JDBC 文件数不作为独立重构目标。
5. 四层记忆架构保留，优化重点从“简化层级”改为“补齐写入、衰减、预算和治理”。
6. OpenSearch 继续作为后续规划，不进入当前优化主线。
7. 扩展性审查按“新增能力需要触碰多少不相关 owner”判断严重程度，不按文件数量本身判断。
8. 自动配置和 JDBC 元数据治理属于 P1 架构债；端口数量治理属于 P2 规则建设，除非出现真实事务边界或替换实现阻塞。
9. 记忆闭环拆为 2a/2b，先完成主链路可信写入，再治理衰减和 token budget。
10. 不新增 `MemoryLifecycleService` 总控层；主链路捕获归 `MemoryCaptureStage`，后台治理归 `KernelMemoryGovernanceService`。
11. starter 治理先新增轻量/聚合新 starter，再迁移 bootstrap，最后 optional 化旧聚合 starter。

---

## 18. 扩展性与职责分离专项审查

### 18.1 审查原则

本次专项审查不把“文件数量多”直接等同于架构问题。真正的问题是新增能力时是否必须修改不相关模块、是否存在多个 owner 共同维护同一业务语义、以及单个类是否同时承担装配、策略、数据访问、治理和可观测职责。

不可破坏的边界：

1. 内核不直接依赖 Milvus、Redis、S3、OpenAI、Elasticsearch、Lucene、JDBC 等外部 SDK。
2. Spring Bean 名称、`@ConditionalOnMissingBean` 和用户自定义 Bean 优先级保持兼容。
3. 四层记忆架构保留，但必须明确 working、short-term、long-term、semantic 的写入、读取、晋升和衰减 owner。
4. 端口只表示外部能力或稳定替换边界，不能因为新增一个页面查询就机械增加端口。

### 18.2 证据摘要

| 维度 | 当前证据 | 判断 |
|------|----------|------|
| Kernel 自动配置 | `SeahorseAgentKernelAutoConfiguration.java` 已先拆出 memory、trace、model、auth/user、chat、ops/management、keyword maintenance、document refresh、metadata governance、retrieval orchestration、knowledge/ingestion 与 plugin/feature registration 主要技术域，当前约 175 行；主配置只保留 `@Import` 聚合与少量跨配置强依赖 Bean | P1，主配置显著瘦身，剩余问题集中在跨域装配边界 |
| Native 自动配置 | `SeahorseAgentNativeAdapterAutoConfiguration.java` 已降至约 53 行，主配置不再直接声明 `@Bean`，仅作为旧外部入口聚合 16 个子配置类 | P1 已完成主要治理，后续关注 starter 依赖边界 |
| starter 依赖 | `seahorse-agent-spring-boot-starter` 直接依赖 web、MQ、AI、MCP、Tika、Feishu、Milvus、PgVector、Redis、S3、ES、Lucene、JDBC 等适配器 | P1，最小部署和新增适配器成本偏高 |
| 出站端口 | `ports/outbound` 当前约 231 个 Java 文件，其中约 103 个接口；`metadata` 包约 58 个文件、18 个接口 | P2，数量高但包含大量 DTO，不能按总文件数判定 |
| JDBC 适配器 | JDBC 主代码约 32 个 Java 文件、30 个 `*Adapter.java`；其中 `JdbcMetadataGovernanceRepositoryAdapter.java` 约 2621 行并实现 14 个 metadata 端口 | P1，元数据治理 JDBC owner 明显过重 |
| 检索编排 | `KernelMultiChannelRetrievalEngine.java` 约 557 行，负责通道发现、并发执行、metadata filter、后处理、trace、observation、空结果事件 | P1，后续扩展检索策略会继续堆叠职责 |
| 聊天主链路 | `KernelChatPipeline.java` 约 352 行，串联会话历史、记忆激活、查询优化、改写、意图、检索、响应、降级和 trace | P1，记忆写入和更多 guardrail 加入后会变成主链路瓶颈 |
| 记忆集成 | 主链路已 `activateMemory()` 读取；`MemoryCaptureStage` 已触发显式记忆写入；短期记忆维护端口已支持过期/低衰减清理；working memory 在主链路中为空，`PromptContext.hasMemory()` 未纳入 working memory | P1，四层记忆已形成最小闭环，预算和质量治理仍需增强 |

### 18.3 P0 问题

本次专项审查未发现新的 P0 架构问题。自动配置过大、端口数量高、JDBC 适配器多、记忆闭环不完整都会影响扩展成本和企业级交付质量，但不会直接导致当前请求不可用、资源耗尽或数据损坏。P0 仍只保留阶段 1 的检索通道超时和默认线程池问题。

### 18.4 P1 问题与改进方案

#### P1-A：自动配置类成为扩展瓶颈

问题：

`SeahorseAgentKernelAutoConfiguration` 过去同时装配插件注册、摄取、检索、聊天、知识库、元数据治理、定时任务、记忆和模型路由；其中 memory 技术域已拆入 `SeahorseAgentKernelMemoryAutoConfiguration`，trace 技术域已拆入 `SeahorseAgentKernelTraceAutoConfiguration`，model 技术域已拆入 `SeahorseAgentKernelModelAutoConfiguration`，auth/user 技术域已拆入 `SeahorseAgentKernelAuthAutoConfiguration`，chat 技术域已拆入 `SeahorseAgentKernelChatAutoConfiguration`，ops/management 技术域已拆入 `SeahorseAgentKernelOpsAutoConfiguration`，keyword maintenance 技术域已拆入 `SeahorseAgentKernelKeywordAutoConfiguration`，document refresh 技术域已拆入 `SeahorseAgentKernelDocumentRefreshAutoConfiguration`，metadata governance 技术域已拆入 `SeahorseAgentKernelMetadataAutoConfiguration`，retrieval orchestration 技术域已拆入 `SeahorseAgentKernelRetrievalAutoConfiguration`，knowledge/ingestion 技术域已拆入 `SeahorseAgentKernelKnowledgeAutoConfiguration`，plugin/feature registration 已拆入 `SeahorseAgentKernelPluginAutoConfiguration`。当前主配置只保留聚合导入与少量跨配置强依赖 Bean。`SeahorseAgentNativeAdapterAutoConfiguration` 同时装配缓存、存储、搜索、向量、JDBC、MQ、AI、本地实现和外部 SDK 适配器。新增一个通道、仓储或治理能力时，继续把逻辑放回主配置会重新引入合并冲突和条件装配回归风险。

当前状态：已完成第一批兼容拆分。storage、observation、cache、local、auth、MQ、AI、vector、keyword、knowledge repository、ingestion repository、memory repository、retrieval repository、ops repository、outbox relay 与 metadata 十六个技术域已迁移到独立自动配置类，主配置通过 `@Import` 引入，Bean 名称、条件和外部自动配置入口保持不变。auth 拆分时将 `JdbcUserRepositoryAdapter` 一并迁入认证配置，原因是它服务认证闭环；knowledge repository 拆分将知识库查询、文档、切片和刷新状态相关 JDBC 仓储集中到独立配置，保持知识库数据域装配边界清晰；ingestion repository 拆分将 pipeline 定义与摄取任务仓储集中到导入链路配置；memory repository 拆分将会话记忆、四层记忆仓储和记忆质量治理仓储集中到记忆数据域配置；retrieval repository 拆分将策略模板与评测数据集仓储集中到检索治理配置；ops repository 拆分将会话、反馈、trace、sample、dashboard、扩展状态、意图树和术语映射等运营仓储集中管理；outbox relay 拆分将跨 outbox 仓储与 MQ 的 relay job 从主配置中移出。MQ 拆分仅迁移 direct/pulsar 基础队列 Bean。AI 拆分将 OpenAI-compatible adapter、streaming executor 和模型端口暴露集中到独立配置，便于后续新增模型 provider。vector 拆分将 Milvus、PgVector、Noop 及向量端口暴露集中管理，主配置不再承载向量 SDK 细节。keyword 拆分聚合关键词 search/index、Lucene/Elasticsearch/JDBC fallback 以及 keyword outbox。metadata 拆分将治理仓储、schema index 同步与治理端口暴露放入同一配置类，避免 `@ConditionalOnBean` 顺序变化导致装配回归。kernel retrieval orchestration 拆分将检索线程池、MCP 编排、检索引擎和评测/模板治理入口迁入独立配置。kernel knowledge/ingestion 拆分将知识库、文档、摄取引擎、任务编排和 metadata backfill 迁入独立配置。kernel plugin/feature registration 拆分将扩展注册表、Feature 激活上下文、健康聚合器、摄取节点注册以及检索 channel/post-processor 注册迁入独立配置，默认启用策略保持兼容。主 kernel 配置已进一步降至约 175 行，仅保留跨域强依赖 Bean 和 `@Import` 聚合。主 native 配置已进一步降至约 53 行，不再直接声明 `@Bean`，本阶段拆分目标完成。

方案：

1. 按职责拆分 kernel 自动配置：
   - `SeahorseAgentPluginAutoConfiguration`
   - `SeahorseAgentRetrievalAutoConfiguration`
   - `SeahorseAgentChatAutoConfiguration`
   - `SeahorseAgentKnowledgeAutoConfiguration`
   - `SeahorseAgentMetadataAutoConfiguration`
   - `SeahorseAgentMemoryAutoConfiguration`
   - `SeahorseAgentSchedulerAutoConfiguration`
   - `SeahorseAgentModelRoutingAutoConfiguration`
2. 按适配器类型拆分 native 自动配置：
   - `SeahorseAgentCacheAutoConfiguration`
   - `SeahorseAgentStorageAutoConfiguration`
   - `SeahorseAgentSearchAutoConfiguration`
   - `SeahorseAgentVectorAutoConfiguration`
   - `SeahorseAgentRepositoryJdbcAutoConfiguration`
   - `SeahorseAgentAiAutoConfiguration`
   - `SeahorseAgentMqAutoConfiguration`
3. `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 引入新配置类；旧配置类保留一轮作为兼容外壳或空壳，不再新增 Bean。
4. 每次拆分只迁移一个业务域，迁移时保持 Bean 方法名、Bean 类型、条件注解和用户自定义 Bean 优先级不变。

验收：

- `SeahorseAgentKernelAutoConfigurationTests` 继续通过。
- 新增一个检索通道或记忆策略时，只需要修改对应域自动配置。
- 单个自动配置类目标规模控制在 250-400 行；超过 500 行必须拆分或说明原因。

#### P1-B：starter 依赖聚合导致最小部署过重

问题：

当前 starter 直接依赖几乎所有适配器模块。好处是 bootstrap 简单，代价是最小部署会携带不一定使用的 SDK 和自动配置候选。新增适配器时也倾向于继续塞进 starter，使“开箱即用”和“按需裁剪”两个目标冲突。

方案：

1. 保留当前 `seahorse-agent-spring-boot-starter` 作为一轮兼容聚合 starter。
2. 新增或规划更细粒度 starter：
   - `seahorse-agent-spring-boot-starter-core`：只含 kernel、web、本地/noop 基础实现。
   - `seahorse-agent-spring-boot-starter-all`：显式聚合所有官方适配器。
   - adapter 自身提供独立 auto-configuration，业务方按需引入 Milvus、PgVector、Redis、S3、ES、Lucene、OpenAI、Pulsar、JDBC。
3. bootstrap 先保持依赖旧聚合 starter；待 `starter-core` 和 `starter-all` 验证通过后，再迁移为 `starter-core` + 显式适配器。
4. 只有 bootstrap 显式依赖路径验证通过后，才 optional 化旧聚合 starter 中的非核心依赖。

验收：

- bootstrap 不改配置仍可启动。
- bootstrap 迁移到 `starter-core` + 显式适配器后仍可启动。
- core starter 不传递 Milvus、PgVector、Redis、S3、ES、Lucene、Pulsar 等重 SDK。
- 适配器 auto-configuration 不依赖聚合 starter 才能生效。

#### P1-C：JDBC 元数据治理适配器违反单一职责

问题：

`JdbcMetadataGovernanceRepositoryAdapter` 约 2621 行，实现 schema registry、dictionary、extraction result、review queue、quarantine、canonical write、backfill、quality report、review management、quarantine management、schema management、dictionary management、extraction management、schema index status、schema usage report 等 14 个端口。它已经不是“文件多”的问题，而是一个类承担了多个事务语义、SQL 映射、JSON 处理、统计报表和运营写入。

方案：

1. 先做事务边界梳理，不改代码：
   - 标记 schema 变更、dictionary 更新、review 决策、quarantine 写入、backfill 创建、canonical write、quality report 统计之间哪些必须原子提交。
   - 对必须同事务的操作，优先放在 kernel application service 的事务边界内协调；不要让拆分后的 JDBC adapter 互相调用。
   - 对允许最终一致的操作，优先使用 domain event/outbox 解耦，避免为了事务把拆分后的 adapter 再绑回一个大类。
2. 再抽共享基础设施，不改变端口：
   - `JdbcMetadataJsonSupport`
   - `JdbcMetadataRowMappers`
   - `JdbcMetadataSqlSupport`
   - `JdbcMetadataSchemaColumnDetector`
3. 再按 bounded context 拆实现类：
   - `JdbcMetadataSchemaRepositoryAdapter`
   - `JdbcMetadataDictionaryRepositoryAdapter`
   - `JdbcMetadataExtractionResultRepositoryAdapter`
   - `JdbcMetadataReviewRepositoryAdapter`
   - `JdbcMetadataQuarantineRepositoryAdapter`
   - `JdbcMetadataBackfillRepositoryAdapter`
   - `JdbcMetadataQualityReportRepositoryAdapter`
   - `JdbcMetadataCanonicalWriteAdapter`
4. native 自动配置中直接暴露各端口 Bean；旧 `JdbcMetadataGovernanceRepositoryAdapter` 保留一轮作为兼容门面，之后删除。

验收：

- 原 metadata 相关端口 Bean 类型仍能被注入。
- `JdbcMetadataGovernanceRepositoryAdapterTests` 拆成 schema/dictionary/review/quarantine/backfill/quality 多组定向测试。
- 任一 metadata 子域改 SQL 时，不需要重新理解 2000 行以上的类。
- 拆分前输出事务边界清单，明确每个跨端口流程使用同事务、domain event 还是 outbox。

#### P1-D：四层记忆架构已读取但未闭环

问题：

主链路已经在 `KernelChatPipeline.activateMemory()` 中调用 `MemoryEnginePort.loadMemory()`，并将 `MemoryContext` 传给查询优化和 Prompt 组装，这是正确方向。但当前闭环仍不完整：

1. `DefaultMemoryEnginePort` 只读取 short-term、long-term、semantic，working memory 返回空列表。
2. `PromptContext.hasMemory()` 只判断 short-term、long-term、semantic，不判断 working memory。
3. `writeMemory()` 已支持显式可信用户声明写入短期记忆，主链路已有最小可信写入来源。
4. `KernelMemoryGovernanceService.runDecay()` 已通过 `ShortTermMemoryMaintenancePort` 执行过期/低衰减短期记忆清理，定时任务能触发实际 decay 链路。
5. `KernelMemoryGovernanceService.runGovernance()` 能从 short-term 晋升到 long-term/semantic，但自动摘要、token budget 和更精细的质量策略仍需增强。

方案：

1. 不新增 `MemoryLifecycleService` 这类总控层，避免和现有 `KernelMemoryGovernanceService` 形成职责重叠。
2. 在聊天响应完成后增加非阻塞 `MemoryCaptureStage`，只负责主链路捕获、候选抽取和调用 `writeMemory()`，不做 promote、decay 或 inference。
3. `KernelMemoryGovernanceService` 保留后台治理职责：promote、decay、inference、quality assess，由手动接口或定时 Job 驱动。
4. 引入 `MemoryWritePolicy`、`MemoryBudgetPolicy` 和 `MemoryLayerRouter`：
   - working：当前会话临时上下文或任务态信息。
   - short-term：近期事实、偏好、行为线索。
   - long-term：稳定偏好、长期事实。
   - semantic：可归一化画像、实体偏好、主题标签。
5. 将衰减职责从 `MemoryEnginePort.executeMemoryDecay()` 迁移到更明确的 `MemoryMaintenancePort`，避免“引擎端口既读写又做后台扫描”。
6. `SeahorseMemoryGovernanceJob` 拆成 promotion/inference 与 decay 两类任务，并配置 scan limit、batch size、dry-run 和失败观测。
7. `DefaultMemoryEnginePort` 的 limit 和 token budget 改为配置项，Prompt 组装前统一裁剪。

验收：

- 一次问答完成后，符合策略的候选记忆可以进入 short-term。
- governance 能将高置信 short-term 晋升到 long-term/semantic。
- working memory 要么进入主链路和 Prompt，要么在文档中明确退化为“管理层可见但不参与回答”的非默认层。
- 记忆链路失败不阻塞聊天响应，但必须有 trace/metric 证据。

#### P1-E：检索编排器继续吸收治理职责

问题：

`KernelMultiChannelRetrievalEngine` 已经通过 `SearchChannelFeature` 和 `SearchResultPostProcessorFeature` 保留了良好的扩展点，但类本身同时负责通道发现、并发执行、metadata filter 编译、usage report、trace、observation、空结果事件、后处理链和异常降级。后续再加入通道超时、熔断、按租户策略、AB 实验和更多观测时，会继续膨胀。

方案：

1. 阶段 1 先补通道级 timeout 和默认线程池，不改变主流程。
2. 后续拆出内部协作者：
   - `SearchChannelExecutor`：并发、timeout、异常降级。
   - `RetrievalPostProcessorChain`：后处理排序、执行、失败跳过。
   - `RetrievalTelemetryReporter`：trace、observation、empty result、metadata usage。
   - `SearchContextFactory`：filter/schema/options/trace 上下文构建。
3. `KernelMultiChannelRetrievalEngine` 保留为 L1 编排门面，只串联协作者。

验收：

- 新增检索通道只实现 `SearchChannelFeature` 并增加对应自动配置。
- 新增后处理器只实现 `SearchResultPostProcessorFeature`，不修改编排器。
- telemetry 改动不影响检索排序和降级语义。

#### P1-F：聊天主链路需要阶段边界

问题：

`KernelChatPipeline` 当前约 352 行，还在可控范围内，但它已经同时承担主链路顺序、降级策略、trace 节点、记忆激活、查询优化、改写、意图解析、检索、空检索响应、模型请求构建和流式回调。记忆写入、工具调用策略、答案安全策略加入后，单类会成为新增功能的必经修改点。

方案：

1. 不立即引入复杂工作流引擎，先抽轻量阶段接口：
   - `ChatPipelineStage`
   - `ChatPipelineContext`
   - `ChatPipelineStageResult`
2. 优先拆出低风险阶段：
   - `MemoryActivationStage`
   - `QueryOptimizationStage`
   - `RetrievalStage`
   - `ResponseStreamingStage`
   - `MemoryCaptureStage`
3. `KernelChatPipeline` 保留固定顺序和 fail-open 语义，阶段实现只负责单一行为。

验收：

- 加入记忆写入阶段时，不需要修改检索和响应流式代码。
- 每个阶段可以独立测试降级语义。
- 主链路 trace 节点名称保持兼容。

### 18.5 P2 问题与改进方案

#### P2-A：端口数量需要准入规则，而不是机械合并

问题：

`ports/outbound` 约 231 个 Java 文件里只有约 103 个接口，其余大量是 record/query/page/status 等契约 DTO。端口总文件数看起来很高，但直接合并会破坏显式边界。真正需要治理的是“什么时候允许新增端口”。

方案：

新增端口必须满足至少一条：

1. 表示一个外部系统、可替换 provider 或跨进程能力。
2. 是内核与适配器之间稳定的隔离边界。
3. 有至少一个生产实现和一个测试/noop 实现需求。
4. 承载明确事务边界或批处理边界。

不满足时优先选择 domain service、query object、DTO、feature 参数或现有端口方法。端口 DTO 可以继续显式存在，但建议按 `domain`、`contract` 或子包归类，避免被误统计为“端口接口爆炸”。

阶段 7 实施前，应把上述准入规则固化为 ADR，后续新增端口在 PR 描述中引用对应准入理由。

#### P2-B：大类阈值与拆分触发器需要制度化

问题：

除自动配置和 JDBC 元数据治理外，仍有多个 350 行以上类，例如 `KernelMetadataBackfillService`、`MetadataExtractorNodeFeature`、`KernelMultiChannelRetrievalEngine`、`KernelChatPipeline`、Milvus/PgVector/OpenAI/Lucene 适配器等。并非每个大类都需要立即拆分，但需要触发器。

方案：

1. 超过 500 行且新增功能需要修改时，必须先判断是否可拆协作者。
2. 超过 800 行且存在 3 个以上变更原因时，列为 P1 拆分候选。
3. 单个适配器类可以较大，但 SQL/SDK 映射、重试/观测、参数转换应能拆成私有协作者或 helper。
4. 拆分后必须保持外部端口和 Bean 契约不变。

#### P2-C：聊天端口对记忆领域的直接依赖需要观察

问题：

`QueryOptimizerPort` 和 `RagPromptPort` 直接消费 `MemoryContext`，当前有利于快速集成记忆，但也让 chat outbound contract 直接感知记忆分层模型。若后续加入用户画像、权限、实验标签、工具偏好等上下文，`MemoryContext` 可能继续膨胀。

方案：

1. 短期保留直接依赖，避免为抽象而抽象。
2. 当第三类上下文进入 Prompt 或查询优化时，引入 `ConversationSignalContext` 或 `PromptSignalContext`，由 memory/profile/experiment/tool preference 聚合成面向聊天的只读信号。
3. 记忆领域内部仍保留 `MemoryContext`，不要把聊天上下文反向传入记忆存储层。

#### P2-D：wrapper 占位实现应有生命周期标签

问题：

wrapper 包保留 audit、circuit breaker、observation、rate limit、retry 等扩展点，但如果长期没有真实包装行为，会造成“看起来可治理，实际未接入”的误导。

方案：

1. 每个 wrapper 标注状态：`active`、`planned`、`placeholder`。
2. placeholder 必须写明删除触发器或接入触发器。
3. 优先让 observation wrapper 与 Micrometer/RAG Trace 形成真实链路，再考虑 retry/rate limit。

### 18.6 建议纳入阶段 7 的任务顺序

1. 先拆自动配置，不拆业务代码，降低后续变更冲突。
2. 再梳理 metadata 事务边界，明确同事务、domain event、outbox 三类处理方式。
3. 在事务边界明确后拆 JDBC 元数据治理适配器，保留端口 Bean 兼容。
4. 然后补端口准入规则和大类阈值，并固化为 ADR，把新增功能入口治理住。
5. 最后按新增功能需要拆 `KernelChatPipeline` 和 `KernelMultiChannelRetrievalEngine` 内部协作者，避免空转重构。

阶段 7 不应阻塞阶段 1 的 P0 检索稳定性，也不应抢在阶段 2a/2b 的记忆闭环之前大规模改主链路。正确顺序是先补齐线上稳定性和记忆闭环，再做职责收敛。
