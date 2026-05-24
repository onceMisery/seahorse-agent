# DefaultMemoryEnginePort decomposition — Checkpoint

- Task ID: 2026-05-24-default-memory-engine-decomposition
- Current todo: 11 个 cut 已落地，可继续 §6 推荐的 reviewApplyClassification 拆分，或停手交接。
- Active slice: spec §8 DefaultMemoryEnginePort 拆分（已达成 §8.4 acceptance 3.5×）
- Blocked on: none
- Next step: 见 HANDOFF.md §6（reviewApplyClassification 服务化）。

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

## DriftCheckDraft — Slice 3 续 总结

- **Scope status**: 11 个 cut 全部停留在 `seahorse-agent-kernel/src/main/java/.../application/memory/` 包内；未触碰 outbound port / Spring auto-config / web controller。
- **Compatibility status**: `MemoryEnginePort` / `MemoryIngestionWorkflowPort` 公开契约 byte-identical；OPERATION_* 字面量保留；metadata key 字面量保留。
- **Retirement status**: facade 内不再有 derivedIndexDispatch / trackWrite / refinementContext / refinerBatch / profileValue / refinementInput / canonicalAlias / refinerMetadata / operationBuild / operationCompletion / refinerFeedback 的私有实现；旧路径完全替换。
- **New risk signals**:
  - metadata key 字面量在 facade 与 cut 4/6/8/11 之间重复（技术债，见 HANDOFF.md §7）。
  - 11 个新 service 均为 facade ctor 直接 `new`，未对外开放；如果需要 mock 替换需后续做 ObjectProvider 注入（subagent B 建议研究）。
  - 仍有 §4.2/§4.3 候选未拆，但边际收益递减，停手是合理决策。
- **Advisory decision**: stop — spec §8.4 acceptance 已达成 3.5×，继续拆需先做 metadata key 公共化清场。
