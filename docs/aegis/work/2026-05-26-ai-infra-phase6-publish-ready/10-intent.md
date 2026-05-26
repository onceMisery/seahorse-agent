# AI Infra Phase 6 Publish-ready - Intent

- Requested outcome: Continue the active AI Infra goal by implementing the Phase 6 Publish-ready slice from section 15.3 of `docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md`.
- Scope:
  - Add latest publish check query through Factory API.
  - Add rollback command/result/status/reason and version activation persistence.
  - Add minimal Agent Catalog query for published/enabled agents.
  - Wire kernel, JDBC, Web, and starter adapters.
- Non-goals:
  - Do not implement frontend Agent Studio.
  - Do not implement canary rollout.
  - Do not mutate historical `AgentVersion` snapshots during rollback.
  - Do not replace the existing Production Gate controller.
- Baseline refs:
  - `docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md` section 15.3.
  - `docs/company-agent/ai-infra-phases/06-agent-factory-studio.md`.
  - `docs/company-agent/Seahorse Agent 企业级 AI Infra 架构基线.md`.
- Impact:
  - Cross-module API and persistence change across kernel ports, JDBC adapter, Web controller, and Spring starter.
  - ArchitectureReviewRequired: yes.
