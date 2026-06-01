---
name: seahorse-memory-system-audit
description: Use when auditing Seahorse Agent memory-system implementation, Gemini memory alignment, four-layer memory invariants, review/approval concurrency, recall quality, maintenance/outbox behavior, JDBC/cache adapters, or memory privacy and governance APIs.
---

# Seahorse Memory System Audit

## Overview

Audit Seahorse memory work against the project memory design, without collapsing the four canonical memory layers or hard-coding Gemini behavior. Focus on architecture boundaries, review safety, recall quality, persistence correctness, and focused test evidence.

## Start Set

Use the user-specified branch/worktree first. Then locate only the relevant docs:

```powershell
rg --files docs | rg "gemini-design|Gemini|memory|记忆|handoff|memory-gemini|default-memory"
```

Common anchors:

- `docs/gemini-design.md`
- `docs/aegis/work/2026-05-23-memory-gemini-handoff/HANDOFF.md`
- `docs/aegis/plans/*memory*.md`
- `docs/aegis/specs/*memory*.md`
- Chinese Gemini memory design and gap documents under `docs/`.

## Scope Decision

Name the slice before auditing. Typical slices are:

- Ingestion/refiner/write pipeline.
- REVIEW candidate query, decision, feedback, and concurrency.
- Recall pipeline, keyword/vector/graph channels, reranking, and golden cases.
- Maintenance, lifecycle, GC, alias merge, outbox, and trace recording.
- Privacy/governance APIs and user memory center.
- JDBC/cache adapter persistence and Spring auto-configuration.

## Invariants

Keep these non-negotiable unless the user explicitly changes the design:

- Four memory layers remain canonical: `WORKING`, `SHORT_TERM`, `LONG_TERM`, `SEMANTIC`.
- Kernel depends on domain objects and ports, not Spring/JDBC/Web.
- Gemini-like behavior is pluggable through ports; do not make Gemini a hard dependency.
- Status/type/reason/source/risk values use enums or named constants.
- Domain objects maintain invariants; application services orchestrate; repository adapters persist/query.
- REVIEW decisions must be stale-safe and double-review-safe. Avoid side effects before the candidate state is claimed or otherwise made idempotent.
- Recall fallbacks must be explicit; missing vector/graph/keyword implementations are product gaps, not silent success.
- Privacy and tenant/user scoping must be enforced at query and write boundaries.

## Workflow

1. Map expected design to actual code:

```powershell
rg -n "MemoryEngine|MemoryReview|MemoryRecall|MemoryVector|MemoryKeyword|MemoryGraph|MemoryOutbox|MemoryAlias|MemoryMaintenance|MemoryLifecycle|MemoryAggregation|MemoryPrivacy|ConversationMemory" seahorse-agent-*
```

2. Check ports and adapters:
   - Kernel inbound/outbound ports under `seahorse-agent-kernel`.
   - JDBC adapters and SQL files under `seahorse-agent-adapter-repository-jdbc`.
   - Redis/cache adapters for aggregation buffers and schedulers.
   - Web controllers under `seahorse-agent-adapter-web`.
   - Spring Boot starter auto-configuration that wires ports into runtime.

3. Audit the slice-specific risks:
   - REVIEW: CAS/optimistic update, stale status checks, side-effect ordering, exception semantics, feedback samples, page/filter behavior.
   - Recall: channel composition, scoring, dedupe, layer constraints, real adapter availability, fallback transparency, golden-case coverage.
   - Maintenance: outbox idempotency, alias merge ordering, GC source-of-truth rules, retry and trace evidence.
   - Persistence: schema constraints, dialect differences, timestamps, tenant/user scope, transaction boundaries.
   - Privacy/governance: opt-out behavior, per-user settings, redaction, API exposure.

4. Verify narrowly:
   - Prefer focused Maven tests named after the touched class or slice.
   - If no focused test exists, identify the smallest test gap instead of running broad suites.
   - Report exact command and result when a command is run.

## Output

Return this shape:

```markdown
Findings
- P1/P2/P3 [file:line]: violated invariant, concrete failure mode, and minimal fix.

Confirmed Evidence
- Implemented contracts and tests that satisfy the slice.

Top Next Slices
- Ordered missing work with why each item matters.

Residual Risk / Test Gaps
- Unverified behavior, missing adapters, dialect risk, or concurrency gaps.

Verification
- Commands run, or "not run" with reason.
```

## Guardrails

- Do not rewrite the memory architecture during an audit.
- Do not mark "No findings" until checking side-effect ordering and runtime wiring, not only domain code.
- Do not treat in-memory/noop adapters as production evidence unless the task explicitly scopes to tests or local demo mode.
- Do not broaden a focused review into every memory subsystem; record adjacent gaps as next slices.
