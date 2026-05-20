# Memory P5 lifecycle management - Checkpoint

- Task ID: 2026-05-20-memory-p5-lifecycle-management
- Current todo: P5 lifecycle governance and management surface verified and ready to commit.
- Active slice: completion-candidate
- Completed todos:
  - P4 committed as `8f141d8 feat(memory): add retrieval outbox loop`.
  - Baseline read set reviewed for lifecycle requirements, memory engine read/write paths, JDBC adapters, management service, controller, and Spring wiring.
  - Added `MemoryLifecyclePort` with no-op fallback.
  - Added `JdbcMemoryLifecycleRepositoryAdapter` for profile-slot obsolescence and read feedback.
  - Extended layered memory schema and upgrade logic with `tenant_id`, `status`, `generation_id`, `profile_slot`, `valid_from`, `valid_until`, and `last_referenced_at` columns plus lifecycle indexes.
  - Updated short-term, long-term, and semantic JDBC repositories to save lifecycle metadata and filter obsolete/deleted records from active reads.
  - Updated `DefaultMemoryEnginePort` to write profile generation metadata, obsolete old same-slot fragments after Profile/Correction writes, and record read feedback for loaded layered memories.
  - Exposed Profile facts, Correction rules, operation records, and outbox tasks through management ports and `/memories/*` APIs.
  - Wired lifecycle repository and expanded management service dependencies in Spring auto-configuration.
- Blocked on: none.
- Evidence refs:
  - `.\mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcMemoryRepositoryAdapterTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`: BUILD SUCCESS, 9 tests.
  - `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=SeahorseWebApiContractTests#shouldKeepTraceKnowledgeMemoryIngestionAndPluginContracts" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`: BUILD SUCCESS, 1 test.
  - `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelMemoryLifecycleServiceTests,DefaultMemoryEnginePortTests,MemoryWorkflowRoutingTests,KernelMemoryGovernanceServiceTests,SeahorseAgentKernelAutoConfigurationTests#shouldRegisterMemoryManagementAndGovernancePortsWhenMemoryStoresExist,SeahorseWebApiContractTests#shouldKeepTraceKnowledgeMemoryIngestionAndPluginContracts" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`: BUILD SUCCESS, 44 tests.
  - `.\mvnw.cmd -pl seahorse-agent-spring-boot-starter -am -DskipTests package "-Dspotless.check.skip=true"`: BUILD SUCCESS.
  - `git diff --check`: exit 0, CRLF warnings only.
- DriftCheckDraft:
  - Scope: aligned with P5 lifecycle governance and management-surface subset.
  - Compatibility: existing tables, constructors, management API methods, and memory store ports remain compatible.
  - Retirement: no store path retired; obsolete status is now honored for active reads.
  - Decision: continue to commit.
- Next step: commit P5 as `feat(memory): add lifecycle governance`.

Method Pack output does not grant completion authority.
