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
- [x] Update evidence and drift check.

## Active Slice

Phase 2 first implementation slice is complete: write-time memory capture now routes through dedicated candidate extraction and value assessment components instead of private inline rules in `DefaultMemoryEnginePort`.

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

## Evidence Refs

- `docs/aegis/work/2026-05-20-memory-filtering/90-evidence.md`
- `docs/aegis/specs/2026-05-20-memory-filtering-architecture.md`
- `docs/aegis/plans/2026-05-20-memory-filtering-implementation.md`
- Target test output from `./mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=MemoryCapturePolicyTests,DefaultMemoryEnginePortTests,KernelChatInboundServiceTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- JDBC test output from `./mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc "-Dtest=JdbcMemoryRepositoryAdapterTests" test "-Dspotless.check.skip=true"`
- Docker E2E DB rows for `codex-memory-e2e-1779213886` and `codex-memory-read-1779213922`

## Blocked-On Items

- None for Phase 1 or the Phase 2 capture-policy slice.
- Full repository test suite was not run; verification focused on the changed memory, chat, repository, and Docker runtime paths.
- Phase 2 conflict queue, candidate decision persistence beyond memory metadata, metric counters, and knowledge-base candidate governance remain planned work.

## ResumeStateHint

Resume by reading:

1. `docs/aegis/specs/2026-05-20-memory-filtering-architecture.md`
2. `docs/aegis/plans/2026-05-20-memory-filtering-implementation.md`
3. `docs/aegis/work/2026-05-20-memory-filtering/90-evidence.md`
4. `git status --short`

Next planned work is Phase 2 continuation: add capture decision logs/metrics, conflict handling, and knowledge-base candidate tagging.

## DriftCheckDraft

- Serves original task intent: yes.
- Inside compatibility boundary: yes.
- New owners introduced as planned: `WebUserIdResolver`, `MemoryPromptFormatter`, `MemoryCaptureCandidateExtractor`, and `MemoryValueAssessor`.
- Runtime identity assumption updated: `admin` login maps to authenticated business `userId = 2001523723396308993`, not literal username `admin`.
- Retirement explicit: duplicated controller user resolution, duplicated prompt memory formatting, and inline write-time extraction/scoring in `DefaultMemoryEnginePort` are retired.
- Decision: Phase 1 complete; Phase 2 first capture-policy slice complete; continue with governance/metrics only as a separate slice.
