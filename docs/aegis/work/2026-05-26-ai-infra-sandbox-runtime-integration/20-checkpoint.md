# AI Infra Sandbox Runtime Integration - Checkpoint

- Task ID: 2026-05-26-ai-infra-sandbox-runtime-integration
- Current todo: Phase 5 sandbox persistence/Web/starter integration is implemented and focused verification passed.
- Active slice: Phase 5 sandbox outer integration
- Completed todos:
  - Re-read section 14.3 of the unfinished phase plan.
  - Re-read current sandbox kernel service, domain records, inbound/outbound ports, starter configuration, and related tests.
  - Confirmed RED for missing JDBC sandbox adapter.
  - Confirmed RED for missing Web close/list artifacts contract and controller.
  - Added sandbox session/execution/artifact query ports without making a broad Agent service.
  - Added repository-backed sandbox runtime orchestration while preserving default fail-closed runtime behavior.
  - Added JDBC sandbox repository adapter and schema for session/execution/artifact persistence.
  - Added sandbox Web API and Spring Boot starter wiring.
  - Ran focused GREEN verification for JDBC, Web, starter, kernel sandbox regressions, and diff whitespace checks.
- Evidence refs:
  - `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcSandboxRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`: RED then GREEN.
  - `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseSandboxControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`: RED then GREEN.
  - `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentSandboxAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`: GREEN.
  - `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=DefaultSandboxPolicyPortTests,KernelSandboxRuntimeServiceTests,SandboxPolicyDecisionTests,SandboxExecutionTests,SandboxArtifactTests' test`: GREEN.
  - `git diff --check -- <sandbox touched files>`: no whitespace errors.
- Blocked on: none
- Next step: Continue recommended order with Phase 8A Audit Ledger foundation.

## DriftCheckDraft

- Scope status: Within Phase 5 Sandbox integration; no real sandbox execution runtime added.
- Compatibility status: Kernel remains port-driven; JDBC/Web/Spring stay adapters.
- Retirement status: Current in-memory constructor remains as compatibility path for existing kernel tests; Spring wiring now uses repository-backed constructor.
- New risk signals:
  - Full Phase 5 still needs connector disable/credential binding/audit integration outside this sandbox slice.
- Advisory decision: continue to Phase 8A
