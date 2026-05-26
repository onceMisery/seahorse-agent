# AI Infra Phase 3 Worker Hardening - Intent

## TaskIntentDraft

- Requested outcome: Implement explicit worker tick for durable runtime using existing run queue, lease, checkpoint, and resume ports.
- Goal: Implement explicit worker tick for durable runtime using existing run queue, lease, checkpoint, and resume ports.
- Success evidence:
- Kernel, JDBC, starter focused tests pass; worker tick does not execute terminal or waiting approval runs; lease conflicts prevent duplicate resume; approved checkpoint delegates to resume owner.
- Stop condition: Done when focused Phase 3 acceptance passes; blocked if existing run/lease/resume contracts cannot support worker tick without changing invariants; needs-verification if tests are incomplete.
- Non-goals:
- No automatic background thread, no MQ workflow, no Temporal/LangGraph, no remote mesh.
- Scope: Phase 3 durable runtime worker hardening only: kernel worker port/service, queue repository port/JDBC adapter, starter wiring; no background thread or workflow engine.
- Change kinds:
- feature
- Risk hints:
- Cross-module runtime contracts; must preserve AgentRun status invariants and existing retry/cancel idempotency.

## BaselineReadSetHint

- docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md#16.2

## ImpactStatementDraft

- Compatibility boundary: Existing approval, checkpoint, resume, lease, retry, and cancel semantics remain compatible.
- Affected layers:
- kernel
- jdbc
- starter
- Owners:
- Kernel runtime worker service
- Invariants:
- Worker must not directly mutate ApprovalRequest and must not call ToolPort; resume execution stays owned by AgentRunResumeInboundPort.
- Non-goals:
- No automatic background thread, no MQ workflow, no Temporal/LangGraph, no remote mesh.

These records are Method Pack drafts / hints, not authoritative runtime decisions.
