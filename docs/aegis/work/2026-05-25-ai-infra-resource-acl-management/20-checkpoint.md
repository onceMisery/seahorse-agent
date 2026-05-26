# AI Infra Resource ACL Management - Checkpoint

- Task ID: 2026-05-25-ai-infra-resource-acl-management
- Current todo: Finish Resource ACL JDBC adapter, Web API, Spring wiring, and focused verification.
- Active slice: Phase 4 Resource ACL management closure.
- Blocked on: none
- Next step: Implement JDBC adapter and schema, then run the RED JDBC test.

## Checkpoint Update

- Current todo: Phase 4 Resource ACL management slice verified; prepare next design-doc slice.
- Active slice: Phase 4 Resource ACL management closure
- Completed todos:
- Implemented ResourceAclRule domain/enums/lookup, management and policy ports/services, JDBC adapter/schema, Web API, starter wiring, and focused tests.
- Evidence refs:
- mvn kernel ResourceAcl tests: 10 tests passed
- mvn JDBC ResourceAcl adapter tests: 2 tests passed
- mvn Web SeahorseAgentControllerTests: 11 tests passed
- mvn Starter SeahorseAgentRegistryAutoConfigurationTests: 2 tests passed
- git diff --check: no whitespace errors
- Blocked on: none
- Next step: Continue to Phase 5B OpenAPI Connector import to ToolCatalog.

## DriftCheckDraft

- Scope status: Within Phase 4 Resource ACL management closure; no ContextPack invariant changes.
- Compatibility status: Kernel uses ports/domain only; JDBC/Web/Spring remain adapters; custom ResourceAccessPolicyPort stays replaceable.
- Retirement status: No old API/table retired; new sa_resource_acl_rule table is additive.
- New risk signals:
- Next phases remain unimplemented; ACL slice is not full AI Infra completion.
- Advisory decision: continue

## DriftCheckDraft

- Scope status: Review fix stayed within Phase 4 Resource ACL effective ordering and port contract.
- Compatibility status: Port semantics are stricter: replacement ResourceAclRepositoryPort implementations must provide findEffective instead of silently returning empty results.
- Retirement status: Retired default-empty findEffective fallback to prevent ACL enforcement bypass; no API/table retired.
- New risk signals:
- DB CHECK constraints for ACL enum columns remain deferred; Java adapters enforce enum mapping.
- Advisory decision: continue
