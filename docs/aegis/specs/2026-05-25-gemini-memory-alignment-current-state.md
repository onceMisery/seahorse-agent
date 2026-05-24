# Gemini memory alignment current-state design

- Date: 2026-05-25
- Status: Current-state closure spec
- Scope: Seahorse Agent memory alignment against `docs/Gemini Agent记忆系统完整设计方案.md`, `docs/Seahorse Agent记忆系统Gemini架构深度分析与实施方案.md`, and `docs/gemini-design.md`
- Baseline: current `main` / `codex/gemini-memory-alignment` at `b5ec6485`

## Goal

This spec records the current implementation state for the Gemini-style memory design. It replaces older "gap" statements only where the current code proves the gap is already closed.

The design target remains:

- Keep `MemoryLayer` as exactly `WORKING / SHORT_TERM / LONG_TERM / SEMANTIC`.
- Model profile, correction, business-document, review, alias, vector, keyword, and graph as horizontal tracks, ports, staging records, derived indexes, or maintenance capabilities.
- Keep kernel code free of Spring, Redis, Pulsar, Milvus, OpenAI, Elasticsearch, and Lucene dependencies.
- Preserve `MemoryEnginePort` and existing ingestion/retrieval callers.
- Make high-risk model-driven changes policy-gated and reviewable.

## Design Assessment

The three Gemini documents are directionally reasonable when interpreted as a production memory workflow rather than a storage-layer replacement. Their valuable parts are debounce aggregation, structured refiner output, multi-route recall, review staging, outbox-based derived-index synchronization, and background maintenance.

The old "seven layer" framing is not valid for Seahorse. The canonical model is `4 layer x N track`. The current target docs already contain this correction, and the code matches it:

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/memory/MemoryLayer.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/memory/MemoryTrack.java`

## Current Architecture

### Write path

The write side is now a port-driven workflow:

- Debounce aggregation buffers chat turns and flushes context blocks through `MemoryIngestionWorkflowPort`.
- Refiner output is a candidate operation model, not direct persistence authority.
- `ADD` can write through deterministic paths.
- `UPDATE`, `DELETE`, risky or uncertain output routes to review staging unless explicitly replayed by a human review directive.
- Derived indexes are synchronized through direct vector attempt plus outbox fallback, and optional keyword/graph outbox tasks.

Evidence:

- `DefaultMemoryAggregationService`
- `MemoryAggregationPolicy`
- `RedisMemoryAggregationBufferPort`
- `RedisMemoryAggregationSchedulerPort`
- `MemoryRefinerPort`
- `LlmMemoryRefinerAdapter`
- `MemoryReviewApplyDirective`
- `MemoryDerivedIndexDispatchService`
- `MemoryOutboxRelayService`

### Read path

The read side has a hybrid recall pipeline:

- `VectorMemoryRecallChannel`
- `KeywordMemoryRecallChannel`
- `GraphMemoryRecallChannel`
- `RrfMemoryFusion`
- `ModelMemoryRecallReranker`
- `MemoryRecallAliasRanker`
- `HybridMemoryRecallPipeline`

The pipeline is optional and adapter-driven. It keeps four-layer memory as source of truth and treats vector, keyword, and graph as recall channels.

### Review path

Review is a staging and governance workflow, not a memory layer:

- `MemoryReviewCandidatePort`
- `MemoryReviewManagementRepositoryPort`
- `MemoryReviewFeedbackRepositoryPort`
- `KernelMemoryReviewService`
- `SeahorseMemoryReviewController`

Human-approved `DELETE` can logically delete targeted four-layer source records and enqueue derived-index cleanup. LLM-originated delete still does not bypass review.

### Maintenance path

Maintenance is a background orchestration layer:

- `DefaultMemoryMaintenanceService`
- `MemoryCompactionService`
- `MemoryAliasResolutionService`
- `MemoryGarbageCollectionService`
- `SeahorseMemoryMaintenanceJob`

Compaction creates a `LONG_TERM` master memory, marks fragments compacted, and enqueues vector/keyword/graph updates and deletes. The summarization step is a pluggable `MemoryCompactionSummarizerPort`, so LLM compaction can be provided by an adapter while remaining disabled by default.

## Gap Matrix

| Requirement | Current status | Evidence | Residual boundary |
| --- | --- | --- | --- |
| Four-layer memory model | Implemented | `MemoryLayer` has only 4 enum values; `MemoryTrack` carries horizontal tracks | None for this scope |
| Debounce and cross-turn aggregation | Implemented | `DefaultMemoryAggregationService`, in-memory buffer, Redis buffer, Redis scheduler, aggregation tests | Advanced semantic topic detection can remain future adapter work |
| LLM Refiner structured candidate output | Implemented | `MemoryRefinerPort`, `LlmMemoryRefinerAdapter`, refiner context/feedback wiring | Model quality evaluation is operational, not architecture gap |
| Refiner safety gate | Implemented | `MemoryRefinerBatchCircuitBreaker`, review staging for update/delete, schema validator | None for current compatibility boundary |
| Hybrid recall Vector/BM25/Graph/RRF | Implemented | `HybridMemoryRecallPipeline`, three channels, RRF, reranker, recall evaluation/golden harness | External graph DB such as Neo4j remains optional adapter work |
| Real keyword backend | Implemented | JDBC memory keyword repository plus Lucene and Elasticsearch keyword adapters | Production choice is configuration/ops |
| Graph recall/index backend | Implemented lightweight | `JdbcMemoryGraphRepositoryAdapter` implements `MemoryGraphPort` and `MemoryGraphIndexPort` | Dedicated graph DB adapter is optional |
| Review closure | Implemented | `KernelMemoryReviewService`, Web controller, JDBC repository, feedback export | Frontend console polish is product work, not kernel gap |
| Review apply derived-index cleanup | Implemented | `MemoryDerivedIndexDispatchService.dispatchDelete`, review delete tests | Depends on derived-index flags and available adapters |
| Compaction | Implemented | `MemoryCompactionService`, canonical entity grouping in JDBC lifecycle adapter, outbox tasks | Optional LLM summarizer adapter may be added later |
| Alias resolution | Implemented | `MemoryAliasResolutionService`, dictionary and merge-candidate scan, alias review apply path | Complex semantic merge remains review-gated |
| GC | Implemented minimal safe version | `MemoryGarbageCollectionService`, derived-index cleanup, archive/physical counters | Physical deletion policy remains operations-specific |
| Observability | Implemented | trace recorder, memory health, aggregation/review/recall/outbox/maintenance events | Dashboarding is outside kernel |

## Compatibility Boundary

Do not change these without a separate design review:

- Do not add `PROFILE`, `CORRECTION`, or `BUSINESS_DOCUMENT` to `MemoryLayer`.
- Do not make vector, keyword, graph, review, alias, or compaction records source of truth.
- Do not let LLM refiner or LLM compactor write or delete source memory directly.
- Do not require Redis, Elasticsearch, Lucene, Milvus, or a graph database in kernel.
- Do not break `MemoryEnginePort`, `MemoryIngestionWorkflowPort`, or existing controller contracts.

## Remaining Non-Goals

The following are useful future enhancements but are not required to satisfy the current Gemini alignment objective:

- A dedicated Neo4j or other graph database adapter.
- Fine-tuning or DPO automation for refiner feedback samples.
- A richer frontend review console.
- A production LLM compaction adapter.
- Advanced semantic topic-shift detection beyond configured cues.
- Physical hard-delete retention policy beyond current safe tombstone/derived-index cleanup model.
