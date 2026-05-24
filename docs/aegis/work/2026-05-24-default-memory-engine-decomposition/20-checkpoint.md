# DefaultMemoryEnginePort decomposition — Checkpoint

- Task ID: 2026-05-24-default-memory-engine-decomposition
- Current todo: 12 个 cut 已落地；`reviewApplyClassification` 服务化已完成。后续如继续拆分，优先考虑低风险 helper cleanup，或先做 `MemoryMetadataKeys` 清场后再进入 refinement cluster。
- Active slice: spec §8 DefaultMemoryEnginePort 拆分（已达成 §8.4 acceptance 3.9×）
- Blocked on: none
- Next step: 见 HANDOFF.md §6（当前状态与后续建议）。不要重复实现 `reviewApplyClassification` 服务化。

## Checkpoint Update — Cut 1 (MemoryDerivedIndexDispatchService, prior session)

- Completed: vector index dispatch + outbox fallback 抽出。
- Evidence ref: commit `e6570c42`（前会话）
- Drift: 无；新文件 + facade ctor 注入 + 替换调用点。
- Decision: continue

## Checkpoint Update — Cut 2 (MemoryTrackWriteService)

- Completed: profile/correction track upsert + slot obsolete 抽出。处理了 facade line 1789 的 mojibake 中文日志字面量（字节级 read + rewrite）。
- Evidence ref: commit `bb3c2671`；测试 62+46=108 pass。
- Drift: 无；OPERATION_CORRECTION_UPSERT / OPERATION_PROFILE_UPSERT 字面量保留。
- Decision: continue

## Checkpoint Update — Cut 3 (MemoryRefinementContextParser)

- Completed: turns/source_spans parsing（11 私有方法 + 2 private record + 3 Pattern）抽出。
- Evidence ref: commit `bc0fbed1`；测试 62+46+10=118 pass。
- Drift: `MemoryRefinementContextZones` private record 升级为 public `MemoryRefinementContextParser.Zones`，accessor 字面量保持不变。
- Decision: continue

## Checkpoint Update — Cut 4 (MemoryRefinerBatchCircuitBreaker)

- Completed: batch operation/delete 比例熔断 → REVIEW 抽出；12 个 circuit-only 常量随服务搬走。
- Evidence ref: commit `663076de`；测试 118 pass。
- Drift: 无；REVIEW classification 与 metadata key 字面量全部保留。
- Decision: continue

## Checkpoint Update — Cut 5 (MemoryProfileValueNormalizer)

- Completed: profile slot 解析 + 按 slot 规整 value 抽出；5 个私有方法移除。
- Evidence ref: commit `dcc0a0c6`；测试 118 pass。
- Drift: 无；OccupationCorrection.normalizeOccupationValue 委托链保留。
- Decision: continue

## Checkpoint Update — Cut 6 (MemoryRefinementInputBuilder)

- Completed: 三层 listByUser + sticky anchors 抽出；6 私有方法 + 2 sticky-only metadata key 移除。
- Evidence ref: commit `a5f0b522`；测试 118 pass。
- Drift: 无；shared key（importance/confidence）仍在 facade，sticky-only key（importanceScore/confidenceLevel）随服务搬走。
- Decision: continue

## Checkpoint Update — Cut 7 (MemoryCanonicalAliasResolver)

- Completed: content 提 token + canonical alias 挂 metadata 抽出；7 个常量随服务搬走。同时清理 6 个 unused imports（Pattern/Matcher/LinkedHashSet/Optional/Set/MemoryAliasResolution）。
- Evidence ref: commit `3cd28a31`；测试 118 pass。
- Drift: 无。
- Decision: continue

## Checkpoint Update — Cut 8 (MemoryRefinerMetadataWriter)

- Completed: refined delta → metadata 写入 + memoryId 构造抽出；4 私有方法移除；3 个 refiner metadata key 字面量在新服务内本地化（与 facade 字面量等价但独立）。
- Evidence ref: commit `b7b31c10`；测试 118 pass。
- Drift: 字面量重复加入技术债清单（详见 HANDOFF.md §7）。
- Decision: continue

## Checkpoint Update — Cut 9 (MemoryOperationBuilder)

- Completed: ingestion command → MemoryOperation 装配抽出；5 私有方法移除；3 helper（sanitizer/preFilter/semanticClassifier）作为新服务构造参数。
- Evidence ref: commit `62c85900`；测试 118 pass。
- Drift: 无。
- Decision: continue

## Checkpoint Update — Cut 10 (MemoryOperationCompletionWriter)

- Completed: operation log "completed" 写入 + status mapping + decisionMap 合并抽出；4 私有方法移除。
- Evidence ref: commit `4237405a`；测试 118 pass。
- Drift: 无；与 cut 8 的 MemoryRefinerMetadataWriter 组合，decisionMap 单一 owner。
- Decision: continue

## Checkpoint Update — Cut 11 (MemoryRefinerFeedbackLookup)

- Completed: recent resolved review feedback 查询抽出；3 私有方法 + RefinerFeedbackScope record 移除；2 个 unused imports（MemoryReviewFeedbackQuery/MemoryReviewStatus）清理。
- Evidence ref: commit `9df495cc`；测试 118 pass。
- Drift: 无；RefinerFeedbackScope 内化为新服务私有 record。
- Decision: continue

## Checkpoint Update — Cut 12 (MemoryReviewApplyClassificationBuilder)

- Completed: review apply 分类构造与 target layer 校验从 facade 抽出到 `MemoryReviewApplyClassificationBuilder`；`DefaultMemoryEnginePort` 改为委托新 service。
- Evidence ref: 当前工作树 `codex/design-alignment-memory-continuation`；新增 `MemoryReviewApplyClassificationBuilderTests` 4 个用例；回归测试 118 pass；全量 reactor 测试 748 pass。
- Drift: 无；`MemoryEnginePort` / `MemoryIngestionWorkflowPort` 签名未变；`WORKING` review target layer 仍被拒绝；空 `targetKind` 仍回退 `"FACT"`；`review_applied` / `memory_review_applied` 字面量保留。
- Decision: continue only for low-risk helper cleanup or pause; spec §8.4 acceptance 已超额达成。

## DriftCheckDraft — Slice 3 续 总结

- **Scope status**: 12 个 cut 全部停留在 `seahorse-agent-kernel/src/main/java/.../application/memory/` 包内；本轮仅新增 1 个 service、1 个 service test，并更新本工作记录；未触碰 outbound port / Spring auto-config / web controller。
- **Compatibility status**: `MemoryEnginePort` / `MemoryIngestionWorkflowPort` 公开契约 byte-identical；OPERATION_* 字面量保留；metadata key 字面量保留。
- **Retirement status**: facade 内不再有 derivedIndexDispatch / trackWrite / refinementContext / refinerBatch / profileValue / refinementInput / canonicalAlias / refinerMetadata / operationBuild / operationCompletion / refinerFeedback / reviewApplyClassification 的私有实现；旧路径完全替换。
- **New risk signals**:
  - metadata key 字面量在 facade 与 cut 4/6/8/11/12 之间重复（技术债，见 HANDOFF.md §7）。
  - 12 个新 service 均为 facade ctor 直接 `new`，未对外开放；如果需要 mock 替换需后续做 ObjectProvider 注入（subagent B 建议研究）。
  - 仍有 §4.2/§4.3 候选未拆，但边际收益递减，停手是合理决策。
- **Advisory decision**: stop or continue with helper cleanup — spec §8.4 acceptance 已超额达成；继续拆高风险 cluster 前需先做 metadata key 公共化清场。
