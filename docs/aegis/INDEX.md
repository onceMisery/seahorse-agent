# Aegis Workspace Index

This index tracks files created under this project's `docs/aegis/` workspace.
Entries are workspace records, not authoritative runtime decisions.

Note: the 2026-06 workspace records (memory-rag-profile E2E, architecture
roadmap, interactive-memory-conflict, agentscope-core-boundary) were removed
from the workspace in earlier cleanups; only the records below exist. The
authoritative runtime facts are `complexity-baseline.txt` and
`docs/architecture/port-inventory.md` — the numbers in the 2026-07-27 spec's
evidence section and ADR-002's 377 baseline are historical snapshots.

| Date | Kind | Path | Title |
| --- | --- | --- | --- |
| 2026-06-27 | baseline | docs/aegis/baseline/2026-06-27-initial-baseline.md | Initial baseline snapshot |
| 2026-07-27 | spec | docs/aegis/specs/2026-07-27-core-runtime-stability-complexity-design.md | Core Agent/RAG runtime stability and complexity reduction design |
| 2026-07-28 | work | docs/aegis/work/2026-07-28-core-runtime-stability-implementation/10-intent.md | Core runtime stability implementation intent |
| 2026-07-28 | work | docs/aegis/work/2026-07-28-core-runtime-stability-implementation/20-checkpoint.md | Core runtime stability implementation checkpoint |
| 2026-07-28 | work | docs/aegis/work/2026-07-28-core-runtime-stability-implementation/90-evidence.md | Core runtime stability implementation evidence |
| 2026-07-30 | plan | docs/aegis/plans/2026-07-28-core-runtime-stability-complexity.md | Core runtime stability and complexity implementation plan |
| 2026-08-03 | adr | docs/aegis/adr/ADR-001-core-runtime-production-boundary.md | Core Agent/RAG runtime production boundary and one-way quarantine dependency |
| 2026-08-03 | adr | docs/aegis/adr/ADR-002-port-classification-and-budget.md | Port classification, consolidation rules, and public Port budget |
| 2026-08-03 | adr | docs/aegis/adr/ADR-003-durable-operation-state-and-unknown.md | Durable operation state and UNKNOWN side-effect semantics |
| 2026-08-03 | adr | docs/aegis/adr/ADR-004-production-evidence-gates.md | Production evidence gates and completion authority |
