# Seahorse Agent 设计文档 ↔ 代码对齐 Review 报告

- 日期：2026-05-24
- 范围：seahorse-agent 主分支（commit `614c0578`）对比 `docs/` 下的 Gemini 记忆设计、Agent capability phases、code-standard-review、hexagonal 架构约束
- 方法：四路并行 subagent 调查 + 关键点人工复核（refiner 实现、`DistributedLockPort` 存在性、`MemoryAggregationPolicy` 字段、controller `@ConditionalOnBean` 模式、god-class 行数）
- 结论速览：架构主干与设计意图基本一致；最大偏移在**类粒度的复杂度**（`DefaultMemoryEnginePort` 2178 行、starter 1075 行）和**Phase D 输出治理整段缺失**；其余多为"已落地但与文档措辞不一致"的小幅偏移，以及若干**文档本身已脱离当前代码事实**的失效条目。

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
| **Phase D 输出治理** | OutputValidatorPort + SelfHealingLoop + ContextReducer | **完全缺失** | ❌ Missing |
| Phase E 记忆工具回路 | memory_read/write/forget tools | 三个 ToolPortAdapter 齐全 | ✅ Aligned |
| Phase F 企业治理 | DataScopePolicy + 审计 + 评测候选采集 + KG 查询 | 审计已落（`ToolInvocationAuditEntry/Record` + `AuditPortWrapper`）；`DataScopePolicyPort` 未抽象；评测候选采集与 KG 查询工具缺失 | ⚠️ Partial |

---

## 2. 核心偏移（按修复价值排序）

### P0：Phase D 输出治理整段缺失 ❌

**事实：** `docs/agent-capability-phases/phase-d-output-governance-design.md` 列出 `OutputValidatorPort`、`OutputValidationResult`、`SelfHealingLoop`、`ContextReducerPort` 等核心端口；当前代码全部为空。

**影响：** 智能体输出无 schema 校验、无 self-healing、相位间 token 预算无缩减，企业级落地（要求 JSON/DDL/Mermaid 结构化产物）会直接撞墙。`agent-capability-phased-implementation-plan.md` 把 Phase D 标为 **P1 必交付**，但 plan 与代码事实存在断裂。

**建议：** 优先级最高的下一刀。最小 MVP 是：
1. `OutputValidatorPort` 接口 + `OutputValidationResult` 记录；
2. `JsonSchemaOutputValidator` 默认实现（kernel 内，不依赖 jakarta）；
3. `KernelAgentLoop` 在 finalize 步骤前增加 validate-then-self-heal 钩子（默认 noop）；
4. ContextReducer 可后置，但 Validator + 一次 self-heal 必须先有。

---

### P0：`DefaultMemoryEnginePort` 2178 行 god-class ❌

**事实：** `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`
- 同时实现 `MemoryEnginePort` + `MemoryIngestionWorkflowPort` 两个接口
- 构造函数（行 122–281）接收 15+ 端口
- 构造内联 `DefaultMemoryRetrievalPipeline`、extractor、assessor、sanitizer、prefilter、classifier、validator
- 单文件承担：聚合、提纯、写入、向量索引、profile 槽位、correction、business doc、retrieval 调度

**问题：** 违反 SRP / KISS / DIP；任何新增记忆轨（如 PROFILE 一等公民）都得直接改这个文件，OCP 被破坏；构造期依赖图复杂使得自动装配 starter 也水涨船高（1075 行）。

**建议：** 按职责拆分，目标 ≤ 500 行façade：
- `MemoryIngestionService`（提纯 → 路由 → 持久化）
- `MemoryProfileWriteService`（profile 槽位归一化）
- `MemoryVectorIndexingService`（outbox 派发）
- `MemoryEngineFacade`（仅做协作，不持有抽取/校验逻辑）

风险点：`MemoryCompactionServiceTests`/`DefaultMemoryEnginePortTests` 等被并行 PR 锁定，需要先与拥有者协调归属再动刀。

---

### P1：`MemoryLayer` 枚举只声明 4 层，但设计强调 7 层 ⚠️

**事实：** `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/memory/MemoryLayer.java` 只有 `WORKING / SHORT_TERM / LONG_TERM / SEMANTIC`；`PROFILE / CORRECTION / BUSINESS_DOCUMENT` 在 `MemoryContext` 上以独立字段呈现（`profileMemories` / `correctionMemories` / `businessDocumentMemories`）。

**两种合理设计的并存：**
- **设计文档**视它们为一等公民层，便于"任何记忆都能按 layer 归类、统一查询、统一权重"
- **当前代码**把它们当作"语义角色而非存储层"，承担"事实/纠错/外挂业务文档"等用途，与 4 层"基于时间/抽象度"的纵向分层正交

→ **这是一个值得 Review 的设计文档不合理点**：把"用途/角色"和"存储层"硬塞进同一个枚举会引入 Z 字形抽象。当前代码的做法（layer = 纵向时间维度，"角色" = MemoryContext 字段）反而更干净。

**建议：** 修订 `gemini-design.md` 中"七层"措辞，明确分**纵向 Layer**（时间/抽象度，枚举）与**横向 Track**（profile/correction/business-doc，字段或独立 Repository Port）。代码层不必动，但需要给出注释/文档解释这个二维度模型。

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

### P1：44 处 controller 重复 `"Service not available"` 样板 ⚠️

**事实：** 16 个 controller 累计 44 次出现 `Map.of("code", "1", "message", "Service not available")`；同时每个 controller 自己定义 `KEY_CODE`/`KEY_DATA`/`SUCCESS_CODE`/`ERROR_CODE` 常量。

**问题：** DRY 与 KISS 双重违反；新加 controller 拷贝同一段模板已成肌肉记忆。

**建议：** 在 adapter-web 内新增 `ApiResponse<T>` record + `ApiResponses.requireService(provider, accessor)` 工具方法。一次性替换 44 处。这是低风险高收益的切片，单 PR 可解决，不会触碰任何 kernel 逻辑。

---

### P2：`SeahorseAgentKernelMemoryAutoConfiguration` 1075 行 + 48+ `@Value` ⚠️

**事实：** 一个文件做了 70+ Bean 的装配，每个都散落 `@Value` 默认值。

**建议：** 引入 `MemoryProperties` `@ConfigurationProperties` 类，集中所有 `seahorse-agent.memory.*` 配置；其后再按"recall / aggregation / maintenance / outbox / review"拆 4–5 个子 `@Configuration`，每个 ≤ 300 行。可与 Property migration 一起做。

---

### P2：Port noop 退路的语义模糊 ⚠️

**事实：** 10+ 端口提供 `static noop()`（如 `MemoryAggregationBufferPort::noop`、`MemoryRefinerPort::noop`、`MemoryCompactionPort::noop`），starter 配置链式 `getIfAvailable(::noop)` 5–10 个端口降级到 noop。

**问题：**
- 优点：本地起服时不需要全套基础设施
- 缺点：生产环境若 misconfig 导致某个端口未注入，会**静默吃掉所有写入**而不是 fail-fast

**建议：** 二选一：
- **保留 noop 但增加 startup warning** —— 在 `@PostConstruct` 阶段，若某 noop 端口被使用且当前 profile 是 `prod`，打 WARN 日志
- **或者**给每个 noop 加上 `OBSERVATION_NOOP_HIT` 计数，dashboard 上一眼可见

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

| 顺序 | 切片 | 工作量 | 风险 | 价值 |
|------|------|--------|------|------|
| 1 | `ApiResponse<T>` + 替换 44 处 controller 样板 | 0.5 天 | 极低（纯重构） | 立竿见影改善可读性 |
| 2 | Phase D MVP：`OutputValidatorPort` + JSON schema validator + 一次 self-heal hook | 2–3 天 | 中（动到 KernelAgentLoop） | 解锁企业级输出场景 |
| 3 | `MemoryProperties` `@ConfigurationProperties` + starter 拆 4 个子 Configuration | 1–2 天 | 中（配置兼容性需保留） | 让 starter 重回可维护尺寸 |
| 4 | `DefaultMemoryEnginePort` 拆分（先与并行 PR 拥有者对齐） | 3–5 天 | 高（脏改协调） | 解锁后续记忆轨扩展 |
| 5 | 修订 Gemini 七层 → 4 layer × N track 二维度模型文档 | 0.5 天 | 极低 | 消除概念偏移 |
| 6 | `MemoryCaptureRuleProperties` + i18n 拒绝原因 enum | 1 天 | 低 | 国际化前置 |
| 7 | Phase C `SnapshotDiffAnalyzer` + 文档术语回写 | 2 天 | 中 | 真正实现 phase 智能失效 |

---

## 6. 评审证据快索引

| 主张 | 证据文件 |
|------|----------|
| MemoryLayer 仅 4 项 | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/memory/MemoryLayer.java:23-27` |
| DefaultMemoryEnginePort 2178 行 | `wc -l` 结果 |
| starter 1075 行 / 48+ `@Value` | 同上 |
| 44 处 "Service not available" | `grep -c` 跨 16 个 controller 总和 |
| Refiner 真实位置 | `seahorse-agent-adapter-ai-openai-compatible/src/main/java/com/miracle/ai/seahorse/agent/adapters/ai/openai/LlmMemoryRefinerAdapter.java` |
| DistributedLockPort 存在 | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/coordination/DistributedLockPort.java` |
| Redis scheduler + buffer 已落地 | commits `a5df98fd`、`c0a26ccf` |
| Channel attribution 已落地 | commit `f320af2a` |
| Golden harness 已落地 | commit `2dd25977` |
| Compaction observation 已落地 | commit `fb26a6e3` |
| Phase D 未落地 | `seahorse-agent-kernel` 全树搜索 `OutputValidatorPort` 无匹配 |
| 控制器 `@ConditionalOnBean` 直注入 | `SeahorseMetadataSchemaUsageController.java:41-44`、`SeahorseRetrievalEvaluationDatasetController.java:46-49` |
| MemoryAggregationPolicy 双触发字段齐全 | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/aggregation/MemoryAggregationPolicy.java:21-29` |

---

## 7. 备注

- 本报告与 `docs/aegis/work/2026-05-23-memory-gemini-handoff/HANDOFF.md` 互补：HANDOFF 记录"已完成的切片"，本报告记录"对齐度 + 余下偏移"
- 第 2 节 P0 两项（Phase D + god-class 拆分）建议作为下一个迭代的主线
- 第 3 节"设计文档自身不合理"列表请由架构组协商后再修订文档，不要 AI 自行改动 Gemini 系列文档
