# AI Infra Agent Factory Integration - Checkpoint

- Task ID: 2026-05-26-ai-infra-agent-factory-integration
- Current todo: Phase 6 Agent Factory JDBC/Web/starter integration implemented and focused tests are green.
- Active slice: Phase 6 Agent Factory integration
- Completed todos:
  - Read existing Agent Factory kernel ports/service, JDBC adapter patterns, Web controller patterns, and Spring Boot starter registry auto-configuration.
  - Confirmed RED for JDBC adapter tests: missing `JdbcAgentTemplateRepositoryAdapter` and `JdbcAgentPublishCheckRepositoryAdapter`.
  - Confirmed RED for Web adapter tests: missing `SeahorseAgentFactoryController`.
  - Added starter RED assertions for `AgentTemplateRepositoryPort`, `AgentPublishCheckRepositoryPort`, `AgentFactoryInboundPort`, and `KernelAgentFactoryService`.
  - Implemented `AgentPublishCheckRepositoryPort.latest(String agentId)` and updated the kernel memory test double.
  - Implemented JDBC template repository and publish-check repository adapters.
  - Added `sa_agent_template` and `sa_agent_publish_check` schema entries.
  - Implemented `SeahorseAgentFactoryController` for template list, from-template, and publish validation APIs.
  - Wired Agent Factory repositories and `KernelAgentFactoryService` through Spring Boot starter auto-configuration.
  - Ran focused JDBC/Web/starter/kernel factory GREEN regressions and targeted diff check.
- Evidence refs:
  - `docs/aegis/work/2026-05-26-ai-infra-agent-factory-integration/90-evidence.md`
- Blocked on: none
- Next step: Continue from section 13.6 with Phase 5 integration hardening or Phase 8 Audit Ledger foundation; Phase 6 rollback/Production Gate integration remains a later Phase 6 publish-ready slice.

## DriftCheckDraft

- Scope status: Stayed inside Phase 6 integration scope: JDBC adapters, Web controller, starter wiring, schema, port query addition, and tests.
- Compatibility status: Kernel still depends only on domain/ports/JDK; JDBC/Web/Spring code remains in adapters/starter. Factory still composes `AgentDefinitionInboundPort` and does not duplicate AgentDefinition/version invariants.
- Retirement status: No existing factory integration owner existed to retire. New adapters implement the small repository ports and can be replaced through Spring `@ConditionalOnMissingBean`.
- New risk signals:
  - Rollback API, Agent Studio UI, Production Gate, Audit Ledger integration, and stronger Phase 8 eval/quota gates remain future slices.
- Advisory decision: continue
