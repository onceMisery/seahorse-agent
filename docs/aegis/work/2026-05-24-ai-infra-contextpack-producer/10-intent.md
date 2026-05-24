# TaskIntentDraft

Requested outcome: continue AI-Infra implementation from the current `main` state, avoid duplicate Phase 3/ContextPack foundation work, and fill the Phase 4 production ContextPack assembly gap.

Scope:
- Build `ContextPack` on RAG chat paths from user input, memory, and retrieval chunks.
- Build `ContextPack` on Agent mode paths before invoking `KernelAgentLoop`.
- Keep fallback behavior when the builder is absent or fails.
- Update Spring wiring to inject the optional builder.

Non-goals:
- No new workflow engine, remote Agent mesh, or broad Phase 5-8 implementation.
- No rewrite of retrieval or memory recall internals.
- No changes to default deny ACL semantics.

BaselineReadSetHint:
- `docs/company-agent/ai-infra-phases/04-context-db-resource-acl.md`
- `docs/aegis/plans/2026-05-24-ai-infra-contextpack-foundation.md`
- `docs/aegis/plans/2026-05-24-ai-infra-contextpack-runtime.md`

ImpactStatementDraft:
- Cross-module kernel chat/runtime wiring and Spring auto-configuration.
- Contract surface: `ContextPackBuilderInboundPort` consumption only; no port invariant changes planned.
