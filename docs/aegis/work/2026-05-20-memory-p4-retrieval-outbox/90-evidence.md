# Memory P4 retrieval outbox - Evidence

## RED

- Command:
  `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=DefaultMemoryEnginePortTests#shouldEnqueueOutboxWhenVectorIndexingFailsWithoutRollingBackMemoryWrite+shouldRecallVectorHitMemoriesAndFilterObsoleteProfileGeneration+shouldAppendBusinessDocumentCandidatesForBusinessRuleQuestions" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Result: expected compile failure before implementation.
- Missing surfaces:
  - `MemoryOutboxPort`
  - `MemoryBusinessDocumentRetrieverPort`
  - `DefaultMemoryEnginePort` constructor accepting vector/outbox/business retriever ports

## GREEN

- Command:
  `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=DefaultMemoryEnginePortTests#shouldEnqueueOutboxWhenVectorIndexingFailsWithoutRollingBackMemoryWrite+shouldRecallVectorHitMemoriesAndFilterObsoleteProfileGeneration+shouldAppendBusinessDocumentCandidatesForBusinessRuleQuestions" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Exit status: 0.
- Result: BUILD SUCCESS, 3 tests.
- Covered:
  - accepted writes keep durable short-term save when vector indexing fails
  - vector failure enqueues `VECTOR_UPSERT` outbox task
  - episodic route calls vector search and resolves hit IDs through memory stores
  - active Profile slot filters stale same-slot vector hit
  - business-rule route appends business document candidate into semantic memory zone

## Repository Evidence

- Command:
  `.\mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcMemoryRepositoryAdapterTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Exit status: 0.
- Result: BUILD SUCCESS, 7 tests.
- Covered:
  - `JdbcMemoryOutboxRepositoryAdapter.enqueue`
  - `pollPending`
  - `markSucceeded`
  - existing Profile, Correction, Operation Log, layered memory repository behavior

## Regression

- Command:
  `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=DefaultMemoryEnginePortTests,MemoryWorkflowRoutingTests,SeahorseAgentKernelAutoConfigurationTests#shouldRegisterMemoryManagementAndGovernancePortsWhenMemoryStoresExist" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Exit status: 0.
- Result: BUILD SUCCESS, 36 tests.

- Command:
  `.\mvnw.cmd -pl seahorse-agent-spring-boot-starter -am -DskipTests package "-Dspotless.check.skip=true"`
- Exit status: 0.
- Result: BUILD SUCCESS.

- Command:
  `git diff --check`
- Exit status: 0.
- Result: no whitespace errors; CRLF warnings only.

## Residual Risk

- Full pgvector or Milvus-backed `MemoryVectorPort` behavior remains dependent on the configured adapter and is outside this P4 minimal loop.
- Outbox worker polling/retry execution is not implemented in this slice; P4 creates durable tasks for later worker processing.
- P5 lifecycle state, generation invalidation jobs, vector GC, and P6 observability remain future phases.

Method Pack evidence does not grant completion authority.
