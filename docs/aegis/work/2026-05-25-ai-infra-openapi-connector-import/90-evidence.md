# AI Infra Phase 5B OpenAPI Connector Import - Evidence

## RED Evidence

- Command:
  - `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests#shouldExposeOpenApiConnectorImportApi' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- Exit status: failed as expected before implementation.
- Expected failure: `SeahorseOpenApiConnectorController` did not exist, proving the Web API test covered a missing user-facing import path.

## GREEN Evidence

- Command:
  - `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-adapter-openapi,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am '-Dtest=ConnectorOperationRiskMapperTests,KernelOpenApiConnectorImportServiceTests,OpenApiSpecParserAdapterTests,JdbcConnectorRepositoryAdapterTests,SeahorseAgentControllerTests#shouldExposeOpenApiConnectorImportApi,SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- Exit status: 0
- Key output:
  - `ConnectorOperationRiskMapperTests`: 1 test, 0 failures.
  - `KernelOpenApiConnectorImportServiceTests`: 3 tests, 0 failures.
  - `OpenApiSpecParserAdapterTests`: 1 test, 0 failures.
  - `JdbcConnectorRepositoryAdapterTests`: 1 test, 0 failures.
  - `SeahorseAgentControllerTests#shouldExposeOpenApiConnectorImportApi`: 1 test, 0 failures.
  - `SeahorseAgentRegistryAutoConfigurationTests`: 2 tests, 0 failures.
  - Reactor result: `BUILD SUCCESS`.
- Covered:
  - OpenAPI operation risk mapping.
  - Import service behavior.
  - OpenAPI parser adapter.
  - JDBC connector persistence.
  - Web import/query/operation enable path.
  - Starter auto-configuration for connector repository and kernel import service.
- Not covered:
  - Real remote OpenAPI HTTP execution, intentionally out of scope for Phase 5B.
  - Full project regression.
  - Audit Ledger integration, deferred to Phase 8A.

## Hygiene Evidence

- Command:
  - `git diff --check`
- Exit status: 0
- Notes: output only contained Git CRLF normalization warnings; no whitespace errors were reported.
