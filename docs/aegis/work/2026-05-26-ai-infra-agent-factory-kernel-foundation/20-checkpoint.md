# AI Infra Agent Factory Kernel Foundation - Checkpoint

- Task ID: 2026-05-26-ai-infra-agent-factory-kernel-foundation
- Current todo: Write RED tests for AgentTemplate and KernelAgentFactoryService
- Active slice: Phase 6 Agent Factory kernel foundation
- Blocked on: none
- Next step: Add failing kernel tests before production code

## Checkpoint Update

- Current todo: Phase 6 kernel Agent Factory foundation implemented and focused kernel tests are green; next continue Phase 6 integration with JDBC/Web/starter slices.
- Active slice: Phase 6 Agent Factory kernel foundation
- Completed todos:
- Created RED tests for AgentTemplate and KernelAgentFactoryService.
- Implemented Agent Factory domain enums/value objects, inbound/outbound ports, and KernelAgentFactoryService by composing existing AgentDefinitionInboundPort.
- Ran focused GREEN regression and git diff check.
- Evidence refs:
- evidence-bundle-draft-red-agent-factory-missing-contract.json
- evidence-bundle-draft-green-agent-factory-kernel-regression.json
- evidence-bundle-draft-diff-check.json
- Blocked on: none
- Next step: Implement Phase 6 integration layer: JDBC template/check repositories, Web controller, and starter wiring with focused tests.

## DriftCheckDraft

- Scope status: Stayed inside Phase 6 kernel foundation scope: domain, ports, application service, tests, and Aegis records.
- Compatibility status: Factory composes existing AgentDefinitionInboundPort; no JDBC/Web/starter/UI dependency was introduced into kernel; existing publish/version invariants remain owned by AgentDefinition service.
- Retirement status: No old factory owner exists to retire; future JDBC/Web/starter adapters will implement the new small ports without changing kernel invariants.
- New risk signals:
- Kernel validation currently returns Phase 8 EVAL/QUOTA as WARN and RESOURCE_ACL as PASS placeholder for this kernel slice; integration and stronger gates remain future work.
- Advisory decision: continue
