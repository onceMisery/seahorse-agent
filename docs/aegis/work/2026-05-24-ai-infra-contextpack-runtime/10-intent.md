# TaskIntentDraft

Requested outcome: continue AI-Infra implementation after Approval API and ContextPack foundation were already present, without duplicating completed Phase 3 work.

Active slice: make runtime prompt assembly consume a supplied `ContextPack` before falling back to legacy `MemoryContext`.

Scope:

- Extend the context weaving port in a backwards-compatible way.
- Carry `ContextPack` through Agent loop and RAG prompt context objects.
- Format authorized ContextPack items with source, sensitivity, ACL decision, and citation provenance.
- Preserve current memory prompt behavior when no usable ContextPack is supplied.

Non-goals:

- Do not rebuild the RAG retrieval, memory recall, or tool-result pipelines in this slice.
- Do not add new ACL mutation APIs, workflow engine behavior, or remote Agent mesh support.
- Do not remove `MemoryContext`; it remains the compatibility fallback.

BaselineReadSetHint:

- `docs/company-agent/ai-infra-phases/00-architecture-baseline.md`
- `docs/company-agent/ai-infra-phases/04-context-db-resource-acl.md`
- `docs/aegis/plans/2026-05-24-ai-infra-contextpack-foundation.md`
- `docs/aegis/plans/2026-05-24-ai-infra-contextpack-runtime.md`

ImpactStatementDraft: updates kernel ports/domain/application prompt composition and the local RAG web adapter while preserving dependency direction. Kernel still depends on port abstractions and domain objects, not Spring, JDBC, or Web implementation details.
