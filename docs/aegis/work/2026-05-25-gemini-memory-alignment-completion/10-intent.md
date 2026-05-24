# Task Intent: Gemini memory alignment completion

## Requested Outcome

Finish the active Gemini memory alignment goal by proving the current Seahorse Agent implementation matches the target memory design documents, preserving Clean Architecture and the four-layer memory model.

## Scope

- Analyze the three Gemini memory docs against current code.
- Keep `MemoryLayer` as `WORKING / SHORT_TERM / LONG_TERM / SEMANTIC`.
- Preserve `MemoryEnginePort` compatibility.
- Respect the handoff recommendation to continue in small slices from the isolated worktree.
- Add current-state design and implementation-plan records.
- Run targeted and full regression tests.

## Non-Goals

- Do not add new memory layers.
- Do not introduce heavyweight middleware.
- Do not wire LLM mutation paths that bypass review.
- Do not stage unrelated root-workspace untracked files.
- Do not implement optional future adapters unless verification proves they are required for the current objective.

## Baseline Read Set

- `docs/Gemini Agent记忆系统完整设计方案.md`
- `docs/Seahorse Agent记忆系统Gemini架构深度分析与实施方案.md`
- `docs/gemini-design.md`
- `docs/aegis/specs/2026-05-24-design-alignment-next-development.md`
- `docs/aegis/work/2026-05-23-memory-gemini-handoff/HANDOFF.md`
- `docs/aegis/work/2026-05-24-default-memory-engine-decomposition/HANDOFF.md`
- Current code under `seahorse-agent-kernel`, `seahorse-agent-spring-boot-starter`, memory adapters, and `seahorse-agent-tests`.

## Impact Statement

This slice is mostly verification and documentation because current `main` already contains the implementation commits for Debounce, Refiner, Hybrid Recall, Review, Compaction, Alias, GC, outbox, and observability. If verification exposes a real gap, the implementation must be fixed with TDD in the isolated worktree.
