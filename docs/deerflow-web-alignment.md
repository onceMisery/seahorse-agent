# Seahorse / deer-flow Web Alignment

最后更新：2026-06-09

本文把本地 deer-flow 调研提升为仓库内可维护的对齐文档。它不要求 Seahorse 复制 deer-flow 的 LangGraph/FastAPI/Python 文件系统运行时；Seahorse 的目标是在 Java/Spring + React 架构内对齐 Web 端体验，并在治理、审计、replay 和 artifact 安全上赶超。

## Upstream Reference

- Upstream remote: `https://github.com/bytedance/deer-flow.git`
- Compared commit: `1651d1f1f57b8c43148ab8433bef0d4caf0d13e9`
- Local investigation root: `D:/code/deer-flow`

Compared paths:

| Area | deer-flow path |
| --- | --- |
| Lead agent prompt and progressive skill guidance | `backend/packages/harness/deerflow/agents/lead_agent/prompt.py` |
| Skill allowed-tools filtering | `backend/packages/harness/deerflow/skills/tool_policy.py` |
| Deferred `tool_search` | `backend/packages/harness/deerflow/tools/builtins/tool_search.py`, `backend/packages/harness/deerflow/config/tool_search_config.py` |
| `present_files` artifact publication | `backend/packages/harness/deerflow/tools/builtins/present_file_tool.py`, `backend/packages/harness/deerflow/agents/thread_state.py` |
| Frontend artifact panel | `frontend/src/components/workspace/chats/chat-box.tsx`, `frontend/src/components/workspace/artifacts/artifact-file-list.tsx`, `artifact-file-detail.tsx` |

## Alignment Matrix

| Capability | deer-flow signal | Seahorse implementation | Status |
| --- | --- | --- | --- |
| Progressive skill loading | Prompt lists available skills and instructs the model to load resources incrementally | `ChatSelectedSkillResolver`, `SkillRuntimeComposer`, `load_skill_resource`, hidden `_seahorseSkillRuntimeBlocks` | Aligned; Seahorse adds runtime diagnostics |
| Skill tool policy | `allowed_tools` union filters available tools when declarations exist | Skill policy intersects with Agent/version allowlist; skills never grant unbound tools | Aligned and safer |
| Deferred tool discovery | `tool_search` searches deferred MCP tools and returns schemas/promotions | `tool_search` returns metadata only from server-injected effective allowlist | Aligned on discoverability; Seahorse is more conservative |
| File/artifact presentation | `present_files` exposes `/mnt/user-data/outputs` paths to frontend artifacts | Generation tools persist `AgentArtifact` rows and emit `agent.artifact` events | Aligned through persisted artifacts rather than sandbox path state |
| Frontend artifact rendering | Artifact sidebar lists thread artifacts, previews/downloads files, installs `.skill` | Workbench Artifact tab merges streamed and persisted artifacts, blocks unsafe downloads, supports save/download; Skills tab renders skill runtime state | Aligned; skill-install-from-artifact is future optional |
| Tool-call rendering | Tool activity is visible in message/workspace UI | Tool Calls tab renders status, risk, argument preview, result summary, approval/error state | Aligned and governance-aware |
| Replay/backfill | LangGraph stream/thread state keeps workspace useful | SSE resume, `AgentRunEventBufferPort`, admin event list, snapshot hydration | Seahorse surpasses with explicit replay/governance views |

## Key Differences

### Runtime owner

deer-flow uses LangGraph state and thread filesystem paths as the main runtime carrier. Seahorse uses Spring/Kernel ports and persisted domain objects:

- `ToolInvocationRequest` carries run/step/tenant/user/allowed-tool context at gateway level.
- `AgentArtifact` is the durable artifact authority.
- `StreamEventEnvelope` carries sequenced events; frontend message state merges them idempotently.
- `AgentRunSnapshot` restores interrupted/historical runs.

### Artifact contract

deer-flow's `present_files` requires final deliverables under `/mnt/user-data/outputs` and updates graph state with normalized virtual paths. Seahorse maps the same product contract to persistent artifacts:

1. generation tool returns a JSON observation to the model.
2. `LocalToolGatewayPort` invokes `ToolArtifactPublicationPort.publish(request, rawResult)` after successful execution.
3. `GenerationToolArtifactPublicationPort` stores bytes through `ObjectStoragePort`, saves `AgentArtifact`, and appends an `agent.artifact` event.
4. Workbench and Agent Inspector render persisted metadata and preview/download state.

This keeps artifact visibility available after browser refresh, reconnect, or admin replay.

### Tool discovery

deer-flow can return full deferred tool schemas through `tool_search` and promote matched tools in graph state. Seahorse intentionally returns only metadata:

- `toolId`
- `name`
- `description`

The model-visible search result is filtered by `_seahorseAllowedToolIds`, which is injected by the server after Agent binding and skill policy calculation. Missing hidden snapshot fails closed.

### Skills

Both projects carry the same public skill family. Seahorse currently bundles 21 public skills under:

`seahorse-agent-spring-boot-autoconfigure/src/main/resources/skills/public/`

Generated-media related built-ins include:

- `image-generation`
- `video-generation`
- `ppt-generation`
- `chart-visualization`
- `newsletter-generation`
- `podcast-generation`
- `frontend-design`

Runtime tool ids for generated artifacts are underscore-form Java tool ids:

- `image_generation`
- `newsletter_generation`
- `ppt_generation`
- `chart_visualization`
- `frontend_design`

## Seahorse Surpass Layer

Seahorse should be considered aligned with deer-flow for the focused Web surfaces once real E2E passes. It already adds enterprise-oriented surfaces not present in the deer-flow comparison baseline:

- Tool Gateway audit records and policy decisions.
- Approval-required tool state and resume/retry controls.
- Artifact scan status, disposition, `canPreview`, and blocked unsafe downloads.
- Agent Inspector replay ordered by `eventSeq`.
- Cost/quota tab with resume/retry actions.
- Spring auto-configuration and optional `ToolArtifactPublicationPort` / `ToolSearchToolPortAdapter` wiring for pluggability.

## Remaining Evidence Gate

The codebase has focused unit/integration coverage, but the long-running goal requires a real E2E test before final completion:

1. Start backend and frontend in the worktree.
2. Run a chat with selected skill(s).
3. Trigger at least one tool call and one generation artifact.
4. Confirm Workbench tabs render Tool Calls, Skills, Artifacts, and Cost/Quota.
5. Interrupt/reconnect or use admin replay to confirm `lastEventSeq` and event ordering behavior.

Do not mark the overall deer-flow alignment goal complete until that E2E evidence exists.
