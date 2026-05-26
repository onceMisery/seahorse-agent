# AI Infra Resource ACL Management - Intent

## TaskIntentDraft

- Requested outcome: Implement Phase 4 Resource ACL persistence and management API according to the expanded AI Infra design plan.
- Goal: 按设计文档推进 Phase 4 Resource ACL 持久化管理闭环。
- Success evidence:
- Kernel, JDBC, Web, and Spring starter focused tests pass; git diff check passes; ACL-backed policy remains replaceable through ResourceAccessPolicyPort.
- Stop condition: Done when Resource ACL domain, repository, Web API, starter wiring, schema, and focused verification are complete; blocked if baseline contracts conflict or required dependencies are unavailable; needs-verification if code exists without passing evidence; scope-exceeded if work drifts into full IAM/RBAC or workflow engine.
- Non-goals:
- Do not introduce workflow engine, remote mesh, complex JSON policy language, or full IAM/RBAC.
- Scope: Phase 4 resource ACL persistence, management API, JDBC schema, and starter composition.
- Change kinds:
- cross-module feature
- Risk hints:
- Public API, persistence schema, and starter auto-configuration change.

## BaselineReadSetHint

- docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md
- docs/company-agent/ai-infra-phases/04-context-db-resource-acl.md
- docs/company-agent/Seahorse Agent 企业级 AI Infra 架构基线.md

## ImpactStatementDraft

- Compatibility boundary: No ContextPack domain invariant changes; no full IAM/RBAC; no policy language engine.
- Affected layers:
- kernel-domain
- kernel-ports
- jdbc-adapter
- web-adapter
- spring-starter
- Owners:
- ResourceAccessPolicyPort remains the runtime authorization extension point; ResourceAclRepositoryPort owns ACL persistence contract.
- Invariants:
- Kernel must not depend on Spring/JDBC/Web; ACL rule status/effect/action/scope use enums or named constants; custom ResourceAccessPolicyPort beans must override default composition.
- Non-goals:
- Do not introduce workflow engine, remote mesh, complex JSON policy language, or full IAM/RBAC.

These records are Method Pack drafts / hints, not authoritative runtime decisions.
