# TaskIntentDraft

Requested outcome:
- Continue AI-Infra implementation without duplicating completed approval, checkpoint, ContextPack, or retry slices.

Current slice:
- Published Agent version model configuration should affect Agent runtime model calls.

Success evidence:
- A failing test proves runtime model calls ignore version model config before implementation.
- Tests pass after adding minimal version model routing.
- Kernel still depends only on ports/domain abstractions.

Stop condition:
- Done: version chat model config flows into `ChatRequest` and OpenAI-compatible streaming payloads.
- Needs-verification: implementation exists but focused regression has not passed.
- Scope-exceeded: work requires new orchestrator, remote mesh, full Agent Studio, or persistence migration.
- Blocked: current code has no stable way to identify a registered Agent/version at execution entry.

Non-goals:
- No workflow engine.
- No remote Agent mesh.
- No complex JSON database type.
- No full Agent Factory UI.

BaselineReadSetHint:
- Phase 1 Agent Registry/run store.
- Phase 2 model/tool policy routing expectations.
- Phase 3 durable runtime boundaries.
- Current `KernelChatInboundService`, `KernelAgentLoop`, `ChatRequest`, and OpenAI-compatible adapter.

ImpactStatementDraft:
- Kernel domain/chat request contract changes.
- Kernel chat application request assembly changes.
- AI adapter consumes the existing request contract.
- Web API can remain compatible unless registered agent selection is added later.
