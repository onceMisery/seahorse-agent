# AI Infra Audit Ledger Foundation - Intent

- Requested outcome: Continue the active AI Infra goal by implementing the Phase 8A Audit Ledger foundation from section 14.6 of `docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md`.
- Scope: Add a unified append-only audit event model, redaction policy, repository/query ports, JDBC/Web/starter adapters, and a minimal Production Gate report bridge.
- Non-goals:
  - Do not wire every Phase 3-7 service into Audit Ledger in this slice.
  - Do not implement full eval, quota, SRE, or canary platforms.
  - Do not let repository code own audit redaction or gate policy decisions.
- Baseline refs:
  - `docs/company-agent/ai-infra-phases/08-production-hardening.md`
  - `docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md`, section 14.6
  - Existing `ToolInvocationAudit*`, `AgentPublishCheck*`, retrieval evaluation, and feature health files.
- Impact statement:
  - Kernel gains small audit/gate domain objects and ports.
  - JDBC/Web/Spring add adapters around those ports.
  - Default audit behavior remains conservative and explicit: `FAIL_CLOSED`, `WARN_AND_CONTINUE`, or `NOOP`.
