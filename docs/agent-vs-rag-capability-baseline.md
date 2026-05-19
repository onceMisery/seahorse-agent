# Seahorse Agent：Agent 能力 vs RAG 增强 — 规划基线

> 本文是一份**规划基线**，不进入 docs/zh 的对外 wiki 内容树。  
> 用途：给后续路线图（Agentic Search、需求分析 Agent、Human-in-the-Loop、知识图谱等）一个统一坐标系，区分哪些能力是真正的 **Agent 能力**，哪些是 **RAG 工程化增强**。  
> 评判口径见文末“判定标准”。

---

## TL;DR

- **完成度**：当前最成熟的能力仍是 **RAG 工程化增强**（多通道检索、RRF、Rerank、元数据过滤、入库 Pipeline、Trace、Eval），并已有完整闭环。
- **Agent 化**：Phase A 已落地第一版基础 Agent Loop：`chatMode=agent` 可进入 `KernelAgentLoop`，通过 OpenAI-compatible function-calling 协议、`ToolRegistryPort` / `ToolPort` 和 MCP allowlist adapter 执行多轮“模型决策 → 工具调用 → observation → 再决策”。
- **当前边界**：Agent Loop 只是基础执行环。状态机、任务快照、Skill/Agent 注册中心、Human-in-the-Loop、Agentic Search、记忆读写工具化、输出自愈等仍未落地。
- **MCP 口径**：RAG 路径里的 MCP 仍是“工具型知识源”；Agent 模式下，allowlist 注册后的 MCP 工具可以被 LLM function-calling 调用。
- **下一步收益最大**的方向：Phase B/C，即把检索、记忆、外部工具做成可被 Agent Loop 调度的一等工具，并补齐 Skill 与可暂停/恢复的长期任务状态机。

---

## 1. 总览矩阵

> 图例：✅=已落地  🟡=基础已落地/部分能力  ❌=未落地
> 维度：**A=Agent 能力 / R=RAG 增强 / I=基础设施**

| # | 能力 | 状态 | 维度 | 代码位置（首要） | 是不是真 Agent？ |
|---|---|---|---|---|---|
| 1 | 文档入库 Pipeline (fetcher→parser→chunker→embedder→indexer) | ✅ | R | `kernel/feature/ingestion/*` + `KernelIngestionEngine` | ❌ 纯 RAG |
| 2 | 多通道检索（向量 + 关键词） | ✅ | R | `feature/retrieval/{VectorGlobalSearch,KeywordSearchChannel}Feature` | ❌ 纯 RAG |
| 3 | RRF 融合 / Rerank / Metadata Guard 后处理链 | ✅ | R | `feature/retrieval/{Rrf,Rerank,MetadataGuard,FinalTruncate}PostProcessorFeature` | ❌ 纯 RAG |
| 4 | 元数据过滤（filters → Milvus/PG 表达式） | ✅ | R | `feature/retrieval/DefaultMetadataFilterCompiler` | ❌ 纯 RAG |
| 5 | 检索 Trace（`KernelRagTraceRecorder` + node trace） | ✅ | R+I | `application/trace`、`application/retrieval/KernelRetrievalObservationSupport` | ❌ 纯 RAG |
| 6 | 检索评测（dataset/strategy template/eval service） | ✅ | R | `KernelRetrievalEvaluation*Service` | ❌ 纯 RAG |
| 7 | 流式对话 RAG 主链路（rewrite→intent→retrieve→prompt→LLM SSE） | ✅ | R | `KernelChatPipeline` + `KernelChat{Preparation,Response}Support` | ❌ 纯 RAG |
| 8 | 查询改写 / 拆分 | ✅ | R | `QueryRewritePort` + `RuleBasedQueryOptimizerPort` / `LlmQueryOptimizerAdapter` | ❌ 是 RAG 前处理 |
| 9 | 意图解析 / 引导 / 系统 prompt | ✅ | R+A | `IntentResolutionPort` / `IntentGuidancePort` / `KernelIntentTreeService` | 🟡 路由层，目前主要服务 RAG |
| 10 | 四层记忆模型（Working/Short/Long/Semantic） + 治理 | 🟡 | A | `application/memory/*`、`feature/memory/MemoryGovernanceFeature` | 🟡 数据/治理已就绪，Agent Loop 尚未把 memory read/write 当作工具调用 |
| 11 | 记忆质量评估 / 冲突日志 / 衰减 | 🟡 | A | `KernelMemoryGovernanceService`、`MemoryConflictLogRepositoryPort` | 🟡 离线侧能力，未喂回 Agent 运行时决策 |
| 12 | MCP 工具调用（Client + Server + Executor） | 🟡 | A | `KernelMcpOrchestrator`、`McpToolAllowlistRegistrar`、`McpToolPortAdapter` | 🟡 双路径：RAG 中仍是知识源；Agent 模式可作为 allowlist 工具被 LLM 调用 |
| 13 | MCP 参数抽取（`McpParameterExtractionPort`） | 🟡 | A | `ports/outbound/mcp/McpParameterExtractionPort` | 🟡 端口已就绪，默认 `noop` |
| 14 | 模型路由策略（多模型选型） | 🟡 | A | `feature/model/ModelRoutingPolicyFeature` | 🟡 Feature 骨架，链路里未做“按子任务动态选模型” |
| 15 | Feature/Extension 注册中心（AgentSPI） | ✅ | I | `kernel/plugin/{AgentSPI,ExtensionRegistry,ExtensionLoader,Wrapper}` | I 基础设施，是不是 Agent 看上层用法 |
| 16 | PortWrapper 横切治理（限流/重试/观测） | ✅ | I | `kernel/plugin/wrapper/*` | I 基础设施 |
| 17 | 流任务管理 / 取消 / SSE | ✅ | I | `LocalStreamTaskPort`、`StreamTaskPort` | I 基础设施；Agent Loop 已接入取消句柄 |
| 18 | 多源接入（飞书） | 🟡 | R | `adapter-source-feishu` | 仍是入库源 |
| 19 | 搜索后端适配（Elasticsearch / Lucene） | 🟡 | R | `adapter-search-elasticsearch`、`adapter-search-lucene` | 仍是 RAG 召回通道 |
| 20 | **LLM 自主决策 / ReAct / Plan-Execute** | 🟡 | A | `application/agent/KernelAgentLoop` | 基础 ReAct-style tool loop 已落地；Plan-Execute 和长期任务计划未落地 |
| 21 | **多步工具调用（Tool Loop）** | ✅ | A | `KernelAgentLoop` + `StreamingChatModelPort.streamChatWithTools` | 已支持 function-calling 循环；检索/记忆等关键工具尚未接入 |
| 22 | **状态机（Suspend/Resume，Human-in-the-Loop）** | ❌ | A | — | 未落地 |
| 23 | **Agent 任务快照 / Git 风格版本树** | ❌ | A | — | 未落地 |
| 24 | **Agentic Search（自我规划检索步骤）** | ❌ | A | — | 检索尚未注册为 `search_knowledge_base` 等工具 |
| 25 | **跨步骤记忆斩断 / 视图切片（数据降维）** | ❌ | A | — | 未落地 |
| 26 | **输出自愈重试环（JSON/Mermaid 语法校验）** | ❌ | A+I | — | 未落地 |
| 27 | **Skill / Agent 注册中心（领域 Agent 编排）** | ❌ | A | — | 没有 `Skill`/`AgentDefinition` 顶层概念 |
| 28 | **权限 / 多租户 / 数据范围隔离 for 检索** | ❌ | R+A | — | 企业级缺口 |
| 29 | **知识图谱 / 条款层级 / 冲突优先级** | ❌ | R | — | 未来项 |
| 30 | **指标驱动的评测闭环（自动回流）** | 🟡 | R+I | `KernelRetrievalEvaluationService` | 已有离线评测，缺线上自动回流 |

---

## 2. 分类总结

### 2.1 真 Agent 能力（A）

#### 已落地基础能力（✅/🟡）

| 能力 | 现状 | 剩余差距 |
|---|---|---|
| Agent Loop | `KernelAgentLoop` 已支持多轮 function-calling、工具 observation、取消、trace 和截断保护 | 还不是持久化状态机；没有 Plan-Execute、暂停/恢复、人工确认 |
| Tool 抽象 | `ToolPort` / `ToolRegistryPort` / `InMemoryToolRegistry` 已存在 | 检索、记忆、Web Search 等核心能力尚未注册为标准工具 |
| OpenAI-compatible tool calling | `StreamingChatModelPort.streamChatWithTools` 和 `OpenAiCompatibleModelAdapter` 已落地 | 仍需补 Anthropic/其他模型协议适配，工具 schema 治理也要继续收敛 |
| MCP 工具适配 | `McpToolPortAdapter` + `McpToolAllowlistRegistrar` 可把 allowlist MCP 工具注册进 Agent 工具表 | RAG 路径仍保留旧的意图触发方式，双路径语义需要长期收敛 |
| 四层记忆 | 数据模型、写入、治理、衰减、质量评估完整 | 没有“由 LLM 在运行时决定是否 read/write/forget”的工具闭环 |

#### 仍然缺失（❌）

1. **状态机 + Suspend/Resume**：还没有持久化任务状态、人工确认点和恢复协议。
2. **Skill 顶层抽象**：没有 `AgentDefinition`、`SkillManifest`、`PhaseHandler` 这类一等公民。
3. **任务快照 / 版本树**：用户“推翻重来”或比较分支方案时仍缺少结构化支撑。
4. **Agentic Search**：检索系统还没有被封装成可由 LLM 多步规划调用的工具。
5. **结构化输出自愈**：JSON / Mermaid / DDL 错误时无校验、反哺和重试环。
6. **视图切片 / 上下文降维**：长 JSON 喂下一阶段会爆 token，未在 Agent 步骤间拦截。

---

### 2.2 RAG 增强（R）

这一段是项目目前**最重最完整**的部分，全部都不是 Agent 能力：

- **入库链路**：fetcher / parser / chunker / embedder / indexer / metadata extractor/normalizer/validator / enhancer / enricher（11 个节点 Feature）
- **检索链路**：vector global / keyword / intent-directed + RRF / Rerank / Metadata Guard / Final Truncate（4+4 个 Feature）
- **查询前处理**：query optimizer（rule/llm 双实现）、rewrite、split
- **意图层**：树/分组/打分
- **评测**：dataset、strategy template、eval service
- **多源/多后端**：飞书、ES、Lucene、Milvus、pgvector
- **Trace**：完整 `KernelRagTraceRecorder` + node 粒度

**这些是非常扎实的 RAG 工程能力**，但本质上无论怎么加 Feature，都是在加强“召回 + 排序 + 上下文质量”这一件事。它们只有被 Agent Loop 作为工具动态调度时，才会进入 Agent 能力范畴。

---

### 2.3 基础设施（I）

- `AgentSPI` / `ExtensionRegistry` / `ExtensionLoader` / `PortWrapper` / `FeatureHealth`
- `StreamTaskPort` / SSE / cancellation
- `ObservationPort` / Micrometer
- `cache` / `coordination`（分布式信号量）/ `mq` / `storage`

**结论**：基础设施已经具备承载 Agent 的能力；Phase A 已补上第一版 application 层 Agent 编排器，但上层 Skill、状态机和工具生态仍需要继续建设。

---

## 3. 关键判断：MCP 是不是真 Agent？

现在需要按**双路径**判断。

### 3.1 RAG 路径：不是 Agent 工具调用

`KernelRetrievalEngine` 仍会在检索阶段按意图分数触发 `KernelMcpOrchestrator`，再把结果塞回 `RetrievalContext.mcpContext` 拼进 RAG Prompt。

**特征**：

- `mcpIntents` 来自意图打分阶段，本质是“看哪个 `IntentNode.mcpToolId` 匹配子问题”。
- 执行一次后把结果作为上下文交给 RAG 回答。
- LLM 没有在看到工具结果后继续决定是否调用下一项工具。

所以在 RAG 路径中，MCP 仍是 **“工具型知识源”**，不是完整 Agent 工具调用。

### 3.2 Agent 模式：具备 Agent 工具调用基础

`chatMode=agent` 进入 `KernelChatInboundService` 后，可调用 `KernelAgentLoop`。`KernelAgentLoop` 通过 `StreamingChatModelPort.streamChatWithTools` 把 `ToolRegistryPort` 中的工具描述传给模型，并按模型返回的 tool calls 派发到对应 `ToolPort`。

**特征**：

- LLM 在运行时根据上下文决定是否调用工具。
- 工具执行结果作为 observation 回到消息序列。
- 循环可以继续，直到模型给出最终回答或达到步数上限。
- MCP 工具只有在 `McpToolAllowlistRegistrar` 成功注册到 `ToolRegistryPort` 后，才会成为 Agent 可调用工具。

所以在 Agent 模式中，MCP 可以成为真正的 **function-calling 工具**，但前提是 allowlist、工具描述、参数 schema 和注册链路都正确配置。

---

## 4. 后续规划基线（Sequenced）

### Phase A — 基础 Agent Loop（已落地）

已完成的基线：

1. `KernelAgentLoop`：基础 ReAct-style 循环、工具调用、observation、trace、取消和截断保护。
2. `ToolPort` / `ToolRegistryPort`：工具抽象与内存注册表。
3. `StreamingChatModelPort.streamChatWithTools`：OpenAI-compatible function-calling 协议入口。
4. `chatMode=rag | agent`：默认 RAG，显式 agent 模式进入 Agent Loop。
5. MCP allowlist adapter：可把允许的 MCP 工具注册为 Agent 工具。

### Phase B — Agentic 检索（让 LLM 决定怎么查）

1. 把 `search_knowledge_base` / `search_web` / `query_metadata` 等做成 Tool。
2. 让 `KernelMultiChannelRetrievalEngine` 既可被 RAG 链路调用，也可被 Agent Loop 调用（提取 retrieval-as-tool facade）。
3. 支持多步检索、reflective rewrite、查询策略选择和检索结果质量 observation。

### Phase C — Skill / 状态机（落地“需求分析 Agent”草稿）

1. 新增 `application/skill/SkillRegistry` + `SkillDefinition` + `PhaseHandler`。
2. 持久化状态机表 `seahorse_task_state` + 快照表 `seahorse_task_snapshot`。
3. SSE 双轨：展示轨 + 逻辑轨（前端断开仍保存）。
4. VCP 协议（前后端 Command/Variable）。
5. Human-in-the-Loop：确认、修改、跳过、回滚、继续。

### Phase D — 输出可信度治理

1. `OutputValidatorPort` + `SelfHealingLoop`：JSON / Mermaid / DDL 校验 + LLM 反哺重试。
2. `RagEvaluator` 双模型策略：检索结果先打分再注入。
3. `ContextReducer` 视图切片，避免上下文爆炸。

### Phase E — 记忆闭环

1. 让 Agent Loop 在运行时调用 `memory_read` / `memory_write` / `memory_forget` 工具。
2. 接入冲突检测和高价值短期记忆晋升。
3. 把记忆质量分、衰减分和用户反馈转成 Agent 可观察信号。

### Phase F — 企业治理

1. 检索权限 / 数据范围 / 多租户。
2. 知识图谱（条款层级、冲突、版本）。
3. 在线评测自动回流。

---

## 5. 判定标准（用于以后新增能力归类）

为避免之后又把“加一个 reranker”当成“Agent 能力提升”，固定如下口径：

| 是不是 Agent 能力 | 判定 |
|---|---|
| ✅ 真 Agent | LLM 在**运行时**根据观察决定下一步动作；存在 Thought/Action/Observation 循环；或带状态机的 multi-step 任务编排（Human-in-the-Loop）。 |
| 🟡 Agent 基础/骨架 | 已有端口、工具、循环或数据模型，但缺少持久状态、领域 Skill、动态工具生态或完整任务治理。 |
| ❌ RAG 增强 | 任何在“Query → Retrieve → Generate”这条直线上加 Feature 的工作（包括 RRF、Rerank、Metadata 过滤、多通道、知识图谱、评测、视图切片用于 RAG 上下文等）。 |
| I 基础设施 | 不直接体现智能，只为上面任一类提供承载（缓存、消息、协调、可观测、Stream、SPI）。 |

> 一句口诀：**“如果把 LLM 拿掉，链路还能跑通”，那它就是 R 或 I；只有“LLM 在跑中改变了链路”才算 A。**

---

## 6. 与已有文档的关系

- `缺少的功能.md`：用户视角的 backlog；本文件给出**架构视角的归类**，二者互补。
- `自定义AGENT接入.md`：需求分析 Agent 的设计草稿，是 **Phase C** 的第一个落地候选。
- `docs/Agent_Memory_系统改进设计方案.md` / `docs/memory-system-improvement-plan.md`：记忆维度更细的子规划，对应 **Phase E**。
- `docs/zh/content/架构设计/企业级可插拔RAG架构设计.md`：对应矩阵中 R 维度，已稳定。
- `README.md`：项目入口说明，应同步体现“RAG 闭环成熟，基础 Agent Loop 已具备，长期 Agent 能力仍在建设”的定位。

---

_最后更新：2026-05-19。新增/调整能力时请同步矩阵和分类。_
