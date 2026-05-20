# Reflection

Status: complete for Phase 1 through Phase 4 within the approved compatibility boundary, with final fresh verification recorded on 2026-05-20.

## Outcome

The reported behavior was reproduced as a compound design and implementation gap rather than a single memory toggle issue. The first deliverable now fixes the runtime chain that matters for the user scenario:

- authenticated Web requests resolve to the authenticated business `userId`;
- valuable user profile statements are normalized and captured;
- memory repositories are actually wired in the Docker backend;
- PostgreSQL JSON/JSONB writes work;
- RAG and generic fallback paths share one memory prompt formatter;
- a new conversation can answer from the previous conversation's student profile memory.

The resumed Phase 2 work moved write-time capture policy out of `DefaultMemoryEnginePort` and into dedicated, testable components:

- `MemoryCaptureCandidateExtractor` owns normalization, high-value signal detection, low-value tail trimming, question rejection, and sensitive credential rejection.
- `MemoryValueAssessor` owns value/risk scoring, policy versioning, accept/reject reasons, and importance/confidence levels.
- `DefaultMemoryEnginePort` now persists capture decision metadata so later governance can inspect why a memory was written.

The resumed Phase 3/4 work closes the existing governance persistence loop:

- `KernelMemoryGovernanceService` persists `memory-governance-v1` quality snapshots when `assessQuality=true`.
- governance detects high-precision same-type explicit `semanticKey` conflicts and writes `PENDING` `SEMANTIC_KEY_CONFLICT` records.
- existing management APIs can list quality snapshots, list pending conflicts, and resolve conflicts.
- JDBC adapters now save and read both quality snapshots and conflict logs using the existing database schema.

## Root Cause Summary

1. Chat/conversation/feedback Web adapters could silently fall back to `default`.
2. Memory repository auto-configuration could miss repository beans because `ObjectMapper` was required too early.
3. Once repositories were enabled, JSON/JSONB writes needed explicit SQL casts.
4. The first extraction rule missed Chinese whitespace and low-value social tails.
5. Memory prompt injection had duplicate ownership and one answer path could miss activated memory.

## Architecture Alignment

- Trigger: yes.
- Scope: identity source-of-truth, memory capture, repository wiring, prompt assembly, cross-session answer flow.
- Baseline checked: existing memory improvement docs, Aegis Phase E memory-loop plan, `KernelChatPipeline`, `DefaultMemoryEnginePort`, `LocalRagPromptAdapter`, Web controllers.
- Result: aligned.
- Evidence: Phase 1 keeps existing ports and schemas, introduces two focused owners, keeps `default` fallback only for unauthenticated/dev contexts, and verifies Docker runtime behavior.
- Evidence update: Phase 2 adds two focused capture-policy owners without changing public ports or database schema.
- Evidence update: Phase 3/4 uses existing snapshot/conflict tables and repositories to persist governance outputs without changing public `MemoryEnginePort` methods.
- Residual architecture risk: full knowledge-base candidate review queue and dashboard UI remain future work outside this slice.

## ADR Backfill Check

- Trigger: yes.
- Suggested action: skip formal ADR for this slice; Aegis spec, plan, checkpoint, and evidence now carry the implementation rationale.
- Evidence source: `docs/aegis/specs/2026-05-20-memory-filtering-architecture.md` and `docs/aegis/plans/2026-05-20-memory-filtering-implementation.md`.
- Baseline sync: not-needed for this slice.
- Skip reason: this slice restores and narrows the intended memory flow, extracts internal policy owners, and activates existing governance persistence tables without changing public memory engine APIs or adding new tables.
- Boundary: advisory method-pack signal only.

## Residual Risks

- Full test suite was not run; targeted regression, JDBC repository, starter package, and Docker runtime checks were used for this slice.
- Current extraction is deliberately high-precision and rule-based; it will still miss implicit but potentially useful facts until generalized candidate strategies are added.
- Memory management APIs for delete, conflict list, and conflict resolve are present and covered by web contract tests; a dedicated management UI was not built.
- Existing stored `default` messages are historical data; this fix prevents new authenticated traffic from falling into that bucket but does not migrate old rows.
- Capture decision evidence is persisted in memory metadata and governance metrics are persisted in quality snapshots; a separate candidate decision log table and metric dashboard UI were not added because new tables were out of scope.
- Full knowledge-base candidate review queue remains out of scope per `10-intent.md`.

## Next Phase

Future enhancement work should add:

- a dedicated knowledge-base candidate tagging and confirmation workflow;
- a dashboard UI over persisted quality snapshots and conflict logs;
- optional durable capture-decision audit storage if new tables become acceptable;
- broader LLM-assisted candidate extraction with review gates;
- historical migration tooling for old `default` user rows if the product needs backfill.
