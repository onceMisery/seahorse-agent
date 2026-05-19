# Reflection

Status: complete for Phase 1.

## Outcome

The reported behavior was reproduced as a compound design and implementation gap rather than a single memory toggle issue. The first deliverable now fixes the runtime chain that matters for the user scenario:

- authenticated Web requests resolve to the authenticated business `userId`;
- valuable user profile statements are normalized and captured;
- memory repositories are actually wired in the Docker backend;
- PostgreSQL JSON/JSONB writes work;
- RAG and generic fallback paths share one memory prompt formatter;
- a new conversation can answer from the previous conversation's student profile memory.

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
- Residual architecture risk: Phase 2 still needs a proper `MemoryCandidateExtractor`, `MemoryValueAssessor`, policy logs, and conflict governance to avoid rule growth inside `DefaultMemoryEnginePort`.

## ADR Backfill Check

- Trigger: yes.
- Suggested action: skip formal ADR for this slice; Aegis spec and plan now carry the implementation rationale.
- Evidence source: `docs/aegis/specs/2026-05-20-memory-filtering-architecture.md` and `docs/aegis/plans/2026-05-20-memory-filtering-implementation.md`.
- Baseline sync: not-needed for Phase 1.
- Skip reason: this slice restores and narrows the intended memory flow without changing public ports or database schema.
- Boundary: advisory method-pack signal only.

## Residual Risks

- Full test suite was not run.
- Current extraction is deliberately high-precision and rule-based; it will miss implicit but potentially useful facts until Phase 2 scoring exists.
- Memory management UI/API for user-visible correction, deletion, and conflict handling remains planned work.
- Existing stored `default` messages are historical data; this fix prevents new authenticated traffic from falling into that bucket but does not migrate old rows.

## Next Phase

Phase 2 should extract memory capture policy into dedicated components:

- `MemoryCandidateExtractor`
- `MemoryValueAssessor`
- `MemoryCapturePolicy`
- decision logs and rejection samples
- unit tests for score thresholds, risk rejection, conflict handling, and knowledge-base candidate tagging
