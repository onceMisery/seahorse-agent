# Reflection

Completion candidate reached for the version model routing slice.

Architecture alignment:
- Version model config parsing belongs to kernel application code.
- Runtime model selection is carried through `ChatRequest`.
- Provider-specific payload building remains in adapters.
- Kernel still depends on domain objects and ports, not Spring, JDBC, or Web.
- Version lookup is an explicit repository port contract, preserving replaceable adapter semantics.

Review outcome:
- Advisory review found the default `findVersion` fallback could reject older immutable versions for non-JDBC repositories.
- The fallback was retired and replaced with an explicit `findVersion(agentId, versionId)` port method.

Residual risk:
- Invalid published `modelConfigJson` currently logs and falls back to default model settings. That preserves runtime compatibility but should be closed later with publish-time validation.
- Agent run resume still uses checkpoint payload data and does not replay version model config in this slice.

ADR Backfill Check:
- Trigger: yes, because the repository port contract and runtime model-routing contract changed.
- Suggested action: skip for now.
- Evidence source: this work record plus focused regression evidence.
- Baseline sync: not-needed for current docs; the existing Phase 1/2 intent already requires version-scoped runtime replay.
- Boundary: advisory method-pack signal only.
