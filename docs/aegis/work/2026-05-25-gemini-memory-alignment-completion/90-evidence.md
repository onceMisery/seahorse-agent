# Evidence Bundle

## Current-State Evidence

Branch/worktree:

- Worktree: `D:\code\seahorse-agent\.worktrees\gemini-memory-alignment`
- Branch: `codex/gemini-memory-alignment`
- Baseline commit: `b5ec6485`

Document evidence:

- `docs/Gemini Agent记忆系统完整设计方案.md` states `MemoryLayer` remains four values and tracks are orthogonal.
- `docs/gemini-design.md` states `MemoryLayer` remains four values and seven-layer enum expansion is an anti-pattern.
- `docs/Seahorse Agent记忆系统Gemini架构深度分析与实施方案.md` states the compatible implementation model is ports/adapters over four layers.

Code evidence:

- Four-layer model: `MemoryLayer.java`, `MemoryTrack.java`.
- Debounce aggregation: `DefaultMemoryAggregationService`, `MemoryAggregationPolicy`, `RedisMemoryAggregationBufferPort`, `RedisMemoryAggregationSchedulerPort`.
- Refiner: `MemoryRefinerPort`, `LlmMemoryRefinerAdapter`, `MemoryRefinerFeedbackLookup`, `MemoryRefinerBatchCircuitBreaker`.
- Hybrid recall: `HybridMemoryRecallPipeline`, `VectorMemoryRecallChannel`, `KeywordMemoryRecallChannel`, `GraphMemoryRecallChannel`, `RrfMemoryFusion`, `MemoryRecallGoldenHarnessService`.
- Review: `KernelMemoryReviewService`, `SeahorseMemoryReviewController`, `MemoryReviewApplyDirective`, review feedback repository ports.
- Derived indexes: `MemoryDerivedIndexDispatchService`, `MemoryOutboxRelayService`, vector/keyword/graph outbox handlers.
- Keyword backends: JDBC memory keyword repository, Lucene keyword adapter, Elasticsearch keyword adapter.
- Graph backend: `JdbcMemoryGraphRepositoryAdapter`.
- Maintenance: `MemoryCompactionService`, `MemoryAliasResolutionService`, `MemoryGarbageCollectionService`, `DefaultMemoryMaintenanceService`, `SeahorseMemoryMaintenanceJob`.
- Optional LLM compaction boundary: `MemoryCompactionSummarizerPort` and `MemoryCompactionServiceTests#shouldUsePluggableSummarizerForMasterMemoryContent`.

## Verification Commands

Targeted Gemini memory regression:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-tests,seahorse-agent-adapter-ai-openai-compatible,seahorse-agent-adapter-search-lucene,seahorse-agent-adapter-search-elasticsearch,seahorse-agent-adapter-repository-jdbc -am test "-Dspotless.check.skip=true" "-Dtest=MemoryAggregationServiceTests,KernelMemoryAggregationControlServiceTests,DefaultMemoryEnginePortTests,KernelMemoryReviewServiceTests,HybridMemoryRecallPipelineTests,MemoryRecallEvaluationServiceTests,MemoryCompactionServiceTests,MemoryCompactionServiceObservationTests,DefaultMemoryMaintenanceServiceTests,MemoryAliasResolutionServiceMaintenanceTests,MemoryDerivedIndexOutboxTaskHandlerTests,MemoryOutboxRelayServiceTests,LlmMemoryRefinerAdapterTests,LuceneKeywordAdapterTests,ElasticsearchKeywordSearchAdapterTests,JdbcMemoryGraphRepositoryAdapterTests,JdbcMemoryKeywordIndexRepositoryAdapterTests,SeahorseAgentKernelAutoConfigurationTests,SeahorseAgentNativeAdapterAutoConfigurationTests,SeahorseWebApiContractTests" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Result:

- Exit status: 0
- Reactor modules: 24 `SUCCESS`
- Test summary: 236 tests run, 0 failures, 0 errors, 0 skipped
- Finished at: 2026-05-25T03:54:14+08:00
- Scope covered: aggregation, `MemoryEnginePort`, review, hybrid recall, recall evaluation, compaction, maintenance, outbox, LLM refiner, Lucene, Elasticsearch, JDBC graph/keyword, Spring auto-configuration, and web API contract tests.

Full Maven regression:

```powershell
.\mvnw.cmd -B test "-Dspotless.check.skip=true"
```

Result:

- Exit status: 0
- Reactor modules: 27 `SUCCESS`
- `seahorse-agent-tests`: 752 tests run, 0 failures, 0 errors, 0 skipped
- Finished at: 2026-05-25T03:56:58+08:00
- Scope covered: full Maven test reactor.

## Merge Evidence

- `git merge-base --is-ancestor codex/gemini-memory-alignment main` returned success before the documentation closeout commit.
- `git log main..codex/gemini-memory-alignment` returned no commits.
- Interpretation: production code from `codex/gemini-memory-alignment` was already included in `main`; this closeout records verification and current-state evidence only.
