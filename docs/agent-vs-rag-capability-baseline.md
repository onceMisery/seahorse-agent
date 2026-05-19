# Seahorse Agent：Agent 能力 vs RAG 增强 — 规划基线

> 本文是一份**规划基线**，不进入 docs/zh 的对外 wiki 内容树。  
> 用途：给后续路线图（Agentic Search、需求分析 Agent、Human-in-the-Loop、知识图谱等）一个统一坐标系，区分哪些能力是真正的 **Agent 能力**，哪些是 **RAG 工程化增强**。  
> 评判口径见文末"判定标准"。

---

## TL;DR

- **完成度**：当前 80% 的代码量在做 **RAG 工程化增强**（多通道检索、RRF、Rerank、元数据过滤、入库 Pipeline、Trace、Eval），并已有完整闭环。
- **Agent 化**：架构已经按"Agent 平台"铺好骨架（`AgentSPI` / Feature / 四层记忆 / MCP 端口 / 模型路由），但**真正的 Agent 闭环（LLM 自主决策 + 工具调用 + 多步执行 + 状态机）尚未串联**。
- **当前瓶颈**：MCP 工具调用不是由 LLM 自主决定的，而是被"意图打分匹配到 `mcpToolId`"驱动的——本质仍是 **"把工具结果当成另一种知识源喂回 RAG"**。
- **下一步收益最大**的方向：把 ReAct / Plan-Execute 这层 LLM-Driven 编排补上，让 MCP/Memory/Retrieval 都成为真正可被 LLM 在运行时调度的工具。

---

## 1. 总览矩阵

> 图例：✅=已落地  🟡=骨架就绪/部分能力  ❌=未落地  
> 维度：**A=Agent 能力 / R=RAG 增强 / I=基础设施**

| # | 能力 | 状态 | 维度 | 代码位置（首要） | 是不是真 Agent？ |
|---|---|---|---|---|---|
| 1 | 文档入库 Pipeline (fetcher→parser→chunker→embedder→indexer) | ✅ | R | `kernel/feature/ingestion/*` + `KernelIngestionEngine` | ❌ 纯 RAG |
| 2 | 多通道检索（向量 + 关键词） | ✅ | R | `feature/retrieval/{VectorGlobalSearch,KeywordSearchChannel}Feature` | ❌ 纯 RAG |
| 3 | RRF 融合 / Rerank / Metadata Guard 后处理链 | ✅ | R | `feature/retrieval/{Rrf,Rerank,MetadataGuard,FinalTruncate}PostProcessorFeature` | ❌ 纯 RAG |
| 4 | 元数据过滤（filters → Milvus/PG 表达式） | ✅ | R | `feature/retrieval/DefaultMetadataFilterCompiler` | ❌ 纯 RAG |
| 5 | 检索 Trace（`KernelRagTraceRecorder` + node trace） | ✅ | R+I | `application/trace`、`application/retrieval/KernelRetrievalObservationSupport` | ❌ 纯 RAG |
| 6 | 检索评测（dataset/strategy template/eval service） | ✅ | R | `KernelRetrievalEvaluation*Service` | ❌ 纯 RAG |
| 7 | 流式对话主链路（rewrite→intent→retrieve→prompt→LLM SSE） | ✅ | R | `KernelChatPipeline` + `KernelChat{Preparation,Response}Support` | ❌ 纯 RAG（已修复空检索回退） |
| 8 | 查询改写 / 拆分 | ✅ | R | `QueryRewritePort` + `RuleBasedQueryOptimizerPort` / `LlmQueryOptimizerAdapter` | ❌ 是 RAG 前处理 |
| 9 | 意图解析 / 引导 / 系统 prompt | ✅ | R+A | `IntentResolutionPort` / `IntentGuidancePort` / `KernelIntentTreeService` | 🟡 路由层，但目前只服务 RAG |
| 10 | 四层记忆模型（Working/Short/Long/Semantic） + 治理 | 🟡 | A | `application/memory/*`、`feature/memory/MemoryGovernanceFeature` | 🟡 数据/治理已就绪，**Agent 编排层未消费** |
| 11 | 记忆质量评估 / 冲突日志 / 衰减 | 🟡 | A | `KernelMemoryGovernanceService`、`MemoryConflictLogRepositoryPort` | 🟡 离线侧能力，未喂回 Pipeline 决策 |
| 12 | MCP 工具调用（Client + Server + Executor） | 🟡 | A | `application/mcp/KernelMcpOrchestrator`、`seahorse-agent-mcp-server` | **❌ 不是 LLM 自主调用**，由"MCP 意图打分→`mcpToolId`"触发，结果回填到 `RetrievalContext.mcpContext` 当成知识源 |
| 13 | MCP 参数抽取（`McpParameterExtractionPort`） | 🟡 | A | `ports/outbound/mcp/McpParameterExtractionPort` | 🟡 端口已就绪，默认 `noop` |
| 14 | 模型路由策略（多模型选型） | 🟡 | A | `feature/model/ModelRoutingPolicyFeature` | 🟡 Feature 骨架，链路里未做"按子任务动态选模型" |
| 15 | Feature/Extension 注册中心（AgentSPI） | ✅ | I | `kernel/plugin/{AgentSPI,ExtensionRegistry,ExtensionLoader,Wrapper}` | I 基础设施，是不是 Agent 看上层用法 |
| 16 | PortWrapper 横切治理（限流/重试/观测） | ✅ | I | `kernel/plugin/wrapper/*` | I 基础设施 |
| 17 | 流任务管理 / 取消 / SSE | ✅ | I | `LocalStreamTaskPort`、`StreamTaskPort` | I 基础设施 |
| 18 | 多源接入（飞书） | 🟡 | R | `adapter-source-feishu` | 仍是入库源 |
| 19 | 搜索后端适配（Elasticsearch / Lucene） | 🟡 | R | `adapter-search-elasticsearch`、`adapter-search-lucene` | 仍是 RAG 召回通道 |
| 20 | **LLM 自主决策 / ReAct / Plan-Execute** | ❌ | A | — | **缺失，最关键的真 Agent 能力空白** |
| 21 | **多步工具调用（Tool Loop）** | ❌ | A | — | 当前是"一次性匹配并执行"，没有"调用→观察→再决定"循环 |
| 22 | **状态机（Suspend/Resume，Human-in-the-Loop）** | ❌ | A | — | 自定义AGENT接入.md 已设计，未落地 |
| 23 | **Agent 任务快照 / Git 风格版本树** | ❌ | A | — | 未落地 |
| 24 | **Agentic Search（自我规划检索步骤）** | ❌ | A | — | `缺少的功能.md` 列为未来项 |
| 25 | **跨步骤记忆斩断 / 视图切片（数据降维）** | ❌ | A | — | 未落地 |
| 26 | **输出自愈重试环（JSON/Mermaid 语法校验）** | ❌ | A+I | — | 未落地 |
| 27 | **Skill / Agent 注册中心（领域 Agent 编排）** | ❌ | A | — | 没有 `Skill`/`AgentDefinition` 顶层概念 |
| 28 | **权限 / 多租户 / 数据范围隔离 for 检索** | ❌ | R+A | — | `缺少的功能.md` 列为企业级缺口 |
| 29 | **知识图谱 / 条款层级 / 冲突优先级** | ❌ | R | — | `缺少的功能.md` 列为未来项 |
| 30 | **指标驱动的评测闭环（自动回流）** | 🟡 | R+I | `KernelRetrievalEvaluationService` | 已有离线评测，缺线上自动回流 |

---

## 2. 分类总结

### 2.1 真 Agent 能力（A）

#### 已就绪（🟡，仅骨架/数据层）
| 能力 | 现状 | 距离"真 Agent"的差距 |
|---|---|---|
| 四层记忆 | 数据模型、写入、治理、衰减、质量评估完整 | 没有"由 LLM 在运行时决定是否 read/write/forget"的闭环 |
| MCP 工具调用 | Client/Server/Registry/Orchestrator 都在 | **触发方式错误**：意图打分→ToolId，不是 LLM function-calling |
| MCP 参数抽取 | 端口存在 | 默认 noop，没有 LLM/JSON Schema 强制约束 |
| 模型路由 | Feature 骨架 | 没有"短问→mini、长问→大模型、代码→code 模型"的策略实现 |
| 意图引导 / 澄清 | `IntentGuidancePort` 存在 | 只在 RAG 入口触发，未作为多轮决策器 |

#### 完全缺失（❌）
1. **LLM-Driven 编排层**：ReAct / Plan-Execute / Reflexion 任一实现都没有。
2. **工具循环**：`Observation → Thought → Action → Observation` 的 loop，目前是"一次直发"。
3. **状态机 + Suspend/Resume**：`自定义AGENT接入.md` 整篇都在设计这个，仍未落代码。
4. **Skill 顶层抽象**：没有 `AgentDefinition`、`SkillManifest`、`PhaseHandler` 这类一等公民。
5. **任务快照 / 版本树**：用户"推翻重来"无解。
6. **结构化输出自愈**：JSON / Mermaid / DDL 错误时无重试环。
7. **视图切片 / 上下文降维**：长 JSON 喂下一阶段会爆 token，未拦截。

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

**这些是非常扎实的 RAG 工程能力**，但本质上无论怎么加 Feature，都是在加强"召回 + 排序 + 上下文质量"这一件事。**它们不会自动变成 Agent**。

---

### 2.3 基础设施（I）

- `AgentSPI` / `ExtensionRegistry` / `ExtensionLoader` / `PortWrapper` / `FeatureHealth`
- `StreamTaskPort` / SSE / cancellation
- `ObservationPort` / Micrometer
- `cache` / `coordination`（分布式信号量）/ `mq` / `storage`

**结论**：基础设施已经具备承载真 Agent 的能力，**问题在 application 层缺一层"Agent 编排器"**。

---

## 3. 关键判断：MCP 是不是真 Agent？

这是最容易被名字误导的地方。看 `@C:/user-data/code/ai/seahorse-agent/seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/mcp/KernelMcpOrchestrator.java:88-100`：

```java
public List<McpToolExecutionResult> executeTools(String question, List<IntentScore> mcpIntentScores) { ... }
```

调用点 `@C:/user-data/code/ai/seahorse-agent/seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/retrieval/KernelRetrievalEngine.java:238`：

```java
List<McpToolExecutionResult> results = mcpOrchestrator.executeTools(question, mcpIntents);
```

**特征**：
- `mcpIntents` 来自意图打分阶段，本质是"看哪个 `IntentNode.mcpToolId` 匹配子问题"
- 执行一次就把结果**塞回 `RetrievalContext.mcpContext`** 拼进 RAG Prompt
- 没有"模型看到工具结果后再决定是否调下一个工具"的循环
- 没有 OpenAI / Anthropic function-calling 协议层
- 没有 ReAct Thought/Action/Observation 输出

**所以**：当前的 MCP 是 **"工具型知识源"**，不是真正的 **"Agent 工具调用"**。要变成真 Agent，关键改造点是：

1. 在 `KernelChatPipeline` 之上加一个 `KernelAgentLoop`（或 `KernelToolUseOrchestrator`），由 LLM function-calling 决定调用什么工具。
2. `KernelMcpOrchestrator` 改成"被 ToolUseLoop 按需触发"，而不是"被检索阶段批量触发"。
3. 检索本身（vector/keyword/RRF/rerank）也注册为一个 `Tool`，叫 `search_knowledge_base`，让 LLM 决定要不要查、查几次。

---

## 4. 后续规划基线（Sequenced）

按"收益 / 成本"和"是否解锁后续能力"排序：

### Phase A — 把 Agent 编排层补上（解锁后续所有 Agent 能力）
1. 新增 kernel 模块 `application/agent/KernelAgentLoop`：实现 ReAct（Thought/Action/Observation）。
2. 新增 `ToolPort` 抽象，把现有的 MCP、检索、记忆封装成 Tool。
3. 在 `ChatModelPort` / `StreamingChatModelPort` 上加 `toolChoice` / `tools` 参数，对齐 OpenAI 兼容 function-calling。
4. `KernelChatPipeline` 增加策略开关：`mode=rag | agent | auto`，默认仍走 RAG（向后兼容）。

### Phase B — Agentic 检索（让 LLM 决定怎么查）
5. 把 `search_knowledge_base` / `search_web` / `query_metadata` 等做成 Tool。
6. 让 `KernelMultiChannelRetrievalEngine` 既可被 RAG 链路调用，也可被 AgentLoop 调用（提取出 retrieval-as-tool facade）。
7. 多步检索 + reflective rewrite。

### Phase C — Skill / 状态机（落地"需求分析 Agent"草稿）
8. 新增 `application/skill/SkillRegistry` + `SkillDefinition` + `PhaseHandler`。
9. 持久化状态机表 `seahorse_task_state` + 快照表 `seahorse_task_snapshot`（参见 `自定义AGENT接入.md`）。
10. SSE 双轨：展示轨 + 逻辑轨（前端断开仍保存）。
11. VCP 协议（前后端 Command/Variable）。

### Phase D — 输出可信度治理
12. `OutputValidatorPort` + `SelfHealingLoop`：JSON / Mermaid / DDL 校验 + LLM 反哺重试。
13. `RagEvaluator` 双模型策略：检索结果先打分再注入。
14. `ContextReducer` 视图切片，避免上下文爆炸。

### Phase E — 记忆闭环
15. 让 AgentLoop 在运行时调用 `memory_read` / `memory_write` 工具（而不是只在 Pipeline 起点 `activateMemory`）。
16. 接入冲突检测和高价值短期记忆晋升。

### Phase F — 企业治理
17. 检索权限 / 数据范围 / 多租户。
18. 知识图谱（条款层级、冲突、版本）。
19. 在线评测自动回流。

---

## 5. 判定标准（用于以后新增能力归类）

为避免之后又把"加一个 reranker"当成"Agent 能力提升"，固定如下口径：

| 是不是 Agent 能力 | 判定 |
|---|---|
| ✅ 真 Agent | LLM 在**运行时**根据观察决定下一步动作；存在 Thought/Action/Observation 循环；或带状态机的 multi-step 任务编排（Human-in-the-Loop）。 |
| 🟡 Agent 骨架 | 端口、Feature、数据模型已存在，但**调用方式仍是固定流水线**，LLM 无决策权。 |
| ❌ RAG 增强 | 任何在 "Query → Retrieve → Generate" 这条直线上加 Feature 的工作（包括 RRF、Rerank、Metadata 过滤、多通道、知识图谱、评测、视图切片用于 RAG 上下文等）。 |
| I 基础设施 | 不直接体现智能，只为上面任一类提供承载（缓存、消息、协调、可观测、Stream、SPI）。 |

> 一句口诀：**"如果把 LLM 拿掉，链路还能跑通"，那它就是 R 或 I；只有"LLM 在跑中改变了链路"才算 A。**

---

## 6. 与已有文档的关系

- `@C:/user-data/code/ai/seahorse-agent/缺少的功能.md`：用户视角的 backlog；本文件给出**架构视角的归类**，二者互补。
- `@C:/user-data/code/ai/seahorse-agent/自定义AGENT接入.md`：需求分析 Agent 的设计草稿，是 **Phase C** 的第一个落地候选。
- `@C:/user-data/code/ai/seahorse-agent/docs/Agent_Memory_系统改进设计方案.md` / `@C:/user-data/code/ai/seahorse-agent/docs/memory-system-improvement-plan.md`：记忆维度更细的子规划，对应 **Phase E**。
- `@C:/user-data/code/ai/seahorse-agent/docs/zh/content/架构设计/企业级可插拔RAG架构设计.md`：对应矩阵中 R 维度，已稳定。
- `@C:/user-data/code/ai/seahorse-agent/README.md`：建议在下次定位调整时，把"是一个 RAG 系统"改写为"是一个基于微内核 + 端口适配器的 Agent 工程平台，首发能力是 RAG 闭环"。

---

_最后更新：2026-05-19。新增/调整能力时请同步矩阵和分类。_
