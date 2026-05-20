# Reflection

Status: complete for Phase 1 and the first Phase 2 capture-policy slice.

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
- Residual architecture risk: Phase 2 still needs durable decision logs/metrics, conflict governance, and knowledge-base candidate tagging.

## ADR Backfill Check

- Trigger: yes.
- Suggested action: skip formal ADR for this slice; Aegis spec, plan, checkpoint, and evidence now carry the implementation rationale.
- Evidence source: `docs/aegis/specs/2026-05-20-memory-filtering-architecture.md` and `docs/aegis/plans/2026-05-20-memory-filtering-implementation.md`.
- Baseline sync: not-needed for Phase 1.
- Skip reason: this slice restores and narrows the intended memory flow and then extracts internal policy owners without changing public ports or database schema.
- Boundary: advisory method-pack signal only.

## Residual Risks

- Full test suite was not run.
- Current extraction is deliberately high-precision and rule-based; it will still miss implicit but potentially useful facts until generalized candidate strategies are added.
- Memory management UI/API for user-visible correction, deletion, and conflict handling remains planned work.
- Existing stored `default` messages are historical data; this fix prevents new authenticated traffic from falling into that bucket but does not migrate old rows.
- Phase 2 scoring exists for write-time chat capture, but metric counters, durable decision logs, conflict queues, and knowledge-base candidate governance are not implemented yet.

## Next Phase

Phase 2 continuation should add:

- durable capture decision logs and rejection samples;
- metric counters for candidate, accept, reject, and sensitive rejection rates;
- conflict handling for competing profile/fact memories;
- knowledge-base candidate tagging and confirmation workflow;
- tests for conflict handling, metric emission, and knowledge-base candidate governance.
