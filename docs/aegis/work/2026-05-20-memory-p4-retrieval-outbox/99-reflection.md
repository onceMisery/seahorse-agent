# Memory P4 retrieval outbox - Reflection

P4 completed the first retrieval/outbox loop without replacing the existing memory stores. The implementation keeps Profile KV and Correction Ledger as higher-priority deterministic sources while adding derived vector/business candidates as optional tracks selected by `MemoryRouterPort`.

What changed:

- Accepted ADD memory writes now attempt `MemoryVectorPort.upsert`.
- Vector indexing failure is converted into a durable `VECTOR_UPSERT` outbox task and does not roll back the short-term memory write or Profile KV update.
- Episodic routes can call `MemoryVectorPort.search`, resolve returned memory IDs through short-term, long-term, and semantic stores, and merge hits into the existing context.
- Active Profile slots continue to suppress stale same-slot memory candidates, including vector-resolved hits.
- Business document routes can append candidates from the existing RAG retrieval engine into the semantic/business zone.
- JDBC schema and Spring auto-configuration now expose `MemoryOutboxPort` and default no-op/bridge behavior for optional ports.

Architecture alignment:

- Result: aligned with P4 minimal loop.
- Compatibility: retained existing `DefaultMemoryEnginePort` constructors and existing store ports.
- Source of truth: durable facts remain in Profile KV, Correction Ledger, and existing memory stores; vector/business results are retrieval candidates, not canonical facts.
- Retirement: no existing path retired in this slice.

ADR backfill signal:

- Suggested action: skip for now.
- Reason: this slice implements the already documented P4 plan and does not supersede the existing memory-system plan. P5/P6 may need ADR backfill when lifecycle state machines, GC, or observability policy become durable architecture.

Method Pack output does not grant completion authority.
