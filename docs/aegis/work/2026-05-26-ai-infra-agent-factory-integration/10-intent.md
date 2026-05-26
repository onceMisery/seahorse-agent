# AI Infra Agent Factory Integration - Intent

## TaskIntentDraft

- Requested outcome: Implement Phase 6 Agent Factory JDBC, Web, and starter integration around the existing kernel ports
- Goal: Continue AI Infra implementation according to Phase 6 design by wiring Agent Factory adapters without changing kernel invariants
- Success evidence:
- RED and GREEN tests cover JDBC template/check repositories, Web template/from-template/validate APIs, and starter auto-configuration for repositories and KernelAgentFactoryService
- Stop condition: done when focused JDBC/Web/starter tests and diff check pass; needs-verification if tests cannot run; scope-exceeded if UI or rollback is required in this slice
- Non-goals:
- Agent Studio frontend, rollback endpoint behavior, canary/eval/quota enforcement, and Audit Ledger integration
- Scope: Repository JDBC adapter, Web adapter, Spring Boot starter wiring, SQL schema, tests, and Aegis records
- Change kinds:
- feature
- Risk hints:
- Cross-module adapter integration; must preserve ports/adapters direction and avoid duplicating AgentDefinition invariants

## BaselineReadSetHint

- docs/company-agent/ai-infra-phases/06-agent-factory-studio.md
- docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md#12.3.7

## ImpactStatementDraft

- Compatibility boundary: No UI, no rollback implementation, no publish blocking, no Phase 8 Eval/Quota hard gate in this slice
- Affected layers:
- adapter-repository-jdbc, adapter-web, spring-boot-starter
- Owners:
- adapters implement AgentTemplateRepositoryPort, AgentPublishCheckRepositoryPort, and AgentFactoryInboundPort wiring
- Invariants:
- Kernel remains independent of JDBC/Web/Spring; Agent Factory composes AgentDefinitionInboundPort and does not bypass draft/version invariants
- Non-goals:
- Agent Studio frontend, rollback endpoint behavior, canary/eval/quota enforcement, and Audit Ledger integration

These records are Method Pack drafts / hints, not authoritative runtime decisions.
