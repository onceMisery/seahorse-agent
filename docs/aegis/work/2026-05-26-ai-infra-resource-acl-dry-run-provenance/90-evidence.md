# AI Infra Resource ACL Dry-run and Provenance - Evidence

## EvidenceBundleDraft

- RED command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ResourceAclRuleTests,KernelResourceAclManagementServiceTests,AclBackedResourceAccessPolicyPortTests,AuditedResourceAccessPolicyPortTests,ResourceAclImportDryRunTests' test`
  - Result before implementation: failed at test compile because `ResourceAclImportDryRunReport`, `ResourceAclImportItem`, `ResourceAclImportItemStatus`, `ResourceAclNaturalKey`, and `ResourceAclImportDryRunCommand` did not exist.
- RED command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcResourceAclRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Result before implementation: blocked by kernel compile failure from missing dry-run/natural-key contract.
- RED command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Result before implementation: blocked by kernel compile failure from missing dry-run/natural-key contract.
- GREEN command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ResourceAclRuleTests,KernelResourceAclManagementServiceTests,AclBackedResourceAccessPolicyPortTests,AuditedResourceAccessPolicyPortTests,ResourceAclImportDryRunTests' test`
  - Result: build success; 15 tests run, 0 failures, 0 errors.
- GREEN command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcResourceAclRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Result: build success; 3 tests run, 0 failures, 0 errors.
- GREEN command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Result: build success; 12 tests run, 0 failures, 0 errors.
- Cleanliness command: `git diff --check`
  - Result: exit code 0; Windows line-ending warnings only.

## Implemented Evidence

- Kernel dry-run report/domain:
  - `ResourceAclImportItem`
  - `ResourceAclNaturalKey`
  - `ResourceAclImportDryRunItem`
  - `ResourceAclImportDryRunReport`
  - `ResourceAclImportItemStatus`
  - `ResourceAclImportReasonCode`
- Kernel service/ports:
  - `ResourceAclImportDryRunCommand`
  - `ResourceAclManagementInboundPort.dryRunImport(...)`
  - `ResourceAclRepositoryPort.findByNaturalKey(...)`
  - `KernelResourceAclManagementService.dryRunImport(...)`
- Adapters:
  - `JdbcResourceAclRepositoryAdapter.findByNaturalKey(...)`
  - ACL table enum/check constraints and active exact-rule unique index in `agent-registry-run-store-postgresql.sql`
  - `POST /api/resource-acl-rules:dry-run-import`
- Provenance floor:
  - `ContextItem` requires non-blank `aclDecisionId`.
  - `KernelContextPackBuilderService` only builds prompt items from `ALLOW` decisions and stores the decision id.

## Evidence Boundary

This record covers Phase 4 Resource ACL dry-run/provenance only. It does not verify full AI Infra completion.
