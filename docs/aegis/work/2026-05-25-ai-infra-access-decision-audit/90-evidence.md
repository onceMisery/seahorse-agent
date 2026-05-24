# AI Infra access decision audit - Evidence

## EvidenceBundleDraft

Verification:
- Focused AccessDecision audit/query regression passed:
  `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am '-Dtest=AuditedResourceAccessPolicyPortTests,KernelAccessDecisionQueryServiceTests,JdbcAccessDecisionRepositoryAdapterTests,SeahorseAgentControllerTests,SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `git diff --check` exited 0. Git only reported LF/CRLF conversion warnings.
- Aegis proof bundle assembled:
  `docs/aegis/work/2026-05-25-ai-infra-access-decision-audit/proof-bundle.md`

Workspace check:
- `python C:\Users\miracle\.codex\aegis\scripts\aegis-workspace.py check --root D:\code\seahorse-agent\.worktrees\ai-infra-access-decision-audit` exited 1 because pre-existing historical `docs/aegis` records are not indexed or use older JSON sidecar shapes.
- The failing paths are outside this AccessDecision audit work directory.

Changed production surfaces:
- Kernel ports:
  `AccessDecisionLogPort`, `AccessDecisionQueryPort`, `AccessDecisionQuery`, `AccessDecisionPage`, `AccessDecisionQueryInboundPort`.
- Kernel services:
  `AuditedResourceAccessPolicyPort`, `KernelAccessDecisionQueryService`.
- JDBC adapter:
  `JdbcAccessDecisionRepositoryAdapter`.
- Web API:
  `GET /api/access-decisions` via `SeahorseAccessDecisionController`.
- Spring starter:
  JDBC access decision repository auto-configuration, audited default `ResourceAccessPolicyPort`, and access decision query inbound service.

Changed tests:
- `AuditedResourceAccessPolicyPortTests`
- `KernelAccessDecisionQueryServiceTests`
- `JdbcAccessDecisionRepositoryAdapterTests`
- `SeahorseAgentControllerTests`
- `SeahorseAgentRegistryAutoConfigurationTests`

Pending evidence:
- Commit and merge back to root `main`.
- Rerun focused regression on root `main`.

## EvidenceBundleDraft

- Artifact key: focused-regression
- Type: test
- Source: mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am '-Dtest=AuditedResourceAccessPolicyPortTests,KernelAccessDecisionQueryServiceTests,JdbcAccessDecisionRepositoryAdapterTests,SeahorseAgentControllerTests,SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
- Summary: Focused AccessDecision audit/query regression passed across kernel, JDBC, Web, and Spring starter.
- Verifier: codex
