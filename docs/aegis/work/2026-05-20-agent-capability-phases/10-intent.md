# Task Intent: Agent Capability Phases Runtime Slice

## Requested Outcome

Implement the plans under `docs/agent-capability-phases/`, redeploy the local Docker backend, and run runtime checks.

## Scope

In scope for this execution slice:

- Preserve existing `chatMode=rag` behavior.
- Use existing `KernelAgentLoop`, `ToolPort`, and `ToolRegistryPort` as the Agent tool-loop owner.
- Add runnable Agent tools for knowledge search, metadata query, and memory read/write/forget.
- Add service-side governance primitives needed by those tools: bounded arguments, scoped user memory operations, structured observations, and tool action audit hooks where feasible.
- Add focused tests for tool contracts and Spring registration.
- Rebuild/restart the Docker backend and run an agent-mode smoke test.

Compatibility boundary:

- Do not replace the existing RAG pipeline.
- Do not add a second retrieval owner.
- Do not let LLM-supplied arguments decide user or tenant authority.
- Do not silently migrate historical data.
- Keep current dirty user files and unrelated untracked files untouched.

## Non-Goals For This Slice

- Full visual Agent workspace UI.
- Complete persisted multi-phase task runtime with all snapshot/action tables.
- Production-grade enterprise dashboard.
- Binding a real graph database for knowledge graph queries.
- Broad full-suite test repair outside the changed Agent capability path.

## BaselineReadSetHint

- `docs/agent-capability-phased-implementation-plan.md`
- `docs/agent-capability-phases/*.md`
- `KernelAgentLoop`
- `ToolPort`
- `ToolRegistryPort`
- `KernelRetrievalEngine`
- `MemoryEnginePort`
- `MemoryManagementInboundPort`
- `SeahorseAgentKernelAgentAutoConfiguration`

## ImpactStatementDraft

ArchitectureReviewRequired: yes.

Shared behavior affected:

- Agent tool registry and model-exposed tool set.
- Retrieval as an Agent tool.
- Memory read/write/delete as Agent tools.
- Service-side scope and audit boundary for tool execution.
