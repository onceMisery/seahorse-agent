# Evidence

## Implemented Roadmap Slices

### M1 Retrieval Strategy Promotion

- Added `RetrievalStrategyPromotionCommand` and `promoteTemplateFromComparison`.
- Promotion reads a saved comparison, requires the target template key to equal the comparison winner, and rejects metric regressions against the baseline.
- Added `recommended` to `RetrievalStrategyTemplate` and `t_retrieval_strategy_template`.
- JDBC promotion clears any prior recommendation for the knowledge base and writes exactly one recommended template.
- Added `POST /knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/comparisons/{comparison-id}/promote`.
- Added `AuditEventType.RETRIEVAL_STRATEGY_PROMOTED` and audit payload with knowledge base, dataset, comparison, template, and metrics.
- Added retrieval evaluation case diagnostics to report JSON: expected targets, missing expected targets, retrieved chunk rank/score/target matches, and negative-hit markers.
- Added retrieval evaluation trace links to case diagnostics. Each evaluation case starts and finishes a RAG trace run, passes the `TraceRunScope` into the retrieval engine, and exposes `diagnostics.traceId`; with no trace recorder, the field remains an empty string.

### Previously Completed In This Goal Run

- M2 ingestion failed-task retry, task node governance fields, node persistence, pipeline versions, and task snapshots.
- M2 ingestion task records now expose unresolved metadata quarantine counts, backed by the existing `t_metadata_quarantine_item.job_id` link and a `job_id/resolved` index for drill-down.
- M2 retry-from-node context restoration preserves the legacy retry API, accepts optional `fromNodeId`, persists recoverable node outputs, restores safe upstream context fields, and starts execution from the requested pipeline node.
- M2 conservative ingestion rollback/compensation adds an explicit rollback API for completed tasks that carry document target metadata and delegates cleanup to the canonical knowledge document deletion owner.
- M3 profile fact disable and advisory cleanup suggestions requiring manual confirmation.
- M4 production gate publish evidence and rollout transition audit events.
- M4 rollout cost summaries expose rollout-window token/call/cost aggregation via a dedicated inbound port and web endpoint.
- M4 rollout dashboard summaries now add rollout-window run status counts, total/succeeded/failed/waiting-approval run counts, error rate, and pending approval count.
- M5 starter-all acceptance matrix covers every official heavy adapter with classpath, auto-configuration, activation properties, required infrastructure, health check, minimal business action, and expected Bean/port types.
- Docs stale-reference cleanup checks for deployment/model/skills operations.

### Near-Term Verification Baseline

- Added `scripts/verify-smoke-contracts.ps1` as a cheap RED/GREEN guard for the canonical backend smoke script.
- Updated `scripts/e2e-backend-smoke.ps1` to align with the current web contract instead of stale pre-roadmap paths:
  - Bearer auth header for authenticated requests.
  - Knowledge document upload via `/knowledge-base/{kbId}/docs/upload`.
  - Manual chunk kickoff via `/knowledge-base/docs/{docId}/chunk`.
  - Chunk log inspection via `/knowledge-base/docs/{docId}/chunk-logs`.
  - RAG smoke via SSE `GET /rag/v3/chat`.
  - Trace page check via `GET /rag/traces/runs`.
  - Memory readiness, profile facts, and maintenance checks via `/memories/readiness`, `/memories/profile-facts`, and `/memories/maintenance/run`.
- This slice first established a repeatable script contract for the roadmap's near-term "deployment/login/RAG/memory/profile verification baseline", then moved it to live `docker-compose.full.yml` evidence.

### Near-Term Live Full-Compose Evidence

- First live smoke exposed a production-path bug: `POST /knowledge-base/{kbId}/docs/upload` returned 400 `知识库不存在或未配置向量集合` for a newly created knowledge base.
- Root cause: `KernelKnowledgeDocumentService.requireKnowledgeBase(kbId)` used `KnowledgeBaseQueryPort.listSearchableKnowledgeBases()`, while `JdbcKnowledgeBaseQueryAdapter.listSearchableKnowledgeBases()` only returns knowledge bases that already have enabled chunks. That is correct for retrieval/search, but wrong for first document upload.
- Repair boundary:
  - Added `KnowledgeBaseQueryPort.findById(Long kbId)` for upload/read validation.
  - Implemented `JdbcKnowledgeBaseQueryAdapter.findById` against non-deleted knowledge bases with a configured collection name, without requiring existing chunks.
  - Changed `KernelKnowledgeDocumentService.requireKnowledgeBase` to use `findById`.
- TDD evidence:
  - `JdbcKnowledgeBaseQueryAdapterTests#findByIdShouldReturnKnowledgeBaseEvenBeforeFirstChunkExists`
  - `KernelKnowledgeDocumentServiceTests#shouldUploadDocumentBeforeKnowledgeBaseHasAnyChunks`
  - Both focused tests passed after the fix.
- Runtime refresh evidence:
  - Rebuilt backend image with `docker compose -f docker-compose.full.yml build backend`.
  - Recreated backend with `docker compose -f docker-compose.full.yml up -d --no-deps backend`.
  - Running image: `sha256:39558a67f6b9a0a860c10bac9182ebeee8376a2adae32c73173c4a6dd37f110e`, created `2026-06-15T16:40:01.173595306Z`.
  - Backend health reached `healthy`; `GET /actuator/health` returned `{"status":"UP"}`.
- Canonical smoke result:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1`
  - Result: 18 checks, 0 failed.
  - Passed: health, login, structured login error, current user, feature flags, user page, quota summary, notification center, data export task, knowledge base CRUD, knowledge document upload/chunk, RAG SSE chat, RAG trace API, memory/profile smoke, agent/tool/skill catalog, audit API, metadata governance API, and SRE health.
- Smoke assertion hardening:
  - `RAG trace API smoke` now requires a trace page with records, fetches `/rag/traces/runs/$traceId/nodes`, and requires retrieval evidence in trace nodes.
  - `Memory/profile smoke` now requires non-empty profile facts and reports the fact count.
  - Chunk logs were later hardened in the M2 chunk-log observability slice so manual chunk completion records must also be visible through `/knowledge-base/docs/{docId}/chunk-logs`.
- Database evidence after smoke:
  - `t_knowledge_chunk`: smoke `doc_id=324951590341529600`, `kb_id=324951589204873216`, `chunks=1`, `enabled_chunks=1`.
  - `t_rag_trace_run` / `t_rag_trace_node`: latest smoke trace `trace_id=324951594456141824`, `status=SUCCESS`, `nodes=11`, `retrieval_nodes=4`.
  - `t_user_profile_fact`: admin/default has `ACTIVE=7`, `HISTORICAL=16`, with latest active update during the smoke window.

### M2 Knowledge Document Chunk-Log Observability

- Previous evidence gap: the canonical smoke could prove document chunk completion through `t_knowledge_chunk`, but `/knowledge-base/docs/{docId}/chunk-logs` could still return an empty page after a successful manual chunk.
- Canonical owner decision: keep the write-side evidence in `JdbcKnowledgeDocumentRepositoryAdapter`, because it already owns document status changes and the read-side chunk-log query for `t_knowledge_document_chunk_log`.
- Implementation:
  - `markSuccess(...)` updates the document status and appends a `success` chunk-log row.
  - `markFailed(...)` updates the document status and appends a `failed` chunk-log row with the error message.
  - The inserted log row copies `process_mode`, `chunk_strategy`, `pipeline_id`, and `tenant_id` from `t_knowledge_document`.
  - Duration fields default to `0`; start/end/create/update timestamps use `CURRENT_TIMESTAMP`.
  - `scripts/e2e-backend-smoke.ps1` now waits up to 30 seconds for non-empty chunk-log records and asserts that records are present.
  - `scripts/verify-smoke-contracts.ps1` now rejects smoke scripts that only call the chunk-log endpoint without proving non-empty records.
- Test fixture compatibility:
  - `JdbcKnowledgeDocumentRepositoryAdapterTests` now uses pipeline id `"1"` to match production `BIGINT` `pipeline_id` semantics.
  - `JdbcKnowledgeBaseQueryAdapterTests` test schema includes `t_knowledge_document_chunk_log`, `chunk_strategy`, and `t_ingestion_pipeline.version`.
  - `KernelKnowledgeDocumentServiceTests.StaticKnowledgeBaseQueryPort` implements `findById(...)`, matching the upload validation owner added in the earlier live-smoke repair.
- RED/GREEN evidence:
  - RED: `.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcKnowledgeDocumentRepositoryAdapterTests#markSuccessShouldAppendChunkLog+markFailedShouldAppendChunkLogWithErrorMessage" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: failed as expected before implementation because chunk-log total was `0`.
  - GREEN: the same command passed after appending chunk-log rows from `markSuccess(...)` and `markFailed(...)`.
- Regression evidence:
  - `.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcKnowledgeDocumentRepositoryAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: build success; 4 tests run.
  - `.\mvnw -pl seahorse-agent-tests -am "-Dtest=KernelKnowledgeDocumentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: build success; 6 tests run.
  - `.\mvnw -pl seahorse-agent-adapter-repository-jdbc,seahorse-agent-tests -am "-Dtest=JdbcKnowledgeDocumentRepositoryAdapterTests,JdbcKnowledgeBaseQueryAdapterTests,KernelKnowledgeDocumentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: build success; JDBC related tests 13, service tests 6.
- Smoke static evidence:
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-smoke-contracts.ps1`
  - Result: passed.
  - `$errors = $null; [void][System.Management.Automation.PSParser]::Tokenize((Get-Content -LiteralPath 'D:\code\seahorse-agent\scripts\e2e-backend-smoke.ps1' -Raw), [ref]$errors); if ($errors -and $errors.Count -gt 0) { $errors | ForEach-Object { Write-Host $_.Message }; exit 1 }`
  - Result: passed; no parser errors.
  - `git diff --check`
  - Result: exit code 0; CRLF normalization warnings only.
- Live verification blocker:
  - `docker compose -f docker-compose.full.yml build backend`
  - Result: failed twice while Maven inside Docker resolved build plugins.
  - Failure boundary: Maven could not resolve `org.apache.maven.plugins:maven-dependency-plugin:pom:3.7.0` from Maven Central because the remote host terminated the TLS handshake.
  - Status: external dependency/network blocker. The backend image was not rebuilt, so the new non-empty chunk-log live smoke assertion has not yet been executed against `docker-compose.full.yml`.
  - Retry on 2026-06-16: `docker compose -f docker-compose.full.yml build backend`
  - Result: timed out after 244 seconds.
  - Image check after timeout: `docker inspect seahorse-agent-backend:latest --format "{{.Id}} {{.Created}}"` still reported `sha256:39558a67f6b9a0a860c10bac9182ebeee8376a2adae32c73173c4a6dd37f110e 2026-06-15T16:36:23.048689088Z`.
  - Container check after timeout: `docker inspect seahorse-backend --format "{{.Image}} {{.Created}}"` still reported `sha256:39558a67f6b9a0a860c10bac9182ebeee8376a2adae32c73173c4a6dd37f110e 2026-06-15T16:40:01.173595306Z`.
  - Conclusion: no fresh backend image was available for live smoke in this run.

### M2 Retry-From-Node Context Restoration

- Previous evidence gap: a failed ingestion task could be retried, but retrying from a specific failed node would be unsafe without restoring the context produced by earlier successful nodes.
- Compatibility decision:
  - Keep the existing `retry(taskId, operator)` port and `POST /ingestion/tasks/{id}/retry` no-body behavior unchanged.
  - Add optional request body field `fromNodeId` and a compatible port overload `retry(taskId, fromNodeId, operator)`.
  - Blank or missing `fromNodeId` delegates to the old behavior.
  - Do not add a new table; reuse existing `t_ingestion_task_node.output_json` exposed as `IngestionTaskNodeRecord.output`.
- Implementation:
  - `LocalIngestionNodeLogAdapter` records recoverable output snapshots for successful nodes: `rawText`, `enhancedText`, `keywords`, `questions`, `metadata`, `normalizedMetadata`, `chunks`, and `vectorSpaceId`.
  - `KernelIngestionTaskService.retry(..., fromNodeId, ...)` validates the requested node against the task's pipeline snapshot, reads successful prior node outputs in node order, restores safe context fields, and stores retry metadata including `retryFromNodeId` and `restoredNodeIds`.
  - `KernelIngestionEngine` starts from `IngestionContext.startNodeId` when present and validates that the node exists in the pipeline.
  - Existing retry without `fromNodeId` still creates a fresh execution using the legacy path.
- RED evidence:
  - `.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-tests -am "-Dtest=KernelIngestionTaskServiceTests,SeahorseIngestionAndIntentControllerTests,LocalIngestionNodeLogAdapterTests,KernelIngestionEngineTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: failed before implementation because `KernelIngestionTaskService.retry(String,String,String)` and `IngestionContext.getStartNodeId()` did not exist.
- GREEN evidence:
  - Same focused command passed after implementation.
  - Test counts: `KernelIngestionTaskServiceTests` 4, `LocalIngestionNodeLogAdapterTests` 1, `SeahorseIngestionAndIntentControllerTests` 14, `KernelIngestionEngineTests` 4; reactor build success.
  - One intermediate focused rerun timed out at the tool layer after 120 seconds and was rerun fresh with a 300 second timeout; the fresh rerun passed.
- Related regression:
  - `.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-adapter-repository-jdbc,seahorse-agent-tests -am "-Dtest=KernelIngestionTaskServiceTests,SeahorseIngestionAndIntentControllerTests,LocalIngestionNodeLogAdapterTests,JdbcIngestionTaskRepositoryAdapterTests,KernelIngestionEngineTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: build success; the JDBC ingestion task repository tests also passed, covering the node-output persistence/read path used for restoration.
- Hygiene:
  - `git diff --check`
  - Result: exit code 0; CRLF normalization warnings only.
- Runtime boundary:
  - This slice proves the code path and repository contract. It does not yet prove a Docker-backed real-document retry-from-node flow across parser, embedding, vector, and keyword index nodes.

### M4 Exact Rollout-Id Attribution

- Previous evidence gap: rollout cost/dashboard summaries were bounded by rollout start/finish timestamps, but run, cost, and approval records could not be queried by exact rollout identity.
- Compatibility decision:
  - Add nullable `rolloutId` fields to existing `AgentRun`, `CostUsageRecord`, and `ApprovalRequest` records.
  - Preserve old constructors and existing API behavior; blank rollout ids normalize to null.
  - Reuse the existing run, cost usage, approval, rollout summary, and JDBC repository boundaries.
  - Keep the rollout time window as an extra bounded range, but make the canonical attribution filter `rollout_id`.
- Implementation:
  - `CostUsageQuery`, `AgentRunQuery`, and `ApprovalRequestQuery` now expose optional `rolloutId`.
  - `JdbcAgentRunRepositoryAdapter`, `JdbcAgentRunQueueRepositoryAdapter`, `JdbcCostUsageRepositoryAdapter`, and `JdbcToolApprovalRequestRepositoryAdapter` read/write the nullable `rollout_id` column and can filter on it.
  - `KernelAgentRolloutCostSummaryService` queries cost records, run status counts, and pending approvals with the rollout id and reports `aggregationScope=AGENT_ROLLOUT_ID`.
  - `AgentRunStartCommand`, `AgentRunStartRequest`, `KernelAgentRunService`, and `SeahorseAgentRunController` now allow explicit rollout attribution when starting runs.
  - `SeahorseCostUsageController` now accepts `rolloutId` on append and aggregate API paths.
  - `resources/database/seahorse_init.sql` and `resources/database/migrations/V33__agent_rollout_exact_attribution.sql` add nullable `rollout_id` columns and rollout indexes for agent runs, approval requests, and cost usage records.
- RED evidence:
  - `.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web -am "-Dtest=KernelAgentRolloutCostSummaryServiceTests,JdbcAgentRunRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests,JdbcToolApprovalRequestRepositoryAdapterTests,SeahorseAgentRolloutControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: failed before implementation because `CostUsageQuery.rolloutId()`, `AgentRunQuery.rolloutId()`, and `ApprovalRequestQuery.rolloutId()` did not exist.
  - `.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web -am "-Dtest=KernelAgentRunServiceTests,SeahorseCostUsageControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: failed before implementation because `AgentRunStartCommand` lacked the explicit rollout attribution constructor/field.
- GREEN evidence:
  - Focused exact-attribution command passed after domain/query/JDBC/schema/summary changes. Test counts: kernel rollout summary 3, web rollout controller 3, JDBC agent-run 5, JDBC cost usage 3, JDBC approval 7.
  - Entry-point command passed after start-run and cost-usage API propagation. Test counts: kernel agent-run service 12, web cost usage controller 2.
- Related regression:
  - `.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelAgentRolloutCostSummaryServiceTests,KernelAgentRunServiceTests,JdbcAgentRunRepositoryAdapterTests,JdbcAgentRunQueueRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests,JdbcToolApprovalRequestRepositoryAdapterTests,SeahorseAgentRolloutControllerTests,SeahorseCostUsageControllerTests,SeahorseAgentRegistryAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: build success; kernel 15 tests, web 5 tests, JDBC 17 tests, auto-configuration 2 tests.
- Hygiene:
  - `git diff --check`
  - Result: exit code 0; CRLF normalization warnings only.
- Runtime boundary:
  - This slice proves explicit rollout attribution through run start, cost append/aggregate, rollout summary, JDBC persistence/query, and schema migration. Automatic approval attribution from tool invocation context remains a future slice because `ToolInvocationRequest` does not currently carry rollout identity.

### M2 Conservative Ingestion Rollback/Compensation

- Previous evidence gap: M2 ingestion governance had retry-from-node, node output restoration, quarantine linkage, and chunk-log observability, but did not yet expose a bounded "undo this completed ingestion task" path.
- Compatibility decision:
  - Roll back only when task metadata contains an explicit document target: `docId` or `documentId`, plus `kbId` or `knowledgeBaseId`.
  - Reject running tasks and tasks without an unambiguous document target instead of guessing affected documents from source metadata.
  - Keep cleanup ownership in the existing knowledge document deletion service; do not create a second vector/index cleanup owner under ingestion.
  - Preserve the existing ingestion task service constructor and default unsupported compensation path for tests or embeddings that do not wire a document service.
- Implementation:
  - Added `IngestionTaskCompensationPort`, `IngestionTaskRollbackTarget`, and `IngestionTaskRollbackResult`.
  - Added `IngestionTaskInboundPort.rollback(taskId, operator)`.
  - `KernelIngestionTaskService.rollback(...)` validates task state and target metadata, delegates compensation, then updates the task to `rolled_back` with chunk count `0`.
  - Rollback metadata records `rollbackStatus`, `rollbackDocId`, `rollbackKbId`, and `rollbackOperator`.
  - Added `POST /ingestion/tasks/{id}/rollback` in `SeahorseIngestionTaskController`.
  - `SeahorseAgentKernelKnowledgeAutoConfiguration` now wires a default compensation port that calls `KnowledgeDocumentInboundPort.delete(target.docId(), operator)`.
- RED evidence:
  - `.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web -am "-Dtest=KernelIngestionTaskServiceTests,SeahorseIngestionAndIntentControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: failed before implementation because rollback result/target/compensation types, the inbound rollback method, and the web rollback endpoint did not exist.
- GREEN evidence:
  - Same focused command passed after implementation.
  - Test counts: `KernelIngestionTaskServiceTests` 6 and `SeahorseIngestionAndIntentControllerTests` 15.
- Related regression:
  - `.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelIngestionTaskServiceTests,SeahorseIngestionAndIntentControllerTests,SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentKernelDocumentRefreshAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: build success; kernel ingestion task service 6 tests, web ingestion/intent controller 15 tests, auto-configuration 3 tests.
- Hygiene:
  - `git diff --check`
  - Result: exit code 0; CRLF normalization warnings only.
- Runtime boundary:
  - This slice proves the service, API, and Spring wiring contract. It does not yet prove Docker-backed rollback across a real parser/embed/vector/keyword-index document pipeline.

### Near-Term RAG Smoke Retrieval Scope

- Previous live smoke warning: runtime logs showed `Vector global search skipped collection after failure` for stale Milvus collection `e2e_collection_99999`.
- Database diagnosis: `t_knowledge_base.id=99999` was still active for tenant `default`, had `collection_name=e2e_collection_99999`, and had enabled chunks, so `JdbcKnowledgeBaseQueryAdapter.listSearchableKnowledgeBases(...)` correctly exposed it as searchable. The Milvus collection itself was absent, so vector search warned and skipped it.
- Root cause: the canonical `/rag/v3/chat` smoke query did not scope retrieval to the knowledge base created by that smoke run. Global vector search therefore scanned recent searchable KB refs, including stale DB rows unrelated to the smoke KB.
- Repair boundary:
  - Add optional `knowledgeBaseIds` to the inbound chat request/command and normalize/deduplicate it.
  - Carry the scope through `StreamChatContext`, `KernelChatInboundService`, and `KernelChatPreparationSupport`.
  - Extend `RetrievalContextPort` / `KernelRetrievalEngine` with a filter-aware overload while keeping legacy overloads backward compatible.
  - In `VectorGlobalSearchFeature`, pre-filter searchable KB refs by `RetrievalFilter.system().knowledgeBaseIds()` and `collectionNames()` before vector calls.
  - Update `scripts/e2e-backend-smoke.ps1` so the RAG SSE check calls `/rag/v3/chat?...&knowledgeBaseIds=$kbId`.
  - Do not delete seeded DB data in this slice, and do not add Milvus existence checks inside JDBC query adapters; that would cross adapter boundaries.
- RED evidence:
  - `.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-tests -am "-Dtest=StreamChatCommandTests,SeahorseChatControllerTests,KernelChatPipelineTests,VectorGlobalSearchFeatureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: failed before implementation because `StreamChatCommand` had no `knowledgeBaseIds` constructor/accessor and downstream retrieval did not accept the scoped filter.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-smoke-contracts.ps1`
  - Result: failed before the smoke script included `knowledgeBaseIds`.
- GREEN evidence:
  - `.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-tests -am "-Dtest=StreamChatCommandTests,SeahorseChatControllerTests,KernelChatPipelineTests,VectorGlobalSearchFeatureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: build success; `StreamChatCommandTests` 7 tests, `SeahorseChatControllerTests` 5 tests, `KernelChatPipelineTests` 11 tests, and `VectorGlobalSearchFeatureTests` 7 tests.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-smoke-contracts.ps1`
  - Result: smoke contract check passed for `scripts/e2e-backend-smoke.ps1`.
- Related regression:
  - `.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-tests -am "-Dtest=StreamChatCommandTests,SeahorseChatControllerTests,KernelChatPipelineTests,KernelChatInboundServiceTests,VectorGlobalSearchFeatureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: build success; `StreamChatCommandTests` 7 tests, `SeahorseChatControllerTests` 5 tests, `KernelChatInboundServiceTests` 6 tests, `KernelChatPipelineTests` 11 tests, and `VectorGlobalSearchFeatureTests` 7 tests.
  - `$errors = $null; [void][System.Management.Automation.PSParser]::Tokenize((Get-Content -LiteralPath 'D:\code\seahorse-agent\scripts\e2e-backend-smoke.ps1' -Raw), [ref]$errors); if ($errors -and $errors.Count -gt 0) { $errors | ForEach-Object { Write-Host $_.Message }; exit 1 }`
  - Result: passed; no PowerShell parse errors.
  - `git diff --check`
  - Result: exit code 0; CRLF normalization warnings only.
- Memory retrieval follow-up:
  - Additional root cause found after scoped RAG tests: memory activation loads business-document context before the main chat retrieval path, and the default Spring `MemoryBusinessDocumentRetrieverPort` adapter still called the unfiltered retrieval overload. That kept the stale global collection reachable even when `/rag/v3/chat` carried `knowledgeBaseIds`.
  - Repair boundary: add normalized `knowledgeBaseIds` to `MemoryLoadRequest`, pass the chat scope through `KernelChatPreparationSupport`, `KernelChatInboundService`, `DefaultMemoryRetrievalPipeline`, and `HybridMemoryRecallPipeline`, and add a backward-compatible scoped overload to `MemoryBusinessDocumentRetrieverPort`.
  - Spring adapter behavior: the legacy 3-argument business-document retrieval remains global; the scoped overload builds a `RetrievalFilter` with `tenantId` and `knowledgeBaseIds(scope)` before calling `KernelRetrievalEngine.retrieveKnowledgeChannels(...)`.
- Memory retrieval RED/GREEN:
  - RED: `.\mvnw -pl seahorse-agent-tests,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelChatPipelineTests#shouldPassKnowledgeBaseScopeToMemoryActivation,MemoryRetrievalPipelineTests#shouldPassKnowledgeBaseScopeToBusinessDocumentRetriever,HybridMemoryRecallPipelineTests#shouldPassKnowledgeBaseScopeToBusinessDocumentRetriever,SeahorseAgentKernelRetrievalAutoConfigurationTests#shouldPassKnowledgeBaseScopeIntoMemoryBusinessDocumentRetrievalFilter" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: failed before implementation because `MemoryBusinessDocumentRetrieverPort.retrieve` only accepted the unscoped 3-argument call.
  - GREEN: same command passed after scope propagation; Spring adapter 1 test plus chat/default/hybrid memory tests passed with 0 failures.
  - Regression: `.\mvnw -pl seahorse-agent-tests,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelChatPipelineTests,KernelChatInboundServiceTests,MemoryRetrievalPipelineTests,HybridMemoryRecallPipelineTests,DefaultMemoryEnginePortTests,SeahorseAgentKernelRetrievalAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - Result: 97 tests, 0 failures/errors/skips.
  - Static checks: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-smoke-contracts.ps1`, PowerShell parser tokenization for `scripts\e2e-backend-smoke.ps1`, and `git diff --check` all passed; `git diff --check` only emitted CRLF normalization warnings.
- Fresh full-compose runtime proof:
  - Backend image before recreate: `docker inspect seahorse-agent-backend:latest --format "{{.Id}} {{.Created}}"` returned `sha256:0c6ed8b2bfae51b06f1352e88f79debf1016873299b9c4ea58711a299f009e82 2026-06-15T18:48:23.639516541Z`.
  - `docker compose -f docker-compose.full.yml up -d --no-deps backend` recreated and started `seahorse-backend`.
  - Running container after recreate: `docker inspect seahorse-backend --format "{{.Image}} {{.Created}}"` returned `sha256:0c6ed8b2bfae51b06f1352e88f79debf1016873299b9c4ea58711a299f009e82 2026-06-15T18:51:16.248252576Z`.
  - Health wait: `GET http://localhost:9090/actuator/health` returned `{"status":"UP"}`.
  - Smoke start: `2026-06-16T02:52:23.6136959+08:00` / `2026-06-15T18:52:23Z`.
  - `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1`
  - Result: 18 checks, 0 failed. IDs: `kbId=324984540316659712`, `docId=324984541398790144`, `traceId=324984552765353984`.
  - Runtime DB evidence:
    - `t_knowledge_chunk`: `doc_id=324984541398790144`, `kb_id=324984540316659712`, `chunks=1`, `enabled_chunks=1`, latest chunk time `2026-06-15 18:52:34.121006`.
    - `t_knowledge_document_chunk_log`: one row for `doc_id=324984541398790144`, `status=success`, `chunk_count=1`, `tenant_id=default`, `create_time=2026-06-15 18:52:34.370377`.
    - `t_rag_trace_run`: `trace_id=324984552765353984`, `status=SUCCESS`, start `2026-06-15 18:52:35.3`, end `2026-06-15 18:52:38.598`.
    - `t_rag_trace_node`: `nodes=11`, `retrieval_nodes=5` for `trace_id=324984552765353984`.
    - `t_user_profile_fact`: admin/default has `ACTIVE=7`, `HISTORICAL=16`, with latest active update `2026-06-15 18:52:35.49925`.
  - Runtime log evidence:
    - `docker logs --since "2026-06-15T18:52:23Z" seahorse-backend 2>&1 | Select-String -Pattern 'e2e_collection_99999|Vector global search skipped collection|collection not found'`
    - Result: no matches.
  - Runtime status: scoped canonical smoke no longer triggers the stale `e2e_collection_99999` Milvus warning on the fresh backend image.

## Verification Commands

```powershell
.\mvnw -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web -am "-Dtest=KernelRetrievalStrategyTemplateServiceTests,JdbcRetrievalStrategyTemplateRepositoryAdapterTests,SeahorseRetrievalAndMemoryControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success.

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-spring-boot-autoconfigure,seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web -am "-Dtest=KernelIngestionTaskServiceTests,KernelProductionGateServiceTests,KernelAgentRolloutServiceTests,SeahorseAgentRegistryAutoConfigurationTests,KernelMemoryGovernanceServiceTests,KernelRetrievalStrategyTemplateServiceTests,JdbcRetrievalStrategyTemplateRepositoryAdapterTests,SeahorseRetrievalAndMemoryControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success.

```powershell
git diff --check
```

Result: no whitespace errors; CRLF warnings only.

```powershell
python C:\Users\miracle\.codex\aegis\scripts\aegis-workspace.py bundle --root D:\code\seahorse-agent --work 2026-06-15-architecture-roadmap-implementation
python C:\Users\miracle\.codex\aegis\scripts\aegis-workspace.py check --root D:\code\seahorse-agent
```

Result: still structurally not green for known workspace reasons. `bundle` failed because `task-intent-draft.json` is missing; `check` failed because `docs/aegis/README.md`, `INDEX.md`, `BASELINE-GOVERNANCE.md`, `adr/`, `baseline/`, `specs/`, and `plans/` are missing.

```powershell
.\mvnw -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests,KernelAgentLoopToolGatewayTests,KernelChatAgentRunStoreTests,KernelAgentRunResumeServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

RED result for M4 automatic rollout-id attribution: failed at test compile because `AgentLoopRequest.Builder.rolloutId(...)`, `ToolInvocationRequest.rolloutId()`, and the rollout-aware `ToolInvocationRequest(...)` constructor did not exist.

```powershell
.\mvnw -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests,KernelAgentLoopToolGatewayTests,KernelAgentRunResumeServiceTests,KernelChatAgentRunStoreTests#shouldPropagateAgentRunRolloutIdToToolGatewayRequest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

GREEN result for M4 automatic rollout-id attribution: build success; 35 tests run, 0 failures. The tests prove rollout id propagation through approval persistence, loop tool invocation, chat-run tool invocation, and resume fallback from `ApprovalRequest.rolloutId()` when an older checkpoint lacks `rolloutId`.

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=LocalToolGatewayPortPolicyTests,LocalToolGatewayPortAuditTests,KernelAgentLoopToolGatewayTests,KernelAgentRunResumeServiceTests,KernelAgentRunServiceTests,KernelAgentRolloutCostSummaryServiceTests,SeahorseAgentChatRunStoreAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Related regression result: kernel module completed successfully with 53 tests run and 0 failures across policy, approval audit, loop gateway, resume, run service, and rollout cost summary. The reactor later failed in `seahorse-agent-spring-boot-autoconfigure` because `SeahorseAgentChatRunStoreAutoConfigurationTests` has two existing configuration assertion failures: `shouldDisableAgentRuntimeOnlyWhenAgentModeAndWebTaskRuntimeAreBothDisabled` found a `KernelAgentLoop` bean, and `shouldWireLocalAgentAsToolPortWhenAdvancedLocalAgentFeatureIsEnabled` did not find a `LocalAgentAsToolPort` bean. These are recorded as unrelated auto-configuration drift, not rollout-id approval attribution failures.

```powershell
git diff --check
```

Result: no whitespace errors; CRLF warnings only.

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web -am "-Dtest=KernelIngestionTaskServiceTests,SeahorseIngestionAndIntentControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

RED result for M2 rollback/compensation: failed before implementation because rollback result/target/compensation types, the inbound rollback method, and the web rollback endpoint did not exist.

GREEN result for M2 rollback/compensation: build success after implementation; `KernelIngestionTaskServiceTests` ran 6 tests and `SeahorseIngestionAndIntentControllerTests` ran 15 tests.

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelIngestionTaskServiceTests,SeahorseIngestionAndIntentControllerTests,SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentKernelDocumentRefreshAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success; kernel ingestion task service 6 tests, web ingestion/intent controller 15 tests, auto-configuration 3 tests. This covers the rollback service contract, controller path, and default Spring compensation wiring.

```powershell
git diff --check
```

Result after M2 rollback/compensation slice: exit code 0; CRLF normalization warnings only.

```powershell
python C:\Users\miracle\.codex\aegis\scripts\aegis-workspace.py bundle --root D:\code\seahorse-agent --work 2026-06-15-architecture-roadmap-implementation
python C:\Users\miracle\.codex\aegis\scripts\aegis-workspace.py check --root D:\code\seahorse-agent
```

Result: structural checks did not pass. `bundle` failed because `task-intent-draft.json` is missing for this work record. `check` failed because the repository does not currently contain the full standard Aegis workspace scaffold: `docs/aegis/README.md`, `INDEX.md`, `BASELINE-GOVERNANCE.md`, `adr/`, `baseline/`, `specs/`, and `plans/`.

```powershell
rg -n "file://resources/docker|file://docs/quick-start|qwen-(plus|emb-8b)" docs --glob "*.md" --glob "!docs/architecture/current-code-architecture.md"
rg -n "localhost:8080/api/skills" docs/skills/SKILL-OPERATIONS.md
```

Result: no matches.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-smoke-contracts.ps1
```

RED result for near-term verification baseline: failed before script updates because `scripts/e2e-backend-smoke.ps1` did not yet cover the current Bearer auth, knowledge document upload/chunk, RAG SSE, trace, or memory governance endpoints expected by the canonical smoke contract.

GREEN result after updating the script: smoke contract check passed for `scripts/e2e-backend-smoke.ps1`.

```powershell
$errors = $null; [void][System.Management.Automation.PSParser]::Tokenize((Get-Content -LiteralPath 'D:\code\seahorse-agent\scripts\e2e-backend-smoke.ps1' -Raw), [ref]$errors); if ($errors -and $errors.Count -gt 0) { $errors | ForEach-Object { Write-Host $_.Message }; exit 1 }
```

Result: passed; no PowerShell parse errors detected in the updated smoke script.

```powershell
git diff --check -- scripts\e2e-backend-smoke.ps1 scripts\verify-smoke-contracts.ps1
```

Result: no whitespace errors; CRLF warnings only.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-smoke-contracts.ps1
git diff --check
```

Final result after live smoke evidence updates: smoke contract check passed. `git diff --check` returned exit code 0 with CRLF normalization warnings only.

```powershell
.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcKnowledgeBaseQueryAdapterTests#findByIdShouldReturnKnowledgeBaseEvenBeforeFirstChunkExists" "-Dsurefire.failIfNoSpecifiedTests=false" test
.\mvnw -pl seahorse-agent-tests -am "-Dtest=KernelKnowledgeDocumentServiceTests#shouldUploadDocumentBeforeKnowledgeBaseHasAnyChunks" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

RED result: the tests failed before the upload owner fix because the new query/service behavior did not exist.
GREEN result: both focused tests passed after adding `KnowledgeBaseQueryPort.findById`, the JDBC implementation, and the service owner change.

```powershell
docker compose -f docker-compose.full.yml build backend
docker compose -f docker-compose.full.yml up -d --no-deps backend
docker inspect seahorse-backend --format "{{.Image}} {{.Created}}"
curl.exe -sS http://localhost:9090/actuator/health
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1
```

Result: backend rebuilt, container recreated with image `sha256:39558a67f6b9a0a860c10bac9182ebeee8376a2adae32c73173c4a6dd37f110e` created `2026-06-15T16:40:01.173595306Z`, health returned `UP`, and live smoke passed 18 checks with 0 failures.

```powershell
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "select doc_id, kb_id, count(*) as chunks, count(*) filter (where enabled=1 and deleted=0) as enabled_chunks from t_knowledge_chunk where doc_id=324951590341529600 group by doc_id,kb_id;"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "select n.trace_id, count(*) as nodes, count(*) filter (where n.node_type ilike '%retriev%' or n.node_name ilike '%retriev%') as retrieval_nodes from t_rag_trace_node n where n.deleted=0 and n.trace_id=324951594456141824 group by n.trace_id;"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "select status, count(*) as facts from t_user_profile_fact where user_id='2001523723396308993' and tenant_id='default' and deleted=0 group by status order by status;"
```

Result: smoke document has 1 enabled chunk; latest smoke trace has 11 nodes including 4 retrieval nodes; admin/default profile facts include 7 active and 16 historical records.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-smoke-contracts.ps1
```

RED result for smoke assertion hardening: failed after updating the contract checker because the smoke script did not yet request `/rag/traces/runs/$traceId/nodes`, assert retrieval trace nodes, or assert non-empty profile facts.

GREEN result after implementation: contract check passed.

```powershell
$errors = $null; [void][System.Management.Automation.PSParser]::Tokenize((Get-Content -LiteralPath 'D:\code\seahorse-agent\scripts\e2e-backend-smoke.ps1' -Raw), [ref]$errors); if ($errors -and $errors.Count -gt 0) { $errors | ForEach-Object { Write-Host $_.Message }; exit 1 }
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\e2e-backend-smoke.ps1
```

Result: PowerShell parser check passed. Live smoke passed 18 checks with 0 failures after hardening; `RAG trace API smoke traceId=324954351028228096`, `Memory/profile smoke facts=7`.

```powershell
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "select doc_id, kb_id, count(*) as chunks, count(*) filter (where enabled=1 and deleted=0) as enabled_chunks from t_knowledge_chunk where doc_id=324954349530861568 group by doc_id,kb_id;"
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "select n.trace_id, count(*) as nodes, count(*) filter (where n.node_type ilike '%retriev%' or n.node_name ilike '%retriev%') as retrieval_nodes from t_rag_trace_node n where n.deleted=0 and n.trace_id=324954351028228096 group by n.trace_id;"
```

Result: hardened smoke document has 1 enabled chunk; hardened smoke trace has 11 nodes including 4 retrieval nodes.

```powershell
python C:\Users\miracle\.codex\aegis\scripts\aegis-workspace.py bundle --root D:\code\seahorse-agent --work 2026-06-15-architecture-roadmap-implementation
python C:\Users\miracle\.codex\aegis\scripts\aegis-workspace.py check --root D:\code\seahorse-agent
```

Result: still not green for structural reasons. `bundle` failed because `task-intent-draft.json` is missing. `check` failed because the repository still lacks the standard `docs/aegis/README.md`, `INDEX.md`, `BASELINE-GOVERNANCE.md`, `adr/`, `baseline/`, `specs/`, and `plans/` scaffold.

```powershell
.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcIngestionTaskRepositoryAdapterTests#shouldAttachUnresolvedQuarantineCountToTaskRecords" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success.

```powershell
.\mvnw -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcIngestionTaskRepositoryAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success.

```powershell
.\mvnw -pl seahorse-agent-kernel "-Dtest=KernelAgentRolloutCostSummaryServiceTests" test
```

RED result: failed before implementation because the rollout cost summary domain/port/service did not exist.
GREEN result: build success after implementation.

```powershell
.\mvnw -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseAgentRolloutControllerTests#shouldExposeRolloutCostSummary" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success.

```powershell
.\mvnw -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SeahorseAgentRegistryAutoConfigurationTests#shouldCreatePhaseOneRegistryAndRunStoreBeans" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success.

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelIngestionTaskServiceTests,JdbcIngestionTaskRepositoryAdapterTests,KernelAgentRolloutServiceTests,KernelAgentRolloutCostSummaryServiceTests,SeahorseAgentRolloutControllerTests,SeahorseAgentRegistryAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success.

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter-all "-Dtest=SeahorseAgentStarterAllSmokeTests" test
```

RED result: failed at test compile because `StarterAllAdapterAcceptanceMatrix` did not exist.

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter-all -am "-Dtest=SeahorseAgentStarterAllSmokeTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

GREEN result: build success; `SeahorseAgentStarterAllSmokeTests` ran 5 tests.

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure,seahorse-agent-spring-boot-starter-all -am "-Dtest=KernelIngestionTaskServiceTests,JdbcIngestionTaskRepositoryAdapterTests,KernelAgentRolloutServiceTests,KernelAgentRolloutCostSummaryServiceTests,SeahorseAgentRolloutControllerTests,SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentStarterAllSmokeTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success; kernel 8 tests, web rollout 3 tests, JDBC ingestion 4 tests, auto-configuration 2 tests, starter-all 5 tests.

```powershell
.\mvnw -pl seahorse-agent-kernel "-Dtest=KernelAgentRolloutCostSummaryServiceTests" test
```

RED result for rollout dashboard metrics: failed as expected before implementation because the service constructor, summary fields, and query constructors did not exist.

GREEN result for rollout dashboard metrics: build success; 2 tests run.

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web -am "-Dtest=KernelAgentRolloutCostSummaryServiceTests,JdbcAgentRunRepositoryAdapterTests,JdbcToolApprovalRequestRepositoryAdapterTests,SeahorseAgentRolloutControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success; kernel rollout summary 2 tests, web rollout 3 tests, JDBC agent-run 4 tests, JDBC approval 6 tests.

```powershell
.\mvnw -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SeahorseAgentRegistryAutoConfigurationTests#shouldCreatePhaseOneRegistryAndRunStoreBeans" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success; 1 test run.

```powershell
git diff --check
```

Result: no whitespace errors; CRLF warnings only.

```powershell
.\mvnw -pl seahorse-agent-kernel "-Dtest=KernelRetrievalEvaluationServiceTests#shouldAttachCaseDiagnosticsForFailedCaseDrillDown" test
```

RED result for M1 case diagnostics: failed at test compile because `RetrievalEvaluationCaseResult.diagnostics()` did not exist.

GREEN result for M1 case diagnostics: build success; 1 test run.

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc -am "-Dtest=KernelRetrievalEvaluationServiceTests,JdbcRetrievalEvaluationDatasetRepositoryAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success; kernel retrieval evaluation 3 tests, JDBC retrieval evaluation dataset repository 1 test.

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web -am "-Dtest=KernelRetrievalEvaluationServiceTests,JdbcRetrievalEvaluationDatasetRepositoryAdapterTests,SeahorseRetrievalAndMemoryControllerTests#shouldExposeRetrievalComparisonCaseDiagnostics" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success; kernel retrieval evaluation 3 tests, JDBC retrieval evaluation dataset repository 1 test, web comparison diagnostics contract 1 test.

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-tests -am "-Dtest=KernelRetrievalEvaluationServiceTests,JdbcRetrievalEvaluationDatasetRepositoryAdapterTests,SeahorseRetrievalAndMemoryControllerTests,KernelRetrievalStrategyTemplateServiceTests,JdbcRetrievalStrategyTemplateRepositoryAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success; kernel module retrieval evaluation 3 tests, web retrieval/memory controller 15 tests, JDBC retrieval evaluation dataset repository 1 test, seahorse-agent-tests retrieval evaluation/strategy 11 tests, strategy template promotion 9 tests.

```powershell
.\mvnw -pl seahorse-agent-kernel "-Dtest=KernelRetrievalEvaluationServiceTests#shouldAttachTraceIdToEvaluationCaseDiagnostics" test
```

RED result for M1 trace link: failed at test compile because `KernelRetrievalEvaluationService` lacked a trace-recorder constructor, `RetrievalEvaluationCaseDiagnostics.traceId()` did not exist, and `KernelRetrievalEngine` lacked the filter/options/trace-scope overload.

GREEN result for M1 trace link: build success; 1 test run.

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-tests -am "-Dtest=KernelRetrievalEvaluationServiceTests,JdbcRetrievalEvaluationDatasetRepositoryAdapterTests,SeahorseRetrievalAndMemoryControllerTests,KernelRetrievalStrategyTemplateServiceTests,JdbcRetrievalStrategyTemplateRepositoryAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

First regression result for M1 trace link: failed in `seahorse-agent-tests` because its test double only overrode the legacy 4-argument retrieval method while the new canonical path calls the 5-argument trace-aware method. Root cause was a stale test double, not production retrieval behavior.

Final regression result after updating the test double: build success; kernel retrieval evaluation 4 tests, web retrieval/memory controller 15 tests, JDBC retrieval evaluation dataset and strategy template repository tests 8 tests, seahorse-agent-tests retrieval evaluation/strategy 11 tests.

```powershell
.\mvnw -pl seahorse-agent-tests -am "-Dtest=KernelRetrievalEvaluationServiceTests,KernelRetrievalStrategyTemplateServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result after stale test-double fix: build success; 11 tests run.

```powershell
.\mvnw -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SeahorseAgentKernelRetrievalAutoConfigurationTests,SeahorseAgentRegistryAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success; 3 tests run.

```powershell
git diff --check
```

Result: no whitespace errors; CRLF warnings only.

### M5 Starter-All / Autoconfigure Property Drift Stabilization

- Previous drift: `SeahorseAgentKernelAgentAutoConfiguration` still read legacy dotted `seahorse.agent.*` properties while the tests and project configuration use canonical dashed `seahorse-agent.*` properties.
- Production fix: moved agent auto-configuration property constants and the class-level kernel condition to canonical `seahorse-agent.*`; custom condition code now checks canonical keys first and falls back to legacy `seahorse.agent.*`.
- Test alignment:
  - MCP allowlist catalog assertions now filter `ToolProvider.MCP` entries because built-in tool catalog persistence is an intentional behavior covered by `BuiltInAgentToolRegistrarTests`.
  - Built-in feature switch coverage now explicitly disables `deferred-search` and treats `load_skill_resource` plus `get_current_datetime` as baseline agent tools.

```powershell
.\mvnw -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SeahorseAgentChatRunStoreAutoConfigurationTests#shouldDisableAgentRuntimeOnlyWhenAgentModeAndWebTaskRuntimeAreBothDisabled+shouldWireLocalAgentAsToolPortWhenAdvancedLocalAgentFeatureIsEnabled" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

RED result: failed before the fix because canonical `seahorse-agent.chat.agent-mode-enabled=false` / `web-task-agent-enabled=false` did not suppress `KernelAgentLoop`, and canonical advanced local-agent flags did not create `LocalAgentAsToolPort`.

GREEN result: same focused command passed after canonical property constants and legacy fallback for custom conditions.

```powershell
.\mvnw -pl seahorse-agent-spring-boot-autoconfigure,seahorse-agent-tests -am "-Dtest=SeahorseAgentChatRunStoreAutoConfigurationTests,SeahorseAgentKernelAgentAutoConfigurationTests,McpToolAllowlistRegistrarTests,ToolSearchAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Intermediate result: after the property fix, MCP allowlist and deferred-search behavior were corrected, but the first complete rerun failed in `SeahorseAgentKernelAgentAutoConfigurationTests#shouldHonorBuiltInToolFeatureSwitches` because the test did not explicitly disable `deferred-search` and did not include the baseline `load_skill_resource` tool.

Final result: build success. Autoconfigure ran `McpToolAllowlistRegistrarTests` 2 tests, `SeahorseAgentChatRunStoreAutoConfigurationTests` 21 tests, and `ToolSearchAutoConfigurationTests` 2 tests. `seahorse-agent-tests` ran `SeahorseAgentKernelAgentAutoConfigurationTests` 9 tests.

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-spring-boot-autoconfigure,seahorse-agent-spring-boot-starter-all -am "-Dtest=LocalToolGatewayPortPolicyTests,LocalToolGatewayPortAuditTests,KernelAgentLoopToolGatewayTests,KernelAgentRunResumeServiceTests,KernelAgentRunServiceTests,KernelAgentRolloutCostSummaryServiceTests,SeahorseAgentChatRunStoreAutoConfigurationTests,SeahorseAgentStarterAllSmokeTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success. Kernel agent/runtime tests ran 53 tests, autoconfigure chat-run-store tests ran 21 tests, and starter-all smoke tests ran 5 tests.

```powershell
git diff --check
```

Result: exit code 0; CRLF normalization warnings only.

### M5 Starter-All Acceptance Matrix Canonical Property Coordinates

- Previous drift: the public starter-all heavy-adapter acceptance matrix still listed many legacy `seahorse.agent.*` activation properties even though the current architecture baseline documents canonical `seahorse-agent.*` configuration coordinates.
- Scope: keep this slice inside `seahorse-agent-spring-boot-starter-all`. It aligns the executable acceptance matrix and does not claim a full migration of every adapter auto-configuration condition.
- Implementation:
  - Added `SeahorseAgentStarterAllSmokeTests#starterAllAcceptanceMatrixUsesCanonicalSeahorseAgentProperties`.
  - Updated `StarterAllAdapterAcceptanceMatrix` activation properties to use `seahorse-agent.*`.
  - Left infrastructure-owned `spring.*` properties unchanged.

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter-all -am "-Dtest=SeahorseAgentStarterAllSmokeTests#starterAllAcceptanceMatrixUsesCanonicalSeahorseAgentProperties" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

First run result: timed out at the tool layer after 120 seconds, so it was not used as RED evidence.

Fresh RED result: failed as expected because matrix entries still exposed legacy `seahorse.agent.*` activation properties such as `seahorse.agent.adapters.ai.embedding-model`, `seahorse.agent.adapters.vector.type`, `seahorse.agent.adapters.cache.type`, `seahorse.agent.adapters.storage.type`, `seahorse.agent.adapters.mq.type`, and keyword-search/index properties.

GREEN result: same focused command passed after the matrix was switched to canonical `seahorse-agent.*` properties.

```powershell
.\mvnw -pl seahorse-agent-spring-boot-starter-all -am "-Dtest=SeahorseAgentStarterAllSmokeTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success. `SeahorseAgentStarterAllSmokeTests` ran 6 tests covering heavy adapter classpath, auto-configuration candidates, matrix coverage, runtime bean coordinates, canonical property coordinates, and auto-configuration discoverability.

### M5 AI/Vector Adapter Selector Canonical Property Drift

- Previous drift: starter-all public matrix now exposed canonical `seahorse-agent.*` adapter coordinates, but AI and Vector auto-configuration selector conditions still depended on legacy `seahorse.agent.*` property names.
- Scope: this slice covers only AI and Vector adapter selectors. Cache, MQ, Storage, Search, and other adapter selectors remain separate follow-up slices.
- Implementation:
  - Added `@ConditionalOnSeahorseAgentProperty` as a canonical-first Spring condition helper.
  - The helper checks canonical `seahorse-agent.*` properties first and falls back to legacy `seahorse.agent.*` names.
  - The helper supports repeatable conditions so multiple required properties on a bean all have to match.
  - `SeahorseAgentAiAdapterAutoConfiguration` and `SeahorseAgentVectorAdapterAutoConfiguration` now use the helper for selector conditions.
  - `seahorseDedicatedEmbeddingModelPort` now also requires `seahorse-agent.adapters.ai.type=openai-compatible`, so canonical `ai.type=mock` cannot be shadowed by the dedicated OpenAI-compatible embedding bean.
- RED evidence:

```powershell
.\mvnw -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SeahorseAgentAiAdapterAutoConfigurationTests#shouldConfigureMockEmbeddingFromCanonicalAdapterProperties,SeahorseAgentVectorAdapterAutoConfigurationTests#shouldSelectMilvusFromCanonicalAdapterProperties" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

First RED result: failed before implementation because canonical `seahorse-agent.adapters.ai.type=mock` did not create a mock embedding port, and canonical `seahorse-agent.adapters.vector.type=milvus` did not select `MilvusVectorAdapter` over legacy vector `noop`.

Intermediate result: after adding the AI type condition to the dedicated embedding bean, the same command still failed for AI because repeatable `@ConditionalOnSeahorseAgentProperty` annotations were not expanded from the generated container. This proved the remaining root cause was the condition helper, not the mock adapter.

GREEN result: same command passed after expanding repeatable condition containers and keeping the AI dedicated embedding guard. `SeahorseAgentAiAdapterAutoConfigurationTests#shouldConfigureMockEmbeddingFromCanonicalAdapterProperties` and `SeahorseAgentVectorAdapterAutoConfigurationTests#shouldSelectMilvusFromCanonicalAdapterProperties` both passed.

```powershell
.\mvnw -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SeahorseAgentAiAdapterAutoConfigurationTests,SeahorseAgentVectorAdapterAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success; AI auto-configuration tests ran 6 and Vector auto-configuration tests ran 5.

```powershell
.\mvnw -pl seahorse-agent-spring-boot-autoconfigure,seahorse-agent-spring-boot-starter-all -am "-Dtest=SeahorseAgentAiAdapterAutoConfigurationTests,SeahorseAgentVectorAdapterAutoConfigurationTests,SeahorseAgentStarterAllSmokeTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: build success; autoconfigure AI/Vector tests ran 11 and starter-all smoke tests ran 6.

## Residual Risk

- The implemented work is a meaningful roadmap subset, not the whole near/mid-term roadmap.
- Full repository test suite was not run after this slice.
- The near-term verification slice now proves a live `docker-compose.full.yml` backend smoke path across login, document upload/chunk, RAG SSE, trace nodes with retrieval evidence, memory/profile facts, governance, and SRE health. It does not prove long-running stability, frontend UI flows, exact RAG answer quality, or every heavy adapter's standalone business action.
- The chunk-log endpoint empty-page gap is repaired at code, static-smoke-contract, and fresh full-compose runtime level.
- M2 retry-from-node context restoration is repaired at service/controller/engine/local-adapter/JDBC-contract level. Full real-dependency retry recovery across parser/embed/index nodes remains an open runtime evidence item.
- M2 rollback/compensation is repaired at service/controller/Spring-wiring level for completed tasks with explicit document targets. Full Docker-backed rollback across parser/embed/vector/keyword-index side effects and source-level multi-document rollback remain open.
- Runtime smoke logs previously included warnings for an older/stale Milvus collection name `e2e_collection_99999` being absent. The scoped-RAG plus scoped-memory repair is now covered by tests, static checks, a fresh Docker-backed live smoke, database evidence, and a post-smoke log window with no stale collection matches.
- Aegis workspace helper structural checks are not green because the standard workspace scaffold and JSON sidecars are missing.
- Remaining open roadmap items should be implemented as separate TDD slices with their own evidence.
- M5 acceptance matrix is not full compose E2E; real Redis/Pulsar/Milvus/Elasticsearch/S3/OpenAI-compatible smoke execution remains open.
- M5 property drift is covered for the agent-runtime auto-configuration, starter-all public acceptance matrix, AI adapter selector, and Vector adapter selector surfaces. Broader Cache/MQ/Storage/Search adapter auto-configuration alias migration and real external adapter actions remain open.
- M4 dashboard metrics now use exact rollout-id attribution for records that carry `rollout_id`; automatic approval attribution from tool invocation context is covered at kernel code-path level. Full Docker-backed rollout approval/resume E2E remains open.
- M1 case diagnostics now include trace ids through the existing RAG trace recorder code path, but this evidence does not prove a Docker-backed `/admin/traces` read path, persisted trace query, or UI/API drill-down E2E flow.
