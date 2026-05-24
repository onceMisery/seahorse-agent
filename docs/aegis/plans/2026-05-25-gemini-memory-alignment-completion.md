# Gemini memory alignment completion plan

## Goal

Complete the active Gemini memory alignment objective by proving the current implementation satisfies the target documents, adding current-state design evidence, and running regressions from the isolated worktree.

## Architecture

The completed architecture is `4 layer x N track`:

- Four durable memory layers remain `WORKING / SHORT_TERM / LONG_TERM / SEMANTIC`.
- Profile, correction, business-document, review, alias, keyword, graph, vector, compaction, and GC are horizontal capabilities.
- Kernel owns contracts and orchestration.
- Adapters own infrastructure.
- Derived indexes are eventually consistent views, not facts.

## Tech Stack

- Java 17+ project built with Maven wrapper.
- Kernel Clean Architecture / ports-adapters.
- Spring Boot starter for wiring only.
- Optional adapters for Redis, Lucene, Elasticsearch, JDBC graph/keyword, and OpenAI-compatible refiner.

## Baseline/Authority Refs

- `docs/Gemini Agent记忆系统完整设计方案.md`
- `docs/Seahorse Agent记忆系统Gemini架构深度分析与实施方案.md`
- `docs/gemini-design.md`
- `docs/aegis/specs/2026-05-24-design-alignment-next-development.md`
- `docs/aegis/work/2026-05-23-memory-gemini-handoff/HANDOFF.md`
- `docs/aegis/work/2026-05-24-default-memory-engine-decomposition/HANDOFF.md`
- `docs/aegis/specs/2026-05-25-gemini-memory-alignment-current-state.md`

## Compatibility Boundary

- Preserve `MemoryEnginePort` behavior.
- Preserve four-layer `MemoryLayer`.
- Keep model-driven mutation behind policy/review.
- Keep kernel free of infrastructure imports.
- Do not stage unrelated main-workspace untracked files.

## Verification

Targeted verification:

```powershell
.\mvnw.cmd -B -pl seahorse-agent-tests,seahorse-agent-adapter-ai-openai-compatible,seahorse-agent-adapter-search-lucene,seahorse-agent-adapter-search-elasticsearch,seahorse-agent-adapter-repository-jdbc -am test "-Dspotless.check.skip=true" "-Dtest=MemoryAggregationServiceTests,KernelMemoryAggregationControlServiceTests,DefaultMemoryEnginePortTests,KernelMemoryReviewServiceTests,HybridMemoryRecallPipelineTests,MemoryRecallEvaluationServiceTests,MemoryCompactionServiceTests,MemoryCompactionServiceObservationTests,DefaultMemoryMaintenanceServiceTests,MemoryAliasResolutionServiceMaintenanceTests,MemoryDerivedIndexOutboxTaskHandlerTests,MemoryOutboxRelayServiceTests,LlmMemoryRefinerAdapterTests,LuceneKeywordAdapterTests,ElasticsearchKeywordSearchAdapterTests,JdbcMemoryGraphRepositoryAdapterTests,JdbcMemoryKeywordIndexRepositoryAdapterTests,SeahorseAgentKernelAutoConfigurationTests,SeahorseAgentNativeAdapterAutoConfigurationTests,SeahorseWebApiContractTests" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Full regression:

```powershell
.\mvnw.cmd -B test "-Dspotless.check.skip=true"
```

## Tasks

### 1. Current-state design closure

Files:

- `docs/aegis/specs/2026-05-25-gemini-memory-alignment-current-state.md`
- `docs/aegis/plans/2026-05-25-gemini-memory-alignment-completion.md`
- `docs/aegis/work/2026-05-25-gemini-memory-alignment-completion/*`
- `docs/aegis/INDEX.md`

Steps:

- [x] Read baseline docs and handoffs.
- [x] Confirm current branch and worktree state.
- [x] Write current-state design matrix.
- [x] Write completion implementation plan.
- [x] Run targeted verification.
- [x] Run full regression.
- [x] Record evidence and final reflection.

### 2. Implementation audit

Files checked only unless verification exposes a defect:

- Memory layer/track domain and ports.
- Aggregation service and Redis adapters.
- Refiner port and OpenAI-compatible adapter.
- Hybrid recall pipeline and keyword/graph/vector channels.
- Review service, controller, repositories, and apply directive.
- Compaction, alias, GC, maintenance job, and outbox handlers.

Steps:

- [x] Map docs to concrete code owners.
- [x] Confirm old gap claims that are now closed.
- [x] Identify residual non-goals.
- [x] Fix any failing verification evidence if found.

### 3. Regression and closeout

Steps:

- [x] Run targeted tests.
- [x] Run full tests.
- [x] Update evidence file.
- [x] Update checkpoint and reflection.
- [x] If all requirements are proven, mark the thread goal complete.

## Risks

- Some root docs still contain historical gap statements. The current-state spec clarifies which statements are superseded by code evidence.
- Full Maven regression is the only broad proof that no module contract was broken.
- Main workspace contains unrelated untracked files; all work is isolated under `.worktrees/gemini-memory-alignment`.

## Retirement

No production fallback is retired by this closure. Historical gap documents remain as history; this plan and the current-state spec become the latest Aegis evidence for the current baseline.
