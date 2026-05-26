# AI Infra Agent Factory Kernel Foundation - Intent

## TaskIntentDraft

- Requested outcome: Implement Phase 6 kernel Agent Factory foundation: read built-in templates, create DRAFT agent from template, and return structured publish validation report
- Goal: Continue AI Infra implementation according to Phase 6 design by adding Agent Factory kernel foundation
- Success evidence:
- RED and GREEN kernel tests cover template tool subset, risk cap, instruction overlay merge, from-template draft creation via AgentDefinitionInboundPort, and publish validation report statuses
- Stop condition: done when focused kernel tests and diff check pass; blocked only if existing AgentDefinition contract cannot support draft creation; needs-verification if tests cannot run; scope-exceeded if JDBC/Web/UI is required in this slice
- Non-goals:
- Full Studio UI, rollback implementation, JDBC template storage, Web API, Eval/Quota enforcement, and publish blocking
- Scope: Kernel domain, inbound/outbound ports, application service, tests, and Aegis work records only
- Change kinds:
- feature
- Risk hints:
- Architecture and contract change: Agent Factory must compose existing AgentDefinition service and preserve draft/version invariants

## BaselineReadSetHint

- docs/company-agent/ai-infra-phases/06-agent-factory-studio.md
- docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md#12.3-phase-6

## ImpactStatementDraft

- Compatibility boundary: No JDBC/Web/starter/UI changes in this slice; no workflow engine; existing AgentDefinition publish behavior unchanged
- Affected layers:
- seahorse-agent-kernel domain/application/ports
- Owners:
- kernel agent factory service composed from existing AgentDefinitionInboundPort and template/check repositories
- Invariants:
- Factory must not rebuild or bypass AgentDefinition invariants; from-template creates DRAFT only; templates can only be narrowed by requested tools/risk
- Non-goals:
- Full Studio UI, rollback implementation, JDBC template storage, Web API, Eval/Quota enforcement, and publish blocking

These records are Method Pack drafts / hints, not authoritative runtime decisions.
