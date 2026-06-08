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
3. Content-generation tools currently return JSON observations only; artifact persistence and artifact events still need a kernel-side publisher.
4. Encoding checks must assert correct Chinese text, not mojibake samples.

Snippets in this document are examples. Verify them against current repository types before implementation.

---

## Review Accuracy Notes

`2026-06-08-implementation-details-review.md` is technically useful, but its text is mojibake. The following points were rechecked against the current repository before changing this companion document:

- Accurate: `StreamEventEnvelope` has `eventId`, `eventSeq`, `eventType`, `runId`, optional `stepId`, `timestamp`, and `typedPayload`; it does not carry `messageId`.
- Accurate: `ToolInvocationRequest` already carries run, step, tenant, user, and allowed-tool context, but `LocalToolGatewayPort` currently calls `ToolPort.invoke(toolCallId, toolId, arguments)`, so individual tool adapters cannot read that context unless the execution contract is extended.
- Accurate: there is no existing `SkillSelectionContext`; current selected-skill validation lives in `ChatSelectedSkillResolver` and produces `SkillRuntimeBlock` values.
- Partly stale: the old suggestion to add `/api/agent-runs/{runId}/events` first conflicts with the main plan. Reuse `resumeRunId` and `lastEventSeq` first; add a separate event list endpoint only after that path is proven insufficient.

---

## Stream Merge Contract

Every live event or snapshot hydration path must follow the same rules:

- Merge arrays by stable id; never replace timeline, sources, artifacts, approvals, quota, memory, tool calls, or skills wholesale.
- Treat `eventSeq` and snapshot sequence values as monotonic recovery metadata.
- If incoming sequence is older than the message's latest applied sequence for that event family, keep the newer live message data.
- Missing optional payload fields leave existing message fields unchanged.
- Do not put `messageId` in `StreamEventEnvelope` fixtures. Route live events to the active streaming assistant message through chat store state, or pass `messageId` separately to snapshot hydration helpers.
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
- `ImageGenerationToolPortAdapter` currently drops the `style` argument despite advertising it in the schema.

Implementation constraints:

- Add artifact persistence through a kernel-side artifact publisher, not through web controllers or `SpringSseEventSender`.
- Preserve tool ids and catalog metadata.
- Preserve `model="default"` fallback.
- Forward image `style` or remove it from schema and docs in the same reviewed change.
- Emit artifact events with `artifactId`, `runId`, `title`, `mimeType`, `previewText`, and safe storage reference metadata.

Context boundary:

- Do not use undefined `ExecutionContext`, `ExecutionMetadata`, `AgentRunContext`, or `SkillSelectionContext` names in implementation or tests unless the same change creates and wires that contract.
- Preferred execution shape: preserve `ToolInvocationRequest` as the gateway-level context carrier, then either pass a typed invocation metadata object into tool adapters or publish artifacts from a gateway/kernel collaborator that still has the full request.
- If a scoped context holder is introduced instead, it must be set and cleared inside `LocalToolGatewayPort` with tests proving cleanup after success, failure, denial, and nested/parallel invocations.
- `AgentArtifact.messageId` is optional in the current domain model. Do not block artifact persistence solely because `messageId` is unavailable at tool-adapter level; use `runId` as the required recovery key and attach `messageId` only when a verified owner supplies it.

Verification focus:

```powershell
.\mvnw.cmd -pl seahorse-agent-kernel -am test -Dtest=ImageGenerationToolPortAdapterTests,*ContentGeneration*Tests,LocalToolGatewayPort*Tests
.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am test -Dtest=BuiltInAgentToolRegistrarTests
```

---

## Task 6 Progressive Skill Loading

Current baseline:

- `ChatSelectedSkillResolver` performs server-side validation for per-turn selected skill names.
- Validated selections become `SkillRuntimeBlock` values and may be downgraded to `METADATA_ONLY`.
- There is no existing `SkillSelectionContext`.

Implementation constraints:

- `load_skill_resource` must only load resources for skills that are already selected or version-bound in the current run context.
- Do not trust skill names from the frontend or from model tool arguments as authorization proof.
- Reuse selected `SkillRuntimeBlock` metadata or an explicit tool invocation metadata carrier; do not create a second independent skill-selection owner.
- Reject absolute paths, parent traversal, empty paths, and paths outside the selected skill revision resource set.
- Register the tool through `SeahorseAgentKernelAgentAutoConfiguration`; `BuiltInAgentToolRegistrar` should discover it as a `DescribedToolPort`.

Required tests:

- selected skill `SKILL.md` loads successfully.
- unselected skill is rejected.
- disabled, inactive, or missing-revision skill is rejected through existing resolver semantics.
- parent traversal and absolute paths are rejected.
- registrar/catalog visibility matches the feature flag.

---

## Task 7 And 8 Tool Policy/Search

Execution constraints:

- Skill policy can reduce the active agent tool set; it must not grant a tool the agent/version did not already allow.
- Deferred tool search returns metadata only and must filter results by the same effective allowed-tool set used for real invocation.
- Tool search must not bypass `ToolPolicyPort`, `ToolCatalogRepositoryPort`, or `BuiltInAgentToolRegistrar` metadata.
- Do not depend on a nonexistent `ExecutionContext`; either pass the selected skills and allowed tools through the same metadata contract introduced for Task 5/6, or keep the service boundary above `ToolPort` where `ToolInvocationRequest` is still available.

Required tests:

- restrictive skill policy intersects with agent allowed tools.
- advisory skill policy leaves the agent allowed tool set unchanged.
- tool search hides denied tools and returns only catalog metadata.
- enabling/disabling the feature flag changes registration and catalog visibility as expected.

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

Implementation constraints:

- Reuse the existing resume stream for chat backfill first.
- Add a separate event list endpoint only if admin replay cannot be covered by the resume stream or snapshot.
- Preserve `SpringSseEventSender` closed-emitter behavior.
- Add frontend tests proving backfill does not duplicate live events.

---

## Migration Boundary

- Do not edit already-applied `V20__github_visual_project_intro_agent.sql` or `V21__github_visual_agent_generation_tools.sql` in place.
- Update `resources/database/seahorse_init.sql` for fresh database snapshots only when seed verification proves it is stale.
- Use the next numbered forward migration for existing database seed repairs.

---

## Execution Checklist

- [ ] Task 1: `chatStreamHandlers.ts` + tests pass
- [ ] Task 2: Encoding guard runs clean
- [ ] Task 3: Snapshot hydration + tests pass
- [ ] Task 4: Artifact lifecycle + unsafe download blocked
- [ ] Task 5: All 5 generation tools persist artifacts
- [ ] Task 6: `load_skill_resource` registered and tested
- [ ] Task 7: Tool gateway policy tests pass
- [ ] Task 8: `tool_search` registered and tested
- [ ] Task 9: Tool calls tab renders
- [ ] Task 10: Skills tab renders
- [ ] Task 11: Event backfill works
- [ ] Task 12: Docs updated

Update this document only when implementation or tests prove a contract needs to change. Update the main plan in the same reviewed change when scope, acceptance, phase order, or compatibility boundaries change.
