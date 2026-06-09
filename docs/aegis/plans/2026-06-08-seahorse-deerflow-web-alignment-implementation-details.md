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

`2026-06-08-implementation-details-review.md` is technically useful, but it should be treated as a point-in-time review, not as the current implementation contract. The file itself is UTF-8; a legacy console or default PowerShell read may render the Chinese text as mojibake even though the file content is valid. If display output looks corrupted, verify with a UTF-8-aware editor or `rg` before copying any text into fixtures. The following points were rechecked against the current worktree on 2026-06-09 before changing this companion document. Items marked as current worktree fixes must still be backed by a code commit and the final E2E gate before the overall plan is considered complete:

- Accepted: `StreamEventEnvelope` has `eventId`, `eventSeq`, `eventType`, `runId`, optional `stepId`, `timestamp`, and `typedPayload`; it does not carry `messageId`. Test fixtures and merge helpers must route live events through the active streaming assistant message, not through an envelope `messageId`.
- Accepted with current implementation detail: `ToolInvocationRequest` already carries run, step, tenant, user, and allowed-tool context. `LocalToolGatewayPort` still calls `ToolPort.invoke(toolCallId, toolId, arguments)`, so individual tool adapters cannot read request context directly unless the runtime deliberately passes a safe, server-owned snapshot in arguments.
- Superseded: the review suggested adding an `ExecutionMetadata` argument to `ToolPort.invoke(...)` for Task 5/6. The current code instead preserves the existing `ToolPort` contract and uses gateway-level context owners: `ToolArtifactPublicationPort.publish(request, rawResult)` for artifact publication, plus server-injected hidden arguments for runtime-only tool context.
- Accepted: there is no existing `SkillSelectionContext`; current selected-skill validation lives in `ChatSelectedSkillResolver` and produces `SkillRuntimeBlock` values.
- Accurate with a narrower scope: `useStreamResponse.ts`, `SeahorseChatController`, and `ResearchSseBridge` already support `resumeRunId` and `lastEventSeq`; reuse that path before adding any separate event-list endpoint.
- Superseded by current code: the named chat/workbench files and `frontend/src/hooks/useStreamResponse.ts` are clean for the known mojibake code points in the scoped scan. Keep the guard, but do not claim a current `useStreamResponse.ts` mojibake defect unless a fresh scan finds one.
- Superseded by current code: the review's "artifact persistence trigger point" concern is resolved by the gateway-level outbound hook `ToolArtifactPublicationPort`, and Task 5 has added the concrete `GenerationToolArtifactPublicationPort` publisher for newsletter, PPT, chart, frontend-design, and image-generation outputs.
- Superseded by current worktree fix: the review predates the remote-image artifact fallback. `GenerationToolArtifactPublicationPort` persists image artifacts when the observation has either `b64Json` or `imageUrl`; `b64Json` is uploaded to object storage, while remote `imageUrl` is stored as the artifact reference.
- Superseded by current code: the review did not account for the Task 8 slice. Current implementation adds `tool_search` as a `DescribedToolPort`, registers it through Spring auto-configuration, injects `_seahorseAllowedToolIds` from `KernelAgentLoop`, returns metadata only, and labels `tool_search` as `延迟发现` in the admin tool catalog. Further Task 8 changes should be driven by new acceptance gaps, not by the original review's missing context.
- Superseded by current code: the review's Task 9-11 "only summary-level" concern no longer applies. Tool-call and skill workbench rendering now have concrete backend stream events, message-scoped frontend state, tabs, and tests. Task 11 now has focused acceptance coverage for SSE resume URLs, admin replay ordering/dedupe, and cost/quota resume/retry rendering. Keep real E2E as the final goal gate, but do not reopen Task 11 from this review alone.
- Accepted with updated baseline: a dedicated event-list endpoint now exists at `/api/agent-runs/{runId}/events?afterSeq=...` through `AgentRunEventBufferPort`. Admin replay should continue using it, while chat reconnect should still prefer the existing `resumeRunId` + `lastEventSeq` SSE path before adding another recovery owner.
- Superseded by current implementation slices: SSE replay buffering now depends on `LocalChatStreamCallbackFactory` resolving `AgentRunEventBufferPort` lazily per callback, and JDBC replay payloads are serialized through `ObjectMapper.writeValueAsString(...)` with a narrow read-side compatibility path for string-literal payloads. Do not evaluate Task 11 only from live SSE delivery; admin replay and database buffer rows are part of the current acceptance boundary.
- Accepted with current worktree fix: the JDBC replay buffer bean must be registered whenever a `DataSource` is available. It must not require an eager `ObjectMapper` bean in `@ConditionalOnBean`; the adapter can receive an `ObjectProvider<ObjectMapper>` and fall back to `new ObjectMapper()` if necessary.

Disposition matrix:

| Review item | Current decision |
| --- | --- |
| `StreamEventEnvelope.messageId` does not exist | Accepted; keep message routing outside the envelope. |
| Undefined `ExecutionContext` / `SkillSelectionContext` | Accepted as a documentation defect; fixed by using existing gateway/runtime context owners, not by inventing hidden globals. |
| Add `ExecutionMetadata` to `ToolPort.invoke(...)` | Rejected for current slices; preserve the stable `ToolPort` contract unless a future reviewed change updates every implementation together. |
| Artifact publication trigger was unclear | Superseded; `ToolArtifactPublicationPort.publish(request, rawResult)` is the implemented owner. |
| Image artifact persistence only covered `b64Json` | Superseded; image observations with remote `imageUrl` persist an `AgentArtifact` reference and emit an artifact event. |
| Task 9-11 lacked detail | Superseded at focused acceptance level; Tasks 9/10/11 now have concrete implementation and tests. |
| SSE live stream worked but admin replay was empty | Accepted as a real implementation risk; current baseline requires lazy buffer binding plus serialized JDBC payloads and must be proven by fresh replay evidence. |
| Event buffer auto-configuration could miss late `ObjectMapper` beans | Accepted; condition only on `DataSource`, inject `ObjectProvider<ObjectMapper>`, and keep the fallback mapper local to the adapter. |

Current code anchors:

| Concern | Current owner |
| --- | --- |
| Stream envelope shape | `frontend/src/types/index.ts` (`StreamEventEnvelope`) |
| Live stream reconnect | `frontend/src/hooks/useStreamResponse.ts`, `SeahorseChatController`, `ResearchSseBridge` |
| Message/workbench merge owner | `frontend/src/stores/chatStreamHandlers.ts`, `frontend/src/stores/chatStore.ts` |
| Tool invocation context | `ToolInvocationRequest` at the gateway boundary; `ToolPort.invoke(toolCallId, toolId, arguments)` remains unchanged |
| Artifact publication | `LocalToolGatewayPort` calls `ToolArtifactPublicationPort.publish(request, rawResult)` after successful raw execution |
| Generation artifact persistence | `GenerationToolArtifactPublicationPort` plus its focused tests |
| Progressive skill loading | `ChatSelectedSkillResolver`, `SkillRuntimeComposer`, `KernelAgentLoop`, `LoadSkillResourceToolPortAdapter` |
| Deferred tool search | `ToolSearchToolPortAdapter` with server-injected `_seahorseAllowedToolIds` |
| SSE event buffering | `LocalChatStreamCallbackFactory` resolves `AgentRunEventBufferPort` lazily; `JdbcAgentRunEventBufferAdapter` persists typed payload JSON |
| Replay buffer auto-configuration | `SeahorseAgentRegistryRepositoryAutoConfiguration` creates `JdbcAgentRunEventBufferAdapter` from `DataSource` plus optional `ObjectMapper` |
| Admin replay/event list | `AgentRunEventBufferPort`, `SeahorseAgentRunController`, `AgentInspectorPage` |

---

## Stream Merge Contract

Every live event or snapshot hydration path must follow the same rules:

- Merge arrays by stable id; never replace timeline, sources, artifacts, approvals, quota, memory, tool calls, or skills wholesale.
- Treat `eventSeq` and snapshot sequence values as monotonic recovery metadata.
- If incoming sequence is older than the message's latest applied sequence for that event family, keep the newer live message data.
- Missing optional payload fields leave existing message fields unchanged.
- Do not put `messageId` in `StreamEventEnvelope` fixtures. Route live events to the active streaming assistant message through chat store state, or pass `messageId` separately to snapshot hydration helpers.
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
- `ToolArtifactPublicationPort` exists in the kernel outbound ports. `LocalToolGatewayPort` calls it after successful tool execution and output redaction, passes the full `ToolInvocationRequest`, and swallows publication exceptions so the tool observation remains authoritative.
- `SeahorseAgentKernelAgentAutoConfiguration` wires an optional `ToolArtifactPublicationPort` into `LocalToolGatewayPort`; when absent, the gateway uses `ToolArtifactPublicationPort.noop()`.
- Implemented status: newsletter, PPT, chart, frontend-design, and image-generation outputs now have direct publisher tests proving persisted `AgentArtifact` rows and `agent.artifact` event payloads. `image_generation` defaults to `b64_json` so generated images can be stored internally; returned and audited observations redact `b64Json`.
- Implemented status: image-generation observations with remote `imageUrl` and empty `b64Json` are also persisted as image artifact references without object-storage upload. This is required for providers that return hosted image URLs despite the preferred `b64_json` request format.

Implementation constraints:

- Treat the concrete generation artifact publisher as implemented behavior. Future changes must preserve the existing gateway-level `ToolArtifactPublicationPort` hook and must not move artifact publication into web controllers, `SpringSseEventSender`, or individual generation tool adapters.
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

Implementation constraints:

- `load_skill_resource` must only load resources for skills that are already selected or version-bound in the current run context.
- Do not trust skill names from the frontend or from model tool arguments as authorization proof.
- Reuse selected `SkillRuntimeBlock` metadata or an explicit tool invocation metadata carrier; do not create a second independent skill-selection owner.
- Because `ToolPort.invoke(...)` currently receives only `toolCallId`, `toolId`, and `arguments`, `load_skill_resource` cannot infer selected skills inside the adapter unless the runtime passes selected-skill state before gateway dispatch.
- Implemented path: `KernelAgentLoop` injects a hidden runtime skill snapshot into `load_skill_resource` arguments from `AgentLoopRequest.skillRuntimeBlocks()`. The adapter validates `skillName` and `resourcePath` only against that injected snapshot, never against frontend/model-provided authorization claims.
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
- `LocalChatStreamCallbackFactory` resolves `AgentRunEventBufferPort` lazily when each callback is created, so a callback created after repository auto-configuration uses the real JDBC buffer instead of permanently capturing `noop`.
- `JdbcAgentRunEventBufferAdapter` serializes typed payloads as JSON and keeps a narrow compatibility path for driver/H2 string-literal payloads on read.
- `SeahorseAgentRegistryRepositoryAutoConfiguration` registers the JDBC `AgentRunEventBufferPort` when `DataSource` is present, even if the application `ObjectMapper` bean is not ready at condition-evaluation time. The adapter receives `ObjectProvider<ObjectMapper>` and falls back to a local mapper.
- `frontend/src/services/agentRunService.ts` already wraps `listAgentRunEvents(runId, afterSeq)`, and `AgentInspectorPage` loads the event list together with snapshot and cost summary data.
- `AgentInspectorPage` sorts replayed events by `eventSeq` and dedupes duplicate sequence numbers before rendering.
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
- Keep repository auto-configuration conditional on `DataSource` only for the event buffer; do not reintroduce a hard `ObjectMapper` bean condition that can silently fall back to `AgentRunEventBufferPort.noop()`.
- Persist replay payloads as JSON objects/arrays/values through `ObjectMapper`; do not hand-roll quoted payload strings.
- Keep frontend tests proving backfill and event-list replay do not duplicate live events and do not overwrite newer live state with older replay/snapshot data.
- Add backend or controller tests only for gaps not already covered by `SeahorseChatControllerReplayTests`, `SpringSseEventSenderTests`, and the event-buffer contract.
- Verify cost/quota governance remains display-only in chat workbench, while resume/retry actions continue to call the Agent Run action APIs.

Focused acceptance evidence:

- reconnect with `resumeRunId` and `lastEventSeq` appends only missing stream events to the active message.
- replay events from `listAgentRunEvents(runId, afterSeq)` render in admin event order without duplicating earlier events.
- a fresh current-worktree live Agent run writes non-zero rows to `sa_agent_run_event_buffer`, and `/api/agent-runs/{runId}/events?afterSeq=0` returns ordered, parseable events for the same run.
- snapshot hydration after replay preserves newer live timeline, source, artifact, tool-call, skill, approval, and quota fields.
- cost/quota tab renders totals, quota pressure, resume, and retry states from message state without creating a second store owner.

---

## Migration Boundary

- Do not edit already-applied `V20__github_visual_project_intro_agent.sql` or `V21__github_visual_agent_generation_tools.sql` in place.
- Update `resources/database/seahorse_init.sql` for fresh database snapshots only when seed verification proves it is stale.
- Use the next numbered forward migration for existing database seed repairs.

---

## Execution Checklist

- [x] Task 1: `chatStreamHandlers.ts` + tests pass
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
- [ ] Final current-worktree E2E gate: backend and frontend both started from this branch; selected skill; at least one tool call; at least one generated artifact; Workbench Tool Calls, Skills, Artifacts, and Cost/Quota render; `sa_agent_run_event_buffer` has rows for the fresh run; `/api/agent-runs/{runId}/events?afterSeq=0` returns ordered/dedupable events; SSE resume or admin replay proves event ordering and dedupe. Historical runs or Docker services built from older commits do not satisfy this gate.

Update this document only when implementation or tests prove a contract needs to change. Update the main plan in the same reviewed change when scope, acceptance, phase order, or compatibility boundaries change.
