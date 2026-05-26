# AI Infra Resource ACL Dry-run and Provenance - Intent

- Requested outcome: Continue the active AI Infra goal by implementing the Phase 4 remainder from section 14.2 of `docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md`.
- Scope: Add Resource ACL import dry-run domain/report, inbound command/API, repository natural-key query, JDBC schema constraints, and minimal Context provenance hardening evidence.
- Non-goals:
  - Do not replace the existing single-rule Resource ACL create/page/disable flow.
  - Do not bulk-write ACL rules from dry-run.
  - Do not implement full Audit Ledger wiring for every context access in this slice.
- Baseline refs:
  - `docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md`, section 14.2.
  - Existing `ResourceAclRule`, `KernelResourceAclManagementService`, `JdbcResourceAclRepositoryAdapter`, `SeahorseResourceAclController`, and ContextPack/AccessDecision domain.
- Impact statement:
  - Kernel gains dry-run domain objects and a small command extension on `ResourceAclManagementInboundPort`.
  - Repository gains natural-key lookup as persistence-only support.
  - Web/JDBC stay adapter-only; dry-run remains non-mutating.
