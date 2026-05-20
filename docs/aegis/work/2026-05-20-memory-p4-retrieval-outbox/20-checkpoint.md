# Memory P4 retrieval outbox - Checkpoint

- Task ID: 2026-05-20-memory-p4-retrieval-outbox
- Current todo: P4 retrieval/outbox implementation verified and ready to commit.
- Active slice: completion-candidate
- Completed todos:
  - P3 committed as `ddd9dd2 feat(memory): add question-aware context weaving`.
  - Baseline read set reviewed for memory engine, vector port, retrieval engine, JDBC schema upgrade, and Spring wiring.
  - RED tests added for vector outbox fallback, vector-hit recall with active Profile filtering, and business document candidates.
  - `MemoryOutboxPort` and `MemoryBusinessDocumentRetrieverPort` added with no-op defaults.
  - `DefaultMemoryEnginePort` now indexes accepted writes through `MemoryVectorPort`, enqueues `VECTOR_UPSERT` outbox tasks on indexing failure, recalls vector hit IDs for episodic routes, filters active Profile-slot stale candidates, and appends business document candidates for business routes.
  - JDBC `t_memory_outbox`, `JdbcMemoryOutboxRepositoryAdapter`, Spring wiring, and init SQL updated.
- Blocked on: none.
- Evidence refs:
  - RED command failed before implementation because `MemoryOutboxPort`, `MemoryBusinessDocumentRetrieverPort`, and the extended `DefaultMemoryEnginePort` constructor did not exist.
  - `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=DefaultMemoryEnginePortTests#shouldEnqueueOutboxWhenVectorIndexingFailsWithoutRollingBackMemoryWrite+shouldRecallVectorHitMemoriesAndFilterObsoleteProfileGeneration+shouldAppendBusinessDocumentCandidatesForBusinessRuleQuestions" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`: BUILD SUCCESS, 3 tests.
  - `.\mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcMemoryRepositoryAdapterTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`: BUILD SUCCESS, 7 tests.
  - `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=DefaultMemoryEnginePortTests,MemoryWorkflowRoutingTests,SeahorseAgentKernelAutoConfigurationTests#shouldRegisterMemoryManagementAndGovernancePortsWhenMemoryStoresExist" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`: BUILD SUCCESS, 36 tests.
  - `.\mvnw.cmd -pl seahorse-agent-spring-boot-starter -am -DskipTests package "-Dspotless.check.skip=true"`: BUILD SUCCESS.
  - `git diff --check`: exit 0, CRLF warnings only.
- DriftCheckDraft:
  - Scope: aligned with P4 minimal retrieval/outbox loop.
  - Compatibility: existing constructors retained through overload delegation and new ports default to no-op.
  - Retirement: no existing memory store path retired; this fills previously empty `MemoryVectorPort` integration and adds durable outbox fallback.
  - Decision: continue to commit.
- Next step: commit P4 as `feat(memory): add retrieval outbox loop`.

Method Pack output does not grant completion authority.
