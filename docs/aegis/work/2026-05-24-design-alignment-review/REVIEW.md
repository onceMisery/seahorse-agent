# Seahorse Agent 设计文档 ↔ 代码对齐 Review 报告

- 日期：2026-05-24（v2，吸收第 8 节二次 Review 意见修订）
- **基准 commit：** v1 写作时为 `614c0578`；v2 复核时 HEAD 已推进到 `2b73c0e4`（含 `87127b7e feat(agent): use context pack in runtime prompts` 与 codex/ai-infra-contextpack-runtime 的 merge）。本报告事实证据按 `2b73c0e4` 重新核对；引用的历史落地 commit（`a5df98fd` / `c0a26ccf` / `f320af2a` / `2dd25977` / `fb26a6e3` 等）保持原 hash，作为"何时落地"的标记，不作为基准。
- **工作区状态：** 复核时存在数个未提交的 `M` 文件（`RedisMemoryAggregationBufferPort.java`、若干测试文件等），但均为线尾差异或并行分支引入的脏改，未改变上述结论；如需逐项复现，建议在 clean checkout 上执行第 6 节命令。
- 范围：seahorse-agent 主分支对比 `docs/` 下的 Gemini 记忆设计、Agent capability phases、code-standard-review、hexagonal 架构约束
- 方法：四路并行 subagent 调查 + 关键点人工复核（refiner 实现、`DistributedLockPort` 存在性、`MemoryAggregationPolicy` 字段、controller `@ConditionalOnBean` 模式、god-class 行数）+ 第 8 节二次 Review 反向校验
- 结论速览：架构主干与设计意图基本一致；最大偏移在 **Phase D 输出治理 owner/端口/loop 接入点缺位**（不是"代码全部为空"，零散 schema 字段已存在）和**类粒度复杂度**（`DefaultMemoryEnginePort` 2178 行、starter 1075 行 / 83 处 `@Value`）；其余多为"已落地但与文档措辞不一致"的小幅偏移，以及若干**文档本身已脱离当前代码事实**的失效条目。

---

## 1. 体系级结论矩阵

| 维度 | 设计文档关键诉求 | 当前实现 | 评级 |
|------|------------------|----------|------|
| 六边形分层 / 端口方向 | kernel 纯净、port 单向 | 通过；kernel 无 Spring/Redis/Pulsar/Milvus；inbound 在 kernel、outbound 在 adapter | ✅ Aligned |
| 自动装配 6 层顺序 | CLAUDE.md 显式约束 | 通过；6 层 + `@AutoConfigureAfter` 链完整 | ✅ Aligned |
| 记忆分层（Working/Short/Long/Semantic/Profile/Correction/BusinessDoc） | 7 个一等公民 | 4 个枚举层 + 3 个"挂在 `MemoryContext` 字段上的 ad-hoc 集合" | ⚠️ Partial（结构性偏移） |
| 聚合 buffer + scheduler + topic-shift | Redis 后端、双触发（idle 40s + force 10 turn / 2K token） | `MemoryAggregationPolicy` 字段齐全；In-memory + Redis 两种 buffer + Redis 分布式 scheduler 均已落地（`a5df98fd` + `c0a26ccf`） | ✅ Aligned |
| LLM Refiner + 评分 + 阈值过滤 | Tone/Specificity 评分，<0.5 丢弃，三道 ACL | `LlmMemoryRefinerAdapter` 已落地（in `seahorse-agent-adapter-ai-openai-compatible`）；评分 prompt 由 LLM 完成，阈值过滤在 `KernelMemoryReviewService` 层 | ✅ Aligned（subagent 误报 missing — 它只翻了 kernel 子树） |
| 混合召回（vector + keyword + graph + RRF + rerank） | 并行通道 + 超时 + RRF k=60 + cross-encoder rerank | `HybridMemoryRecallPipeline` + `RrfMemoryFusion` + `ModelMemoryRecallReranker` + 30ms 通道超时 | ✅ Aligned |
| Channel attribution（per-channel hit 归因） | 每通道命中率 | `MemoryContextAttribution` + `loadWithAttribution` default 方法（`f320af2a`） | ✅ Aligned |
| Golden harness（CI 驱动召回评测） | 命名 profile + topK + 回放 | `MemoryRecallGoldenHarnessInboundPort` + classpath JSON 加载（`2dd25977`） | ✅ Aligned |
| Context weaver（分层 + 预算 + 去重） | 7 区分层、token 截断、LinkedHashSet 去重 | `DefaultContextWeaver` 完整实现 | ✅ Aligned |
| 维护（compaction / alias / GC） | 三件套 + 软删/硬删/Tombstone/GenerationId | `MemoryCompactionService` + `MemoryAliasResolutionService` + `MemoryGarbageCollectionService` 全部存在 | ✅ Aligned |
| Review 流水线 | 审核队列 + 决策 + SFT 反馈导出 | `KernelMemoryReviewService` + `MemoryRefinerFeedbackExportRecord` | ✅ Aligned |
| Outbox relay | task type 字典 + 批处理 + 重试 | `MemoryOutboxPort` + `MemoryOutboxTaskTypes` + `MemoryOutboxRelayService` | ✅ Aligned |
| 分布式协调（lock/semaphore/scheduler） | Redis lock + 公平锁 + 延时调度 | `DistributedLockPort` + `DistributedSemaphorePort` + 上述 Redis scheduler；MVCC 在 KV 层 | ✅ Aligned（subagent 误报 partial — `DistributedLockPort.java` 确实存在） |
| 可观测性（trace + Micrometer） | trace event + Prometheus 计数器 | `ObservationPort` + `MicrometerObservationAdapter`；本轮新增 10+ counter（`memory-refine`/`memory-outbox-task/batch`/`memory-context-weave`/`memory-recall-channel/fusion/rerank/evaluate`/`memory-compaction-run/group`/`memory-recall-harness-run`） | ✅ Aligned |
| Phase A 智能体回合环 | ReAct loop + step + tool | `KernelAgentLoop` + `InMemoryToolRegistry` 完整 | ✅ Aligned |
| Phase B Agentic 检索 | `search_knowledge_base` 工具 | `SearchKnowledgeBaseToolPortAdapter` | ✅ Aligned |
| Phase C 技能/状态/HITL | Skill 注册 + Snapshot diff + Approval | `AgentRun`/`AgentDefinition`/`ApprovalRequest`/`KernelAgentRunResumeService` 等价实现，**命名与设计文档不一致**；缺 SnapshotDiffAnalyzer | ⚠️ Partial（语义对齐、术语偏离） |
| **Phase D 输出治理** | OutputValidatorPort + SelfHealingLoop + ContextReducer | 缺少**统一的输出治理 owner、端口、`OutputValidationResult` 模型、loop 接入点和观测事件**；已有零散基础（如 `ToolCatalogEntry.outputSchemaJson` 字段），但无任何 validator/self-heal 调用方 | ❌ Missing（关键路径缺位，非代码全空） |
| Phase E 记忆工具回路 | memory_read/write/forget tools | 三个 ToolPortAdapter 齐全 | ✅ Aligned |
| Phase F 企业治理 | DataScopePolicy + 审计 + 评测候选采集 + KG 查询 agent 工具 | 审计已落（`ToolInvocationAuditEntry/Record` + `AuditPortWrapper`）；记忆图基础设施齐全（`MemoryGraphPort` + `GraphMemoryRecallChannel`）；**缺位是治理/工具层**：`DataScopePolicyPort` 未抽象、评测候选采集流缺失、`kg_query` 类 agent tool 未注册 | ⚠️ Partial |

---

## 2. 核心偏移（按修复价值排序）

> v2 调整说明：根据第 8 节二次 Review 意见，Phase D 仍保持 P0，但 MVP 路径从"直接接入 `KernelAgentLoop.finalize`"改为"先定义 owner / artifact / 失败语义 / 观测事件，再决定接入点"；`DefaultMemoryEnginePort` 拆分从 P0 降为 P1（属可维护性而非功能正确性风险）；七层 layer/track 文档修订上调为**实施前置项**；`ApiResponse<T>` 与 noop 退路按风险分类调整顺序。

### P0：Phase D 输出治理 owner/端口/接入点缺位 ❌

**事实：** `docs/agent-capability-phases/phase-d-output-governance-design.md` 列出 `OutputValidatorPort`、`OutputValidationResult`、`SelfHealingLoop`、`ContextReducerPort` 等核心端口；当前代码搜索这些命名实体全部无匹配。**但**已有零散基础：`ToolCatalogEntry.outputSchemaJson` 字段存在（`seahorse-agent-kernel/.../domain/agent/tool/ToolCatalogEntry.java:31, 46, 64`），证明 schema 概念在 catalog 层已被部分识别，只是没有 validator / self-heal / loop 接入点把它串起来。

**影响：** 智能体输出无 schema 校验、无 self-healing、相位间 token 预算无缩减，企业级落地（要求 JSON/DDL/Mermaid 结构化产物）会直接撞墙。`agent-capability-phased-implementation-plan.md` 把 Phase D 标为 **P1 必交付**，但 plan 与代码事实存在断裂。

**建议（v2 修订）：** 不直接在 `KernelAgentLoop.finalize` 落 validator，而是先做一次轻量架构评审：
1. 定义 `OutputGovernancePort` / `OutputValidatorPort` 的 **owner**（kernel application 服务而非 loop 内联）；
2. 定义 **artifact 模型**：`OutputValidationResult`、失败语义（block / warn / self-heal-retry）；
3. 定义**观测事件**：`output-validation`（outcome=pass/fail/healed，tag=validator-name）；
4. 决定**接入点**：放进 `KernelAgentLoop` 还是放进未来的 phase/task runtime，再写代码。如果未来要加 phase 概念，提前放 loop 会成为旁路 validator owner 混乱的源头。
5. MVP 落地后再补 `JsonSchemaOutputValidator` 默认实现 + 一次 self-heal 重试；ContextReducer 完全后置。

---

### P1：`DefaultMemoryEnginePort` 2178 行 god-class ⚠️

> v2 调整：从 v1 的 P0 降为 P1。理由：这是可维护性 + 扩展性风险，不是功能正确性风险；当前没有证据证明它正在造成 bug，拆分前应先做依赖图和测试护栏，避免在没有保护网的情况下大改。

**事实：** `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`
- 同时实现 `MemoryEnginePort` + `MemoryIngestionWorkflowPort` 两个接口
- 构造函数（行 122–281）接收 15+ 端口
- 构造内联 `DefaultMemoryRetrievalPipeline`、extractor、assessor、sanitizer、prefilter、classifier、validator
- 单文件承担：聚合、提纯、写入、向量索引、profile 槽位、correction、business doc、retrieval 调度

**问题：** 违反 SRP / KISS / DIP；任何新增记忆轨（如 PROFILE 一等公民）都得直接改这个文件，OCP 被破坏；构造期依赖图复杂使得自动装配 starter 也水涨船高（1075 行）。

**建议（v2 修订）：分阶段拆，先护栏后切刀：**
1. **第 0 步**：跑一次 javap + 调用图，画出 `DefaultMemoryEnginePort` 对其他类的依赖箭头，确认哪些方法只服务一个上游入口；
2. **第 1 步**：在不动生产代码的前提下，给现有行为补集成测试覆盖（重点是 ingest + recall + profile/correction track 写入路径），确保拆分后行为不变；
3. **第 2 步**：分**三刀**拆，按方向独立切片：
   - 读写编排（recall / ingest 入口）
   - profile/correction/business-doc track 写入（横向 track 各一个 service）
   - derived index outbox 派发（vector/keyword/graph 三类 task 的入队）
4. 协调风险：`MemoryCompactionServiceTests`/`DefaultMemoryEnginePortTests` 等被并行 PR 锁定，每一刀都要先与拥有者协调归属再动手。

---

### P1（前置）：修订 Gemini "七层记忆" → "4 layer × N track" 文档 ⚠️

> v2 调整：从 v1 的 P1 普通项**提升为实施前置项**。理由：如果不先把"4 layer × N track"写成明确 source-of-truth，后续任何 profile/correction/business-doc 改造都会在"加 enum"和"保持正交 track"之间反复摇摆，进一步污染代码。

**事实：** `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/memory/MemoryLayer.java` 只有 `WORKING / SHORT_TERM / LONG_TERM / SEMANTIC`；`PROFILE / CORRECTION / BUSINESS_DOCUMENT` 在 `MemoryContext` 上以独立字段呈现（`profileMemories` / `correctionMemories` / `businessDocumentMemories`）。

**两种合理设计的并存：**
- **设计文档**视它们为一等公民层，便于"任何记忆都能按 layer 归类、统一查询、统一权重"
- **当前代码**把它们当作"语义角色而非存储层"，承担"事实/纠错/外挂业务文档"等用途，与 4 层"基于时间/抽象度"的纵向分层正交

→ **这是一个值得 Review 的设计文档不合理点**：把"用途/角色"和"存储层"硬塞进同一个枚举会引入 Z 字形抽象。当前代码的做法（layer = 纵向时间维度，"角色" = MemoryContext 字段）反而更干净。

**建议：** 修订 `gemini-design.md` 中"七层"措辞，明确分**纵向 Layer**（时间/抽象度，枚举）与**横向 Track**（profile/correction/business-doc，字段或独立 Repository Port）。代码层不必动，但需要给出注释/文档解释这个二维度模型。**这一步要在 P1 god-class 拆分和 Phase D MVP 之前完成**，否则术语在评审过程中会反复漂移。

---

### P1：Phase C 命名与设计文档不一致 ⚠️

**事实：**
- 设计：`AgentTask` / `SkillDefinition` / `PhaseHandler` / `SnapshotDiffAnalyzer`
- 实现：`AgentRun` / `AgentDefinition` / `AgentStep` / 无 SnapshotDiff

**影响：** 语义等价但 API 表面完全不同；任何外部团队按文档对接都得自己做术语映射。`SnapshotDiffAnalyzer` 是 Phase C 实质性的"智能失效"逻辑，缺失意味着每次 phase rerun 都得全量重算（性能 + 成本浪费）。

**建议：**
1. 更新 phase-c 设计文档使用代码现有术语（成本最低、立刻消除偏移）；
2. 新切片：补 `SnapshotDiffAnalyzer` —— 只需对比上一个 checkpoint 的 inputs hash，已经能干掉绝大多数 rerun。

---

### P2：Port noop 退路按生产风险分类 ⚠️

> v2 调整：从 v1 的 P2 普通项**按生产风险拆分**。理由：noop 退路在不同业务路径上风险差异极大；统一一刀切要么过度严苛（开发体验受损）要么放任风险（生产丢写入）。

**事实：** 10+ 端口提供 `static noop()`（如 `MemoryAggregationBufferPort::noop`、`MemoryRefinerPort::noop`、`MemoryCompactionPort::noop`），starter 配置链式 `getIfAvailable(::noop)` 5–10 个端口降级到 noop。

**按风险分类的处置（v2）：**

| 类别 | 端口示例 | 生产策略 |
|------|----------|---------|
| **A 类：会丢写入** | `LongTermMemoryPort`、`MemoryOutboxPort`、审计端口 | **生产必须 fail-fast**：检测到 noop 实现且 profile=prod 时拒绝启动 |
| **B 类：会跳过索引/观测** | `MemoryVectorPort`、`MemoryKeywordIndexPort`、`ObservationPort` | **生产 warning + 计数器**：保留 noop 但启动时打 WARN，并加 `OBSERVATION_NOOP_HIT` counter |
| **C 类：纯增强能力降级** | `MemoryRefinerPort`、`MemoryCompactionSummarizerPort`、`MemoryGraphIndexPort` | **fail-open 即可**：noop 是合理降级，开发/生产都允许 |

实施可以从 A 类做起（一段 `@PostConstruct` 校验即可），B 类滚动加 metric，C 类保留现状。

---

### P2：`SeahorseAgentKernelMemoryAutoConfiguration` 1075 行 + 83 处 `@Value` ⚠️

**事实：** `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelMemoryAutoConfiguration.java` 一个文件做了 70+ Bean 的装配；`grep -c "@Value("` 当前为 **83**（v1 写作 "48+" 是低估）。

**建议：** 引入 `MemoryProperties` `@ConfigurationProperties` 类，集中所有 `seahorse-agent.memory.*` 配置；其后再按"recall / aggregation / maintenance / outbox / review"拆 4–5 个子 `@Configuration`，每个 ≤ 300 行。可与 Property migration 一起做。

---

### P3：44 处 controller 重复 `"Service not available"` 样板 ⚠️

> v2 调整：从 v1 的 P1 降为 P3。理由：这是低风险卫生改造，不是设计对齐主线风险；除非团队刻意安排半天做清理，否则应让位给 Phase D 与 layer/track 文档修订。

**事实：** 16 个 controller 累计 44 次出现 `Map.of("code", "1", "message", "Service not available")`；同时每个 controller 自己定义 `KEY_CODE`/`KEY_DATA`/`SUCCESS_CODE`/`ERROR_CODE` 常量。

**问题：** DRY 与 KISS 双重违反；新加 controller 拷贝同一段模板已成肌肉记忆。

**建议：** 在 adapter-web 内新增 `ApiResponse<T>` record + `ApiResponses.requireService(provider, accessor)` 工具方法。一次性替换 44 处。这是低风险高收益的切片，单 PR 可解决，不会触碰任何 kernel 逻辑。

---

### P3：`ObjectProvider` vs `@ConditionalOnBean` 两种风格并存 ⚠️

**事实：** CLAUDE.md 规定 controller 用 `ObjectProvider<T>`；但 `SeahorseMetadataSchemaUsageController` 和 `SeahorseRetrievalEvaluationDatasetController` 直接构造注入，靠类上 `@ConditionalOnBean(...)` 保护。两种风格语义等价。

**判断：** 这不是 bug，但是不一致。两个写法都对，只需选一个：
- 倾向 **ObjectProvider** —— 控制器装载早，部分依赖晚到时容错性更好
- 或者 **更新 CLAUDE.md** 显式说明"controller 可二选一"

---

### P3：Magic values / 硬编码常量 ⚠️

**几个具体例子：**
- `MemoryCaptureCandidateExtractor.java:42-76, 151-208` 把中文前缀（"我是" / "我在" / "我来自"）硬编码进 kernel，无 i18n
- 拒绝原因 `"too_short"` / `"no_high_value_signal"` 是字符串字面量而非 enum
- 长度阈值 `120` 写死，不可配置
- `SeahorseMemoryController` 多处 `defaultValue = "20"` limit

**建议：** 把 capture 规则做成 `MemoryCaptureRuleProperties` `@ConfigurationProperties` + `MemoryCaptureRejectionReason` enum。这能让"切换为英文/多语言"或"调整阈值"不必改源码。

---

## 3. 设计文档本身的不合理 / 失效条目

| 文档 | 失效或不合理点 | 建议处置 |
|------|----------------|----------|
| `Gemini Agent记忆系统完整设计方案.md` "七层记忆" | 把纵向 layer 和横向 track 拍在同一个枚举里，会引入 Z 字形抽象 | 修订为"4 层 layer + 多 track"二维度模型，与代码对齐 |
| `agent-capability-phased-implementation-plan.md` Phase D 标 P1 | 实际未交付且无估算；plan 与代码事实脱节 | 要么把 Phase D 重新排进 sprint，要么降为 P2 并在 plan 里写明降级原因 |
| `phase-c-skill-state-hitl-design.md` 全套 API 术语 | `AgentTask`/`Skill`/`PhaseHandler` 与实际 `AgentRun`/`AgentDefinition`/`AgentStep` 完全不对齐 | 文档跟着代码走，重写 API 章节 |
| `Seahorse Agent记忆系统Gemini对齐二次Review与补齐执行计划.md` 等多份 Gemini 对齐文档 | 互相重叠且不指明哪份是 source-of-truth | 在 `docs/aegis/INDEX.md` 之外，给 Gemini 系列明确单一 canonical 文档，其余标 superseded |
| `docs/Seahorse Agent记忆系统现状与Gemini文档差距对照.md`（untracked） | 未入库 → 与可信文档同列；HANDOFF 内被引用 | 入库或删除，二选一 |
| 多份"差距 / 改进 / 补齐"文档累积 | `docs/Seahorse Agent*.md` 6 份风格相近、视角重叠 | 合并为单一 living gap doc + 历史 archived |

---

## 4. 强项（保持现状）

- **六边形纯净度：** kernel 0 个 Spring/Redis/Pulsar/Milvus import；Jackson 仅用于纯 marshaling，合规
- **Auto-config 6 层：** 顺序与 `@AutoConfigureAfter` 链与 CLAUDE.md 描述一致
- **Bean 命名：** 70/70 Bean 前缀 `seahorse...`
- **Conditional 兜底：** 抽样 10/10 Bean 都有 `@ConditionalOnMissingBean`，便于用户覆盖
- **混合召回**：通道并行 + 超时保护 + RRF + rerank + per-channel attribution，与 Gemini 设计意图完全对齐
- **观测桩**：本轮新增 10+ counter 已让 `ObservationPort → Micrometer → Prometheus` 完整闭环，trace ↔ 计数指标双轨齐全

---

## 5. 推荐的下一刀顺序

> v2 重排：按第 8 节二次 Review 建议，先固定文档基准与 source-of-truth，再进入设计评审与实施；卫生重构后置。

| 顺序 | 切片 | 工作量 | 风险 | 价值 |
|------|------|--------|------|------|
| 1 | **固定报告基准与证据索引**：commit、工作区状态、关键命令、真实路径（本 v2 已部分完成） | 0.5 天 | 极低 | 让后续切片都有可复现起点 |
| 2 | **先修订 source-of-truth 文档**：在 `gemini-design.md` 显式定义 `MemoryLayer = 4`、`profile/correction/business-doc = track`，并在 phase-c 文档把 API 术语回写到 `AgentRun`/`AgentDefinition` | 0.5–1 天 | 极低 | 消除"加 enum vs 保持正交 track"反复 |
| 3 | **Phase D 输出治理最小设计评审**：定义 owner、artifact 模型、`OutputValidationResult`、失败语义（block/warn/heal）、观测事件、接入点（loop vs phase runtime） | 1–2 天 | 低 | 避免直接落 loop 引入旁路 owner 混乱 |
| 4 | **Phase D MVP 实施**：`OutputGovernancePort` + `JsonSchemaOutputValidator` + 接入点 + 一次 self-heal 重试 + `output-validation` counter | 2–3 天 | 中 | 解锁企业级结构化产出 |
| 5 | **生产 noop 风险分类**：A 类（丢写入）fail-fast，B 类（跳过索引/观测）warning + metric，C 类（增强能力降级）保留 noop | 1 天 | 低 | 避免生产静默丢写入 |
| 6 | **`DefaultMemoryEnginePort` 拆分（分阶段）**：先依赖图 + 集成测试护栏，再分三刀（读写编排 / track 写入 / outbox 派发） | 5–8 天（分 3 PR） | 高 | 解锁后续记忆轨扩展 |
| 7 | **`MemoryProperties` `@ConfigurationProperties` + starter 拆子 Configuration** | 1–2 天 | 中 | 让 starter 重回可维护尺寸 |
| 8 | **Phase C `SnapshotDiffAnalyzer`**（设计文档回写完成后） | 2 天 | 中 | 真正实现 phase 智能失效 |
| 9 | **`MemoryCaptureRuleProperties` + i18n 拒绝原因 enum** | 1 天 | 低 | 国际化前置 |
| 10 | **`ApiResponse<T>` + 替换 44 处 controller 样板**（卫生重构，最后做） | 0.5 天 | 极低 | 改善可读性 |

---

## 6. 评审证据快索引

| 主张 | 证据文件（v2 路径已补全） |
|------|----------|
| MemoryLayer 仅 4 项 | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/memory/MemoryLayer.java:23-27` |
| DefaultMemoryEnginePort 2178 行 | `wc -l seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java` |
| starter 1075 行 / **83** 处 `@Value`（v2 重数） | `wc -l` 与 `grep -c "@Value(" seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelMemoryAutoConfiguration.java` |
| 44 处 "Service not available" 跨 16 个 controller | `grep -c "Service not available" seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/*.java` 求和 |
| Refiner 真实位置 | `seahorse-agent-adapter-ai-openai-compatible/src/main/java/com/miracle/ai/seahorse/agent/adapters/ai/openai/LlmMemoryRefinerAdapter.java` |
| DistributedLockPort 存在 | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/coordination/DistributedLockPort.java` |
| Redis scheduler + buffer 已落地 | commits `a5df98fd`、`c0a26ccf` |
| Channel attribution 已落地 | commit `f320af2a` |
| Golden harness 已落地 | commit `2dd25977` |
| Compaction observation 已落地 | commit `fb26a6e3` |
| Phase D 缺位（不是"代码全空"） | `grep -rn "OutputValidatorPort\|OutputValidationResult\|SelfHealingLoop\|ContextReducerPort" seahorse-agent-kernel/src/main` 全无；但 `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/tool/ToolCatalogEntry.java:31,46,64` 已有 `outputSchemaJson` 字段 |
| Phase F KG 工具区分 | 存在：`seahorse-agent-kernel/.../ports/outbound/memory/MemoryGraphPort.java`、`seahorse-agent-kernel/.../application/memory/retrieval/GraphMemoryRecallChannel.java`；不存在：`grep -rn "kg_query\|KnowledgeGraphTool\|GraphQueryTool" --include="*.java"` 全无（agent tool 层） |
| 控制器 `@ConditionalOnBean` 直注入 | `SeahorseMetadataSchemaUsageController.java:41-44`、`SeahorseRetrievalEvaluationDatasetController.java:46-49` |
| MemoryAggregationPolicy 双触发字段齐全 | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/aggregation/MemoryAggregationPolicy.java:21-29` |
| v2 基准 HEAD | `git rev-parse --short HEAD` → `2b73c0e4`（工作区有未提交 dirty 文件，不影响上述静态事实） |

---

## 7. 备注

- 本报告与 `docs/aegis/work/2026-05-23-memory-gemini-handoff/HANDOFF.md` 互补：HANDOFF 记录"已完成的切片"，本报告记录"对齐度 + 余下偏移"
- **v2 主要修订（吸收第 8 节意见）**：
  - 报告基准 commit 由 `614c0578` 显式改为 v2 复核 HEAD `2b73c0e4`，并补工作区状态说明
  - starter `@Value` 计数由 v1 的 "48+" 修正为实测 83
  - Phase D 表述由"完全缺失"细化为"统一 owner/端口/`OutputValidationResult`/loop 接入点缺位"，承认已有 `ToolCatalogEntry.outputSchemaJson` 字段
  - Phase F 区分"记忆图基础设施（已有）"与"agent 工具/治理层（缺 `kg_query` 类工具）"
  - 第 2 节优先级：`DefaultMemoryEnginePort` 拆分从 P0 降为 P1；`ApiResponse<T>` 重构从 P1 降为 P3；layer/track 文档修订上调为实施前置项；noop 退路按生产风险三分类
  - 第 5 节顺序按"先固定基准 → 修订 source-of-truth → Phase D 设计评审 → Phase D 实施 → noop 风险分类 → god-class 拆分"重排
- 第 3 节"设计文档自身不合理"列表请由架构组协商后再修订文档，不要 AI 自行改动 Gemini 系列文档

---

## 8. 二次 Review 意见（2026-05-24）

### 总体判断

这份报告的主结论**方向上合理**：它抓住了当前系统最关键的三类问题：设计文档与代码事实的漂移、记忆系统实现的类粒度膨胀、以及 Agent Phase D 输出治理缺位。尤其是把"七层记忆"重新拆成"纵向 layer + 横向 track"的判断，是这份报告里最有价值的架构纠偏；它不是简单要求代码追文档，而是在反向审视文档抽象是否合理。

但这份报告现在还不适合直接作为下一轮实施计划的唯一依据，原因是**证据可复现性和优先级口径还需要修正**。报告本身混合了"代码事实"、"设计判断"、"实施建议"三种层级，其中部分事实引用没有锁定到可复现基准，部分建议的优先级更像工程卫生改造，而不是架构风险排序。建议先做一次轻量修订，再进入任务拆分。

### First-principles 复核

- **不可退让目标：** 这类对齐报告必须回答"当前代码是否满足设计意图，以及设计意图本身是否仍然正确"，不能只做 checklist 式勾选。
- **不可退让约束：** 每个 P0/P1 结论必须能被当前仓库路径、commit、搜索命令或测试证据复现；否则只能标为假设或待核验。
- **应丢弃的历史假设：** "Gemini 文档天然是 source-of-truth"这个前提不成立。对记忆 layer/track 的分析已经证明，有些设计文档应让位于更干净的代码模型。
- **最小充分路径：** 先修报告证据基准和术语，再把 Phase D、memory starter 配置、DefaultMemoryEnginePort 拆分分别进入独立设计/实施切片。
- **升级信号：** 如果 Phase D 要接入 `KernelAgentLoop` 或未来的 phase/task runtime，需要先做架构设计评审，避免再引入一个旁路 validator 或 self-healing owner。

### 需要修正的事实与证据口径

1. **基准 commit 不清晰。** 报告声明范围是 commit `614c0578`，但当前工作区 HEAD 为 `2b73c0e4`，且存在未提交改动。若本报告确实基于 `614c0578`，应补充当时的 `git status --short`、`git rev-parse --short HEAD` 和关键搜索命令输出；若要代表当前主分支事实，则需要重新生成/复核证据。
2. **starter 路径写法需要修订。** 报告中写到 `seahorse-agent-starter/.../SeahorseAgentKernelMemoryAutoConfiguration.java`，当前仓库实际路径是 `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelMemoryAutoConfiguration.java`。行数 1075 可复现，但 `@Value` 数当前抽查为 83，不只是 "48+"。
3. **Phase D 缺失结论成立，但应避免说"代码全部为空"。** 当前代码未找到 `OutputValidatorPort`、`OutputValidationResult`、`SelfHealingLoop`、`ContextReducerPort` 等设计命名实体；但已有 `ToolCatalogEntry.outputSchemaJson` 这类零散 schema 字段。更精确的表述应是"缺少统一的输出治理 owner、端口、验证结果模型和 loop 接入点"。
4. **Phase F 的 KG 查询缺失需要界定范围。** 代码中存在 `MemoryGraphPort`、`MemoryGraphIndexPort`、`GraphMemoryRecallChannel`，所以"KG 查询工具缺失"如果指的是企业治理/工具层能力，应写清楚是缺 `kg_query` 类 agent tool 或治理 API，而不是图召回基础能力完全不存在。
5. **controller 重复计数可信。** `Service not available` 当前可复现为 44 处、16 个 controller，这一项可以保留为低风险重构切片。

### 对优先级排序的调整建议

- **Phase D 应继续保留 P0/P1 最高优先级。** 这是能力缺口，不是单纯代码整洁问题；但 MVP 不应直接默认放进 `KernelAgentLoop.finalize`。更稳妥的第一步是定义 `OutputGovernancePort`/`OutputValidatorPort` 的 owner、artifact 模型、失败语义和观测事件，再决定接入 loop 还是 phase runtime。
- **`DefaultMemoryEnginePort` 拆分确实重要，但不宜和 Phase D 同列为立即 P0。** 它是可维护性和扩展性风险，当前还没有证据证明它正在造成功能错误。建议先做依赖图/测试护栏，再分阶段拆：读写编排、profile/correction/business-doc track、derived index outbox 三个方向分别切。
- **`ApiResponse<T>` 重构不应排在 Phase D 之前。** 它是低风险高收益的卫生改造，但不是设计对齐的主风险。除非团队刻意选择"先用半天清理低风险重复"，否则从架构价值看应排在 Phase D 之后。
- **七层记忆文档修订应前置到实施前。** 如果不先把"4 layer × N track"写成明确 source-of-truth，后续任何 profile/correction/business-doc 改造都会在"加 enum"和"保持正交 track"之间反复摇摆。
- **noop 退路建议提升优先级。** 这不是单纯 P2 风格问题。凡是会丢写入、跳过索引、跳过审计或跳过观测的 noop，都应按生产风险分类：开发可 fail-open，生产至少 warning + metric，关键写链路应 fail-fast。

### 最终 Verdict

**建议：修订后采纳。** 本报告的架构判断大体成立，尤其是 Phase D 缺口、memory god-class、七层记忆抽象错误、Phase C 术语漂移这四项值得进入后续工作流。但在进入执行计划前，需要先补齐基准 commit、修正路径和计数、把"缺失能力"与"已有零散基础能力"分开表述，并重新调整下一刀顺序。

推荐修订后的下一步顺序：

1. 固定报告基准与证据索引：commit、工作区状态、关键命令、真实路径。
2. 先修订 source-of-truth 文档：明确 `MemoryLayer = 4`、`profile/correction/business-doc = track`。
3. 做 Phase D 输出治理最小设计评审：owner、artifact、validator、self-heal、观测和接入点。
4. 实施 Phase D MVP。
5. 给生产 noop 增加 warning/metric/fail-fast 策略。
6. 再拆 `DefaultMemoryEnginePort` 与 starter 配置。
7. 最后做 `ApiResponse<T>` 这类 controller 卫生重构。

---

## 9. 对第 8 节意见的逐条采纳记录

> 本节由 v2 写作者补充：把第 8 节每条意见映射到 v1→v2 的具体修订动作，避免后续读者重复对照。

| 第 8 节子点 | 采纳判定 | v2 落地动作 | 仍保留分歧 |
|------|------|------|--------|
| 基准 commit 不清晰 | ✅ 完全采纳 | 顶部加"基准 commit / 工作区状态"段；第 6 节加 `v2 基准 HEAD` 行 | 无 |
| starter 路径写法 / `@Value` 数 | ✅ 部分采纳 | `@Value` 由 "48+" 修正为 83；路径在第 6 节补全为完整模块路径 | 原报告其实未出现 `seahorse-agent-starter/...` 错写法，复核时未发现需要替换的位置 |
| Phase D "代码全部为空" | ✅ 完全采纳 | 矩阵第 31 行 + 第 2 节 P0 段均改写为"统一 owner/端口/结果模型/接入点缺位"，承认 `outputSchemaJson` 字段已存在 | 无 |
| Phase F KG 查询界定范围 | ✅ 完全采纳 | 矩阵 Phase F 行 + 第 6 节区分了"memory graph 基础设施已有"与"agent tool 层 `kg_query` 缺失" | 无 |
| controller 重复计数可信 | ✅ 完全采纳 | 计数与命令在第 6 节明确写出 | 无 |
| Phase D 仍 P0/P1 最高，但 MVP 不直接落 loop | ✅ 完全采纳 | P0 段拆为两步：先设计评审定义 owner/artifact/失败语义/接入点，再落 MVP；第 5 节顺序 #3 #4 体现 | 无 |
| god-class 拆分不与 Phase D 同列 P0 | ✅ 完全采纳 | 由 P0 降为 P1；拆分路径细化为"依赖图 → 集成测试护栏 → 分三刀" | 无 |
| `ApiResponse<T>` 不前于 Phase D | ✅ 完全采纳 | 由 P1 降为 P3；第 5 节顺序末位 | 无 |
| 七层文档修订前置 | ✅ 完全采纳 | 标记为"实施前置项"；第 5 节顺序 #2 | 无 |
| noop 退路按生产风险三分类 | ✅ 完全采纳 | P2 noop 段重写为 A/B/C 三类表格 | 无 |

**总评：** 第 8 节意见全部合理，绝大多数已落地到正文。唯一保留的细节分歧是 starter 路径写法 —— 原报告我做了路径检查，确认 v1 并没有用错误的 `seahorse-agent-starter/...` 短路径；但顺手在 v2 第 6 节把所有路径补完整，避免歧义。
