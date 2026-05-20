# TodoCheckpointDraft

## Current Todo

- [x] Read relevant Aegis skills.
- [x] Read existing memory architecture docs.
- [x] Inspect current worktree and existing partial fixes.
- [x] Write Aegis spec.
- [x] Write Aegis implementation plan.
- [x] Add/adjust regression tests.
- [x] Implement user identity source-of-truth.
- [x] Implement robust extraction and shared prompt formatter.
- [x] Fix memory repository auto-configuration and JDBC JSON writes.
- [x] Run target regression tests.
- [x] Rebuild and restart Docker backend.
- [x] Run Docker E2E for write and cross-session recall.
- [x] Split Phase 2 capture policy into candidate extractor and value assessor.
- [x] Add explainable Phase 2 policy tests.
- [x] Add governance persistence tests for quality snapshots and conflict records.
- [x] Implement quality snapshot persistence and high-precision semantic-key conflict logging.
- [x] Wire governance repositories through Spring auto-configuration.
- [x] Verify JDBC save/read for memory quality snapshots and conflict logs.
- [x] Rebuild and restart Docker backend after governance changes.
- [x] Run Docker governance E2E for quality snapshot and pending conflict visibility.
- [x] Update evidence and drift check.
- [x] Run final fresh verification for target regression, JDBC repository, and starter package paths.

## Active Slice

Phase 1 through Phase 4 planned implementation slices are complete within the existing compatibility boundary. The latest slice closes the governance loop by persisting quality snapshots and pending semantic-key conflicts without adding new tables or changing public `MemoryEnginePort` methods.

## Completed Todos

- Confirmed baseline failure mode: historical messages were written as `user_id = default`, and short-term memory had no captured student profile.
- Added `WebUserIdResolver` and routed chat, conversation, and feedback controllers through it.
- Added `MemoryPromptFormatter` and reused it in both RAG and generic fallback prompt paths.
- Strengthened `DefaultMemoryEnginePort` extraction for Chinese whitespace, low-value social tails, and explicit remember phrases.
- Fixed memory repository bean creation by removing early `ObjectMapper` bean conditional pressure.
- Fixed PostgreSQL JSON/JSONB writes with explicit `CAST(? AS JSON)` in memory repositories.
- Rebuilt and restarted `seahorse-backend`.
- Verified `admin/admin` maps to business `userId = 2001523723396308993` in the current Docker environment.
- Verified `我 是一名学生，很高兴认识你` writes `PROFILE` memory content `我是一名学生`.
- Verified a new conversation asking `我的职业是什么？` answers from memory: `您提到自己是一名学生。`
- Added `MemoryCaptureCandidate`, `MemoryCaptureCandidateExtractor`, `MemoryCaptureDecision`, and `MemoryValueAssessor`.
- `DefaultMemoryEnginePort.writeMemory` now persists decision metadata including `capturePolicyVersion`, `valueScore`, `riskScore`, `captureSignals`, and `captureReasons`.
- Cleaned duplicate restored test methods in memory and chat test files after stash recovery.
- Verified target tests: `MemoryCapturePolicyTests`, `DefaultMemoryEnginePortTests`, and `KernelChatInboundServiceTests`.
- Added `save(...)` support to `MemoryQualitySnapshotRepositoryPort` and `MemoryConflictLogRepositoryPort` with no-op empty adapters for compatibility.
- Added JDBC persistence for `t_memory_quality_snapshot` and `t_memory_conflict_log`, including real schema columns `memory_id_1` and `memory_id_2`.
- Hardened JDBC JSON parsing for H2/PostgreSQL-mode string-wrapped JSON values.
- `KernelMemoryGovernanceService.runGovernance(..., assessQuality=true)` now writes a `memory-governance-v1` quality snapshot.
- `KernelMemoryGovernanceService` now records high-precision `SEMANTIC_KEY_CONFLICT` pending records when same-type memories share an explicit `semanticKey` but differ in content.
- `SeahorseAgentKernelMemoryAutoConfiguration` now supplies optional snapshot/conflict repositories to governance ports.
- Verified management API can list pending conflicts after governance creates them.

## Evidence Refs

- `docs/aegis/work/2026-05-20-memory-filtering/90-evidence.md`
- `docs/aegis/specs/2026-05-20-memory-filtering-architecture.md`
- `docs/aegis/plans/2026-05-20-memory-filtering-implementation.md`
- Target test output from `./mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=MemoryCapturePolicyTests,DefaultMemoryEnginePortTests,KernelChatInboundServiceTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- JDBC test output from `./mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc "-Dtest=JdbcMemoryRepositoryAdapterTests" test "-Dspotless.check.skip=true"`
- Docker E2E DB rows for `codex-memory-e2e-1779213886` and `codex-memory-read-1779213922`
- RED evidence from missing `save(...)` methods in quality snapshot and conflict repositories.
- GREEN evidence from `KernelMemoryGovernanceServiceTests` and `JdbcMemoryRepositoryAdapterTests`.
- Regression evidence from `MemoryCapturePolicyTests`, `DefaultMemoryEnginePortTests`, `KernelChatInboundServiceTests`, `KernelMemoryGovernanceServiceTests`, and `SeahorseWebApiContractTests`.
- Docker governance E2E evidence for run id `cdxgov113633`.
- Final fresh verification on 2026-05-20: target regression tests, JDBC repository tests with and without `-am`, and starter package all passed.

## Blocked-On Items

- None for Phase 1 through Phase 4 within the existing schema/API compatibility boundary.
- Full repository test suite was not run; verification focused on the changed memory, chat, repository, and Docker runtime paths.
- Full knowledge-base candidate review queue remains out of scope per `10-intent.md` because it would require new durable workflow/storage beyond this slice.
- Metric dashboard UI was not built; governance metrics are persisted as quality snapshot JSON and verified through API/DB.

## ResumeStateHint

Resume by reading:

1. `docs/aegis/specs/2026-05-20-memory-filtering-architecture.md`
2. `docs/aegis/plans/2026-05-20-memory-filtering-implementation.md`
3. `docs/aegis/work/2026-05-20-memory-filtering/90-evidence.md`
4. `git status --short`

Next planned work, if scope is expanded, is a dedicated knowledge-base candidate review queue and dashboard UI. The current memory filtering/cross-session recall plan is otherwise completed.

## DriftCheckDraft

- Serves original task intent: yes.
- Inside compatibility boundary: yes.
- New owners introduced as planned: `WebUserIdResolver`, `MemoryPromptFormatter`, `MemoryCaptureCandidateExtractor`, and `MemoryValueAssessor`.
- Governance write ownership now also includes existing `MemoryQualitySnapshotRepositoryPort` and `MemoryConflictLogRepositoryPort`.
- Runtime identity assumption updated: `admin` login maps to authenticated business `userId = 2001523723396308993`, not literal username `admin`.
- Retirement explicit: duplicated controller user resolution, duplicated prompt memory formatting, inline write-time extraction/scoring in `DefaultMemoryEnginePort`, and read-only governance skeletons are retired.
- Decision: Phase 1 through Phase 4 complete for this plan; final fresh verification passed; remaining knowledge-base candidate workflow is intentionally outside this slice.
