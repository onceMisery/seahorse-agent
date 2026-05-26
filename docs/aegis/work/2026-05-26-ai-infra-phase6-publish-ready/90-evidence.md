# AI Infra Phase 6 Publish-ready - Evidence

## EvidenceBundleDraft

- RED JDBC:
  - `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentFactoryRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Expected failure: missing `JdbcAgentVersionActivationRepositoryAdapter` and `JdbcAgentCatalogQueryAdapter`.
- GREEN JDBC:
  - Same command.
  - Result: BUILD SUCCESS; `JdbcAgentFactoryRepositoryAdapterTests` ran 4 tests with 0 failures/errors.
- RED Web:
  - `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentFactoryControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Expected failure: `GET /api/agents/hr-assistant/publish-checks/latest` returned 404 before endpoint implementation.
- GREEN Web:
  - Same command.
  - Result: BUILD SUCCESS; `SeahorseAgentFactoryControllerTests` ran 2 tests with 0 failures/errors.
- RED starter:
  - `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Expected failure: missing bean of type `AgentVersionActivationRepositoryPort`.
- GREEN starter:
  - Same command.
  - Result: BUILD SUCCESS; `SeahorseAgentRegistryAutoConfigurationTests` ran 2 tests with 0 failures/errors.
- Focused acceptance:
  - `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAgentFactoryServiceTests' test`
  - Result: BUILD SUCCESS; 8 tests, 0 failures/errors.
  - `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentFactoryRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Result: BUILD SUCCESS; 4 tests, 0 failures/errors.
  - `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentFactoryControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Result: BUILD SUCCESS; 2 tests, 0 failures/errors.
  - `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Result: BUILD SUCCESS; 2 tests, 0 failures/errors.
  - `git diff --check`
  - Result: exit code 0; only existing LF-to-CRLF warnings were reported.

## Evidence Boundary

This record covers the Phase 6 Publish-ready slice only. It does not verify full AI Infra completion.
