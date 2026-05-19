# Phase B 实施计划：Agentic Search（让 LLM 决定如何检索）

> 该计划依赖 Phase A 已完成（KernelAgentLoop + ToolPort + chatMode 路由 + OpenAI function-calling 适配）。
> 完成后 LLM 在 Agent 模式下可以 **自主多次调用检索工具、改写问题、追加过滤条件**，把 RAG 从硬编码流水线升级为可决策的工具调用。

## Goal

把现有的多通道检索能力暴露成一组语义明确的 **Tool**（`search_knowledge_base`、`search_keyword`、`search_metadata_filter`、`rewrite_query`），让 `KernelAgentLoop` 在运行时按需调用；同时新增 `KernelRetrievalToolFacade` 作为"检索-即-工具"的稳定门面，使 RAG 主链路与 Agent 工具调用共用同一份检索内核。

## Architecture

```
        KernelAgentLoop (Phase A)
              │ invoke(toolId, args)
              ▼
       ToolRegistryPort
              │
   ┌──────────┼──────────────────────────────┬──────────────────────┐
   │          │                              │                      │
search_kb   search_keyword         search_metadata_filter      rewrite_query
   │          │                              │                      │
   └─────┬────┴───────┬──────────────────────┘                      │
         ▼            ▼                                              ▼
KernelRetrievalToolFacade  ────►  KernelMultiChannelRetrievalEngine  QueryRewritePort
         │                                  │
         │                                  ├── VectorGlobalSearchFeature
         │                                  ├── KeywordSearchChannelFeature
         │                                  └── IntentDirectedSearchFeature
         │                                  + RrfFusionPostProcessor
         │                                  + RerankPostProcessor
         │                                  + MetadataGuard
         └── 复用现有 PostProcessor 链
```

**关键设计**：
- 不重写 `KernelMultiChannelRetrievalEngine`，**只在它之上加一层 Facade**，把 `SubQuestionIntent`-驱动接口翻译为 LLM 友好的 `(query, filters, topK, channelHints)` 接口。
- 旧 RAG 主链路继续通过 `RetrievalContextPort` 调用 Engine，**完全不动**。

## Tech Stack

- Java 17 + 现有 Kernel
- 复用：`KernelMultiChannelRetrievalEngine`、`SearchChannelFeature`、`QueryRewritePort`、`MetadataFilterCompiler`
- 不引入新的向量库 / 关键词后端依赖

## Baseline / Authority Refs

- `@C:/user-data/code/ai/seahorse-agent/docs/agent-vs-rag-capability-baseline.md` — Phase B
- `@C:/user-data/code/ai/seahorse-agent/docs/aegis/plans/2026-05-19-phase-a-agent-loop.md` — 前置条件
- `@C:/user-data/code/ai/seahorse-agent/seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/retrieval/KernelMultiChannelRetrievalEngine.java`
- `@C:/user-data/code/ai/seahorse-agent/seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/feature/retrieval/SearchChannelFeature.java`
- `@C:/user-data/code/ai/seahorse-agent/seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/feature/retrieval/DefaultMetadataFilterCompiler.java`

## Compatibility Boundary

**必须保持稳定**：
- `KernelMultiChannelRetrievalEngine` 公共方法签名与行为 100% 不变
- `KernelChatPipeline` RAG 主链路不变
- `SearchChannelFeature` / `SearchResultPostProcessorFeature` SPI 不变
- `RetrievalContextPort.retrieve(subIntents, topK)` 旧入口保留

**允许变化**：
- 新增包 `kernel/application/agent/tools/retrieval/`
- `KernelMultiChannelRetrievalEngine` 已有 `retrieveKnowledgeChannels(subIntents, topK, filter, options, traceRunScope)` 5 参方法保持不变，仅被新 Facade 调用

## Verification

```bash
mvn -pl seahorse-agent-tests -am -Dtest='*Retrieval*Tool*,*Agentic*' test
```

Phase B 最终通过：`KernelChatPipelineTests` 4/4、Phase A 全部、Phase B 新增 12 个用例全绿。

## Risks / Rollback

| 风险 | 缓解 |
|---|---|
| LLM 把 `filters` 写错抛 `MetadataFilterCompiler` 异常 | Facade 内捕获，转 `ToolInvocationResult.failed(...)`，让 LLM 重试 |
| LLM 反复检索导致 token 爆 | Phase A 的 `maxSteps`（默认 6）+ Phase B 工具内部 `topK` 上限 20 |
| Trace 节点暴增 | Tool 调用一次 = 一个 trace node，沿用 `KernelRetrievalObservationSupport` |
| Schema 漂移 | Tool JSON Schema 在 `KernelRetrievalToolFacade` 常量中固化，单元测试断言不变 |

## Retirement

无下线项。Phase B 仅新增；旧 RAG 主链路保留。

---

## File Map

**新建**：
- `seahorse-agent-kernel/.../kernel/domain/retrieval/AgenticSearchRequest.java`
- `.../kernel/domain/retrieval/AgenticSearchResult.java`
- `.../kernel/domain/retrieval/AgenticSearchChannelHint.java`
- `.../kernel/application/agent/tools/retrieval/KernelRetrievalToolFacade.java`
- `.../kernel/application/agent/tools/retrieval/SearchKnowledgeBaseTool.java`
- `.../kernel/application/agent/tools/retrieval/SearchKeywordTool.java`
- `.../kernel/application/agent/tools/retrieval/SearchMetadataFilterTool.java`
- `.../kernel/application/agent/tools/retrieval/RewriteQueryTool.java`
- `seahorse-agent-spring-boot-starter/.../SeahorseAgentKernelAgentRetrievalToolsAutoConfiguration.java`
- 测试：
  - `KernelRetrievalToolFacadeTests`
  - `SearchKnowledgeBaseToolTests`
  - `SearchKeywordToolTests`
  - `SearchMetadataFilterToolTests`
  - `RewriteQueryToolTests`
  - `AgenticSearchEndToEndTests`（用 Fake LLM 串完整循环）

**修改**：
- `seahorse-agent-spring-boot-starter/src/main/resources/application.properties` — `seahorse-agent.chat.agent.tools.retrieval-enabled=true`

---

## Tasks

### Task B1 — AgenticSearchRequest / Result / ChannelHint 领域模型

**Files**:
- create: `kernel/domain/retrieval/AgenticSearchRequest.java`
- create: `kernel/domain/retrieval/AgenticSearchResult.java`
- create: `kernel/domain/retrieval/AgenticSearchChannelHint.java`
- create test: `seahorse-agent-tests/.../kernel/domain/retrieval/AgenticSearchDomainTests.java`

**Why**: LLM 友好接口与现有 `SubQuestionIntent` 解耦。

**Compatibility**: 新文件，独立包。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=AgenticSearchDomainTests test`

**Steps**:
1. **Write test** —
   - `AgenticSearchRequest.builder().query("入职流程").topK(8).build()` → `filters()==Map.of()`、`channelHints()` 默认 `ALL`；
   - `topK` 上限 20，越界归一化；下限 1。
   - `channelHints()=={VECTOR, KEYWORD}` 校验枚举；非法字符串 → `IllegalArgumentException`。
   - `AgenticSearchResult(chunks, channelStats, truncated)` 不可变；`AgenticSearchResult.empty()` 工厂。
   - `AgenticSearchChannelHint` 枚举：`VECTOR`、`KEYWORD`、`INTENT_DIRECTED`、`ALL`。
2. **Verify RED** — 失败。
3. **Minimal code** — 3 个 record/枚举。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(retrieval): 新增 AgenticSearch 领域模型`

---

### Task B2 — KernelRetrievalToolFacade：检索-即-工具门面

**Files**:
- create: `kernel/application/agent/tools/retrieval/KernelRetrievalToolFacade.java`
- create test: `seahorse-agent-tests/.../kernel/application/agent/tools/retrieval/KernelRetrievalToolFacadeTests.java`

**Why**: 把 `KernelMultiChannelRetrievalEngine`（`SubQuestionIntent`-驱动）适配为 `(query, filters, topK, channelHints)` 接口。

**Compatibility**: 仅读取 Engine，不修改其内部。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=KernelRetrievalToolFacadeTests test`

**Steps**:
1. **Write test** —
   - Mock Engine `retrieveKnowledgeChannels(...)` 返回 3 个 `RetrievedChunk` → Facade `search(req)` 返回 3 条 `AgenticSearchResult.chunks()`、`channelStats` 含通道计数；
   - `filters` 传 `{"docType":"sop","department":"R&D"}` → Engine 入参 `filter` 被 `DefaultMetadataFilterCompiler` 编译后传入；
   - `channelHints={KEYWORD}` 时 Engine 调用层的 `options.allowedChannels` 仅含 `KEYWORD`；
   - 编译异常时 Facade 抛 `AgenticSearchException(reason)`，不在 Facade 内吞掉。
2. **Verify RED** — 失败。
3. **Minimal code** —
   - 内部把 `query` 包成单元素 `SubQuestionIntent`（与现有 Engine 兼容的最小构造）；
   - filters → `MetadataFilterCompiler.compile(filtersMap)`；
   - channelHints → 传给 Engine 的 options（如不存在该字段则在 PostProcessor 阶段做兜底过滤，断言用 spy）。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(retrieval): 新增 KernelRetrievalToolFacade`

---

### Task B3 — SearchKnowledgeBaseTool（向量检索）

**Files**:
- create: `kernel/application/agent/tools/retrieval/SearchKnowledgeBaseTool.java`
- create test: `seahorse-agent-tests/.../kernel/application/agent/tools/retrieval/SearchKnowledgeBaseToolTests.java`

**Why**: 提供 LLM 第一个、最常用的检索工具。

**Compatibility**: 实现 `ToolPort`。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=SearchKnowledgeBaseToolTests test`

**Steps**:
1. **Write test** —
   - `descriptor().toolId()=="search_knowledge_base"`；`jsonSchema()` 包含 `query`(string, required)、`topK`(integer, default 5, max 20)、`filters`(object)；
   - `invoke("call-1", "search_knowledge_base", {"query":"入职流程","topK":3})` → 调 Facade，`AgenticSearchResult.chunks` 序列化为 JSON 字符串 `ToolInvocationResult.ok(...)`，每条只保留 `chunkId/title/score/snippet`（不泄露内部字段）；
   - Facade 抛 `AgenticSearchException` → `ToolInvocationResult.failed(message)` 不抛异常。
2. **Verify RED** — 失败。
3. **Minimal code** — 用 `ObjectMapper` 序列化精简 DTO。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(agent): 新增 search_knowledge_base Tool`

---

### Task B4 — SearchKeywordTool（仅关键词通道）

**Files**:
- create: `kernel/application/agent/tools/retrieval/SearchKeywordTool.java`
- create test: `seahorse-agent-tests/.../kernel/application/agent/tools/retrieval/SearchKeywordToolTests.java`

**Why**: 让 LLM 在精确词、缩写、品牌名等场景显式跳过向量召回。

**Compatibility**: 与 B3 并列。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=SearchKeywordToolTests test`

**Steps**:
1. **Write test** —
   - `descriptor().toolId()=="search_keyword"`；调用时 Facade 收到 `channelHints==Set.of(KEYWORD)`；
   - JSON Schema 必填 `query`、可选 `topK`。
2. **Verify RED** — 失败。
3. **Minimal code** — 复用 B3 模式，channelHints 固定。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(agent): 新增 search_keyword Tool`

---

### Task B5 — SearchMetadataFilterTool（强制 filters 必填）

**Files**:
- create: `kernel/application/agent/tools/retrieval/SearchMetadataFilterTool.java`
- create test: `seahorse-agent-tests/.../kernel/application/agent/tools/retrieval/SearchMetadataFilterToolTests.java`

**Why**: 显式工具让 LLM 学会"先按部门 / 文档类型过滤再召回"，避免 LLM 在 `search_knowledge_base` 中把 filters 漏掉。

**Compatibility**: 独立 Tool。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=SearchMetadataFilterToolTests test`

**Steps**:
1. **Write test** —
   - JSON Schema：`filters` 字段为 `required`，且最少包含 1 个键，否则 `ToolInvocationResult.failed("filters 不能为空")`；
   - 正确入参 → Facade 收到 filters 与 query；
   - filter 编译失败 → `failed("过滤条件不合法: ...")`。
2. **Verify RED** — 失败。
3. **Minimal code** — `invoke` 内先做轻量校验再调 Facade。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(agent): 新增 search_metadata_filter Tool`

---

### Task B6 — RewriteQueryTool（仅改写，不检索）

**Files**:
- create: `kernel/application/agent/tools/retrieval/RewriteQueryTool.java`
- create test: `seahorse-agent-tests/.../kernel/application/agent/tools/retrieval/RewriteQueryToolTests.java`

**Why**: 让 LLM 显式调用 `rewrite_query` 重写指代不清问题；与现有 `QueryRewritePort` 解耦。

**Compatibility**: 复用 `QueryRewritePort` 出站端口。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=RewriteQueryToolTests test`

**Steps**:
1. **Write test** —
   - 入参 `query`(string, required)、`historyHint`(string, optional)；
   - 调 Mock `QueryRewritePort.rewriteWithSplit(query, history)` 返回 `RewriteResult("入职流程", List.of("HR 入职","IT 入职"))`；
   - 输出 `ToolInvocationResult.ok(json)` 字段：`rewrittenQuestion`、`subQuestions[]`；
   - 端口异常 → `failed(...)`。
2. **Verify RED** — 失败。
3. **Minimal code** — 简单封装。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(agent): 新增 rewrite_query Tool`

---

### Task B7 — Spring 自动装配：检索 Tools 注册到 ToolRegistry

**Files**:
- create: `seahorse-agent-spring-boot-starter/.../SeahorseAgentKernelAgentRetrievalToolsAutoConfiguration.java`
- modify: `seahorse-agent-spring-boot-starter/src/main/resources/application.properties`
- create test: `seahorse-agent-tests/.../adapters/spring/SeahorseAgentKernelAgentRetrievalToolsAutoConfigurationTests.java`

**Why**: 只在 Agent 模式且 retrieval-tools 开启时装配。

**Compatibility**: `@ConditionalOnProperty(name="seahorse-agent.chat.agent.tools.retrieval-enabled", havingValue="true", matchIfMissing=true)` + `@ConditionalOnBean(KernelMultiChannelRetrievalEngine.class)`。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=SeahorseAgentKernelAgentRetrievalToolsAutoConfigurationTests test`

**Steps**:
1. **Write test** —
   - `agent-mode-enabled=true` 且 retrieval-tools 默认开 → ToolRegistry 含 4 个工具的 descriptor。
   - 显式关闭 `retrieval-enabled=false` → 4 个 Bean 都不存在。
   - 缺 `KernelMultiChannelRetrievalEngine` → 不装配且不抛错。
2. **Verify RED** — 失败。
3. **Minimal code** — 装配 Facade + 4 个 Tool Bean，通过 `ApplicationRunner` 调 `ToolRegistryPort.register(...)`（在 InMemoryToolRegistry 提供注册方法）。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(starter): 装配 Agent 检索工具集`

---

### Task B8 — 端到端：Fake LLM 驱动 AgentLoop 调检索工具

**Files**:
- create test: `seahorse-agent-tests/.../kernel/application/agent/AgenticSearchEndToEndTests.java`

**Why**: 锁住"LLM 决策 → Tool 选择 → 检索 → 拼回上下文 → 最终回答"完整链路，防 Phase B 后续重构破坏。

**Compatibility**: 仅测试代码。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=AgenticSearchEndToEndTests test`

**Steps**:
1. **Write test** —
   - **场景 A**：Fake LLM 第一步选 `search_knowledge_base({"query":"入职流程"})`，Fake Facade 回 2 条 chunk；第二步选给最终回答，输出含 "入职流程" → 断言 `result.finalAnswer().contains("入职流程")`，`result.steps().size()==2`，第一步 observation 含 `chunkId`。
   - **场景 B**：Fake LLM 先 `rewrite_query` → 再 `search_metadata_filter`(`{department:"R&D"}`) → 最终回答；断言三步顺序、filter 传递正确。
   - **场景 C**：Fake LLM 选了不存在的 toolId `web_search` → observation `failed`，LLM 第二步改选 `search_knowledge_base` 完成回答；断言 `truncated==false`。
2. **Verify RED** — 失败。
3. **Minimal code** — 已具备所有组件，仅测试代码组装。
4. **Verify GREEN** — 绿。
5. **Commit** — `test(agent): 新增 Agentic 检索端到端用例`

---

## Self-Review

- **Spec coverage** — "Tool 化检索"、"Filter 显式工具"、"Rewrite 显式工具"、"装配开关"、"E2E"全部映射。
- **Placeholder scan** — 无。
- **Type consistency** — `AgenticSearchRequest/Result/ChannelHint`、`ToolInvocationResult` 跨任务一致。
- **Compatibility** — `KernelMultiChannelRetrievalEngine` 不动；RAG 主链路不动；新 Bean 走条件装配。
- **Verification** — 每个 Task 有 `mvn` 命令；B8 兜底端到端。
- **Dual-track** — RAG 主链路继续走 `RetrievalContextPort`；Agent 模式走 Facade。后续若把 RAG 主链路改为复用 Facade，再开 retirement 计划。
- **Decision hygiene** — Facade 是新 owner（语义层），与 Engine（执行层）职责清晰。

## Retirement Track

无。Phase B 仅新增；不替换、不下线既有路径。
