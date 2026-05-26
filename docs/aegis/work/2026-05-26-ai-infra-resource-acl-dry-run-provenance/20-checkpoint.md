# AI Infra Resource ACL Dry-run and Provenance - Checkpoint

- Task ID: 2026-05-26-ai-infra-resource-acl-dry-run-provenance
- Current todo: Phase 4 Resource ACL dry-run and Context Provenance hardening completed; move to Phase 6 Publish-ready.
- Active slice: Completed Phase 4 Resource ACL dry-run and Context Provenance hardening.
- Completed todos:
  - Re-read section 14.2 of the unfinished phase plan.
  - Scanned existing Resource ACL domain/service/JDBC/Web code.
  - Confirmed single-rule ACL management exists; dry-run import and natural-key query are missing.
  - Added RED tests for dry-run domain/service behavior, JDBC natural-key lookup/schema constraints, and Web dry-run API.
  - Implemented `ResourceAclImportItem`, `ResourceAclNaturalKey`, dry-run report/result enums, and inbound command.
  - Extended `ResourceAclManagementInboundPort` with `dryRunImport(...)`.
  - Extended `ResourceAclRepositoryPort` with `findByNaturalKey(...)`.
  - Implemented kernel dry-run classification rules with non-mutating behavior.
  - Implemented JDBC natural-key lookup and ACL enum/check constraints.
  - Added `POST /api/resource-acl-rules:dry-run-import`.
  - Confirmed Context provenance floor remains enforced by `ContextItem.aclDecisionId` and `KernelContextPackBuilderService` only admits allowed items with decision ids.
- Evidence refs:
  - RED: kernel/JDBC/Web target tests failed before implementation due to missing dry-run/natural-key/API types and methods.
  - GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ResourceAclRuleTests,KernelResourceAclManagementServiceTests,AclBackedResourceAccessPolicyPortTests,AuditedResourceAccessPolicyPortTests,ResourceAclImportDryRunTests' test`
  - GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcResourceAclRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Cleanliness: `git diff --check` passed with Windows line-ending warnings only.
- Blocked on: none
- Next step: Start Phase 6 Publish-ready from section 14.4 of the unfinished phase plan.

## DriftCheckDraft

- Scope status: Within Phase 4 dry-run/provenance; no bulk commit or workflow engine.
- Compatibility status: Existing Resource ACL create/page/disable stays active; dry-run is additive.
- Retirement status: No existing API/table retired.
- New risk signals:
  - Dry-run must never call repository save.
  - `RESOURCE_TYPE` scope is unsupported in this slice.
- Advisory decision: Phase 4 slice complete; continue to Phase 6.
