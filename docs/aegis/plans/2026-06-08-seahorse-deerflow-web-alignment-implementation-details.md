# Seahorse DeerFlow Web Alignment Implementation Details

> **Authority:** Execution companion for `2026-06-08-seahorse-deerflow-web-alignment-plan.md`. The plan remains the source of truth for goals, phase order, compatibility boundaries, and acceptance.
>
> **Audience:** Developers executing Tasks 1-3 first, then extending the same contracts through artifact, skill, tool, and replay tasks.
>
> **Status:** Living companion. Update this only when implementation or tests prove a contract needs to change; update the main plan at the same time if scope, risk, or acceptance changes.

---

## Scope

This document captures execution contracts that are easy to lose in a long plan:

1. Stream events and snapshots must merge into message state idempotently.
2. `frontend/src/hooks/useStreamResponse.ts` already supports `resumeRunId` and `lastEventSeq`; reuse that path before adding a second backfill owner.
3. Content-generation tools still return JSON observations to the model, while Task 5 now publishes persisted generation artifacts through the gateway-level artifact publication hook.
4. Encoding checks must assert correct Chinese text, not mojibake samples.

Snippets in this document are examples. Verify them against current repository types before implementation.

---

## Review Accuracy Notes

`2026-06-08-implementation-details-review.md` is useful review evidence, but it reviewed an older revision of this companion document. On 2026-06-09 the codebase has already landed several implementation slices that change the correct action for the review findings. Do not execute the review as a live task list; use the dispositions below as the current contract.

A legacy console or default PowerShell read may render valid UTF-8 Chinese as mojibake. If Chinese text appears corrupted, verify with a UTF-8-aware editor, byte/code-point scan, or `rg` before copying it into fixtures or treating it as source corruption.

Review outcome:

- Accurate and still binding: `StreamEventEnvelope` does not carry `messageId`; `SkillSelectionContext` does not exist; chat reconnect should reuse the existing `resumeRunId` plus `lastEventSeq` SSE path before adding another chat backfill owner.
- Accurate problem, superseded fix: the review correctly noticed missing runtime context in the old pseudo-code, but the current architecture keeps `ToolPort.invoke(toolCallId, toolId, arguments)` stable and uses `ToolInvocationRequest`, `ToolArtifactPublicationPort`, and server-injected hidden snapshots at the gateway/runtime boundary.
- Superseded by current implementation: artifact persistence, remote image artifact fallback, `load_skill_resource`, `tool_search`, Tool Calls/Skills rendering, admin replay, cost/quota rendering, and historical Chat Workbench hydration now have concrete owners and focused tests. Reopen them only from fresh acceptance gaps.
- Residual risk: `LocalChatStreamCallbackFactory` reconciles the proven gateway-publisher-to-callback interleaving, but `AgentRunEventBufferPort` still has no shared atomic append-next-sequence contract across all writers.
- Remaining gate: current-branch focused tests cover the implemented units, and the 2026-06-09 backend/frontend E2E evidence remains useful historical evidence for the tool/skill/artifact/SSE/replay path. It does not close the final gate after commit `ead0e304`, because that commit changed the `/rag/v3/chat` Consumer Web entry protection. The final gate requires a fresh backend and frontend started from the latest branch tip, plus a fresh E2E run through the normal Chat UI path.
- Consumer Web nuance: `AGENT_RUN_MANAGEMENT` can be enabled as a core feature in current Consumer Web mode, so chat-entry protection must not rely only on that feature flag. The current contract is narrower: server-owned controlled templates may resolve to Agent mode without explicit `agentId/versionId`; raw `chatMode=agent` and explicit `agentId/versionId` remain rejected at `/rag/v3/chat`.

Disposition matrix:

| Review item | Accuracy against current branch | Current decision |
| --- | --- | --- |
| `StreamEventEnvelope.messageId` does not exist | Accurate | Keep live message routing outside the envelope. Other DTOs may carry optional `messageId`, but test fixtures must not invent an envelope field. |
| Backend JSON may return `eventSeq` as a string | Accurate for current Spring serialization | Keep replay/admin normalization for safe decimal strings until the frontend wire type is audited and widened deliberately. |
| Undefined `ExecutionContext` / `SkillSelectionContext` | Accurate for the old document | Use existing gateway/runtime owners. Do not invent hidden globals or a second skill-selection owner. |
| Add `ExecutionMetadata` to `ToolPort.invoke(...)` | Plausible alternative, not current architecture | Do not apply from this review. Preserve the stable `ToolPort` contract unless a future reviewed slice updates every implementation together. |
| Artifact publication trigger was unclear | Accurate for the old document, superseded by implementation | `ToolArtifactPublicationPort.publish(request, rawResult)` is the implemented owner after successful raw tool execution. |
| Image artifact persistence only covered `b64Json` | Superseded by implementation | Image observations with either `b64Json` or remote `imageUrl` persist an `AgentArtifact` reference and emit an artifact event. |
| Task 8 missing context | Superseded by implementation | `tool_search` is registered, policy-filtered by server-injected allowed ids, metadata-only, and rendered as deferred discovery in admin. |
| Task 9-11 lacked detail | Superseded at focused acceptance level | Tasks 9/10/11 now have concrete implementations and tests; final E2E remains the plan gate. |
| SSE live stream worked but admin replay was empty | Accurate as an implementation risk | Current baseline requires lazy buffer binding, serialized JDBC payloads, and fresh replay evidence. |
| Event buffer auto-configuration could miss late `ObjectMapper` beans | Accurate | Condition only on `DataSource`, inject `ObjectProvider<ObjectMapper>`, and keep the fallback mapper local to the adapter. |
| Concurrent event sequence allocation is not atomic across all writers | Accurate residual hardening gap | The current callback fix covers observed sequential/single-stream interleavings; a future repository-level slice should add atomic append-next-sequence or unique-key plus retry semantics. |
| Admin Inspector showed `No events` while replay API returned rows | Accurate as a current-worktree E2E finding | Fixed by supporting both `/agent-runs/{runId}/events` and `/api/agent-runs/{runId}/events`, aligning both Vite configs with `VITE_PROXY_TARGET`, and normalizing string `eventSeq` values in `AgentInspectorPage`. |
| Chat Workbench could not trigger the GitHub visual Agent from normal chat | Accurate as a current-worktree E2E gap | Fixed by adding the `github-visual-project-intro` task template and resolving its `defaultAgentId` server-side. Frontend may send the template selection, but backend remains authoritative: Consumer Web only allows controlled templates without explicit `agentId/versionId`, while raw `chatMode=agent` and arbitrary consumer-web `agentId` selection are rejected. |
| GitHub visual intro governance cost tier | Accurate as a current-branch gap, now fixed | Template metadata says HIGH/LONG; controller rate limiting now includes the template, and quota summaries now prefer `TaskTemplateQueryInboundPort` metadata. |
| Historical Chat messages did not recover Workbench state | Accurate as a final-gate gap found during current-worktree E2E | Fixed by hydrating selected-session assistant messages from `getAgentRunSnapshot`, `listAgentRunEvents`, and `getAgentRunCostSummary`; replay events are normalized, sorted, deduped, merged through `chatStreamHandlers`, and guarded against stale session writes and duplicate in-flight loads. |
| `artifactStore.ts` was a duplicate artifact owner | Accurate for the current worktree after the production-reference scan | Retired after same-slice frontend tests/build proved the message-bound workspace owner still covers artifact rendering and recovery. |

Current code anchors:

| Concern | Current owner |
| --- | --- |
| Stream envelope shape | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/stream/StreamEventEnvelope.java`, `frontend/src/types/index.ts` (`StreamEventEnvelope`) |
| Live stream reconnect | `frontend/src/hooks/useStreamResponse.ts`, `SeahorseChatController`, `ResearchSseBridge` |
| Message/workbench merge owner | `frontend/src/stores/chatStreamHandlers.ts`, `frontend/src/stores/chatStore.ts` |
| Artifact UI state owner | Message-bound workspace state in `chatStore.ts`; `artifactStore.ts` is retired in the current cleanup slice |
| Tool invocation context | `ToolInvocationRequest` at the gateway boundary; `ToolPort.invoke(toolCallId, toolId, arguments)` remains unchanged |
| Artifact publication | `LocalToolGatewayPort` calls `ToolArtifactPublicationPort.publish(request, rawResult)` after successful raw execution |
| Generation artifact persistence | `GenerationToolArtifactPublicationPort` plus its focused tests |
| Progressive skill loading | `ChatSelectedSkillResolver`, `SkillRuntimeComposer`, `KernelAgentLoop`, `LoadSkillResourceToolPortAdapter` |
| Deferred tool search | `ToolSearchToolPortAdapter` with server-injected `_seahorseAllowedToolIds` |
| SSE event buffering | `LocalChatStreamCallbackFactory` resolves `AgentRunEventBufferPort` lazily; `JdbcAgentRunEventBufferAdapter` persists typed payload JSON |
| Replay buffer auto-configuration | `SeahorseAgentRegistryRepositoryAutoConfiguration` creates `JdbcAgentRunEventBufferAdapter` from `DataSource` plus optional `ObjectMapper` |
| Admin replay/event list | `AgentRunEventBufferPort`, `SeahorseAgentRunController`, `AgentInspectorPage` |
| Chat task template to Agent binding | `KernelTaskTemplateQueryService` exposes `github-visual-project-intro`; `SeahorseChatController` maps controlled templates to Agent mode only when the request does not carry explicit `agentId/versionId`; `KernelChatInboundService` resolves the template `defaultAgentId` and final output artifact type through `TaskTemplateQueryInboundPort` |
| Task template governance classification | `KernelTaskTemplateQueryService` owns declared cost/duration metadata; `KernelQuotaSummaryService` reads it through `TaskTemplateQueryInboundPort`; `SeahorseChatController.HIGH_COST_TASK_TEMPLATES` mirrors high-cost consumer-web templates while `CONTROLLED_WEB_AGENT_TEMPLATES` remains the explicit exposure allowlist |
| Frontend dev proxy | Keep `frontend/vite.config.ts` and `frontend/vite.config.js` aligned until one config is retired |

---

## Stream Merge Contract

Every live event or snapshot hydration path must follow the same rules:

- Merge arrays by stable id; never replace timeline, sources, artifacts, approvals, quota, memory, tool calls, or skills wholesale.
- Treat `eventSeq` and snapshot sequence values as monotonic recovery metadata.
- If incoming sequence is older than the message's latest applied sequence for that event family, keep the newer live message data.
- Missing optional payload fields leave existing message fields unchanged.
- Do not put `messageId` in `StreamEventEnvelope` fixtures. Route live events to the active streaming assistant message through chat store state, or pass `messageId` separately to snapshot/artifact hydration helpers whose real source type carries it.
- If a fixture needs a target message, keep that identifier in the test harness or message object. Do not add it to the event envelope just to simplify test setup.
- Plain text SSE `message` deltas continue to work when there is no Agent run.

Preferred helper shape:

```typescript
type Mergeable = { id: string };

export function mergeById<T extends Mergeable>(
  current: T[] | undefined,
  incoming: T[],
  mergeItem: (current: T, incoming: T) => T = (a, b) => ({ ...a, ...b })
): T[] {
  const byId = new Map<string, T>();
  for (const item of current ?? []) byId.set(item.id, item);
  for (const item of incoming) {
    const previous = byId.get(item.id);
    byId.set(item.id, previous ? mergeItem(previous, item) : item);
  }
  return Array.from(byId.values());
}
```

Artifact content events need special handling:

- `append=true` appends only the new content delta.
- completion state and preview metadata merge by id.
- server artifacts from `AgentArtifact` remain the persisted authority.

---

## Task 1 Checklist

- Add `frontend/src/stores/chatStreamHandlers.ts` with pure merge helpers.
- Move `onStreamEvent` handling out of inline `chatStore.ts` switch logic.
- Preserve existing approval behavior while expanding to timeline, sources, artifacts, quota, memory, and tool calls.
- Add tests for duplicate events, stale events, artifact append, and "live event arrives while snapshot fetch is in flight".
- Retire or deprecate `frontend/src/stores/artifactStore.ts` only after production consumers are checked.
- Current worktree status: `rg` finds no production references to `artifactStore`, `useArtifactStore`, `useActiveArtifacts`, or the exported artifact-store mutators under `frontend/src`. `chatStore`, `chatStreamHandlers`, workbench rendering tests, and the frontend build passed after the deletion.

Focused verification:

```powershell
cd frontend
npm test -- chatStreamUtils chatStreamHandlers chatStore
npm run build
```

---

## Task 2 Encoding Guard

Current baseline:

- `frontend/src/components/chat/workbench/WorkspaceInspector.tsx`, `frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx`, `frontend/src/stores/chatStore.ts`, and `frontend/src/services/agentArtifactService.ts` are clean for the known mojibake code points from the review scan.
- `frontend/src/hooks/useStreamResponse.ts` is also clean in the current scoped scan; watchdog comments and the stream-timeout message render as normal Chinese when read as UTF-8.
- `frontend/src/stores/chatWorkspaceEncoding.test.ts` asserts the user-visible Chinese labels from the workbench and artifact inspector. Keep those assertions on readable Chinese strings, never on mojibake output captured from a misconfigured console.

Execution constraints:

- Treat this task as a guard plus targeted repair, not a broad rewrite.
- Do not batch-rewrite unrelated Chinese copy.
- Validate suspected mojibake from file bytes/code points, not from a terminal rendering alone. PowerShell or console output can display valid UTF-8 Chinese incorrectly.
- If the scan finds a real hit, record the exact file and line in the commit/review note.

Guard these files when they are touched:

- `frontend/src/hooks/useStreamResponse.ts`
- `frontend/src/stores/chatStore.ts`
- `frontend/src/components/chat/workbench/WorkspaceInspector.tsx`
- `frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx`
- `frontend/src/services/agentArtifactService.ts`

PowerShell scan:

```powershell
cd frontend
$badCodePoints = @(0x9354,0x9239,0x7035,0x6D93,0x95AB,0x59AB,0x9983,0x9241,0x9242,0xFFFD)
$pattern = ($badCodePoints | ForEach-Object { [char]$_ }) -join "|"
$paths = @(
  "src/hooks/useStreamResponse.ts",
  "src/stores/chatStore.ts",
  "src/components/chat/workbench/WorkspaceInspector.tsx",
  "src/components/chat/workbench/ArtifactInspectorTab.tsx",
  "src/services/agentArtifactService.ts"
)
Get-ChildItem $paths -Recurse -File | Select-String -Pattern $pattern
```

Correct label assertions should use normal Chinese strings, for example:

- `运行详情`
- `关闭检查器`
- `复制内容`
- `下载`
- `文件未通过安全扫描`

Do not assert mojibake samples as expected output.

---

## Task 3 Snapshot Hydration

Snapshot hydration must call the same merge helpers as live events.

Required tests:

- snapshot adds missing timeline/source/artifact data to an interrupted message.
- snapshot with older sequence does not overwrite a newer live event.
- snapshot missing optional arrays does not clear existing message arrays.
- snapshot status updates message status without losing streamed text.

---

## Task 5 Generation Tool Contract

Current baseline:

- `AbstractChatContentGenerationToolPortAdapter` returns JSON fields `artifactType`, `format`, and `content`.
- `ImageGenerationToolPortAdapter` returns JSON fields `status`, `prompt`, `model`, `imageUrl`, `b64Json`, and `mimeType`.
- Tool registration through `BuiltInAgentToolRegistrar` already covers built-in generation tools.
- `model="default"` resolves to the configured default model.
- `ImageGenerationToolPortAdapter` forwards the advertised `style` argument and defaults to `b64_json` so default image generations can be persisted as internal artifacts.
- `ToolArtifactPublicationPort` exists in the kernel outbound ports. `LocalToolGatewayPort` calls it after successful raw tool execution and before output redaction, passes the full `ToolInvocationRequest`, and swallows publication exceptions so the tool observation remains authoritative.
- `SeahorseAgentKernelAgentAutoConfiguration` wires an optional `ToolArtifactPublicationPort` into `LocalToolGatewayPort`; when absent, the gateway uses `ToolArtifactPublicationPort.noop()`.
- Implemented status: newsletter, PPT, chart, frontend-design, and image-generation outputs now have direct publisher tests proving persisted `AgentArtifact` rows and `agent.artifact` event payloads. `image_generation` defaults to `b64_json` so generated images can be stored internally; returned and audited observations redact `b64Json`.
- Implemented status: image-generation observations with remote `imageUrl` and empty `b64Json` are also persisted as image artifact references without object-storage upload. This is required for providers that return hosted image URLs despite the preferred `b64_json` request format.

Implementation constraints:

- Treat the concrete generation artifact publisher as implemented behavior. Future changes must preserve the existing gateway-level `ToolArtifactPublicationPort` hook and must not move artifact publication into web controllers, `SpringSseEventSender`, or individual generation tool adapters.
- The review's suggested `ExecutionMetadata` parameter is not the current contract. Treat `ToolInvocationRequest` as the only typed runtime context at the gateway boundary; only use hidden tool arguments for server-owned snapshots that the adapter must validate, such as selected skills or allowed searchable tools.
- Preserve tool ids and catalog metadata.
- Preserve `model="default"` fallback.
- Keep forwarding image `style`; if the contract is intentionally retired later, remove it from schema, docs, and tests in the same reviewed change.
- Keep emitting artifact events with `artifactId`, `runId`, `title`, `mimeType`, `previewText`, and safe storage reference metadata.
- Keep both image persistence paths covered: `b64Json` uploads bytes to object storage, and remote `imageUrl` stores the URL as the artifact `storageRef`. Do not treat a hosted image URL as a failed generation solely because `b64Json` is absent.

Context boundary:

- Do not use undefined `ExecutionContext`, `ExecutionMetadata`, `AgentRunContext`, or `SkillSelectionContext` names in implementation or tests unless the same change creates and wires that contract.
- Preferred execution shape for Task 5 is now explicit and implemented: preserve `ToolInvocationRequest` as the gateway-level context carrier and publish artifacts from a `ToolArtifactPublicationPort` implementation that can see the full request plus the raw successful result.
- Do not extend `ToolPort.invoke(...)` just to satisfy the review's former `ExecutionMetadata` suggestion. If a future feature truly requires changing the signature, update all implementations, `LocalToolGatewayPort`, auto-configuration, registry tests, and artifact-publication tests in the same slice. Keep the new parameter explicit and typed; do not make generation tools read global state.
- Keep `ToolPort` result semantics stable. The publisher should treat generation tool output as an observation to parse and persist, not as a replacement for the tool result returned to the model.
- Persist artifacts in a gateway/kernel collaborator that can see `runId`, `stepId`, `tenantId`, `userId`, `allowedToolIds`, and the raw successful `ToolInvocationResult`; do not rely on a web-layer `ApplicationEventPublisher` or SSE sender as the source of truth. Redact the result before returning it to the model and before audit summaries so large image `b64Json` payloads and secrets are not surfaced.
- If a scoped context holder is introduced instead, it must be set and cleared inside `LocalToolGatewayPort` with tests proving cleanup after success, failure, denial, and nested/parallel invocations.
- `AgentArtifact.messageId` is optional in the current domain model. Do not block artifact persistence solely because `messageId` is unavailable at tool-adapter level; use `runId` as the required recovery key and attach `messageId` only when a verified owner supplies it.

Verification focus:

```powershell
.\mvnw.cmd -pl seahorse-agent-kernel -am test -Dtest=ImageGenerationToolPortAdapterTests,*ContentGeneration*Tests,LocalToolGatewayPort*Tests
.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am test -Dtest=BuiltInAgentToolRegistrarTests,SeahorseAgentChatRunStoreAutoConfigurationTests#shouldWireArtifactPublisherIntoToolGateway -Dsurefire.failIfNoSpecifiedTests=false
.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=GenerationToolArtifactPublicationPortTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

---

## Task 6 Progressive Skill Loading

Current baseline:

- `ChatSelectedSkillResolver` performs server-side validation for per-turn selected skill names.
- Validated selections become `SkillRuntimeBlock` values and may be downgraded to `METADATA_ONLY`.
- There is no existing `SkillSelectionContext`.
- Current implementation has a registered, catalog-visible `load_skill_resource` tool. `KernelAgentLoop` injects a hidden runtime skill snapshot into its arguments before dispatching through `ToolGatewayPort`; the legacy inline `load_skill` alias remains only for compatibility with existing model calls.
- `LoadSkillResourceToolPortAdapter` intentionally reads `_seahorseSkillRuntimeBlocks` from server-injected arguments because `ToolPort.invoke(...)` has no typed metadata parameter. Frontend/model-provided skill names are request intent only, not authorization evidence.

Implementation constraints:

- `load_skill_resource` must only load resources for skills that are already selected or version-bound in the current run context.
- Do not trust skill names from the frontend or from model tool arguments as authorization proof.
- Reuse selected `SkillRuntimeBlock` metadata or an explicit tool invocation metadata carrier; do not create a second independent skill-selection owner.
- Because `ToolPort.invoke(...)` currently receives only `toolCallId`, `toolId`, and `arguments`, `load_skill_resource` cannot infer selected skills inside the adapter unless the runtime passes selected-skill state before gateway dispatch.
- Implemented path: `KernelAgentLoop` injects a hidden runtime skill snapshot into `load_skill_resource` arguments from `AgentLoopRequest.skillRuntimeBlocks()`. The adapter validates `skillName` and `resourcePath` only against that injected snapshot, never against frontend/model-provided authorization claims.
- `load_skill_resource` currently exposes `SKILL.md` only. Adding arbitrary resource-file loading requires a new reviewed contract for revision resource indexing, path normalization, and storage lookup; do not infer it from the old review.
- Do not replace this with a typed metadata carrier unless the same slice updates every `ToolPort` implementation, `LocalToolGatewayPort`, registry tests, and artifact publication tests.
- If the selected-skill set is carried into tool invocation, derive it from the same `ChatSelectedSkillResolver` result used to compose the prompt/runtime blocks.
- Reject absolute paths, parent traversal, empty paths, and paths outside the selected skill revision resource set.
- Register the tool through `SeahorseAgentKernelAgentAutoConfiguration`; `BuiltInAgentToolRegistrar` should discover it as a `DescribedToolPort`.
- Route `load_skill_resource` through `ToolGatewayPort` after registration so policy, audit, catalog, and future tool-search behavior stay consistent. Keep the inline legacy `load_skill` path only when compatibility tests justify it.

Required tests:

- selected skill `SKILL.md` loads successfully.
- unselected skill is rejected.
- disabled, inactive, or missing-revision skill is rejected through existing resolver semantics.
- parent traversal and absolute paths are rejected.
- registrar/catalog visibility matches the feature flag.
- gateway/audit path is exercised for `load_skill_resource`, while legacy `load_skill` compatibility is covered separately if retained.

---

## Task 7 And 8 Tool Policy/Search

Execution constraints:

- Skill policy can reduce the active agent tool set; it must not grant a tool the agent/version did not already allow.
- Deferred tool search returns metadata only and must filter results by the same effective allowed-tool set used for real invocation.
- Tool search must not bypass `ToolPolicyPort`, `ToolCatalogRepositoryPort`, or `BuiltInAgentToolRegistrar` metadata.
- Do not depend on a nonexistent `ExecutionContext`; keep policy and runtime context at the `KernelAgentLoop`/`ToolInvocationRequest` boundary unless a future reviewed slice updates every `ToolPort` implementation.
- `tool_search` receives its allowed-tool snapshot through the hidden `_seahorseAllowedToolIds` argument injected by the server. Model-supplied values are not authorization authority.
- `tool_search` is intentionally metadata-only. It returns `toolId`, `name`, and `description`; it does not expose `schemaJson`, policy internals, credentials, or runtime-only helper snapshots.
- `tool_search` must fail closed when the hidden allowed-tool snapshot is missing.
- `tool_search` may be exposed as a runtime helper when the effective business-tool allowlist is non-empty. Its injected snapshot must contain only effective business tools, not helper tools such as `tool_search` itself.
- Runtime gateway allowlists may include `tool_search` so the helper can execute, but search results must remain filtered to the injected effective business-tool snapshot.
- `tool_search` responses must include only `toolId`, `name`, and `description`; do not return `schemaJson` or secrets.
- Spring registration currently treats `tool_search` as a `TOOL` catalog resource through `BuiltInAgentToolRegistrar`.
- Admin tool catalog currently labels `tool_search` as `延迟发现` and explains that it only exposes authorized tool metadata; this satisfies the current frontend/admin Task 8 diagnostic requirement.

Required tests:

- restrictive skill policy intersects with agent allowed tools.
- advisory skill policy leaves the agent allowed tool set unchanged.
- restrictive selected skills with empty `allowedTools` expose no Agent tools, while still allowing the runtime-only `load_skill_resource` compatibility tool when loadable skills exist.
- tool search hides denied tools and returns only catalog metadata.
- tool search rejects missing server-injected allowed-tool snapshots.
- `KernelAgentLoop` injects effective restrictive allowlists into tool-search calls.
- enabling/disabling the feature flag changes registration and catalog visibility as expected.
- any future frontend/admin expansion should preserve the distinction between eager visible tools and deferred searchable tools.

---

## Task 9 And 10 Frontend Rendering

Execution constraints:

- Normalize tool-call and skill events into message-bound workspace state using the same merge helpers from Task 1.
- Treat unknown event types as ignorable, not fatal.
- Keep admin inspector views read-only; chat workspace state remains the user-facing rendering owner.
- Avoid adding duplicated artifact, skill, or tool-call stores unless production consumers of existing stores are retired in the same change.

---

## Task 11 SSE And Replay

Current baseline:

- `SpringSseEventSender` sends named events and emits `error` followed by `done` on failure.
- `SeahorseChatController`, `ResearchSseBridge`, and `useStreamResponse.ts` already have `resumeRunId` and `lastEventSeq` support.
- `AgentRunEventBufferPort` stores stream envelopes by run, and `SeahorseAgentRunController` exposes `/api/agent-runs/{runId}/events?afterSeq=...` for admin replay/event listing.
- The event-list endpoint also exposes `/agent-runs/{runId}/events?afterSeq=...` for the frontend dev proxy path, because the current Vite proxy strips the leading `/api` prefix before forwarding to the backend.
- `LocalChatStreamCallbackFactory` resolves `AgentRunEventBufferPort` lazily when each callback is created, so a callback created after repository auto-configuration uses the real JDBC buffer instead of permanently capturing `noop`.
- `LocalChatStreamCallbackFactory` also flushes externally buffered events, such as `AGENT_ARTIFACT` events appended by `GenerationToolArtifactPublicationPort`, back to the live SSE stream before sending later callback-owned events or completion. The callback tracks the highest sent `eventSeq` and reads the buffer's latest sequence before assigning a new local sequence, preventing duplicate sequence numbers for the observed sequential interleaving between gateway-level artifact publication and callback-owned events.
- `GenerationToolArtifactPublicationPort` still appends its event by reading `getLatestSeq(runId) + 1`; the callback now reconciles with the buffer before it emits later callback-owned events. This is sufficient for the observed gateway-publisher-to-callback ordering gap, but it is not a repository-wide atomic sequence allocator.
- `JdbcAgentRunEventBufferAdapter` serializes typed payloads as JSON and keeps a narrow compatibility path for driver/H2 string-literal payloads on read.
- `SeahorseAgentRegistryRepositoryAutoConfiguration` registers the JDBC `AgentRunEventBufferPort` when `DataSource` is present, even if the application `ObjectMapper` bean is not ready at condition-evaluation time. The adapter receives `ObjectProvider<ObjectMapper>` and falls back to a local mapper.
- `frontend/src/services/agentRunService.ts` already wraps `listAgentRunEvents(runId, afterSeq)`, and `AgentInspectorPage` loads the event list together with snapshot and cost summary data.
- `AgentInspectorPage` sorts replayed events by `eventSeq` and dedupes duplicate sequence numbers before rendering. It must accept both numeric `eventSeq` values and backend JSON string values that parse to safe integers.
- `frontend/vite.config.ts` and `frontend/vite.config.js` both read `VITE_PROXY_TARGET`, defaulting to `http://localhost:9090`. Current-worktree E2E against a non-default backend port must restart the frontend after setting this variable; checking only `vite.config.ts` is insufficient because Vite may load `vite.config.js`.
- `useStreamResponse.test.ts` proves reconnect URLs include `resumeRunId` and `lastEventSeq`.
- `AgentInspectorPage.test.tsx` proves replay ordering and dedupe.
- `CostQuotaInspectorTab` already renders run cost, quota, resume, and retry controls from message-bound workbench state.
- `CostQuotaInspectorTab.test.tsx` proves readable Chinese labels plus resume/retry actions.

Implementation constraints:

- Reuse the existing resume stream for chat backfill first.
- Treat the existing event-list endpoint as the admin replay owner; do not add another event-list endpoint unless tests prove the current buffer contract cannot preserve required ordering or filtering.
- Chat reconnect/backfill should use the existing SSE resume path and then merge through `chatStreamHandlers.ts`; admin replay can consume `listAgentRunEvents`.
- Preserve `SpringSseEventSender` closed-emitter behavior.
- Keep stream-buffer binding lazy; eager resolution at Spring bean creation time can make live SSE appear healthy while admin replay stays empty.
- Keep externally buffered event flushing inside the chat stream callback, not inside generation tools, controllers, or `SpringSseEventSender`. `ToolArtifactPublicationPort` remains the artifact-publication owner; the callback only bridges canonical buffered envelopes into the live transport.
- When flushing buffered events, use `eventSeq` as the dedupe cursor and synchronize local sequence allocation with `AgentRunEventBufferPort.getLatestSeq(runId)`. A callback-owned event emitted after an externally appended artifact must receive the next sequence, not reuse the artifact sequence.
- Residual hardening gap: `AgentRunEventBufferPort` still has no atomic "append next sequence" contract shared by all writers. The current callback fix covers the proven single-stream interleavings and live-delivery gap, but a future repository-level slice should add atomic sequence allocation or a unique `(run_id, event_seq)` constraint plus retry semantics before treating concurrent multi-writer ordering as fully hardened.
- Keep repository auto-configuration conditional on `DataSource` only for the event buffer; do not reintroduce a hard `ObjectMapper` bean condition that can silently fall back to `AgentRunEventBufferPort.noop()`.
- Persist replay payloads as JSON objects/arrays/values through `ObjectMapper`; do not hand-roll quoted payload strings.
- Keep frontend tests proving backfill and event-list replay do not duplicate live events and do not overwrite newer live state with older replay/snapshot data.
- Keep frontend admin replay tests using at least one string `eventSeq` fixture, matching the real backend large-number serialization contract.
- Keep both tracked Vite config files aligned for dev proxy changes, or retire one of them in a dedicated cleanup slice with build/dev verification.
- Add backend or controller tests only for gaps not already covered by `SeahorseChatControllerReplayTests`, `SpringSseEventSenderTests`, and the event-buffer contract.
- Verify cost/quota governance remains display-only in chat workbench, while resume/retry actions continue to call the Agent Run action APIs.

Focused acceptance evidence:

- reconnect with `resumeRunId` and `lastEventSeq` appends only missing stream events to the active message.
- replay events from `listAgentRunEvents(runId, afterSeq)` render in admin event order without duplicating earlier events.
- admin replay through the current-worktree frontend dev server renders events even when the backend serializes `eventSeq` as strings.
- a fresh current-worktree live Agent run writes non-zero rows to `sa_agent_run_event_buffer`, and `/api/agent-runs/{runId}/events?afterSeq=0` returns ordered, parseable events for the same run.
- a fresh generated image/document artifact is visible to the frontend through live `stream_event`/named `agent.artifact` when the stream remains connected, and is also recoverable through snapshot hydration, admin replay, or `/api/agent-runs/{runId}/artifacts` after reconnect/refresh.
- sequence ordering is proven for interleaved events: `RUN_STARTED`, externally buffered `AGENT_ARTIFACT`, and the next callback-owned event must appear as ordered, non-duplicated `eventSeq` values in both the replay buffer and the live SSE envelope stream.
- snapshot hydration after replay preserves newer live timeline, source, artifact, tool-call, skill, approval, and quota fields.
- cost/quota tab renders totals, quota pressure, resume, and retry states from message state without creating a second store owner.

---

## Migration Boundary

- Do not edit already-applied `V20__github_visual_project_intro_agent.sql` or `V21__github_visual_agent_generation_tools.sql` in place.
- Update `resources/database/seahorse_init.sql` for fresh database snapshots only when seed verification proves it is stale.
- Use the next numbered forward migration for existing database seed repairs.

---

## Execution Checklist

- [x] Task 1: `chatStreamHandlers.ts` + tests pass; `artifactStore.ts` had no production references and is retired after cleanup-slice verification
- [x] Task 2: Encoding guard runs clean
- [x] Task 3: Snapshot hydration + tests pass
- [x] Task 4: Artifact lifecycle baseline + unsafe download blocked
- [x] Task 5: All 5 generation tools persist artifacts
- [x] Task 6: `load_skill_resource` registered and tested
- [x] Task 7: Backend tool gateway policy tests pass; frontend advisory/restrictive diagnostics render
- [x] Task 8: Backend `tool_search` registered/tested; admin deferred-tool diagnostics render
- [x] Task 9: Tool calls tab renders
- [x] Task 10: Skills tab renders
- [x] Task 11: Event backfill unit/controller coverage works
- [x] Task 12: Docs updated
- [ ] Final current-worktree E2E gate: backend and frontend both started from the latest branch tip; Chat UI selects `github-visual-project-intro`; the user selects at least one skill such as `image-generation`; at least one tool call occurs; at least one generated artifact is persisted; live SSE includes the generated artifact as `stream_event` and named `agent.artifact` when the stream remains connected; Workbench Tool Calls, Skills, Artifacts, and Cost/Quota render for the Chat UI-started assistant message during live or persisted recovery; `sa_agent_run_event_buffer` has rows for the fresh run; `/api/agent-runs/{runId}/events?afterSeq=0` returns ordered/dedupable events; SSE resume or admin replay proves event ordering and dedupe. Historical runs, stale local services, or Docker services built from older commits do not satisfy the live-generation parts of this gate.

  Evidence boundary:

  - The focused post-commit tests after `ead0e304` cover Consumer Web chat-entry protection, SSE sender failure closeout, gateway audit preview limits, tool search, and registrar wiring.
  - The backend/frontend E2E evidence below was captured before `ead0e304`. It remains a regression reference, but it must be replayed from a freshly rebuilt backend and restarted frontend at the latest commit before this checkbox can be marked complete.

  Historical backend E2E evidence from 2026-06-09 before `ead0e304`:

  - The then-current backend ran from the branch jar in Docker as `seahorse-backend-current-branch` on `http://127.0.0.1:19094`, because local Windows DNS resolved `apihub.agnes-ai.com` to loopback while Docker compose already pins the provider host through `extra_hosts`.
  - Run `run_322660365068206080` was submitted through `/rag/v3/chat` with `chatMode=agent`, `taskTemplateId=github-visual-project-intro`, and `selectedSkillNames=image-generation`.
  - The run finished `SUCCEEDED`, loaded skills, called `github_repository_reader` and `image_generation`, persisted one clean `IMAGE` artifact (`artifactId=322660651702747136`, `image/png`, remote `imageUrl` storage reference), and wrote 105 ordered replay-buffer rows with `eventSeq` 1..105.
  - Live SSE included `AGENT_ARTIFACT` as both `stream_event` and named `agent.artifact` before `finish` and `done`.
  - Evidence files are under `output/e2e/current-branch-20260609-1631/`, especially `chat-sse-current-branch-19094-image-only-1.txt`, `replay-events-run_322660365068206080.json`, and `artifacts-run_322660365068206080.json`.

  Historical frontend E2E evidence from 2026-06-09 before `ead0e304`:

  - The then-current frontend ran on `http://127.0.0.1:5176` with the dev proxy targeting the then-current backend at `http://127.0.0.1:19094`.
  - Admin Inspector rendered run `run_322660365068206080` at `/admin/agent-inspector/run_322660365068206080`: Events showed `RUN_STARTED`, `SKILL_LOADED`, `TOOL_CALL_STARTED`, `AGENT_ARTIFACT`, `ARTIFACT_CREATED`, and `STEP_FINISHED`; Tools showed `github_repository_reader` and `image_generation`; Artifacts showed `Generated image`, `IMAGE`, and `image/png`.
  - Chat controls exposed the normal consumer-web entry point: the task selector contained `GitHub visual intro`, selecting it changed the quota hint to `HIGH / LONG`, and the skill picker contained and selected `image-generation`.
  - Evidence screenshots are under `output/e2e/current-branch-20260609-1631/`: `admin-inspector-artifacts-run_322660365068206080.png`, `chat-template-github-visual-high-long.png`, and `chat-skill-picker-image-generation-selected.png`.
  - Historical Chat session `/chat/58437786` recovered persisted Agent runs through the normal frontend route. The then-current branch requested `/api/agent-runs/{runId}/snapshot`, `/events?afterSeq=0`, and `/cost-summary` once per run after reload; the message summary showed steps, artifacts, and cost; opening `查看运行详情` exposed Workbench `Tool Calls`, `Skills`, `Artifacts`, and `Cost` tabs with `image_generation` and `image-generation` content. Focused test coverage is `npm test -- chatStore.test.ts` with 8 passing tests, including historical hydration, duplicate load dedupe, and stale session failure guards.
  - Evidence screenshot: `output/e2e/current-branch-20260609-1631/chat-workbench-historical-run-hydrated.png`.
  - Browser auth note: setting `seahorse_agent_user` through the Playwright local-storage helper mangled JSON quoting; use normal login or page evaluation with `JSON.stringify(user)` when reproducing this E2E path.

  This historical evidence proved the real backend tool/skill/artifact/SSE/replay path, admin replay, Chat control entry point, and persisted Chat Workbench recovery for the then-current branch state. Because the chat controller was changed afterwards, the remaining completion work is a fresh current-tip E2E run plus a full post-commit audit of the complete plan, not a known missing Workbench tab implementation.

Update this document only when implementation or tests prove a contract needs to change. Update the main plan in the same reviewed change when scope, acceptance, phase order, or compatibility boundaries change.
