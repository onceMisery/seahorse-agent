# Seahorse Agent 记忆系统 Gemini 对齐二次 Review 与补齐执行计划

> 日期：2026-05-21
> 基线文档：`docs/Seahorse Agent记忆系统差距分析与Gemini对齐改进方案.md`
> 当前实现基线：`main` 分支提交 `1e484a3 feat(memory): align Seahorse memory with Gemini design`

## 1. Review 结论

当前 Seahorse Agent 已经从“规则捕获 + 三层表读取”推进到“Profile KV + Correction Ledger + Ingestion Workflow + Router + Context Weaver + Outbox + 生命周期字段”的架构形态，核心方向与 Gemini 方案一致，但仍不能判定为完全对齐。

本次二次 Review 发现的主要差距不是“缺少文件名或表名”，而是部分能力仍停留在轻量骨架：

1. 业务文档轨道在 `MemoryTrack` 中存在，但 `MemoryContext` 没有独立 business document list，读取结果被混入 `semanticMemories`，Context Weaver 只能输出 `[Business / Semantic Memory]` 混合区。
2. Correction Ledger 已经最高优先注入，但读取管道没有基于 correction target 对冲突 Profile 或旧碎片做硬压制。若 Correction 指向 `identity.occupation=teacher`，仍可能同时织入 active Profile `student`。
3. Context Weaver 已经有分区和总预算，但条目缺少来源元数据、slot/doc/version/generation 等说明，不满足 Gemini 方案对“可溯源、可版本化、冲突说明”的要求。
4. Retrieval Pipeline 已独立，但 `DefaultMemoryEnginePort` 仍残留一批旧读取辅助方法，形成双 owner 的维护风险。
5. 写入工作流具备 sanitizer、pre-filter、classifier、schema validator 和 operation log，但 LLM Refiner、复杂跨轮聚合、REVIEW 人工闭环仍未落地。这部分依赖后续模型调用与管理端能力，不适合在本次小步补齐中伪装完成。
6. Vector/BM25/Graph 真实多路召回仍是接口和 noop/轻量适配阶段，当前只能保证 outbox 和 generation 字段准备好，不能声称完整达到 Gemini 的多索引召回标准。

## 2. 已对齐项

| Gemini 要求 | 当前 Seahorse 状态 | 判定 |
| --- | --- | --- |
| Profile KV 强事实源 | `t_user_profile_fact`、`ProfileMemoryPort`、版本历史、read feedback 已落地 | 基本对齐 |
| Correction Ledger Ring 0 | `t_memory_correction_ledger`、`CorrectionLedgerPort`、Prompt 优先注入已落地 | 部分对齐，缺硬压制 |
| 写入由工作流拥有最终权威 | `MemoryIngestionWorkflowPort` 由 engine 实现，LLM/工具只提交 command，operation log 记录决策 | 基本对齐 |
| Router 按问题激活轨道 | `DefaultMemoryRouter` 支持 Correction/Profile/Episodic/Business/Short Window | 基本对齐 |
| Context Weaver 分区与预算 | `DefaultContextWeaver` 支持优先级 zone 和 `ContextBudget` | 部分对齐，缺元数据 |
| Outbox 派生索引补偿 | `MemoryOutboxPort`、`MemoryOutboxRelayService`、Spring job 已落地 | 部分对齐，真实向量 adapter 仍待接入 |
| 生命周期字段与读反馈 | Profile、分层表、vector 表具备状态/引用字段，读取期记录 feedback | 部分对齐 |

## 3. 本次补齐范围

本次只处理可以在当前代码库内闭环、且不会引入外部 LLM/RAG/vector 依赖的差距：

### G7：业务文档独立轨道

目标：

- `MemoryContext` 增加 `businessDocumentMemories`。
- `DefaultMemoryRetrievalPipeline` 将 `MemoryBusinessDocumentRetrieverPort` 的结果写入独立轨道，不再混入 `semanticMemories`。
- `DefaultContextWeaver` 输出 `[Business Documents]` 独立 zone，并保留 `[Semantic Memory]`。

验收：

- 业务规则问题激活 business document track 时，业务文档出现在 `context.getBusinessDocumentMemories()`。
- Semantic memory 不再承载 business document 候选。
- Prompt 中 `[Business Documents]` 先于普通 semantic/episodic。

### G8：Correction Ledger 硬压制冲突 Profile

目标：

- 读取期从 active correction 中解析 `targetKind=PROFILE_SLOT` 和 `targetKey=<slot>`。
- 若 correction 指向某个 Profile slot，则过滤同 slot 的 Profile active fact 和旧分层碎片，避免 Prompt 同时出现新旧强事实。
- Correction 本身仍保留在 Ring 0。

验收：

- correction target `identity.occupation` 存在时，Profile slot `identity.occupation=student` 不进入 `profileMemories`。
- 同 slot 的 short/long/semantic 旧碎片也不进入 Prompt。

### G9：Context Weaver 元数据说明

目标：

- Prompt 条目追加轻量来源信息，不暴露复杂 JSON，但要能看出 `slot`、`docId`、`version`、`generationId`、`source` 等关键事实。
- 保持预算控制，元数据也计入 `ContextBudget`。

验收：

- Profile 条目包含 `slot=identity.occupation` 或同等来源标记。
- Business doc 条目包含 `docId`/`version` 或从 metadata 中可解析出的来源标记。
- 超预算时仍按 zone 优先级裁剪。

### G10：读取 owner 清理

目标：

- 删除 `DefaultMemoryEnginePort` 中已由 `DefaultMemoryRetrievalPipeline` 拥有的旧读取辅助方法。
- `DefaultMemoryEnginePort.loadMemory()` 保持只委托 `MemoryRetrievalPipelinePort`。

验收：

- 编译通过。
- 既有 `DefaultMemoryEnginePortTests` 与 `MemoryRetrievalPipelineTests` 通过。

## 4. 暂不纳入本次的差距

以下项目仍是与 Gemini 方案的差距，但需要更大的产品/基础设施工作：

| 差距 | 原因 | 后续建议 |
| --- | --- | --- |
| LLM Refiner 结构化 ADD/UPDATE/DELETE/IGNORE | 需要模型调用、成本控制、灰度和失败降级 | 单独 P2b 阶段实现，默认关闭 |
| 会话静默期/Debounce/跨轮聚合 | 需要聊天会话生命周期事件和队列调度 | 单独 P2c 阶段实现 |
| REVIEW 人工审核闭环 | 需要管理端 API/UI 和候选表 | 单独 P5b 阶段实现 |
| 真实 Vector/BM25/Graph 多路召回 | 需要具体 adapter 和索引后端 | P4 后续阶段接入 |
| 自动 compaction、alias alignment、GC | 需要离线任务和质量策略 | P5 后续阶段接入 |

## 5. 执行步骤

1. [x] 写 RED 测试：业务文档结果进入独立 `businessDocumentMemories`，Context Weaver 输出 `[Business Documents]`。
2. [x] 写 RED 测试：Correction target slot 压制冲突 Profile slot。
3. [x] 修改 `MemoryContext` 增加字段。
4. [x] 修改 `DefaultMemoryRetrievalPipeline`：业务文档独立承载；Correction target slot 过滤 profile 和 legacy profile fragments。
5. [x] 修改 `DefaultContextWeaver`：独立 zones 与元数据摘要。
6. [x] 删除 `DefaultMemoryEnginePort` 中旧读取辅助方法及无用 import。
7. [x] 运行目标回归：

```powershell
.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc -am "-Dtest=DefaultMemoryEnginePortTests,MemoryWorkflowRoutingTests,MemoryOutboxRelayServiceTests,MemoryRetrievalPipelineTests,KernelMemoryLifecycleServiceTests,KernelMemoryObservabilityServiceTests,JdbcMemoryRepositoryAdapterTests,JdbcChatSchemaUpgradeTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
```

## 7. 执行记录

| 项目 | 状态 | 证据 |
| --- | --- | --- |
| RED：业务文档独立轨道 | 已完成 | 首次运行 `MemoryRetrievalPipelineTests,MemoryWorkflowRoutingTests` 因 `MemoryContext.getBusinessDocumentMemories()` 不存在而编译失败 |
| RED：Correction 压制冲突 Profile | 已完成 | 同一测试批次新增 correction target slot 断言 |
| GREEN：业务文档独立轨道 | 已完成 | `MemoryContext.businessDocumentMemories`、`DefaultMemoryRetrievalPipeline`、`DefaultContextWeaver` 已实现 |
| GREEN：Correction 硬压制 | 已完成 | `DefaultMemoryRetrievalPipeline` 使用 active correction target slot 过滤同 slot profile 和 legacy fragments |
| GREEN：Context 元数据 | 已完成 | `DefaultContextWeaver` 输出 `slot/docId/version/generationId/source` 摘要 |
| GREEN：读取 owner 清理 | 已完成 | `DefaultMemoryEnginePort` 删除旧读取 helper；`seahorse-agent-kernel` 编译通过 |
| 目标测试 | 通过 | `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=MemoryRetrievalPipelineTests,MemoryWorkflowRoutingTests,DefaultMemoryEnginePortTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`，42 tests，0 failures |
| 完整记忆回归 | 通过 | `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc -am "-Dtest=DefaultMemoryEnginePortTests,MemoryWorkflowRoutingTests,MemoryOutboxRelayServiceTests,MemoryRetrievalPipelineTests,KernelMemoryLifecycleServiceTests,KernelMemoryObservabilityServiceTests,JdbcMemoryRepositoryAdapterTests,JdbcChatSchemaUpgradeTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`，49 tests，0 failures |

## 6. 风险与回滚

| 风险 | 控制 |
| --- | --- |
| `MemoryContext` 新字段影响 Lombok builder 调用 | 字段为 list，未设置时 Weaver/Pipeline 用 null-safe 读取 |
| 过滤过强导致 Profile 不显示 | 只在 active correction 明确 target `PROFILE_SLOT` 且 targetKey 匹配时压制 |
| 元数据摘要占用预算 | 仍通过 `BudgetedBuilder.fits()` 统一裁剪 |
| 删除旧辅助方法误删写入逻辑依赖 | 只删除 `loadMemory` 迁移后未引用的方法，执行编译测试兜底 |
