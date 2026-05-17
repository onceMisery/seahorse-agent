# Seahorse Agent 架构深度审查报告（校准版）

> **原始审查日期**: 2026-05-14
> **校准日期**: 2026-05-17
> **审查范围**: 当前 `main` 工作树中的后端 Maven 模块、前端应用、数据库脚本、依赖管理
> **校准方法**: 以代码事实复核原报告结论，修正过时统计、自相矛盾结论和未被代码证据支撑的优先级判断

---

## 目录

1. [执行摘要](#1-执行摘要)
2. [架构层面分析](#2-架构层面分析)
3. [适配器模式评估](#3-适配器模式评估)
4. [性能问题分析](#4-性能问题分析)
5. [依赖管理评估](#5-依赖管理评估)
6. [数据库设计评估](#6-数据库设计评估)
7. [前端架构分析](#7-前端架构分析)
8. [可观测性评估](#8-可观测性评估)
9. [记忆管理分析](#9-记忆管理分析)
10. [元数据治理子系统分析](#10-元数据治理子系统分析)
11. [改进建议汇总](#11-改进建议汇总)
12. [架构亮点](#12-架构亮点)

---

## 1. 执行摘要

### 1.1 项目规模量化

以下数字按当前工作树复核。原报告中“31 个 Maven 模块”“231 个出站端口接口”“24 张数据库表”等口径不准确。

| 指标 | 当前值 | 说明 |
|------|--------|------|
| Maven 模块 | 24 个 | 以根 `pom.xml` `<module>` 为准 |
| Adapter 模块 | 19 个 | `seahorse-agent-adapter-*`，含 Web、Lucene、Elasticsearch、Feishu 等 |
| Kernel Java 文件 | 500 个 | `seahorse-agent-kernel/src/main/java` |
| 出站端口目录 Java 文件 | 231 个 | 其中约 103 个是 `interface`，其余主要是 record/DTO/class/enum |
| 入站端口目录 Java 文件 | 85 个 | 其中约 31 个是 `interface`，其余主要是命令/结果 record |
| Metadata outbound 文件 | 58 个 | 其中约 18 个是端口接口，不能等同为 58 个端口 |
| JDBC 适配器 Java 文件 | 32 个 | `adapter-repository-jdbc/src/main/java` |
| Feature 实现 | 33 个 | ingestion/retrieval/memory/mcp 等 |
| 基础数据库表 | 28 张 | `resources/database/seahorse_init.sql` |
| 治理扩展表 | 8 张 metadata + 4 张 retrieval | 位于 adapter JDBC resources 下的治理 SQL |
| 前端源码文件 | 86 个 | `frontend/src` |
| Kernel 自动配置 | 1266 行 / 82 个 `@Bean` | `SeahorseAgentKernelAutoConfiguration.java` |
| Native Adapter 自动配置 | 1209 行 / 106 个 `@Bean` | `SeahorseAgentNativeAdapterAutoConfiguration.java` |

### 1.2 整体评价

| 维度 | 评分 | 校准后判断 |
|------|------|------------|
| 架构设计 | B+ | 端口适配器边界清楚，kernel 依赖轻；主要风险是配置装配和功能面增长带来的维护成本 |
| 端口设计 | B | 文件数偏多属实，但原报告把 DTO 文件当作端口接口，严重放大了问题 |
| 适配器质量 | A- | 技术边界清晰；少数适配器存在命名、线程池、配置化不足问题 |
| 性能设计 | B+ | 检索通道级超时和默认线程池已补齐；Rerank 已有超时，不应混为一谈 |
| 数据库设计 | B+ | 主体可用；复合索引和向量维度配置化值得补齐 |
| 前端架构 | A- | 技术栈合理，SSE Hook 完整；`chatStore.ts` 偏大但不是阻断项 |
| 可观测性 | B+ | RAG Trace 是亮点；采样率和 TTL 清理已完成最小治理，Micrometer 仍偏通用 |
| 记忆管理 | B+ | 四层设计合理，读链路、显式记忆写入和短期记忆衰减维护已接入；冲突检测和 token 预算仍未闭环 |

### 1.3 关键发现

**高优先级（P0）**

1. 检索通道并行执行曾缺少通道级超时，且缺少线程池 Bean 时会退化为 `Runnable::run` 同步执行；当前实现已补齐通道级超时、默认线程池和超时观测。

**中优先级（P1）**

2. 记忆系统已完成读链路激活，并已支持响应完成后捕获显式可信用户记忆写入短期记忆；读取 limit、捕获开关和短期记忆过期/低衰减清理已配置化接入，token 预算仍未闭环。
3. `seahorse-agent-spring-boot-starter` 传递依赖几乎所有适配器，基础部署会被 Milvus、Pulsar、S3、Tika、Elasticsearch、Lucene 等 SDK 膨胀。
4. `OpenAiCompatibleModelAdapter.streamChat()` 使用 `CompletableFuture.runAsync` 默认 common pool，阻塞 SSE 读取可能影响高并发稳定性。
5. `MilvusVectorAdapter` 的 HNSW 参数、内容最大长度硬编码已配置化；业务 JSON 已统一到 Jackson，Milvus SDK `JsonObject/JsonArray` 仅作为边界载体保留。
6. 自动配置类体量较大，应作为可维护性问题拆分，但不应列为 P0 阻断项。
7. RAG Trace 已补齐采样率和 TTL 清理机制，生产长期运行的表增长风险已具备最小治理闭环；后续可增强异步写入和指标聚合。

**低优先级（P2）**

8. Wrapper 占位实现、`ObjectStoragePort.upload/reliableUpload` 重复已收敛；`adapter-cache-local` 已补充命名说明，后续仅在需要拆分 artifact 时继续治理。
9. `chatStore.ts` 527 行，可按会话、消息、流式状态拆分。
10. 元数据治理后端 API 已存在，前端已新增 Review/Quarantine/Schema/质量报表的最小管理入口；完整编辑体验和高级筛选仍可继续增强。

---

## 2. 架构层面分析

### 2.1 微内核体量问题

`seahorse-agent-kernel` 当前约 500 个 Java 文件，代码体量已经不像“小内核”。但它的 Maven 依赖仍保持轻量，主要依赖 Jackson、SLF4J、Lombok，没有把 Spring、Milvus、Elasticsearch、Tika 等外部 SDK 泄漏进 kernel。这说明问题不是“边界失守”，而是“业务能力都沉在同一个 kernel 模块里，维护面变大”。

**校准结论：**

- “kernel 文件多”是维护性风险，不是立即拆模块的充分理由。
- 原报告建议把 `kernel/feature` 和 `kernel/plugin` 直接拆出模块，方向可以讨论，但不应作为当前强制改造项。
- 更稳妥的路线是先拆自动配置和 starter 依赖，再观察 kernel 内部包边界是否仍然影响开发效率。

### 2.2 端口数量问题

原报告把 `ports/outbound` 下 231 个 Java 文件全部称为“出站端口接口”，不准确。当前复核结果是：

| 目录 | Java 文件 | 接口数 | 说明 |
|------|-----------|--------|------|
| `ports/outbound` | 231 | 约 103 | 其余主要是 record/DTO/class/enum |
| `ports/inbound` | 85 | 约 31 | 其余主要是 command/result record |
| `ports/outbound/metadata` | 58 | 约 18 | metadata 文件多，但不等于 58 个端口 |

**校准结论：**

- 文件数偏多确实会增加导航成本。
- 但按“减少接口数到 130 个”“合并 JDBC 到 20 个”的目标做机械合并，没有明确收益证明。
- 端口是否应合并，应看职责边界、事务边界、生命周期和独立替换需求，而不是只看文件数量。

**建议：**

- 优先清理只作为占位或重复语义的端口/包装器。
- 对 metadata 这类 DTO 密集目录，可先优化包结构和命名，而不是把所有仓储强行合成大接口。
- 记忆四层端口暂不建议合并，详见记忆章节。

### 2.3 自动配置类过大

`SeahorseAgentKernelAutoConfiguration` 当前约 1266 行、82 个 `@Bean`；`SeahorseAgentNativeAdapterAutoConfiguration` 约 1209 行、106 个 `@Bean`。原报告只关注 kernel 自动配置，遗漏 native adapter 自动配置同样偏大。

**校准结论：**

- 这是 P1 可维护性问题，不是 P0 运行时阻断。
- 拆分方向合理，但必须保持条件装配语义不变。

**建议拆分方向：**

- `SeahorseChatAutoConfiguration`
- `SeahorseRetrievalAutoConfiguration`
- `SeahorseIngestionAutoConfiguration`
- `SeahorseMemoryAutoConfiguration`
- `SeahorseMetadataAutoConfiguration`
- `SeahorseNativeSearchAutoConfiguration`
- `SeahorseNativeStorageAutoConfiguration`

### 2.4 Wrapper 空壳实现

`AuditPortWrapper`、`CircuitBreakerPortWrapper`、`RateLimitPortWrapper`、`RetryPortWrapper`、`ObservationPortWrapper` 当前 `wrap()` 都只返回原始 delegate。

**校准结论：**

- 原报告指出“误导开发者认为已有横切能力”是合理的。
- 这属于 P2 清理项；如果短期要保留，应在文档/类名中明确是“占位注册顺序”而非真实审计、限流、熔断、重试。

---

## 3. 适配器模式评估

### 3.1 适配器拆分合理性

当前共有 19 个 `seahorse-agent-adapter-*` 模块。整体拆分方向合理：模型、向量、关键词搜索、存储、消息队列、缓存/协调、观测、Web、JDBC 仓储等技术栈边界清楚。

| 类别 | 适配器 | 校准后评估 |
|------|--------|------------|
| AI 模型 | `adapter-ai-openai-compatible` | 覆盖 Chat/Streaming/Embedding/Rerank/Token/Health，类职责偏重 |
| 向量库 | Milvus / PGVector / NoOp | 边界合理；Milvus 配置化不足 |
| 关键词搜索 | Elasticsearch / Lucene / JDBC fallback | 当前设计与最新检索设计文档一致 |
| 消息队列 | Direct / Pulsar | 合理 |
| 对象存储 | Local / S3 | 合理，但 `reliableUpload` 与 `upload` 语义重复 |
| 文档解析 | Tika | 合理 |
| 数据仓储 | JDBC | 单技术栈边界合理，文件数量偏多但不应机械合并 |
| 可观测 | NoOp / Micrometer | 合理；Micrometer 指标语义偏通用 |
| Web | adapter-web | 合理 |

### 3.2 OpenAI 适配器职责过重

`OpenAiCompatibleModelAdapter` 实现 7 个端口：

```java
ChatModelPort, StreamingChatModelPort, EmbeddingModelPort,
RerankModelPort, ModelProviderPort, TokenCounterPort, ModelHealthPort
```

其中 `TokenCounterPort.countTextTokens()` 仍是近似估算，`ModelHealthPort.recordSuccess/recordFailure()` 是空实现，`isHealthy()` 主要检查模型是否可用。

**校准结论：**

- “职责偏重”成立。
- 但拆分不是 P0；可以先把 Streaming 专用线程池、Health 记录和 token 计数策略补齐，再考虑类拆分。

### 3.3 JDBC 适配器文件数量

JDBC 适配器当前 32 个 Java 文件，数量偏多。但这些文件按不同业务端口和表边界拆分，仍然保持单一 JDBC 技术栈边界。

**校准结论：**

- “文件偏多”成立。
- “合并到约 20 个”缺少代码收益证明，不建议作为独立目标。
- 可以优先清理重复 SQL helper、公共 JSON 读写、分页查询模板，而不是按文件数量重构。

### 3.4 `adapter-cache-local` 命名误导

该模块实际包含 `LocalSemaphoreAdapter`，实现的是 `DistributedSemaphorePort`，不是单纯缓存。

**当前结论：** 已在模块 README 与 POM 描述中明确其定位为“本地内存缓存 + 单 JVM 协调”适配器。短期不建议直接重命名 artifact，避免破坏 Maven 坐标、starter 依赖和用户侧显式依赖。后续如确需拆分，应新增 `adapter-coordination-local` 并提供兼容迁移期。

### 3.5 `ObjectStoragePort` 方法冗余

`upload()` 和 `reliableUpload()` 在 Local/S3 两个实现中都委托到同一写入逻辑。

**建议：** 若调用侧没有可靠上传的特殊语义，应合并为 `upload()`；若要保留，则必须明确 retry、幂等、断点续传或一致性保证差异。

---

## 4. 性能问题分析

### 4.1 检索通道缺少通道级超时（P0）

`KernelMultiChannelRetrievalEngine` 对启用的 `SearchChannelFeature` 使用 `CompletableFuture.supplyAsync()`，随后直接 `join()` 等待所有通道。

**风险：**

- 任一慢通道会拖慢整个检索链路。
- starter 缺少 `ragRetrievalThreadPoolExecutor` 等线程池 Bean 时会回退 `Runnable::run`，并行退化为同步。
- `RetrievalOptions` 已有 `vectorTimeout`、`keywordTimeout` 字段，但当前多通道执行未统一使用这些超时。

**建议：**

- 为每个通道增加 `completeOnTimeout(emptyResult)` 或等价超时降级。
- 根据 `SearchChannelType` 使用 vector/keyword timeout。
- starter 提供默认检索线程池 Bean，避免无配置时同步执行。

### 4.2 Streaming Chat 使用 common pool

`OpenAiCompatibleModelAdapter.streamChat()` 使用 `CompletableFuture.runAsync()`，未传入专用 executor。SSE 消费中的 `readLine()` 是阻塞 I/O，高并发时可能占用公共 ForkJoinPool。

**建议：**

- 注入专用 streaming executor。
- 或改用 OkHttp `enqueue` 异步回调模型。

### 4.3 Milvus 参数硬编码

`MilvusVectorAdapter` 中：

- `CONTENT_MAX_LENGTH = 65535`
- HNSW `M=48`、`efConstruction=200`、`mmap.enabled=false`
- 业务 JSON 已统一到 Jackson，Milvus SDK 行数据仍使用 `JsonObject/JsonArray` 作为边界载体

**建议：**

- 提取到 `MilvusVectorProperties`。
- 默认参数保守化，按知识库规模调优。
- 统一使用 Jackson，减少 JSON 栈差异。

### 4.4 Rerank 超时已完整实现

原报告容易让人误解为检索后处理都没有超时。实际 `RerankPostProcessorFeature` 已使用 `future.get(timeoutMs, TimeUnit.MILLISECONDS)` 支持 rerank timeout。

**校准结论：** 当前 P0 重点是“检索通道级超时”，不是 rerank。

---

## 5. 依赖管理评估

### 5.1 依赖方向

模块依赖方向整体正确：

```text
bootstrap -> starter -> kernel
adapter-* -> kernel
adapter-web -> kernel
tests -> kernel + adapter-*
```

kernel 依赖轻量，符合端口适配器架构目标。

### 5.2 Starter 依赖过重（P1）

`seahorse-agent-spring-boot-starter` 当前直接依赖 Web、Pulsar、Tika、Feishu、Milvus、PGVector、Redis、S3、Elasticsearch、Lucene、JDBC 等大量适配器模块。

**影响：**

- 最小部署被迫带上大量 SDK。
- 依赖冲突和启动扫描范围扩大。
- 私有化部署难以按能力裁剪。

**建议：**

- 拆分为能力 starter，例如 `starter-core`、`starter-web`、`starter-vector-milvus`、`starter-search-elasticsearch`。
- 或将非核心适配器依赖标记为 optional，并由 bootstrap 或部署工程显式选择。

---

## 6. 数据库设计评估

### 6.1 表数量校准

当前基础 SQL 中约 28 张表，另有 metadata governance 8 张扩展表、retrieval governance 4 张扩展表。原报告“24 张表，不含 metadata governance”已过时。

### 6.2 索引问题

**成立：`t_knowledge_chunk` 缺少复合索引**

当前只有 `idx_doc_id ON t_knowledge_chunk(doc_id)`，但代码中存在多处按 `kb_id + doc_id` 更新或检索关键词索引的 SQL。

建议增加：

```sql
CREATE INDEX IF NOT EXISTS idx_chunk_kb_doc
ON t_knowledge_chunk (kb_id, doc_id)
WHERE deleted = 0;
```

**需降级：`t_intent_node(kb_id, parent_code)`**

原报告建议该复合索引，但当前 JDBC 查询主要是全量列出、按 id 查找、按 intent_code 判断存在，并未看到按 `(kb_id, parent_code)` 查询的主路径。因此这条不应列为 P1，除非后续前端/接口改成按父节点懒加载。

**需修正：`idx_conversation_summary`**

当前不存在 `idx_conversation_summary` 这个索引名。实际是 `idx_conv_user ON t_conversation_summary(conversation_id, user_id)`，且删除会话摘要时会按这两个字段更新。不能按原报告直接删除。

### 6.3 向量维度硬编码

`t_knowledge_vector.embedding vector(1536)` 和 `t_long_term_memory_vector.embedding vector(1536)` 硬编码为 1536 维。更换 embedding 模型时需要改 schema。

**建议：** 中期引入按 collection/model 维度的迁移策略，至少在部署文档中声明维度约束。

---

## 7. 前端架构分析

### 7.1 技术栈

React 18、TypeScript、Vite、Zustand、TailwindCSS、Radix UI、React Router、Axios 的选择合理。

### 7.2 SSE Hook 是亮点

`useStreamResponse.ts` 约 175 行，支持事件分发、重试、取消和 SSE 解析，质量较好。

### 7.3 `chatStore.ts` 偏大

`chatStore.ts` 当前 527 行，集中处理会话、消息、流式增量、反馈、取消等逻辑。

**建议：** 低优先级拆分为：

- `sessionStore`
- `messageStore`
- `streamStore`

### 7.4 `ChatPage` useEffect 数量校准

原报告写 4 个 `useEffect`，当前代码是 3 个。这个问题不是关键架构风险。

### 7.5 元数据治理 UI 缺口

后端已存在 Metadata Backfill、Review、Quarantine、Quality、Schema、Dictionary 等 controller，前端 admin 已新增元数据治理最小管理页，覆盖 Schema 字段、Review 队列、Quarantine 隔离区和 Quality 报表。

**建议：** 这属于 P2/P1 之间的产品闭环缺口，优先级取决于是否要把元数据治理交给业务管理员使用。

---

## 8. 可观测性评估

### 8.1 Micrometer 适配器偏通用

`MicrometerObservationAdapter` 当前主要暴露：

- `seahorse.agent.observation.duration`
- `seahorse.agent.observation.events`

它能承接通用观测事件，但缺少更易用的业务指标命名和 Gauge。

**建议：**

- 为检索、LLM、入库、Outbox、回填等关键路径增加业务指标。
- 为延迟指标启用 histogram 或按部署侧 meter filter 配置 P95/P99。
- 增加活跃任务数、待处理 outbox、回填任务状态等 Gauge。

### 8.2 RAG Trace 是亮点，生命周期治理已完成最小闭环

RAG Trace 已覆盖 ChatPipeline 和 RetrievalEngine 的关键节点，前端也有 trace 页面。当前生产治理已完成最小闭环：

- `KernelRagTraceRecorder` 支持 `seahorse-agent.rag-trace.sample-rate`，采样在 run 入口判定一次，后续 node 继承该结果。
- `RagTraceRepositoryPort.deleteRunsBefore(...)` 提供 TTL 清理端口，JDBC adapter 对 run/node 做同步软删。
- `SeahorseRagTraceCleanupJob` 通过分布式锁和定时任务触发清理，默认保留 30 天、批量 1000 条。

剩余增强项：

- Trace 写入仍是同步 DB 写入，极高并发场景可改为异步队列或批量写入。
- Micrometer 指标仍偏通用，可补充业务维度指标，例如 trace 写入失败率、清理删除数量、采样命中率。
- 写入路径后续可考虑异步化。

---

## 9. 记忆管理分析

### 9.1 四层记忆架构是否过度设计

原报告早期“建议简化为两层”的判断应修正。当前四层记忆不是简单复制：

- 短期记忆有 TTL、importance、decay、access_count。
- 长期记忆有 category、title、source、confidence、vector_ref。
- 语义记忆有 semantic_key/type 和 upsert 语义。
- 工作记忆复用会话消息，不需要独立表。

**校准结论：** 四层结构本身合理，不建议为了减少端口或表数量而合并。

### 9.2 已完成能力

- `KernelChatPipeline` 已在 `activateMemory` 阶段调用 `MemoryEnginePort.loadMemory()`。
- `KernelChatPipeline` 已在响应完成时通过 `MemoryCaptureStage` 触发记忆捕获。
- `DefaultMemoryEnginePort.loadMemory()` 已编排短期、长期、语义三层读取，并做限量和去重。
- `DefaultMemoryEnginePort.writeMemory()` 已支持显式可信用户声明写入短期记忆，并过滤普通问题，避免把原始噪声写入记忆。
- 记忆读取 limit 和捕获开关已通过 `MemoryEngineOptions` / starter 属性配置化。
- `KernelMemoryGovernanceService.runDecay()` 已通过 `ShortTermMemoryMaintenancePort` 执行短期记忆过期/低衰减清理，JDBC adapter 已实现扫描和软删。
- QueryOptimizer 已有规则实现，LLM QueryOptimizer 也已实现并默认关闭。
- `KernelMemoryGovernanceService` 已集成 `MemoryInferencePort`，由 `inferenceEnabled` 控制。

### 9.3 仍存在的问题

| 问题 | 当前状态 | 优先级 |
|------|----------|--------|
| 记忆写入增强 | 已支持显式可信用户声明写入短期记忆；摘要生成、LLM/规则候选抽取仍需增强 | P1 |
| 衰减闭环增强 | 已支持过期/低衰减扫描和软删；复杂衰减分更新和晋升策略仍需增强 | P1 |
| 记忆限量硬编码 | 已配置化 | 已完成 |
| Token 预算管理 | 尚未实现按工作/短期/长期/语义分配 | P1 |
| MemoryVectorPort | 接口存在，实际语义检索适配未闭环 | P2 |
| 冲突检测 | 表和端口存在，自动检测写入不足 | P2 |

### 9.4 修正后的建议

不需要“实现 MemoryEnginePort 并接入主链路”作为 P0，因为这两点已完成。记忆系统下一步应作为 P1 能力闭环推进：

1. 在现有显式记忆捕获基础上，继续补齐摘要生成、LLM/规则候选抽取和可观测事件。
2. 在现有短期记忆清理链路基础上，补齐 token budget、复杂衰减分更新和高价值记忆晋升策略。

---

## 10. 元数据治理子系统分析

### 10.1 后端能力

元数据治理后端已经具备较完整的 controller 和端口，包括 Schema、Dictionary、Backfill、Review、Quarantine、Quality 等。

原报告说“缺少 UI 消费者”基本成立，但需要修正为：

- 后端 API 已存在。
- 前端 admin 当前没有完整的专用页面来消费这些治理 API。
- 是否补 UI 取决于产品交付目标：如果治理只由后台任务和 API 操作，优先级可降；如果交给业务管理员使用，则需要提升为 P1。

### 10.2 端口文件多的问题

metadata outbound 目录约 58 个 Java 文件，但接口约 18 个。原报告“60 个出站端口”夸大。

**建议：**

- 优先优化 DTO 命名和包结构。
- 对 Review/Quarantine/Quality 是否合并仓储端口，应根据事务和查询边界决定，不按文件数决策。

---

## 11. 改进建议汇总

### 11.1 高优先级（P0）

| 序号 | 问题 | 位置 | 改进措施 | 影响 |
|------|------|------|----------|------|
| 1 | 检索通道无通道级超时 | `KernelMultiChannelRetrievalEngine` | 已按 channel type 使用 timeout，超时返回空结果并记录观测 | 避免慢通道阻塞整体检索 |
| 2 | 检索线程池缺省退化同步 | starter 自动配置 | 已提供默认 `ragRetrievalThreadPoolExecutor` / inner retrieval executor / context executor | 保证并行检索语义 |

### 11.2 中优先级（P1）

| 序号 | 问题 | 位置 | 改进措施 | 影响 |
|------|------|------|----------|------|
| 4 | Starter 依赖过重 | `seahorse-agent-spring-boot-starter/pom.xml` | 拆能力 starter 或 optional 化非核心适配器 | 降低部署体积和依赖冲突 |
| 5 | OpenAI 流式响应使用 common pool | `OpenAiCompatibleModelAdapter.streamChat()` | 注入专用 streaming executor 或 OkHttp async | 提升高并发稳定性 |
| 6 | Milvus 参数硬编码 | `MilvusVectorAdapter` | 提取 HNSW、max length、mmap 配置 | 支持不同规模知识库 |
| 7 | 自动配置类过大 | starter auto configuration | 按 chat/retrieval/ingestion/memory/metadata/native adapter 拆分 | 提升维护性 |
| 8 | RAG Trace 采样/TTL 已完成最小治理 | trace repository / recorder / cleanup job | 后续增强异步写入和指标聚合 | 降低高并发写库和治理盲区风险 |
| 9 | Token 预算缺失 | `DefaultMemoryEnginePort` / prompt assembly | 增加 token budget 策略 | 控制 prompt 成本 |
| 10 | `t_knowledge_chunk` 缺少复合索引 | DB schema | 增加 `(kb_id, doc_id)` 部分索引 | 优化关键词索引维护和分块查询 |
| 11 | 记忆写入增强 | `MemoryCaptureStage` / `DefaultMemoryEnginePort.writeMemory()` | 在显式记忆写入基础上补摘要、候选抽取、开关和观测 | 提升自动积累质量 |
| 12 | 记忆衰减策略增强 | `KernelMemoryGovernanceService` / `ShortTermMemoryMaintenancePort` | 在已实现清理链路基础上增加复杂衰减分更新和晋升策略 | 控制记忆质量和存量增长 |

### 11.3 低优先级（P2）

| 序号 | 问题 | 位置 | 改进措施 | 影响 |
|------|------|------|----------|------|
| 13 | Wrapper 占位实现 | `kernel/plugin/wrapper` | 删除或明确标注占位 | 减少误导 |
| 14 | Gson/Jackson 不统一 | `MilvusVectorAdapter` | 业务 JSON 已统一 Jackson；保留 SDK JsonObject 边界 | 减少 JSON 栈差异 |
| 15 | `ObjectStoragePort` 冗余方法 | storage port/adapters | 合并或明确 reliable 语义 | 简化接口 |
| 16 | `adapter-cache-local` 命名误导 | module name | 已补 README/POM 说明；后续按需新增 coordination local | 降低理解成本 |
| 17 | `chatStore.ts` 偏大 | frontend store | 拆 session/message/stream store | 提升前端维护性 |
| 16 | 元数据治理 UI 缺口 | frontend admin | 已新增 Schema/Review/Quarantine/Quality 最小管理页 | 完成企业治理操作闭环 |

### 11.4 不建议直接采纳的原报告项

| 原报告建议 | 校准意见 |
|------------|----------|
| 把自动配置拆分列为 P0 | 降为 P1，可维护性问题，不是当前阻断 |
| 出站端口 231 个接口、合并到 ~130 | 统计口径错误；应按接口/DTO 区分，避免机械合并 |
| JDBC 适配器强行合并到 ~20 个 | 缺少收益证明，先抽公共 helper 更稳 |
| 删除 `idx_conversation_summary` | 当前没有该索引名；实际 `idx_conv_user` 有查询使用 |
| 记忆四层简化为两层 | 不建议，四层存储语义有差异 |
| P0：实现 MemoryEnginePort 并接入主链路 | 已完成，真正缺口是写入和衰减闭环 |
| `t_intent_node(kb_id,parent_code)` 作为中高优先索引 | 当前主查询路径未证明需要，降级为按后续功能触发 |

---

## 12. 架构亮点

1. **Kernel 依赖轻量**：没有把外部 SDK 泄漏到核心模块，端口适配器边界仍成立。
2. **适配器技术边界清楚**：向量、关键词搜索、存储、消息、解析、观测、Web 都有独立模块。
3. **Feature 扩展点清晰**：入库节点、检索通道、后处理器和 MCP 能力都通过 Feature/SPI 扩展。
4. **RAG Trace 贴合业务**：相比通用 tracing，更容易展示 RAG 检索和生成链路。
5. **前端 SSE Hook 完整**：支持流式事件、取消、重试等关键体验。
6. **混合检索主闭环已成型**：Elasticsearch、PostgreSQL FTS、Lucene Embedded、RRF、Rerank、评测闭环都已落地。
7. **记忆四层设计有延展空间**：读链路、显式写入和短期清理已接入主流程，后续补 token budget 和质量策略后能提升生产可控性。

---

## 附录：校准依据摘要

| 项目 | 当前代码事实 |
|------|--------------|
| Maven 模块 | 根 `pom.xml` 24 个 `<module>` |
| Outbound 文件 / 接口 | 231 个 Java 文件，约 103 个 `interface` |
| Inbound 文件 / 接口 | 85 个 Java 文件，约 31 个 `interface` |
| 自动配置 | kernel auto config 1266 行 / 82 bean；native adapter auto config 1209 行 / 106 bean |
| 检索通道 | `KernelMultiChannelRetrievalEngine` 已支持通道级 timeout，单通道超时按空结果降级并记录观测 |
| Rerank | `RerankPostProcessorFeature` 已有 timeout |
| 记忆读链路 | `KernelChatPipeline.activateMemory()` 调用 `MemoryEnginePort.loadMemory()` |
| 记忆写链路 | `MemoryCaptureStage` 在响应完成后触发；`DefaultMemoryEnginePort.writeMemory()` 支持显式可信用户声明写入短期记忆 |
| 记忆衰减链路 | `KernelMemoryGovernanceService.runDecay()` 调用 `ShortTermMemoryMaintenancePort`；JDBC adapter 支持过期/低衰减扫描与软删 |
| 前端 | `chatStore.ts` 527 行，`ChatPage.tsx` 当前 3 个 `useEffect` |
| 数据库 | 基础 SQL 28 张表，metadata governance 8 张表，retrieval governance 4 张表 |

复核方法：

```powershell
rg --files | rg '\.java$' | Measure-Object
rg -n '^public interface ' seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound
rg -n '^public interface ' seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound
(Get-Content seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelAutoConfiguration.java).Count
rg -n '@Bean' seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelAutoConfiguration.java
```
