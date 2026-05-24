# AI Infra access decision audit - Intent

## TaskIntentDraft

- Requested outcome: Close the Phase 4 AccessDecision audit/query gap with a minimal port/service/adapter/API loop.
- Goal: Close the Phase 4 AccessDecision audit/query gap with a minimal port/service/adapter/API loop.
- Success evidence:
- Focused kernel, JDBC, Web, and Spring starter regressions pass; diff check passes; branch is committed and merged back to root main.
- Stop condition: Done when AccessDecision decisions are logged by the default policy wrapper, queryable through admin-only kernel service and HTTP API, auto-wired by the starter, verified, committed, and merged; blocked if schema or port semantics conflict with existing implementation; scope-exceeded if the slice requires full ACL management or a workflow engine.
- Non-goals:
- No POST /api/resources/{type}/{id}/acl.
- No remote Agent mesh, workflow engine, or complex JSON ACL model.
- No RAG/Memory pipeline rewrite.
- Scope: AccessDecision log/query ports, JDBC adapter, kernel query service, Web query API, Spring auto-configuration, focused tests, and Aegis work records.
- Change kinds:
- feature
- Risk hints:
- Cross-module public API, persistence adapter, and auto-configuration change.

## BaselineReadSetHint

- docs/company-agent/ai-infra-phases/04-context-db-resource-acl.md
- docs/company-agent/ai-infra-phases/99-current-implementation-handoff.md

## ImpactStatementDraft

- Compatibility boundary: Custom ResourceAccessPolicyPort beans still override the default audited wrapper; no full ACL storage management API is introduced.
- Affected layers:
- kernel
- jdbc
- web
- spring-starter
- Owners:
- ResourceAccessPolicyPort remains the policy extension point; AccessDecisionLogPort and AccessDecisionQueryPort own persistence contracts.
- Invariants:
- Kernel depends on ports and domain types only; JDBC/Web/Spring remain adapters; access query requires admin role.
- Non-goals:
- No POST /api/resources/{type}/{id}/acl.
- No remote Agent mesh, workflow engine, or complex JSON ACL model.
- No RAG/Memory pipeline rewrite.

These records are Method Pack drafts / hints, not authoritative runtime decisions.
