# TaskIntentDraft

Requested outcome: continue the AI-Infra phased implementation without repeating already merged approval/checkpoint/resume/ContextPack work, and close the next concrete Phase 3 gap found in current `main`.

Scope:
- Add a minimal Agent run retry state/API slice.
- Preserve existing approval resume and lease behavior.
- Keep runtime worker scheduling out of scope.

Non-goals:
- No full DurableAgentOrchestrator.
- No background worker pickup loop.
- No tool execution during retry API call.
- No Phase 5 connector/credential/sandbox work in this slice.

BaselineReadSetHint:
- `docs/company-agent/ai-infra-phases/03-durable-runtime-hitl.md`
- `docs/company-agent/ai-infra-phases/99-current-implementation-handoff.md`
- `docs/aegis/work/2026-05-23-ai-infra-approval-management/99-reflection.md`
- `docs/aegis/work/2026-05-24-ai-infra-contextpack-producer/99-reflection.md`

ImpactStatementDraft:
- Crosses kernel domain, inbound port, kernel application service, Web adapter, and tests.
- Adds a new non-terminal `AgentRunStatus.RETRYING`.
- ArchitectureReviewRequired: yes, because this touches runtime state semantics and public API.

