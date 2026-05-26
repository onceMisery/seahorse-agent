# AI Infra Sandbox Runtime Integration - Evidence

## EvidenceBundleDraft

- RED: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcSandboxRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 1.
  - Expected failure: `JdbcSandboxRepositoryAdapter` was missing.
- RED: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseSandboxControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 1.
  - Expected failure: `SandboxRuntimeInboundPort.close/listArtifacts` and `SeahorseSandboxController` were missing.
- RED: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentSandboxAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 1.
  - Expected failure surfaced before starter because the Web sandbox contract was missing in the reactor.
- GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcSandboxRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 0.
  - Covered: JDBC sandbox session/execution/artifact persistence and prompt-visible artifact query.
- GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseSandboxControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 0.
  - Covered: create session, execute, close, and list artifacts API routing.
- GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentSandboxAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 0.
  - Covered: JDBC adapter ports, default sandbox policy/runtime, runtime service wiring, and custom runtime replacement.
- GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=DefaultSandboxPolicyPortTests,KernelSandboxRuntimeServiceTests,SandboxPolicyDecisionTests,SandboxExecutionTests,SandboxArtifactTests' test`
  - Exit status: 0.
  - Covered: existing sandbox policy/domain/service invariants after inbound/outbound port expansion.
- Check: `git diff --check -- <sandbox touched files>`
  - Exit status: 0.
  - Covered: whitespace errors for the touched sandbox integration files.

## Evidence Boundary

This record covers the Phase 5 Sandbox persistence/Web/starter integration slice only. It does not verify full AI Infra completion.
