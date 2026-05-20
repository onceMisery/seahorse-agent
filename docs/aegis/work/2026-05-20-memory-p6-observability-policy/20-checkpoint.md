# Memory P6 observability policy - Checkpoint

- Task ID: 2026-05-20-memory-p6-observability-policy
- Current todo: P6 observability and dynamic policy controls verified and ready to commit.
- Active slice: completion-candidate
- Completed todos:
  - P5 committed as `0bd8bbe feat(memory): add lifecycle governance`.
  - Baseline read set reviewed for P6 requirements, Gemini policy/observability principles, value assessment, management service, controller, and Spring wiring.
  - Added `MemoryPolicyConfig`, `MemoryPolicyConfigPort`, `MemoryHealthReport`, and `InMemoryMemoryPolicyConfigPort`.
  - Updated `MemoryValueAssessor` to read capture, high-value, and risk thresholds from `MemoryPolicyConfigPort`.
  - Added constructor overloads so `DefaultMemoryEnginePort` can consume policy config while older constructors keep default behavior.
  - Extended `MemoryManagementServicePorts` and `KernelMemoryManagementService` with policy config and health-report aggregation.
  - Added default health/config methods to `MemoryManagementInboundPort`.
  - Exposed `/memories/health`, `/memories/policy-config` GET, and `/memories/policy-config` POST endpoints.
  - Wired `InMemoryMemoryPolicyConfigPort` from Spring properties.
  - Added kernel observability tests and extended Spring/web contract tests.
- Blocked on: none.
- Evidence refs:
  - `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelMemoryObservabilityServiceTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`: BUILD SUCCESS, 2 tests.
  - `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelMemoryObservabilityServiceTests,SeahorseWebApiContractTests#shouldKeepTraceKnowledgeMemoryIngestionAndPluginContracts,SeahorseAgentKernelAutoConfigurationTests#shouldRegisterMemoryManagementAndGovernancePortsWhenMemoryStoresExist+shouldInitializeMemoryPolicyConfigFromProperties" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`: BUILD SUCCESS, 5 tests.
  - `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelMemoryObservabilityServiceTests,KernelMemoryLifecycleServiceTests,DefaultMemoryEnginePortTests,MemoryWorkflowRoutingTests,KernelMemoryGovernanceServiceTests,SeahorseAgentKernelAutoConfigurationTests#shouldRegisterMemoryManagementAndGovernancePortsWhenMemoryStoresExist+shouldInitializeMemoryPolicyConfigFromProperties,SeahorseWebApiContractTests#shouldKeepTraceKnowledgeMemoryIngestionAndPluginContracts" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`: BUILD SUCCESS, 47 tests.
  - `.\mvnw.cmd -pl seahorse-agent-spring-boot-starter -am -DskipTests package "-Dspotless.check.skip=true"`: BUILD SUCCESS.
  - `git diff --check`: exit 0, CRLF warnings only.
- DriftCheckDraft:
  - Scope: aligned with P6 observability and dynamic policy subset.
  - Compatibility: existing stores, constructors, management APIs, Profile/Correction priority, and workflow authority remain compatible.
  - Retirement: no legacy metric or table retired; static capture constants are superseded by policy config while default values preserve behavior.
  - Decision: continue to commit.
- Next step: commit P6 as `feat(memory): add observability policy controls`.

Method Pack output does not grant completion authority.
