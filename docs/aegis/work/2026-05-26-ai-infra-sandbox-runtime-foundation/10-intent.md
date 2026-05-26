# AI Infra Sandbox Runtime Foundation - Intent

## TaskIntentDraft

- Requested outcome: Implement Phase 5C kernel-only sandbox runtime foundation with default-deny policy and unsupported runtime contract
- Goal: Continue AI Infra implementation according to design docs by adding the Phase 5C sandbox runtime foundation
- Success evidence:
- RED and GREEN kernel tests cover default deny policy, unsupported runtime fail-closed behavior, enum decision reasons, execution status transitions, and artifact prompt visibility rules
- Stop condition: done when focused kernel tests and diff check pass; blocked only if existing code conflicts make kernel port boundaries impossible; needs-verification if tests cannot run; scope-exceeded if Web/JDBC/local execution is required
- Non-goals:
- Local shell, browser automation, code interpreter implementation, persistence, Web API, and Audit Ledger integration
- Scope: Kernel domain, inbound/outbound ports, application service, tests, and Aegis work records only
- Change kinds:
- feature
- Risk hints:
- Architecture and security contract change: sandbox must fail closed and kernel must not depend on Spring, JDBC, Web, or local execution runtimes

## BaselineReadSetHint

- docs/company-agent/ai-infra-phases/05-connectors-credentials-sandbox.md#5-sandbox-runtime
- docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md#12.2-phase-5

## ImpactStatementDraft

- Compatibility boundary: No Web/JDBC/starter changes in this slice; no workflow engine; no remote mesh; existing connector and credential behavior unchanged
- Affected layers:
- seahorse-agent-kernel domain/application/ports
- Owners:
- kernel sandbox runtime service composed from ports
- Invariants:
- Main JVM must not execute arbitrary sandbox code; network defaults to deny; artifacts are prompt-visible only after scan and non-secret classification
- Non-goals:
- Local shell, browser automation, code interpreter implementation, persistence, Web API, and Audit Ledger integration

These records are Method Pack drafts / hints, not authoritative runtime decisions.
