# AI Infra Agent Factory Integration - Evidence

## RED Evidence

- JDBC RED:
  - Command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentFactoryRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 1
  - Expected failure: test compile failed because `JdbcAgentTemplateRepositoryAdapter` and `JdbcAgentPublishCheckRepositoryAdapter` were missing.
- Web RED:
  - Command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentFactoryControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 1
  - Expected failure: test compile failed because `SeahorseAgentFactoryController` was missing.
- Starter RED:
  - Command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 1
  - Expected failure path: build stopped at Web test compile because the Agent Factory controller was still missing; starter assertions had already been added to require Agent Factory beans.

## GREEN Evidence

- JDBC GREEN:
  - Command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentFactoryRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 0
  - Result: 2 tests, 0 failures, 0 errors.
  - Covered: enabled/all template listing, template lookup by id, publish-check save, latest publish-check lookup.
- Web GREEN:
  - Command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentFactoryControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 0
  - Result: 1 test, 0 failures, 0 errors.
  - Covered: `GET /api/agent-templates`, `POST /api/agents/from-template`, `POST /api/agents/{agentId}/validate`.
- Starter GREEN:
  - Command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 0
  - Result: 2 tests, 0 failures, 0 errors.
  - Covered: repository auto-configuration and `KernelAgentFactoryService` bean wiring.
- Kernel regression:
  - Command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentTemplateTests,KernelAgentFactoryServiceTests' test`
  - Exit status: 0
  - Result: 6 tests, 0 failures, 0 errors.
  - Covered: Agent Template invariants and kernel factory service behavior after extending `AgentPublishCheckRepositoryPort`.
- Targeted diff check:
  - Command: `git diff --check -- <Phase 6 integration files>`
  - Exit status: 0
  - Output contained only existing Windows line-ending warnings for tracked files.

## Evidence Boundary

This evidence covers Phase 6 Agent Factory JDBC/Web/starter integration only. It does not cover rollback behavior, Agent Studio UI, Production Gate integration, Audit Ledger integration, or full AI Infra completion.
