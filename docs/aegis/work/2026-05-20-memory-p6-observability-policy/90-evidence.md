# Memory P6 observability policy - Evidence

## Targeted Kernel Evidence

- Command:
  `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelMemoryObservabilityServiceTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Exit status: 0.
- Result: BUILD SUCCESS, 2 tests.
- Covered:
  - user/tenant memory health aggregation
  - runtime policy config read/update behavior

## Contract and Wiring Evidence

- Command:
  `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelMemoryObservabilityServiceTests,SeahorseWebApiContractTests#shouldKeepTraceKnowledgeMemoryIngestionAndPluginContracts,SeahorseAgentKernelAutoConfigurationTests#shouldRegisterMemoryManagementAndGovernancePortsWhenMemoryStoresExist+shouldInitializeMemoryPolicyConfigFromProperties" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Exit status: 0.
- Result: BUILD SUCCESS, 5 tests.
- Covered:
  - management health endpoint contract
  - policy config GET/POST endpoint contract
  - Spring property-backed policy config initialization
  - kernel management-service wiring with policy config port

## Broader Regression Evidence

- Command:
  `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelMemoryObservabilityServiceTests,KernelMemoryLifecycleServiceTests,DefaultMemoryEnginePortTests,MemoryWorkflowRoutingTests,KernelMemoryGovernanceServiceTests,SeahorseAgentKernelAutoConfigurationTests#shouldRegisterMemoryManagementAndGovernancePortsWhenMemoryStoresExist+shouldInitializeMemoryPolicyConfigFromProperties,SeahorseWebApiContractTests#shouldKeepTraceKnowledgeMemoryIngestionAndPluginContracts" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Exit status: 0.
- Result: BUILD SUCCESS, 47 tests.
- Covered:
  - P6 observability and policy behavior
  - P5 lifecycle behavior
  - existing memory engine behavior and graceful degradation
  - ingestion workflow routing and governance services
  - Web management contract and Spring auto-configuration wiring

## Package and Diff Checks

- Command:
  `.\mvnw.cmd -pl seahorse-agent-spring-boot-starter -am -DskipTests package "-Dspotless.check.skip=true"`
- Exit status: 0.
- Result: BUILD SUCCESS.

- Command:
  `git diff --check`
- Exit status: 0.
- Result: no whitespace errors; CRLF warnings only.

## Residual Risk

- Policy config is runtime in-memory state initialized from properties; updates do not survive process restart unless a persistent `MemoryPolicyConfigPort` is supplied later.
- Health metrics are sampled from existing ports rather than dedicated aggregate SQL queries; counts are bounded by the configured sample limit.
- Recall hit-rate, prompt-token consumption, and GC counts are not yet emitted as full Micrometer metrics in this P6 slice.

Method Pack evidence does not grant completion authority.
