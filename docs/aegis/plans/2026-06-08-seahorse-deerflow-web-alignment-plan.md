# Seahorse DeerFlow Web Alignment and Surpass Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aegis:subagent-driven-development` or `aegis:executing-plans` to implement this plan task by task. Steps use checkbox syntax for tracking.

**Goal:** Make Seahorse Agent's web-side agent experience at least align with deer-flow in tool invocation, skill invocation, artifact presentation, and frontend rendering, then surpass it with enterprise governance, replayability, cost visibility, and safer operations.

**Architecture:** Keep Seahorse's Java/Spring + React architecture. Do not clone deer-flow's LangGraph/FastAPI shape. Add an **Agent Workspace Runtime** layer that connects existing SSE events, run snapshots, artifacts, tool calls, approvals, skills, and the chat workbench into one canonical runtime surface.

**Tech Stack:** Java 17, Spring Boot, JDBC/PostgreSQL, React 18, TypeScript, Vite, Zustand, Axios, Vitest, Testing Library, Maven.

**Command Environment:** Verification commands assume Windows PowerShell and `.\mvnw.cmd`. Linux/macOS execution should use equivalent shell syntax and `./mvnw`.

**Execution Estimate:** 15-20 working days for the full plan, assuming Phase 0 starts after this plan review is incorporated and no new backend event persistence gap appears.

**Execution Companion:** `docs/aegis/plans/2026-06-08-seahorse-deerflow-web-alignment-implementation-details.md` contains task-level stream merge, snapshot hydration, and early frontend handler contracts. If the companion and this plan conflict, this plan is authoritative until code review updates both documents together.

**Baseline/Authority Refs:**
- `docs/README.md`
- `docs/aegis/plans/2026-06-02-frontend-backend-gap-remediation-plan.md`
- `docs/aegis/plans/2026-06-03-agent-skills-full-stack-implementation-plan.md`
- `D:/code/deer-flow/backend/packages/harness/deerflow/agents/lead_agent/prompt.py`
- `D:/code/deer-flow/backend/packages/harness/deerflow/skills/tool_policy.py`
- `D:/code/deer-flow/backend/packages/harness/deerflow/tools/builtins/present_file_tool.py`
- `D:/code/deer-flow/backend/packages/harness/deerflow/tools/builtins/tool_search.py`
- `D:/code/deer-flow/frontend/src/components/workspace/messages/message-group.tsx`
- `D:/code/deer-flow/frontend/src/components/workspace/artifacts/artifact-file-detail.tsx`
- `frontend/src/hooks/useStreamResponse.ts`
- `frontend/src/stores/chatStreamUtils.ts`
- `frontend/src/stores/chatStore.ts`
- `frontend/src/components/chat/workbench/WorkspaceInspector.tsx`
- `frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/tool/AbstractChatContentGenerationToolPortAdapter.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/tool/ImageGenerationToolPortAdapter.java`
- `seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/BuiltInAgentToolRegistrar.java`
- `seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelAgentAutoConfiguration.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/local/SpringSseEventSender.java`
- `resources/database/migrations/V20__github_visual_project_intro_agent.sql`
- `resources/database/migrations/V21__github_visual_agent_generation_tools.sql`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/chat/ChatSelectedSkillResolver.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/skill/SkillRuntimeComposer.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseSkillController.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentArtifactController.java`

The `D:/code/deer-flow/...` paths are local investigation references, not permanent project authority. Task 12 must promote the deer-flow comparison into repository-local docs with upstream remote URL, commit hash, and short excerpts or summaries so future CI and developers are not dependent on one workstation path.

**Compatibility Boundary:**
- Preserve Seahorse's existing Spring controller, kernel port, repository adapter, and React workbench ownership.
- Do not migrate to LangGraph, FastAPI, Python runtime orchestration, or deer-flow file-system state as the canonical owner.
- Existing `/rag/v3/chat` SSE message streaming must keep working for plain text conversations.
- Existing `AgentRunSnapshot`, artifact APIs, approval APIs, Skill APIs, ToolCatalog APIs, and MCP allowlist behavior remain the backend source of truth.
- Skill `allowedTools` remains either advisory metadata or a restrictive filter; it must never grant tools outside Agent/ToolCatalog policy.
- New frontend rendering must degrade gracefully when an event type, artifact preview, skill resource, or tool call payload is absent.
- No real secrets, `.env` values, hidden install paths, or raw unsafe artifact content may be surfaced.
- Touched chat/workbench/SSE hook files must pass an encoding guard; repair mojibake only when the guard or review identifies real hits, and do not batch-rewrite unrelated pages in the same commit.

**Verification:**
- Frontend focused:
  ```powershell
  cd frontend
  npm test -- chatStreamUtils chatStore WorkspaceInspector ArtifactInspectorTab
  npm run build
  ```
- Backend focused:
  ```powershell
  .\mvnw.cmd -pl seahorse-agent-kernel -am test -Dtest=KernelAgentLoopToolGatewayTests,ChatSelectedSkillResolverTests,KernelChatSkillSelectionTests,KernelAgentRunSnapshotServiceTests,KernelAgentArtifactQueryServiceTests
  .\mvnw.cmd -pl seahorse-agent-adapter-web -am test -Dtest=SeahorseChatControllerTests,SeahorseAgentArtifactControllerTests,SeahorseSkillControllerTests,SeahorseToolCatalogControllerTests
  .\mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc -am test -Dtest=JdbcAgentArtifactRepositoryAdapterTests
  ```
- Cross-cutting:
  ```powershell
  .\mvnw.cmd -pl seahorse-agent-tests -am test -Dtest=*Skill*,*Artifact*,*AgentRun*
  ```
- Real E2E before final completion:
  ```powershell
  cd frontend
  npm run test:e2e
  ```
  If the repository E2E command changes, use the current Playwright/browser E2E entry point and record the exact command, app URL, backend profile, and covered user flow in the final evidence.

---

## Executive Diagnosis

Seahorse already has many of the raw capabilities needed to match deer-flow:

- 21 built-in public skills live under `seahorse-agent-spring-boot-autoconfigure/src/main/resources/skills/public`.
- The built-in skills include `image-generation`, `video-generation`, `ppt-generation`, and `chart-visualization`.
- Chat input already supports selected skills through `ChatInput.tsx` and `SkillTrigger.tsx`.
- Backend skill resolution already exists in `ChatSelectedSkillResolver`.
- Skill prompt composition already exists in `SkillRuntimeComposer`.
- Artifact backend already exists through `SeahorseAgentArtifactController`, `KernelAgentArtifactQueryService`, `KernelAgentArtifactUpdateService`, and `JdbcAgentArtifactRepositoryAdapter`.
- Frontend workbench tabs already exist for timeline, artifacts, sources, approvals, cost, memory, and UI.
- Agent run snapshot already exists through `KernelAgentRunSnapshotService` and `AgentRunSnapshot`.
- Tool catalog and MCP allowlist management already exist through `SeahorseToolCatalogController` and `McpToolAllowlistRegistrar`.
- Native image generation tool support exists through `ImageGenerationToolPortAdapter` with tool id `image_generation`.

The largest gap is not missing infrastructure. The largest gap is that live agent events are normalized but not consistently attached to the active assistant message. `chatStore.ts` currently handles approval events in `onStreamEvent`, while timeline, source, artifact, quota, memory, and tool-call events do not flow into the message/workbench in real time.

The strategy is **three layers to align, two layers to surpass**:

- Align layer 1: stream events bind to active assistant messages and workbench rendering.
- Align layer 2: artifacts behave like deer-flow `present_files`, including previews, downloads, images, and skill installation.
- Align layer 3: skills and tools use progressive loading and real policy-aware filtering.
- Surpass layer 1: enterprise governance, approvals, audit, cost, and safety states are visible during and after the run.
- Surpass layer 2: AgentOps replay, run snapshots, event backfill, and degradation diagnostics are first-class.

---

## Plan Basis

### Facts

- Deer-flow uses a progressive skill prompt pattern: the prompt lists skill names, descriptions, and file locations; the model then reads the skill file only when needed.
- Deer-flow uses `present_files` to mark output files visible to the client and merges them into the runtime artifact state.
- Deer-flow uses deferred tool search so the prompt can expose a searchable catalog instead of injecting every full tool schema.
- Deer-flow frontend renders message groups, tool activity, artifacts, and file detail as an integrated workspace.
- Seahorse frontend already parses SSE and normalizes many `agent.*` event types in `chatStreamUtils.ts`.
- Seahorse frontend workbench already has places to render artifacts, sources, timeline, approvals, cost, memory, and A2UI Lite.
- Seahorse backend already has persisted artifacts and run snapshot APIs.
- Review verification on 2026-06-08 found `chatStore.ts`, `WorkspaceInspector.tsx`, `ArtifactInspectorTab.tsx`, and `agentArtifactService.ts` clean for the mojibake code points listed in Task 2. Keep an encoding guard because prior project work has had visible mojibake and new Chinese labels can regress.
- `frontend/src/stores/artifactStore.ts` currently has message-scoped artifact merge helpers but no production consumers outside itself/tests; it is a duplicate-owner risk once message state owns live artifact merging.
- Backend run snapshot and event APIs already expose sequence-like data (`eventSeq`/checkpoint sequence). Frontend hydration must treat sequence as monotonic recovery metadata, not as permission to overwrite newer live state.

### Current Baseline Review Impact

- 2026-06-08 baseline review found the current content-generation code correctly treats `model="default"` as the configured default model in both chat-based generation tools and `image_generation`; preserve this behavior.
- Task 5 execution has now added gateway-level generation artifact publication. Newsletter, PPT, chart, frontend-design, and image-generation outputs have direct tests proving persisted `AgentArtifact` rows and `agent.artifact` event payloads.
- `ImageGenerationToolPortAdapter` now forwards `style` and defaults to `b64_json` so default image generations can be stored internally. Returned and audited tool observations redact `b64Json` while the artifact publisher receives the raw successful result.
- `BuiltInAgentToolRegistrar` already registers all Spring `DescribedToolPort` beans into `ToolRegistryPort` and `ToolCatalogRepositoryPort`; model-generation tools are cataloged as `MEDIUM` risk, `EXECUTE` action, and `MODEL` resource type.
- `SpringSseEventSender` already sends named SSE events, emits an `error` event followed by `done` on failure, completes quietly, and has tests for closed-emitter behavior. Task 11 should preserve this sender contract and focus on frontend/admin consumption, replay, and backfill.
- `SeahorseChatController`, `ResearchSseBridge`, and `frontend/src/hooks/useStreamResponse.ts` already expose a resume path using `resumeRunId` and `lastEventSeq`; execution should leverage this for chat reconnect/backfill. A dedicated `/api/agent-runs/{runId}/events?afterSeq=...` endpoint already exists for admin replay through `AgentRunEventBufferPort`; do not add another recovery owner unless tests prove the current contracts are insufficient.
- `V20__github_visual_project_intro_agent.sql` and `V21__github_visual_agent_generation_tools.sql` are baseline migrations that seed the GitHub visual intro Agent and generation-tool bindings. Do not edit already-applied migrations in place; use a forward migration and update `resources/database/seahorse_init.sql` only if runtime database evidence proves seed data is missing or stale.
- `frontend/src/hooks/useStreamResponse.ts` is clean in the current scoped mojibake scan, but Task 2 must keep guarding it because it sits on the SSE sender/retry path.

### Assumptions

- The target is web-side functional parity and user experience parity, not line-for-line architecture parity.
- The existing Seahorse skill management implementation is recent enough that this plan should extend and harden it rather than replace it.
- The chat workbench should remain message-scoped: active assistant message is the owner of live timeline, sources, artifacts, approvals, quota, memory, and tool calls during streaming.
- Persisted run snapshot remains the recovery and replay source after refresh, reconnection, or stream interruption.

### Unknowns

- Whether all backend `agent.*` events include stable `runId`, `messageId`, and sequence numbers today.
- Which kernel collaborator should own generation artifact publication without coupling tool adapters directly to web controllers or transport-specific SSE senders.
- Whether selected chat skills and Agent-version bound skills are merged, deduplicated, and surfaced in snapshots.
- Whether MCP catalog metadata is rich enough for frontend search, grouping, and risk display.

### Non-goals

- No migration to deer-flow runtime internals.
- No wholesale frontend redesign.
- No removal of existing admin Skill, ToolCatalog, or Agent Inspector pages.
- No secret-bearing `.env` edits in this plan.
- No implementation in this document; this is the execution plan.

---

## First-Principles Decision Hygiene

### First-principles invariants

- Non-negotiable goal: a user must see what the agent is doing, what tools and skills it used, what files it produced, and how to inspect or reuse those outputs without leaving the chat flow.
- Non-negotiable constraints: backend policy remains authoritative; frontend only renders and requests declared APIs; artifacts and skills must not bypass safety scanning or tool allowlists.
- Historical assumptions to delete: "matching deer-flow" does not require copying LangGraph, Python file paths, or deer-flow's exact workspace storage model.

### Owner / retirement matrix

- New canonical owner: `Agent Workspace Runtime`, implemented as message-bound frontend state plus persisted backend `AgentRunSnapshot` and `AgentArtifact`.
- Old owner: ad hoc stream handlers that parse events but drop most normalized payloads.
- Compat-only carrier: existing plain text SSE `message` deltas and existing snapshot refresh method.
- Retirement trigger: after live event binding and snapshot replay pass, remove unused staging-only approval logic and any duplicate artifact-only stores that no longer receive events.

### Falsification matrix

- Dependency-removal test: if deer-flow source code is unavailable, Seahorse must still have a complete implementation based on Seahorse-owned contracts.
- Counterexample scenario: a run creates an image artifact and a tool approval while streaming; refresh happens before finish; the user must still recover the message, timeline, approval, and artifact from snapshot.
- Must remain correct cases: plain chat, no selected skill, disabled skill, denied tool, unsafe artifact, interrupted SSE, and missing optional event payload fields.

### Verdict

Adopt the Seahorse-native Agent Workspace Runtime approach. Escalate to ADR only if execution proposes changing the canonical owner away from `AgentRunSnapshot`/`AgentArtifact`, or if Skill policy starts granting tool permissions.

---

## File Map

### Frontend Stream and Message State

- Modify `frontend/src/stores/chatStore.ts`
- Modify `frontend/src/stores/chatStoreTypes.ts`
- Modify `frontend/src/stores/chatStreamUtils.ts`
- Create `frontend/src/stores/chatStreamHandlers.ts`
- Modify `frontend/src/hooks/useStreamResponse.ts` only if event envelope sequencing or reconnect metadata is missing
- Modify `frontend/src/types/index.ts`
- Review and delete or retire `frontend/src/stores/artifactStore.ts` and its tests after message-state artifact merging is proven
- Test `frontend/src/stores/chatStreamUtils.test.ts`
- Test `frontend/src/stores/chatStreamHandlers.test.ts`
- Test or create `frontend/src/stores/chatStore.test.ts`

### Frontend Workbench Rendering

- Modify `frontend/src/components/chat/workbench/WorkspaceInspector.tsx`
- Modify `frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx`
- Modify `frontend/src/components/chat/workbench/TimelineInspectorTab.tsx`
- Modify `frontend/src/components/chat/workbench/SourcesInspectorTab.tsx`
- Modify `frontend/src/components/chat/workbench/ApprovalsInspectorTab.tsx`
- Modify `frontend/src/components/chat/workbench/UIInspectorTab.tsx`
- Create `frontend/src/components/chat/workbench/ToolCallsInspectorTab.tsx`
- Test `frontend/src/components/chat/workbench/WorkspaceInspector.test.tsx`
- Test `frontend/src/components/chat/workbench/ArtifactInspectorTab.test.tsx`

### Frontend Skill and Tool Surfaces

- Modify `frontend/src/components/chat/ChatInput.tsx`
- Modify `frontend/src/components/chat/SkillTrigger.tsx`
- Modify `frontend/src/services/skillService.ts`
- Modify `frontend/src/pages/admin/skills/SkillManagementPage.tsx`
- Modify `frontend/src/pages/admin/tools/ToolCatalogPage.tsx`
- Create `frontend/src/services/toolSearchService.ts` only if backend exposes runtime tool search outside ToolCatalog admin

### Backend Agent Runtime Events

- Modify `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/KernelAgentLoop.java`
- Modify tool gateway event emission near `KernelAgentLoopToolGatewayTests` coverage
- Modify `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/runtime/KernelAgentRunSnapshotService.java`
- Modify `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseChatController.java`
- Modify or create event DTO records under `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/`
- Review and extend `SpringSseEventSenderTests` only if sender error/done semantics or close behavior changes

### Backend Artifacts

- Modify `AbstractChatContentGenerationToolPortAdapter.java`
- Modify `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentArtifactController.java`
- Modify `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/artifact/KernelAgentArtifactQueryService.java`
- Modify `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/artifact/KernelAgentArtifactUpdateService.java`
- Modify `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcAgentArtifactRepositoryAdapter.java`
- Modify content generation tools:
  - `ImageGenerationToolPortAdapter.java`
  - `PptGenerationToolPortAdapter.java`
  - `ChartVisualizationToolPortAdapter.java`
  - `NewsletterGenerationToolPortAdapter.java`
  - `FrontendDesignToolPortAdapter.java`
- Test `SeahorseAgentArtifactControllerTests`
- Test `KernelAgentArtifactQueryServiceTests`
- Test `KernelAgentArtifactUpdateServiceTests`
- Test `JdbcAgentArtifactRepositoryAdapterTests`

### Backend Skills and Tools

- Modify `ChatSelectedSkillResolver.java`
- Modify `SkillRuntimeComposer.java`
- Create `LoadSkillResourceToolPortAdapter.java` or extend existing local tool registration with `load_skill_resource`
- Create backend deferred tool search support if not present:
  - `DeferredToolCatalog.java`
  - `ToolSearchToolPortAdapter.java`
  - `KernelDeferredToolCatalogService.java`
- Modify `SeahorseToolCatalogController.java`
- Modify `McpToolAllowlistRegistrar.java`
- Modify `seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/BuiltInAgentToolRegistrar.java`
- Modify `seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelAgentAutoConfiguration.java`
- Test `ChatSelectedSkillResolverTests`
- Test `KernelChatSkillSelectionTests`
- Test `SeahorseToolCatalogControllerTests`
- Test `McpToolAllowlistRegistrarTests`
- Test `BuiltInAgentToolRegistrarTests`

### Documentation

- Modify `docs/README.md`
- Create `docs/agent-workspace-runtime.md`
- Create `docs/deerflow-web-alignment.md`
- Modify `docs/aegis/plans/2026-06-08-seahorse-deerflow-web-alignment-implementation-details.md`
- Modify `docs/aegis/INDEX.md`

---

## Phased Tasks

### Task 1: P0 Bind Live Stream Events to the Active Assistant Message

**Files:**
- Modify `frontend/src/hooks/useStreamResponse.ts`
- Modify `frontend/src/stores/chatStore.ts`
- Modify `frontend/src/stores/chatStoreTypes.ts`
- Modify `frontend/src/stores/chatStreamUtils.ts`
- Create `frontend/src/stores/chatStreamHandlers.ts`
- Modify `frontend/src/types/index.ts`
- Test `frontend/src/stores/chatStreamUtils.test.ts`
- Test `frontend/src/stores/chatStreamHandlers.test.ts`
- Test or create `frontend/src/stores/chatStore.test.ts`

**Why:** The user should see timeline, sources, artifacts, approvals, quota, and memory appear while the agent is running. Today normalized payloads mostly stop before message state.

**Impact/Compatibility:** Plain text streaming keeps the current `message` delta path. New event handling only enriches the current assistant message.

**Repair Track:**
- Root cause: `onStreamEvent` normalizes many event types but `chatStore.ts` only stages approval payloads.
- Canonical owner: active assistant `Message` in `chatStore`.
- Minimal change: extract pure stream handlers into `chatStreamHandlers.ts`; add a single `applyAgentStreamEventToMessage` helper that merges normalized items into message arrays by stable id.
- Merge rule: event application must be idempotent and monotonic. Never replace whole arrays when merging timeline, source, artifact, approval, quota, memory, tool-call, or skill items; merge by stable id and preserve newer local fields when incoming data is older or less complete.
- Sequence rule: store `lastEventSeq` on the message when the envelope has sequence metadata. Events with a lower sequence must not overwrite fields written by newer live events.
- Compatibility: if no matching current assistant message exists, ignore the event and log only in development.
- Verification: focused handler/store tests must prove no duplicate rows when the same event arrives twice and no data loss when a live event arrives during a snapshot fetch.

**Retirement Track:**
- Old owner/fallback: approval-only staging in `chatStore.ts`.
- Active status: keep until approval merge is included in the shared helper.
- Deletion trigger: after approval events are rendered in `message.approvals`, remove `stagedApprovals` if it has no remaining consumer.
- Artifact-store trigger: after `applyAgentStreamEventToMessage` owns artifact merge and `ArtifactInspectorTab`/`WorkspaceInspector` tests pass, delete or formally deprecate `frontend/src/stores/artifactStore.ts` and its test file. If retained, document whether it is a consumer cache or mirror, not a second owner.

**Verification:**
```powershell
cd frontend
npm test -- chatStreamUtils chatStreamHandlers chatStore
npm run build
```

- [ ] Write RED tests for applying `agent.step.started`, `agent.source.found`, `agent.artifact.content`, `agent.tool.waiting_user`, `agent.quota`, and `agent.memory` to the current assistant message.
- [ ] Write RED tests proving duplicate events are idempotent and `live event arrived during snapshot fetch should not be lost`.
- [ ] Verify RED by running `npm test -- chatStreamUtils chatStreamHandlers chatStore`.
- [ ] Implement `chatStreamHandlers.ts` with id-based merge, artifact append semantics, and sequence-aware monotonic updates.
- [ ] Wire `onStreamEvent` to the helper and preserve current approval behavior.
- [ ] Delete or deprecate `artifactStore.ts` if it has no production consumer after message-state artifact merge is complete.
- [ ] Verify GREEN with the focused frontend commands.
- [ ] Commit: `feat: bind agent stream events to chat messages`

### Task 2: P0 Add Chat and Workbench Encoding Guard

**Files:**
- Modify `frontend/src/hooks/useStreamResponse.ts`
- Modify `frontend/src/stores/chatStore.ts`
- Modify `frontend/src/components/chat/workbench/WorkspaceInspector.tsx`
- Modify `frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx`
- Modify `frontend/src/services/agentArtifactService.ts`
- Create or modify a focused frontend encoding test if no existing guard covers these files
- Test existing frontend build and focused tests

**Why:** A 2026-06-08 review flagged possible mojibake around chat/workbench and the SSE retry path. Current scoped scans show the named files, including `frontend/src/hooks/useStreamResponse.ts`, are clean for the known bad code points. Keep this as a P0 guard because the runtime work will touch Chinese labels and prior project work has had encoding regressions.

**Impact/Compatibility:** Test/source-scan guard only unless the scan finds real mojibake. No API or state contract changes.

**Repair Track:**
- Root cause: previous project files have had non-UTF-8 or mojibake text, while the currently named chat/workbench/SSE hook files are clean in the scoped scan. Keep the guard because these files are user-visible and future label or retry-copy edits can regress.
- Canonical owner: UTF-8 source files.
- Minimal change: add or keep a guard over touched chat/workbench/SSE hook files; repair only actual hits reported by the guard, and record line numbers in the execution evidence.
- Compatibility: keep keys, component structure, and behavior unchanged.
- Verification: build plus source scan for common mojibake code points must return no hits.

**Retirement Track:**
- Old owner/fallback: manual visual inspection only.
- Active status: keep the guard in focused tests or CI.
- Deletion trigger: none; encoding guard stays as a regression check.

**Verification:**
```powershell
cd frontend
npm test -- chatStore WorkspaceInspector ArtifactInspectorTab
npm run build
$badCodePoints = @(0x9354,0x9239,0x7035,0x6D93,0x95AB,0x59AB,0x9983,0x9241,0x9242,0xFFFD)
$paths = @("src/hooks/useStreamResponse.ts","src/stores/chatStore.ts","src/components/chat/workbench","src/services/agentArtifactService.ts")
Get-ChildItem $paths -Recurse -File | Select-String -Pattern (($badCodePoints | ForEach-Object { [char]$_ }) -join "|")
```

- [ ] Write or update tests that assert visible labels and messages such as "运行详情", "关闭检查器", "复制内容", "下载", "文件未通过安全扫描", and the stream timeout message.
- [ ] Run the code-point scan and record whether there are real hits. If there are no hits, do not manufacture a RED test.
- [ ] Repair user-visible text and comments only for actual hit lines.
- [ ] Verify focused tests, build, and mojibake scan pass.
- [ ] Commit: `test: guard chat workspace encoding`

### Task 3: P0 Hydrate Interrupted or Historical Runs from Snapshot

**Files:**
- Modify `frontend/src/stores/chatStore.ts`
- Modify `frontend/src/services/agentRunService.ts`
- Modify `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/runtime/KernelAgentRunSnapshotService.java`
- Test `KernelAgentRunSnapshotServiceTests`
- Test `chatStore.test.ts`

**Why:** deer-flow's workspace remains useful after tool execution and file presentation. Seahorse should recover the same workspace state after refresh, reconnect, or stream interruption.

**Impact/Compatibility:** Existing `refreshRunSnapshot(messageId, runId)` behavior is extended to hydrate all message workbench fields, not just text and thinking.

**Repair Track:**
- Root cause: snapshot refresh only applies `messageSnapshot.content` and `thinking`.
- Canonical owner: `AgentRunSnapshot` for persisted recovery.
- Minimal change: map snapshot `steps`, `sources`, `artifacts`, approvals, cost, and status into `Message` through the same merge helpers used by live stream events.
- Merge rule: snapshot hydration merges by id and must not replace arrays wholesale. Missing fields leave existing message fields unchanged.
- Sequence rule: if snapshot sequence metadata is lower than `message.lastEventSeq`, the hydration path must not overwrite newer live fields for that event family.
- Verification: tests prove an interrupted message can recover timeline and artifact data, and a live event that arrives during snapshot fetch is not lost.

**Retirement Track:**
- Old owner/fallback: live-only message enrichment.
- Active status: keep live stream merge as the primary real-time path.
- Deletion trigger: none; snapshot remains recovery source.

**Verification:**
```powershell
cd frontend
npm test -- chatStore
cd ..
.\mvnw.cmd -pl seahorse-agent-kernel -am test -Dtest=KernelAgentRunSnapshotServiceTests
```

- [ ] Write RED frontend test for `refreshRunSnapshot` hydrating status, timeline, sources, artifacts, approvals, and cost summary.
- [ ] Write RED frontend test for `live event arrived during snapshot fetch should not be lost`.
- [ ] Write RED backend test if snapshot omits any needed artifact or event field.
- [ ] Implement frontend snapshot mapping through a typed helper.
- [ ] Implement backend snapshot field additions only where tests prove missing data.
- [ ] Verify focused frontend and backend tests pass.
- [ ] Commit: `feat: hydrate chat workspace from run snapshots`

### Task 4: P1 Complete Artifact Workspace and present-files Equivalent

**Files:**
- Modify `SeahorseAgentArtifactController.java`
- Modify `KernelAgentArtifactQueryService.java`
- Modify `KernelAgentArtifactUpdateService.java`
- Modify `JdbcAgentArtifactRepositoryAdapter.java`
- Modify `ArtifactInspectorTab.tsx`
- Modify `UIInspectorTab.tsx`
- Test artifact controller, query, update, JDBC, and frontend artifact tests

**Why:** deer-flow's `present_files` gives the user a clear contract: generated files are visible, previewable, downloadable, and tied to the thread. Seahorse should expose the same contract through persisted AgentArtifact rows.

**Impact/Compatibility:** Existing artifact APIs remain. Add richer preview/disposition semantics only where needed.

**Repair Track:**
- Root cause: Seahorse has artifact APIs and UI, but generated content and live artifact events are not guaranteed to close the loop from tool output to user-visible artifact.
- Canonical owner: persisted `AgentArtifact`.
- Minimal change: standardize artifact lifecycle states `created`, `content`, `complete`, `scanStatus`, `previewText`, and `download`.
- Schema status: `AgentArtifact`, `SeahorseAgentArtifactController.AgentArtifactResponse`, and `sa_agent_artifact` already carry the core fields needed for `previewText`, `mimeType`, `scanStatus`, `canPreview`, and `disposition`. Do not add a migration unless tests prove a missing column or incompatible type; if a migration is needed, create the next numbered migration such as `V22__agent_workspace_artifact_lifecycle.sql`.
- Compatibility: existing artifacts without preview still show metadata and download when clean.
- Verification: artifact tests cover preview, update, unsafe scan blocking, and download.

**Retirement Track:**
- Old owner/fallback: local-only artifact blocks that are never persisted.
- Active status: keep for streaming text artifacts before backend persistence completes.
- Deletion trigger: when all content-generation tools persist artifacts, local-only blocks become transient display only.

**Verification:**
```powershell
cd frontend
npm test -- ArtifactInspectorTab WorkspaceInspector
npm run build
cd ..
.\mvnw.cmd -pl seahorse-agent-kernel -am test -Dtest=KernelAgentArtifactQueryServiceTests,KernelAgentArtifactUpdateServiceTests
.\mvnw.cmd -pl seahorse-agent-adapter-web -am test -Dtest=SeahorseAgentArtifactControllerTests
.\mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc -am test -Dtest=JdbcAgentArtifactRepositoryAdapterTests
```

- [ ] Write RED tests for live artifact append, server artifact merge, blocked unsafe download, preview rendering, and saved edit.
- [ ] Verify RED against the current implementation.
- [ ] Normalize artifact lifecycle payloads in `chatStreamUtils.ts` and backend event DTOs if needed.
- [ ] Ensure controller returns `previewText`, `mimeType`, `scanStatus`, `canPreview`, `disposition`, and download metadata consistently.
- [ ] Verify all artifact tests and frontend build pass.
- [ ] Commit: `feat: complete artifact workspace lifecycle`

### Task 5: P1 Close Image, PPT, Chart, and Visual Generation Output Loops

**Files:**
- Modify `ImageGenerationToolPortAdapter.java`
- Modify `AbstractChatContentGenerationToolPortAdapter.java`
- Modify `PptGenerationToolPortAdapter.java`
- Modify `ChartVisualizationToolPortAdapter.java`
- Modify `FrontendDesignToolPortAdapter.java`
- Modify `NewsletterGenerationToolPortAdapter.java`
- Modify artifact services and tests as needed
- Test `ContentGenerationToolPortAdapterTests`

**Why:** The user specifically asked whether there is image/text generation. Seahorse has image and visual generation primitives; the web experience must show outputs as inspectable artifacts, not hidden tool return text.

**Impact/Compatibility:** Tool return text can keep a summary, but every generated visual/document output must also create a persisted artifact event. Preserve the current `model="default"` fallback behavior. Preserve the advertised `style` argument for image generation or formally remove it from the schema and docs in the same task.

**Repair Track:**
- Root cause: current content-generation tools are registered and callable, but their adapters return JSON observations only; they do not create persisted `AgentArtifact` rows or publish `agent.artifact.*` events into the run stream.
- Canonical owner: a kernel-side artifact publication collaborator used by content-generation tools, backed by persisted `AgentArtifact` and run events.
- Minimal change: extend `AbstractChatContentGenerationToolPortAdapter` or add an injected publication collaborator so chat-based generation tools share artifact creation without coupling to Spring MVC or transport-specific `SpringSseEventSender`.
- Compatibility: preserve existing tool ids and descriptions, including `image_generation`; keep `model="default"` resolving to the configured default model; keep image `style` forwarding unless product/API evidence says the parameter should be retired.
- Verification: tests prove each tool type creates the expected artifact metadata and preview/download behavior.

**Retirement Track:**
- Old owner/fallback: tool result text containing raw file references.
- Active status: keep human-readable summary.
- Deletion trigger: remove any duplicate ad hoc artifact creation once shared adapter coverage exists.

**Verification:**
```powershell
.\mvnw.cmd -pl seahorse-agent-kernel -am test -Dtest=ContentGenerationToolPortAdapterTests
.\mvnw.cmd -pl seahorse-agent-tests -am test -Dtest=*Artifact*,*AgentRun*
```

- [x] Write RED tests for image, PPT, chart, frontend design, and newsletter tools creating clean `AgentArtifact` metadata while preserving `model="default"` fallback and image `style` forwarding.
- [x] Verify RED against current tool behavior: JSON observations are returned, but no persisted `AgentArtifact` or artifact stream event exists yet.
- [x] Implement or extend shared artifact publication through a kernel artifact publisher; avoid direct dependencies from tool adapters to web controllers or `SpringSseEventSender`.
- [x] Emit artifact stream events with `artifactId`, `runId`, `title`, `mimeType`, `previewText`, and `storageRef` where allowed.
- [x] Verify focused backend tests pass.
- [x] Commit: `feat: publish generation tool artifacts`, `feat: publish image generation artifacts`, `fix: persist default image generations safely`, `test: cover all generation artifact publishing`

### Task 6: P1 Add Skill Progressive Loading Resource Tool

**Files:**
- Modify `ChatSelectedSkillResolver.java`
- Modify `SkillRuntimeComposer.java`
- Create `LoadSkillResourceToolPortAdapter.java`
- Modify `seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/BuiltInAgentToolRegistrar.java`
- Modify `seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelAgentAutoConfiguration.java`
- Test `ChatSelectedSkillResolverTests`
- Test `KernelChatSkillSelectionTests`
- Test `BuiltInAgentToolRegistrarTests`

**Why:** deer-flow does not inject every full skill body up front. It lists skills and lets the model load the skill file/resource when relevant. Seahorse should keep direct injection for small selections but add progressive loading for large skills and referenced resources.

**Impact/Compatibility:** Existing selected-skill flow remains. New tool only reads skill resources already selected or bound to the current chat/Agent runtime.

**Repair Track:**
- Root cause: current composer can switch to metadata-only, but metadata-only needs a safe way to load the full skill content later.
- Canonical owner: selected or version-bound skill snapshot plus `load_skill_resource` tool.
- Minimal change: expose a read-only local tool that accepts skill name and relative resource path.
- Compatibility: unselected skills and absolute/parent traversal paths are rejected.
- Verification: tests prove selected skills load and unselected skills fail.

**Retirement Track:**
- Old owner/fallback: direct prompt injection of full skill content.
- Active status: keep for small skill selections.
- Deletion trigger: if progressive loading is reliable, lower direct injection threshold later through config, not in this phase.

**Verification:**
```powershell
.\mvnw.cmd -pl seahorse-agent-kernel -am test -Dtest=ChatSelectedSkillResolverTests,KernelChatSkillSelectionTests
.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am test -Dtest=BuiltInAgentToolRegistrarTests
.\mvnw.cmd -pl seahorse-agent-tests -am test -Dtest=*Skill*
```

- [x] Write RED tests proving metadata-only skills include load instructions and model-visible `load_skill_resource` exposure.
- [x] Write RED tests proving `load_skill_resource` returns selected skill `SKILL.md`.
- [x] Write RED tests proving unselected skill names, parent traversal, absolute paths, missing resources, and missing injected runtime snapshots fail.
- [x] Implement the read-only tool and register it through `SeahorseAgentKernelAgentAutoConfiguration` and `BuiltInAgentToolRegistrar`.
- [x] Extend `BuiltInAgentToolRegistrarTests` to prove `load_skill_resource` is registered and visible in ToolCatalog.
- [x] Verify focused backend tests pass:
  - `.\mvnw.cmd -pl seahorse-agent-kernel -am test "-Dtest=ChatSelectedSkillResolverTests,KernelChatSkillSelectionTests,KernelAgentLoopToolGatewayTests,LoadSkillResourceToolPortAdapterTests"`
  - `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am test "-Dtest=BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false"`
- [x] Commit: `feat: add progressive skill resource loading`

### Task 7: P1 Enforce Skill-Aware Tool Policy

**Files:**
- Modify `ChatSelectedSkillResolver.java`
- Modify tool gateway policy code covered by `KernelAgentLoopToolGatewayTests`
- Modify `SeahorseToolCatalogController.java` only if API response needs policy diagnostics
- Test `KernelAgentLoopToolGatewayTests`
- Test `ChatSelectedSkillResolverTests`

**Why:** deer-flow filters available tools by skill `allowed_tools`. Seahorse already renders skill tools as prompt-side advisory metadata through `SkillRuntimeComposer`; this task promotes an optional advisory-to-restrictive mode into the Tool Gateway, while preserving the rule that skills can never grant tools outside the existing Agent/ToolCatalog allowlist.

**Impact/Compatibility:** Existing Agent allowed tools remain the maximum permission set.

**Repair Track:**
- Root cause: current behavior is prompt-side advisory. Deer-flow parity requires either a real restrictive policy path or an explicit diagnostic that the current mode is advisory.
- Canonical owner: Tool Gateway policy.
- Minimal change: compute `effectiveAllowedToolIds = agentAllowedToolIds ∩ selectedSkillAllowedTools` only when selected skills opt into restrictive mode; otherwise expose advisory labels.
- Compatibility: default behavior can remain advisory to avoid breaking existing agents.
- Verification: tests prove a skill cannot add a tool and restrictive mode can reduce tools.

**Retirement Track:**
- Old owner/fallback: frontend labels implying skill tools are executable permissions.
- Active status: update labels to "工具依赖" or "建议工具" unless restrictive mode is active.
- Deletion trigger: after restrictive mode ships, remove any misleading `allowedTools` copy from UI.

**Verification:**
```powershell
.\mvnw.cmd -pl seahorse-agent-kernel -am test -Dtest=KernelAgentLoopToolGatewayTests,ChatSelectedSkillResolverTests
cd frontend
npm test -- SkillManagementPage AgentSkillBindingPanel
```

- [x] Write RED tests proving skill `allowedTools` never grants a denied Agent tool.
- [x] Write RED tests proving restrictive selected skill mode reduces available tools.
- [x] Implement effective policy calculation in the backend owner closest to tool execution.
- [x] Update frontend labels and diagnostics for advisory vs restrictive mode.
- [x] Verify focused backend policy tests pass:
  - `.\mvnw.cmd -pl seahorse-agent-kernel -am test "-Dtest=KernelAgentLoopToolGatewayTests"`
- [x] Verify focused frontend tests pass after label/diagnostic work:
  - `npm test -- SkillManagementPage AgentSkillBindingPanel`
- [x] Commit backend slice: `feat: enforce skill aware tool policy`
- [x] Commit frontend label/diagnostic slice.

### Task 8: P1 Add Deferred Tool Search for Runtime Tool Discovery

**Files:**
- Create `DeferredToolCatalog.java`
- Create `ToolSearchToolPortAdapter.java`
- Create `KernelDeferredToolCatalogService.java`
- Modify `seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/BuiltInAgentToolRegistrar.java`
- Modify `seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelAgentAutoConfiguration.java`
- Modify `SeahorseToolCatalogController.java`
- Modify `ToolCatalogPage.tsx`
- Test `SeahorseToolCatalogControllerTests`
- Test `KernelAgentLoopToolGatewayTests`
- Test `BuiltInAgentToolRegistrarTests`

**Why:** deer-flow exposes deferred tools through `tool_search`, reducing prompt bloat while preserving discoverability. Seahorse can surpass this by combining search with governance metadata and MCP allowlist state.

**Impact/Compatibility:** Existing tool catalog remains. Deferred search is additive and feature/config gated.

**Repair Track:**
- Root cause: large tool catalogs either bloat prompts or hide available tools.
- Canonical owner: ToolCatalog plus policy-filtered deferred catalog.
- Minimal change: add a read-only `tool_search` tool that returns matching tool metadata only after policy filtering; keep schema access on the existing catalog/admin surfaces.
- Compatibility: enabled by default under agent runtime, but can be disabled through `seahorse-agent.chat.agent.tools.deferred-search.enabled=false` while agents still rely on eager schema injection.
- Verification: tests prove denied tools never appear in search results.

**Retirement Track:**
- Old owner/fallback: full prompt injection of all tool schemas.
- Active status: keep until deferred search is enabled per Agent or tenant.
- Deletion trigger: after rollout evidence, reduce full schema injection for large catalogs.

**Verification:**
```powershell
.\mvnw.cmd -pl seahorse-agent-kernel -am test -Dtest=KernelAgentLoopToolGatewayTests
.\mvnw.cmd -pl seahorse-agent-adapter-web -am test -Dtest=SeahorseToolCatalogControllerTests
.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am test -Dtest=BuiltInAgentToolRegistrarTests
cd frontend
npm test -- ToolCatalogPage
```

- [x] Write RED tests for search result filtering, metadata-only output, missing snapshot denial, and policy-filtered denial.
- [x] Verify RED against current catalog behavior.
- [x] Implement deferred catalog assembly from the Agent/skill effective allowlist; MCP and tenant filtering remain inherited from the existing allowed-tool pipeline.
- [x] Register `tool_search` as a local read-only tool through `SeahorseAgentKernelAgentAutoConfiguration` and `BuiltInAgentToolRegistrar`.
- [x] Extend auto-configuration and registrar tests to prove the tool is registered/cataloged when enabled and not registered when `seahorse-agent.chat.agent.tools.deferred-search.enabled=false`.
- [x] Add admin diagnostics showing deferred vs eagerly injected tools.
- [x] Verify focused tests pass.
- [x] Commit: `feat: add deferred tool search runtime`

### Task 9: P2 Add Tool Call Rendering and Action Details to Workbench

**Files:**
- Create `frontend/src/components/chat/workbench/ToolCallsInspectorTab.tsx`
- Modify `WorkspaceInspector.tsx`
- Modify `TimelineInspectorTab.tsx`
- Modify `chatStreamUtils.ts`
- Modify `types/index.ts`
- Backend event DTOs if tool call started/finished/result events lack fields
- Test `WorkspaceInspector.test.tsx`

**Why:** deer-flow visually exposes tool activity inside the message workspace. Seahorse should show tool name, status, arguments preview, result summary, approval state, duration, and errors.

**Impact/Compatibility:** Tool arguments must be redacted and preview-limited. Existing timeline remains.

**Repair Track:**
- Root cause: tool calls are normalized as generic timeline items and approval items, losing action detail.
- Canonical owner: message-scoped `toolCalls` array.
- Minimal change: add `AgentToolCall` type and workbench tab, while still adding timeline rows.
- Compatibility: missing arguments show "No preview".
- Verification: frontend tests cover started, waiting approval, finished, failed, and redacted argument cases.

**Retirement Track:**
- Old owner/fallback: generic timeline-only rendering.
- Active status: keep timeline summaries for scanning.
- Deletion trigger: none; tool tab complements timeline.

**Verification:**
```powershell
cd frontend
npm test -- chatStreamHandlers WorkspaceInspector
npm run build
```

- [x] Write RED tests for tool-call tab count and detail rendering.
- [x] Verify RED.
- [x] Add `toolCalls` to message state and stream normalization.
- [x] Render the new Tool Calls tab with redacted argument preview and result summary.
- [x] Verify focused frontend tests and build pass.
- [x] Commit: `feat: render tool calls in chat workbench`

### Task 10: P2 Add Skill Invocation Rendering and Runtime Diagnostics

**Files:**
- Modify `chatStreamUtils.ts`
- Modify `types/index.ts`
- Modify `WorkspaceInspector.tsx`
- Create `SkillInspectorTab.tsx`
- Modify `SkillRuntimeComposer.java` and runtime event emission if needed
- Test `WorkspaceInspector.test.tsx`
- Test `ChatSelectedSkillResolverTests`

**Why:** Users should know which selected or bound skills were considered, loaded, or skipped. This is especially important once progressive loading exists.

**Impact/Compatibility:** Rendering diagnostics must not expose full custom skill content unless the user has permission and explicitly opens it through Skill APIs.
Rejected per-turn skill selections are still handled before an Agent run starts by `ChatSelectedSkillResolver`; they are covered by resolver/chat tests and do not emit runtime workbench events because no run exists yet.

**Repair Track:**
- Root cause: selected skills affect prompt/runtime but are largely invisible during the chat turn.
- Canonical owner: runtime skill resolution events plus message-scoped `skills`.
- Minimal change: emit and render `skill.selected`, `skill.loaded`, `skill.skipped`, and `skill.resource_loaded` diagnostics.
- Compatibility: existing chat skill selection remains unchanged.
- Verification: tests prove selected skills display and disabled/not-found skills degrade clearly.

**Retirement Track:**
- Old owner/fallback: user remembers what they clicked in `ChatInput`.
- Active status: no reliable after-send visibility.
- Deletion trigger: after Skill tab exists, remove redundant selected-skill chips from finished messages if duplicated.

**Verification:**
```powershell
cd frontend
npm test -- chatStreamUtils WorkspaceInspector
cd ..
.\mvnw.cmd -pl seahorse-agent-kernel -am test -Dtest=ChatSelectedSkillResolverTests,KernelChatSkillSelectionTests
```

- [x] Write RED tests for selected, metadata-only, loaded, and skipped runtime diagnostics; rejected selections remain covered at resolver/chat boundary.
- [x] Verify RED.
- [x] Add backend skill runtime events or snapshot fields if missing.
- [x] Add Skill workbench tab and message summary metrics.
- [x] Verify focused tests pass.
- [x] Commit: `feat: show skill invocation diagnostics`

### Task 11: P2 Add AgentOps Replay, Event Backfill, and Cost Governance

**Files:**
- Modify `KernelAgentRunSnapshotService.java`
- Modify `AgentRunSnapshot.java`
- Modify or verify `SeahorseChatController.java`
- Modify or verify `ResearchSseBridge.java`
- Modify or verify `frontend/src/hooks/useStreamResponse.ts`
- Modify `frontend/src/pages/admin/agent-inspector/AgentInspectorPage.tsx`
- Modify `frontend/src/components/chat/workbench/CostQuotaInspectorTab.tsx`
- Modify `agentRunService.ts`
- Test `KernelAgentRunSnapshotServiceTests`
- Test `AgentInspectorPage.test.tsx`

**Why:** This is where Seahorse can surpass deer-flow: enterprise users need replay, cost, approvals, quotas, governance state, and failure recovery across sessions.

**Impact/Compatibility:** AgentOps additions must not alter run execution. They enrich views and replay/backfill only.

**Repair Track:**
- Root cause: Seahorse has Agent Inspector, cost APIs, SSE error/done handling, and resume plumbing, but chat workbench and admin replay are not fully connected to those contracts.
- Canonical owner: persisted run events and snapshots.
- Minimal change: reuse the existing `/rag/v3/chat?resumeRunId=...&lastEventSeq=...` stream path and `stream_event` envelopes for chat reconnect/backfill; use the existing `/api/agent-runs/{runId}/events?afterSeq=...` event list for admin replay. Extend the current event buffer or snapshot contracts only if tests prove fields are missing.
- Compatibility: if event history is empty, snapshot still renders.
- Sender compatibility: preserve `SpringSseEventSender` behavior of named events, `error` followed by `done` on failure, and quiet completion after client disconnects.
- Verification: tests prove replay uses event order and backfill does not duplicate live events.

**Retirement Track:**
- Old owner/fallback: manual refresh of run snapshot only.
- Active status: keep refresh button as recovery action.
- Deletion trigger: after automatic backfill exists, refresh becomes secondary.

**Verification:**
```powershell
.\mvnw.cmd -pl seahorse-agent-kernel -am test -Dtest=KernelAgentRunSnapshotServiceTests
cd frontend
npm test -- AgentInspectorPage CostQuotaInspectorTab chatStore
npm run build
```

- [x] Write RED tests for event backfill after stream reconnect using existing `resumeRunId` and `lastEventSeq` semantics.
- [x] Write RED tests for Agent Inspector replay order and cost/quota rendering.
- [x] Implement event sequence tracking in frontend message state.
- [x] Reuse the existing resume stream path for chat backfill; use the existing event-list endpoint for admin replay and extend backend event list/snapshot only if fields are missing.
- [x] Verify focused tests and build pass.
- [x] Commit: `feat: add agentops replay and governance diagnostics`

### Task 12: Documentation, README, and Product Comparison Update

**Files:**
- Modify `docs/README.md`
- Create `docs/agent-workspace-runtime.md`
- Create `docs/deerflow-web-alignment.md`
- Modify `docs/aegis/plans/2026-06-08-seahorse-deerflow-web-alignment-implementation-details.md`
- Modify `docs/aegis/INDEX.md`
- Modify `resources/database/seahorse_init.sql` only if seed verification proves the shipped init snapshot is stale
- Create the next forward migration only if an already-applied seed must be corrected in existing databases

**Why:** The original goal included README and related-doc analysis. Once implementation slices are done, docs must describe what is implemented, what is intentionally Seahorse-native, and how users access built-in skills and generated artifacts.

**Impact/Compatibility:** Documentation only unless seed verification proves the GitHub visual intro Agent data in existing databases needs a forward repair migration.

**Repair Track:**
- Root cause: docs can overstate or understate implemented functionality after rapid feature work.
- Canonical owner: `docs/README.md` plus focused architecture docs.
- Minimal change: update README with verified implemented capabilities and link to the runtime/workspace docs. If seed data is stale, add a forward migration that updates `github-visual-project-intro-agent` and its tool bindings without editing already-applied `V20` or `V21`.
- Compatibility: do not claim deer-flow parity until acceptance matrix passes.
- Verification: docs links exist and no stale "planned only" statements remain for shipped features.

**Retirement Track:**
- Old owner/fallback: scattered comparison notes and temporary reports.
- Active status: keep temporary reports unreferenced unless they are promoted.
- Deletion trigger: archive or remove temporary reports only with explicit user approval.

**Verification:**
```powershell
rg -n "deer-flow|deerflow|skill|artifact|tool_search|present_files|Agent Workspace Runtime" docs/README.md docs/agent-workspace-runtime.md docs/deerflow-web-alignment.md
```

- [x] Write docs update after code slices pass verification.
- [x] Verify README links to the new docs.
- [x] Verify the docs name the 21 built-in skills location and generated-media skills.
- [x] Promote local deer-flow references into `docs/deerflow-web-alignment.md` with upstream remote URL, commit hash, compared file paths, and short excerpts or summaries for progressive skill loading, tool policy, deferred `tool_search`, `present_files`, and frontend artifact rendering.
- [x] Verify `resources/database/seahorse_init.sql` and applied migrations agree for the GitHub visual intro Agent; no forward migration needed and already-applied `V20`/`V21` remain untouched.
- [x] Verify the docs distinguish aligned features from surpass features.
- [x] Commit: `docs: document deerflow web alignment`

---

## Acceptance Matrix

| Capability | Seahorse target | deer-flow parity signal | Surpass signal |
| --- | --- | --- | --- |
| Live stream timeline | Every normalized event updates active message | Message workspace shows running steps | Event backfill restores missed events |
| Tool calls | Tool start/wait/finish/error render in workbench | Tool activity visible near message | Redacted args, approvals, policy reasons, cost |
| Skill invocation | Selected/bound skills visible and loadable | Progressive loading pattern works | Skill diagnostics, version snapshots, policy mode |
| Artifact presentation | Generated outputs are persisted, previewed, downloadable | Equivalent to `present_files` | Safety scan, edit/update, A2UI Lite, governance |
| Image generation | `image_generation` produces visible image artifacts | Images are inspectable in web UI | Cost, provenance, scan status, retry/replay |
| PPT/chart/newsletter/frontend design | Each output creates artifact rows/events | Files can be presented and downloaded | Typed previews and enterprise audit |
| Tool discovery | Deferred `tool_search` available after policy filtering | Searchable deferred tools | MCP allowlist and risk metadata included |
| Run recovery | Snapshot hydrates message and workbench | Refresh keeps workspace useful | Replay, event backfill, governance timeline |
| Docs | README reflects implemented status | Capabilities are findable | Seahorse-native architecture is explicit |

---

## Rollout Plan

### Phase 0: Critical Chat Runtime Closure

Deliver Tasks 1, 2, and 3.

**Exit evidence:**
- Live events update the active assistant message.
- Chat/workbench touched files pass the encoding guard, and any real mojibake hit has a recorded line-level repair.
- Snapshot refresh hydrates timeline, sources, artifacts, approvals, and status.

### Phase 1: Artifact and Generation Closure

Deliver Tasks 4 and 5.

**Exit evidence:**
- Artifact lifecycle works from tool output to preview/download.
- Image, PPT, chart, newsletter, and frontend-design tools publish artifacts.
- `model="default"` still resolves to configured generation models, and image `style` is either forwarded or formally retired from schema and docs.
- Unsafe artifacts are visible as blocked but cannot be downloaded.

### Phase 2: Skill and Tool Runtime Parity

Deliver Tasks 6, 7, and 8.

**Exit evidence:**
- Progressive skill loading works for selected skills.
- Skill tool metadata cannot grant unauthorized tools.
- Deferred `tool_search` returns only policy-allowed tools.

### Phase 3: Workbench Rendering and Seahorse Surpass Features

Deliver Tasks 9, 10, and 11.

**Exit evidence:**
- Tool Calls and Skills tabs exist in the workbench.
- Agent Inspector supports replay/backfill.
- Chat reconnect/backfill reuses the existing resume stream path unless tests prove it cannot satisfy the scenario.
- Cost, quota, approvals, and policy diagnostics are visible.

### Phase 4: Documentation and Review

Deliver Task 12 after implementation verification.

**Exit evidence:**
- README and new docs describe implemented features accurately.
- A code review finds no P0/P1 regressions.
- All verification commands pass or documented failures have owners.

---

## Risks

### Duplicate Runtime Owners

Risk: frontend `artifactStore`, chat message state, backend artifacts, and snapshots can become competing owners.

Mitigation: message state owns live rendering; backend snapshot/artifact owns persistence; any separate store must either feed message state or be retired.

### Prompt and Tool Surface Bloat

Risk: full skill content plus full tool schemas can inflate prompts.

Mitigation: progressive `load_skill_resource` and deferred `tool_search` reduce default injection while preserving discoverability.

### Policy Confusion

Risk: users may read skill `allowedTools` as permission grants.

Mitigation: UI labels and backend tests must prove Agent/ToolCatalog policy is the maximum boundary.

### Unsafe Generated Outputs

Risk: generated files may be downloaded before scan or provenance checks.

Mitigation: artifacts carry `scanStatus`, `canPreview`, and `disposition`; frontend blocks unsafe downloads.

### Generation Tool Contract Drift

Risk: fixing artifact publication can accidentally change existing tool contracts, such as passing literal `default` as a model id or dropping image `style` even though the schema advertises it.

Mitigation: Task 5 must preserve `model="default"` fallback, verify image `style` forwarding or formal retirement, and keep `BuiltInAgentToolRegistrarTests` green for tool ids and catalog metadata.

### Encoding Regression

Risk: new Chinese labels can reintroduce mojibake even though the reviewed chat/workbench files are currently clean.

Mitigation: touched chat/workbench/SSE hook files must pass source scan and build before commit; when the scan finds no hits, Task 2 should produce a guard/evidence commit rather than an empty "repair" commit.

### Backend Event Shape Drift

Risk: frontend normalizers accept many shapes, but backend might change event fields without tests.

Mitigation: add contract tests or backend DTO tests for all `agent.*` events used by the workbench.

### Duplicate Backfill Channels

Risk: adding another replay/backfill endpoint before exhausting the existing `resumeRunId`/`lastEventSeq` stream path and `/api/agent-runs/{runId}/events?afterSeq=...` event-list path can split recovery behavior across multiple owners.

Mitigation: Task 11 must prove whether the existing resume stream supports chat backfill first and use the existing event-list endpoint for admin replay; extend only for tested gaps that the current contracts cannot cover.

### Applied Migration Mutation

Risk: editing already-applied `V20` or `V21` seed migrations can make existing databases diverge from fresh installs.

Mitigation: leave applied migrations immutable; use `resources/database/seahorse_init.sql` for fresh snapshots and a next-numbered forward migration for existing database seed repairs when evidence requires it.

### Live Snapshot Race

Risk: a snapshot request can return older arrays after newer live events have already updated the message.

Mitigation: live event and snapshot hydration paths must use the same idempotent, id-based, sequence-aware merge helpers and must not replace whole event arrays.

### chatStore Growth

Risk: adding live event binding, snapshot hydration, tool calls, skills, and event backfill directly into `chatStore.ts` will make the store hard to test and review.

Mitigation: Task 1 extracts pure handlers into `chatStreamHandlers.ts`; later tasks extend those handlers rather than growing inline Zustand logic.

---

## Rollback Surface

- Task 1 can be rolled back by disabling the new `applyAgentStreamEventToMessage` path while retaining plain message deltas.
- Task 3 can be rolled back by keeping snapshot hydration limited to text/thinking.
- Tasks 4 and 5 can be rolled back per tool by returning summaries while keeping existing artifact APIs.
- Tasks 6 and 8 can be feature/config gated and disabled without breaking existing selected-skill direct injection.
- Task 7 restrictive mode must default to advisory unless explicitly enabled.
- Workbench tabs in Tasks 9 and 10 can be hidden if backend events are incomplete.

---

## Retirement Plan

- Retire approval-only stream handling once the generic event merge helper covers approvals.
- Retire or formally deprecate `frontend/src/stores/artifactStore.ts` once message-state artifact merging is verified. If retained, document it as a cache/mirror with no authority over persisted artifacts.
- Retire local-only artifact storage as a durable source once all generation tools persist artifacts.
- Retire misleading "allowed tools" UI copy if it implies permission grants.
- Retire full tool schema prompt injection for large catalogs after deferred `tool_search` is verified.
- Retire temporary comparison reports only after README and `docs/deerflow-web-alignment.md` become the maintained documentation source, and only with explicit approval.

---

## Review Checklist

- [ ] Does every stream event used by the workbench have a stable id and merge behavior?
- [ ] Do live event merge and snapshot hydration use the same idempotent helper and preserve newer live data?
- [ ] Does plain text chat still work with no Agent run and no selected skill?
- [ ] Does refresh/reconnect recover the same workbench state from snapshot?
- [ ] Are generated image/PPT/chart artifacts persisted and downloadable only when clean?
- [ ] Do generation tools preserve `model="default"` fallback and image `style` contract?
- [ ] Does Skill policy restrict but never grant tools?
- [ ] Are new runtime tools registered through `SeahorseAgentKernelAgentAutoConfiguration` and `BuiltInAgentToolRegistrar`, with registrar tests?
- [ ] Does SSE recovery reuse `resumeRunId`/`lastEventSeq` where possible and preserve `SpringSseEventSender` error/done behavior?
- [ ] Does deferred `tool_search` hide denied tools?
- [ ] Are tool arguments redacted and preview-limited?
- [ ] Are all touched Chinese strings valid UTF-8?
- [ ] Are already-applied migrations left immutable, with forward migrations used for any seed repair?
- [ ] Do frontend contract tests include new API calls?
- [ ] Do docs avoid claiming parity until the acceptance matrix is green?

---

## Completion Evidence Template

Use this evidence shape when executing the plan:

```text
Task:
Commit:
Files changed:
Verification commands:
Passing output summary:
Manual browser checks:
Residual risks:
Follow-up owner:
```
