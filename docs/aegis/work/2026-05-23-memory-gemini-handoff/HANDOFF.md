# Seahorse Memory Gemini Alignment Handoff

更新时间：2026-05-23

当前分支：`codex/memory-gemini-m2`

最近相关提交：`3c2080c2 feat(memory): explain hybrid recall fusion`

## 1. 接手目标

继续把 Seahorse Agent 记忆系统和 `docs/gemini-design.md` 对齐。目标不是替换当前记忆模型，而是在现有 Clean Architecture / 端口适配器架构下，把 Gemini 文档里的能力落成可插拔、可测试、可回退的工程模块。

核心不变量：

- `WORKING / SHORT_TERM / LONG_TERM / SEMANTIC` 四层记忆仍是 canonical storage model。
- Debounce、LLM Refiner、Hybrid Recall、Review、Compaction、Alias、GC 都应作为端口、策略、后台服务或派生索引能力叠加。
- 派生索引、召回通道、审核队列、维护任务不能成为新的事实源。
- 高风险自动写入、覆盖、删除要经过策略门禁、REVIEW 或熔断。
- 每个开发阶段都要提交代码；当前工作区有并行开发脏改，提交必须路径限定。

## 2. 必读基线

下一位开发开始前先读这些文件，不要只依赖记忆：

- `docs/gemini-design.md`
- `docs/Seahorse Agent记忆系统Gemini对齐二次Review与补齐执行计划.md`
- `docs/Seahorse Agent记忆系统Gemini对齐差距补齐开发设计与执行计划.md`
- `docs/Seahorse Agent记忆系统现状与Gemini文档差距对照.md`，当前在工作区里显示为未跟踪文件，读取可以，除非明确要提交，不要顺手加入提交。
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/MemoryEngineOptions.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/aggregation/DefaultMemoryAggregationService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/retrieval/HybridMemoryRecallPipeline.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/retrieval/RrfMemoryFusion.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/KernelMemoryReviewService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/maintenance/DefaultMemoryMaintenanceService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/retrieval/MemoryRecallEvaluationService.java`

## 3. 当前已完成状态

已提交的关键 Gemini 对齐提交：

- `2154b3c2 feat(memory): explain review decision traces`
- `76b4574a feat(memory): configure refiner context policy`
- `e14826a5 feat(memory): bind refiner context policy properties`
- `051ae62e feat(memory): preserve review feedback actions`
- `13918176 feat(memory): report gc stage counts`
- `3c2080c2 feat(memory): explain hybrid recall fusion`

### Debounce / 跨轮聚合

当前实现位置：

- `DefaultMemoryAggregationService`
- `MemoryAggregationPolicy`
- `InMemoryMemoryAggregationBufferPort`
- `MemoryContextBlockFormatter`
- `KernelMemoryAggregationControlService`
- `MemoryAggregationServiceTests`
- `KernelMemoryAggregationControlServiceTests`

已具备能力：

- 按 session 缓冲多轮 `MemoryTurnEvent`。
- idle flush、force turns、force tokens、session closed、manual flush。
- topic-shift flush，当前是显式 cue 检测。
- flush 时生成标准 context block，并交给 `MemoryIngestionWorkflowPort`。
- trace 已记录 append / flush details，且已有 context block 脱敏测试。

剩余差距：

- 当前默认 buffer 是内存实现，不是 Redis Stream / Lua CAS / 分布式延迟队列。
- 分布式 debounce 后端属于后续 adapter 深水区，不建议在 kernel 内硬编码 Redis。

适配建议：

- 保持 `MemoryAggregationBufferPort` 和 `MemoryAggregationSchedulerPort` 不变。
- 后续新增 `seahorse-agent-adapter-redis` 或已有基础设施 adapter，实现 Redis buffer、atomic rename、delayed token scheduler。
- kernel 只依赖端口和 `MemoryAggregationPolicy`。

### LLM Refiner

当前实现位置：

- `MemoryRefinerPort`
- `MemoryRefinementRequest`
- `MemoryRefinementResult`
- `RefinedMemoryOperation`
- `DefaultMemoryEnginePort`
- `MemoryEngineOptions`
- OpenAI compatible adapter 中的 `LlmMemoryRefinerAdapter`

已具备能力：

- Refiner 是可选 outbound port，未配置时走 noop。
- Refiner 能以结构化 operation 影响 ADD / UPDATE / DELETE / IGNORE / REVIEW。
- 有 existing memory read mask、target zone、sticky anchors、feedback examples。
- `MemoryEngineOptions` 已包含 `maxRefinerBatchOperations=8` 和 `maxRefinerDeleteRatio=0.7`。
- `DefaultMemoryEnginePort` 已把超量 batch 或 DELETE-heavy batch 转入 REVIEW，避免模型误删造成大面积污染。
- Review feedback export 已能反哺 Refiner。

剩余差距：

- 真实模型输出质量和 prompt 评估仍依赖 adapter 层和样本集。
- Refiner 的生产灰度、成本指标、模型版本对比还可以继续补。

适配建议：

- 不要把 Refiner 写死进 kernel。
- 所有模型调用都留在 adapter，kernel 只保留 schema、策略、熔断和 review staging。

### 多路召回 / Hybrid Retrieval

当前实现位置：

- `MemoryRecallChannelPort`
- `VectorMemoryRecallChannel`
- `KeywordMemoryRecallChannel`
- `GraphMemoryRecallChannel`
- `RrfMemoryFusion`
- `ModelMemoryRecallReranker`
- `MemoryRecallAliasRanker`
- `HybridMemoryRecallPipeline`
- `HybridMemoryRecallPipelineTests`
- `RrfMemoryFusionTests`
- `MemoryRecallChannelAliasRankingTests`

已具备能力：

- vector / keyword / graph 通道并行召回。
- channel timeout 和失败降级。
- RRF 融合、channel weights、time decay、final topK。
- reranker port 可插拔。
- alias resolve 会改写 query，并把 canonical alias filters 传给通道。
- trace 已暴露 `fusionExplanations`，能解释来源通道、rank、score、contribution，同时不暴露 raw query/content。

剩余差距：

- 真实 BM25/vector/graph 后端质量评估还不完整。
- 默认聊天链路是否已经切到 hybrid pipeline 需要接手者重新核对 starter auto configuration，不能只看旧文档断言。
- Recall evaluation 指标仍偏薄，是推荐的下一刀。

适配建议：

- 继续把通道作为 `MemoryRecallChannelPort` 列表组合，不要在 pipeline 里 switch 具体实现。
- 派生索引由 outbox 和 adapter 维护，四层记忆仍是主事实源。

### REVIEW 人工审核闭环

当前实现位置：

- `MemoryReviewCandidatePort`
- `MemoryReviewManagementRepositoryPort`
- `MemoryReviewFeedbackRepositoryPort`
- `KernelMemoryReviewService`
- `MemoryReviewInboundPort`
- `MemoryReviewDecisionCommand`
- `KernelMemoryReviewServiceTests`

已具备能力：

- Refiner / policy 触发 REVIEW 时进入 staging。
- 支持 pending summary、page、approve、modify、reject。
- approve / modify 通过 ingestion workflow apply 回主写入链路。
- delete review 和 alias review 已有专门处理。
- 人工决策会记录 feedback sample，且反馈写失败不影响审核决策主流程。
- export Refiner feedback samples 已支持 MODIFY / REJECT 样本。
- review trace 已包含 decision details、apply operation id、feedback sample id、reviewed memory id。

剩余差距：

- Review Console / Web API 完整性还需要核对，尤其是列表、详情、决策、反馈导出是否全部对外暴露。
- UI 管理台不在本次内核切片里完成。

适配建议：

- 控制台/API 只调用 inbound port，不绕过 `KernelMemoryReviewService`。
- 审核状态流转保持 repository CAS 语义，避免并发审核重复 apply。

### 自动维护 / Compaction / Alias / GC

当前实现位置：

- `MemoryCompactionService`
- `MemoryAliasResolutionService`
- `MemoryGarbageCollectionService`
- `DefaultMemoryMaintenanceService`
- `MemoryMaintenanceRunRepositoryPort`
- `MemoryGarbageCollectionResult`
- `DefaultMemoryMaintenanceServiceTests`
- `MemoryGarbageCollectionServiceTests`
- `MemoryAliasResolutionServiceMaintenanceTests`

已具备能力：

- Maintenance service 能编排 compaction、alias、GC。
- GC 结果已新增 `derivedIndexCandidateCount / archiveCandidateCount / physicalDeleteCandidateCount`。
- maintenance trace 已暴露 GC 分阶段计数。
- maintenance run repository 会记录 compaction / alias / GC 运行结果，保存失败只影响观测，不改变维护执行语义。

剩余差距：

- Compaction 测试文件当前有并行脏改，暂时不要碰，除非先确认这些改动归属。
- 真实物理索引 GC 和归档存储策略仍是 adapter/ops 深水区。

适配建议：

- 维护任务继续通过 inbound command 和 outbound ports 编排。
- 不要让 GC 直接删除主表事实；优先 tombstone / archive / derived index cleanup。

## 4. 四层记忆与 Gemini 方案是否冲突

不冲突，但有模型层级差异。

Gemini 文档描述的是生产级长期记忆系统的工程流水线：入口 debounce、Refiner、review staging、多路召回、后台 self-healing。它不是一个新的存储分层模型。

Seahorse 当前的四层记忆是事实存储和上下文装配模型：

- `WORKING`：当前工作缓冲。
- `SHORT_TERM`：短期对话事实。
- `LONG_TERM`：长期用户事实。
- `SEMANTIC`：语义概念和结构化知识。

适配方式：

- 写入侧：Gemini 的 Refiner 产出的是操作候选，最终仍路由到四层中的一层。
- 读取侧：Gemini 的 Vector/BM25/Graph 是派生召回通道，召回结果最终仍折叠为 `MemoryContext`。
- 治理侧：Compaction/Alias/GC 是后台维护机制，处理四层记录及其派生索引，不创建新的主事实源。
- 审核侧：REVIEW 是 shadow/staging，不是第五层记忆。

值得借鉴的地方：

- 防抖聚合能显著降低碎片写入和 Refiner token 成本，已适配为 aggregation service。
- Refiner 的结构化 delta 和 DELETE/UPDATE 明确语义能减少冲突记忆，已通过 operation model 和 batch circuit breaker 适配。
- 多路召回能弥补纯向量检索对专名、编号、关系跳转的不稳定，已通过 channel ports + RRF 适配。
- 人审闭环能把模型错误变成训练/提示优化样本，已通过 feedback repository/export 适配。

需要克制的地方：

- 不要为了贴合文档引入硬编码 Redis、BM25、Graph DB 或特定 LLM。
- 不要把 REVIEW 候选当作可直接召回的记忆层。
- 不要让 alias canonical id 替换四层记录 id；它应是检索和治理辅助键。

## 5. 当前工作区风险

`git status --short --branch` 显示当前分支有大量并行开发改动。下一位开发不要 broad add，也不要 revert。

已知需要避让的脏改范围：

- `seahorse-agent-adapter-repository-jdbc/...`
- `seahorse-agent-kernel/src/main/java/.../LocalToolGatewayPort.java`
- `seahorse-agent-kernel/src/test/java/.../LocalToolGatewayPortAuditTests.java`
- `seahorse-agent-spring-boot-starter/.../SeahorseAgentRegistryRepositoryAutoConfiguration.java`
- `seahorse-agent-tests/src/test/java/.../MemoryCompactionServiceTests.java`
- 未跟踪的 approval/domain/repository 文件
- `.claude/`
- `.playwright-cli/`
- `frontend/.playwright-cli/`
- `CLAUDE.md`
- `docs/code-standard-review.md`

提交规则：

```powershell
git add -- <本切片明确修改的文件>
git commit -m "feat(memory): <slice summary>"
```

提交前确认 staged 内容：

```powershell
git diff --cached --stat
git diff --cached -- <本切片明确修改的文件>
```

## 6. 推荐下一刀：补 Recall Evaluation precision 指标

这是当前最适合继续推进的低风险切片：它对齐 Gemini 生产级召回质量评估诉求，不触碰并行脏改，也不会改变主链路行为。

### 目标

给 `MemoryRecallEvaluationService` 增加 per-case precision 和 report averagePrecision。

当前指标只有：

- `hitRate`
- `meanReciprocalRank`
- `averageRecall`

建议新增：

- `MemoryRecallEvaluationResult.precision`
- `MemoryRecallEvaluationReport.averagePrecision`

建议定义：

- `matchedCount = expectedMemoryIds - missingExpectedMemoryIds`
- `precision = retrievedMemoryIds.isEmpty() ? 0D : matchedCount / retrievedMemoryIds.size()`
- `averagePrecision = scored cases 的 precision 平均值`

这个定义衡量已召回集合的信噪比；如果更想要严格 `precision@K = matchedCount / topK`，要在字段名和测试里写清楚，避免指标含义漂移。

### 预计修改文件

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/retrieval/MemoryRecallEvaluationService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/memory/MemoryRecallEvaluationReport.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/memory/MemoryRecallEvaluationResult.java`
- `seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/memory/retrieval/MemoryRecallEvaluationServiceTests.java`
- 可能需要同步：`seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseWebApiContractTests.java`

### 兼容性注意

`MemoryRecallEvaluationReport` 和 `MemoryRecallEvaluationResult` 是 Java record。新增 record component 会改变 canonical constructor。

建议做法：

- 新字段加在 `results` 或 `recall` 附近，使 JSON 输出语义清晰。
- 在 record 中提供旧签名的显式兼容构造器，把旧调用委托到新 canonical constructor，默认 precision 为 `0D` 或由可用字段计算。
- 同步更新 service 构造新 record 的位置。
- Web contract test 如果断言响应 JSON，需要补 expected 字段。

### TDD 步骤

1. 先在 `MemoryRecallEvaluationServiceTests` 加 RED：
   - case 1：expected 2 个，retrieved top3 中命中 2 个，precision 为 `2 / 3`。
   - case 2：expected 2 个，retrieved top2 中命中 1 个，precision 为 `1 / 2`。
   - report averagePrecision 为两个 scored case 的平均。
   - empty expected case 仍是 non-scored，precision 不参与平均。
2. 跑窄测试确认失败。
3. 实现 record 字段、兼容构造器和 service 计算逻辑。
4. 跑 kernel 编译和窄测试。
5. 如果 web contract 受影响，跑对应 web contract 测试。
6. `git diff --check`。
7. 路径限定提交。

推荐命令：

```powershell
.\mvnw.cmd -pl seahorse-agent-kernel install "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.apply.skip=true" "-Dspotless.check.skip=true"

.\mvnw.cmd -pl seahorse-agent-tests test "-Dtest=MemoryRecallEvaluationServiceTests" "-Dmaven.compiler.testIncludes=**/MemoryRecallEvaluationServiceTests.java" "-Dspotless.apply.skip=true" "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"

.\mvnw.cmd -pl seahorse-agent-tests test "-Dtest=SeahorseWebApiContractTests" "-Dmaven.compiler.testIncludes=**/SeahorseWebApiContractTests.java" "-Dspotless.apply.skip=true" "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"

git diff --check -- seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/retrieval/MemoryRecallEvaluationService.java seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/memory/MemoryRecallEvaluationReport.java seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/memory/MemoryRecallEvaluationResult.java seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/memory/retrieval/MemoryRecallEvaluationServiceTests.java seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseWebApiContractTests.java
```

提交示例：

```powershell
git add -- seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/retrieval/MemoryRecallEvaluationService.java seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/memory/MemoryRecallEvaluationReport.java seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/memory/MemoryRecallEvaluationResult.java seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/memory/retrieval/MemoryRecallEvaluationServiceTests.java seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseWebApiContractTests.java
git commit -m "feat(memory): report recall precision metrics"
```

## 7. 后续切片建议

按风险从低到高推进：

1. Recall evaluation precision / retrieved noise metrics。
2. Review Web API / Console contract gap 核对，只补 inbound port 暴露和 contract tests，不改 review 核心状态机。
3. Maintenance observability：补 Micrometer 或 trace 查询侧对 compaction/alias/GC 结果的聚合视图。
4. Hybrid recall 默认装配核对：确认 starter 是否在 port 可用时启用 hybrid pipeline，并保留 classic fallback。
5. Redis/distributed aggregation adapter：实现 `MemoryAggregationBufferPort` / scheduler 的生产后端。
6. 真实 BM25/vector/graph 后端质量评估：建立 golden cases，衡量 channel contribution、recall、precision、MRR。
7. Compaction 深化：等 `MemoryCompactionServiceTests` 的并行改动归属明确后再接。

## 8. 可用 subagents 的建议

只有在任务能并行、且不会写同一批文件时再开 subagents。

可并行的研究任务：

- Agent A：核对 Review Web API / controller / contract tests，输出缺口，不改 kernel。
- Agent B：核对 starter auto configuration 是否默认接入 hybrid recall，输出装配图。
- Agent C：核对 docs 与当前代码差距，更新 checklist，不碰生产代码。

不建议并行的实现任务：

- `MemoryRecallEvaluationReport/Result` record 改动和 web contract 同时被多人改，容易冲突。
- maintenance/compaction 当前有脏改，暂时不要交给 subagent 自动改。

## 9. 最近验证证据

最近已跑过并通过的测试：

- `MemoryGarbageCollectionServiceTests`：7/7 pass
- `DefaultMemoryMaintenanceServiceTests`：9/9 pass
- `HybridMemoryRecallPipelineTests`：8/8 pass

最近每次 kernel 变更后已执行：

```powershell
.\mvnw.cmd -pl seahorse-agent-kernel install "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.apply.skip=true" "-Dspotless.check.skip=true"
```

本交接文档切片只新增文档，不修改生产代码。

## 10. 完成对齐的判断标准

可以认为和 `docs/gemini-design.md` 基本对齐的条件：

- Debounce：有本地实现、控制接口、trace、测试；生产 Redis adapter 有明确后续计划或实现。
- Refiner：结构化 operation、策略门禁、读上下文、反馈样本、批量熔断、默认可关闭。
- Recall：vector/keyword/graph channel、RRF、rerank、alias、timeout、trace explanation、golden evaluation metrics。
- Review：pending/apply/reject/modify、delete/alias apply、feedback export、API/Console 至少有后端契约。
- Maintenance：compaction/alias/GC 编排、run record、trace/metrics、derived index cleanup 路径。
- 四层记忆：仍是唯一 canonical model，所有新增能力均可插拔。
- 验证：窄测试、关键 contract tests、必要时 starter auto config tests 都通过。

下一步最小安全动作：按第 6 节实现 recall precision metrics，并提交。
