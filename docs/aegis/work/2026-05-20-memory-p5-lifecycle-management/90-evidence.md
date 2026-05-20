# Memory P5 lifecycle management - Evidence

## Targeted Repository Evidence

- Command:
  `.\mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcMemoryRepositoryAdapterTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Exit status: 0.
- Result: BUILD SUCCESS, 9 tests.
- Covered:
  - obsolete memory filtering for active reads
  - lifecycle read feedback persistence
  - profile-slot fragment obsolescence across layered stores
  - existing Profile, Correction, Operation Log, Outbox, and layered repository behavior

## Management API Contract Evidence

- Command:
  `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=SeahorseWebApiContractTests#shouldKeepTraceKnowledgeMemoryIngestionAndPluginContracts" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Exit status: 0.
- Result: BUILD SUCCESS, 1 test.
- Covered:
  - existing trace, knowledge, memory, ingestion, and plugin contracts remain stable
  - `/memories/profile-facts`
  - `/memories/corrections`
  - `/memories/operations`
  - `/memories/outbox`

## Kernel and Wiring Regression

- Command:
  `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelMemoryLifecycleServiceTests,DefaultMemoryEnginePortTests,MemoryWorkflowRoutingTests,KernelMemoryGovernanceServiceTests,SeahorseAgentKernelAutoConfigurationTests#shouldRegisterMemoryManagementAndGovernancePortsWhenMemoryStoresExist,SeahorseWebApiContractTests#shouldKeepTraceKnowledgeMemoryIngestionAndPluginContracts" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Exit status: 0.
- Result: BUILD SUCCESS, 44 tests.
- Covered:
  - Profile generation invalidation lifecycle call path
  - read feedback call path
  - existing memory engine behavior and graceful degradation
  - ingestion workflow routing
  - governance service behavior
  - Spring auto-configuration for management/governance memory ports

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

- Offline compaction, physical vector/BM25 GC, and full REVIEW candidate workflow remain future P5/P6 follow-up work.
- Lifecycle obsolescence for layered Profile fragments uses metadata/profile-slot matching and compatibility fallbacks; heavily malformed historical metadata may require manual cleanup or backfill.
- Live database upgrade still needs deployment verification against the running local Docker stack.

Method Pack evidence does not grant completion authority.
