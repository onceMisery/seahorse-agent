# AI Infra access decision audit - Reflection

## Reflection

Goal:
- Close the Phase 4 AccessDecision audit/query gap without expanding into full ACL management.

Repair track:
- Root issue: `sa_access_decision_log` existed in the registry/run-store schema, but resource access decisions were not logged through a dedicated port and were not queryable through kernel/Web APIs.
- Canonical owner: `ResourceAccessPolicyPort` still owns policy decisions; `AccessDecisionLogPort` and `AccessDecisionQueryPort` own audit persistence contracts; `KernelAccessDecisionQueryService` owns the admin query boundary.
- Minimal change: wrap the default policy with `AuditedResourceAccessPolicyPort`, persist decisions with a JDBC adapter, expose admin-only query through an inbound port, and map it to `GET /api/access-decisions`.
- Compatibility boundary: custom `ResourceAccessPolicyPort` beans still replace the default audited wrapper; no ACL mutation model or resource ACL management API was introduced.

Retirement track:
- Old no-op audit behavior remains available through `AccessDecisionLogPort.empty()` for non-JDBC or custom setups.
- Spring starter now records default policy decisions when an `AccessDecisionLogPort` is available.
- Full resource ACL storage/mutation remains deferred until a stable ACL model is specified.

Risk / Unknown:
- AccessDecision query is admin-only because the audit log is operational/security data.
- This slice logs decisions produced by the default policy wrapper; custom `ResourceAccessPolicyPort` implementations must opt into auditing themselves or provide their own composition.
- Aegis workspace check is currently blocked by historical `docs/aegis` records outside this work directory that are not indexed or use older JSON shapes.

Decision:
- Focused regression and diff check passed in the feature worktree.
- Continue to commit, merge into root `main`, and rerun focused regression on `main`.

Method Pack output does not grant completion authority.
