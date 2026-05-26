# AI Infra Phase Plan Refresh - Intent

## TaskIntentDraft

- Requested outcome: Read `docs/company-agent/` and `docs/company-agent/ai-infra-phases/`, then add one more detailed and concrete design/development plan for every unfinished AI Infra phase.
- Goal: Keep the remaining AI Infra implementation plan aligned with current worktree reality instead of the older handoff state.
- Success evidence:
  - `09-unfinished-phase-design-development-plans.md` reflects current Phase 4 ACL and Phase 5B OpenAPI progress.
  - Phase 4, Phase 5, Phase 6, Phase 7, and Phase 8 each have a concrete next development plan with goals, non-goals, file boundaries, TDD order, verification, and rollback.
  - The plan keeps kernel independent from Spring/JDBC/Web/OpenAPI parser/runtime adapters.
- Stop condition: Document refresh complete, self-check passes, and evidence is recorded. This does not mean the full AI Infra implementation is complete.
- Non-goals:
  - Do not implement new production code in this slice.
  - Do not revert existing uncommitted implementation work.
  - Do not claim AI Infra is fully complete.

## BaselineReadSetHint

- `docs/company-agent/Seahorse Agent 企业级 AI Infra 架构基线.md`
- `docs/company-agent/Seahorse Agent 企业级 AI Infra 测试基线.md`
- `docs/company-agent/Seahorse Agent 企业级 AI Infra 分阶段开发规划.md`
- `docs/company-agent/Seahorse Agent 与企业级 Agent 差距分析.md`
- `docs/company-agent/ai-infra-phases/00-architecture-baseline.md`
- `docs/company-agent/ai-infra-phases/04-context-db-resource-acl.md`
- `docs/company-agent/ai-infra-phases/05-connectors-credentials-sandbox.md`
- `docs/company-agent/ai-infra-phases/06-agent-factory-studio.md`
- `docs/company-agent/ai-infra-phases/07-multi-agent-a2a-mesh.md`
- `docs/company-agent/ai-infra-phases/08-production-hardening.md`
- `docs/company-agent/ai-infra-phases/99-current-implementation-handoff.md`
- `docs/aegis/work/2026-05-25-ai-infra-resource-acl-management/20-checkpoint.md`
- `docs/aegis/work/2026-05-25-ai-infra-openapi-connector-import/10-intent.md`

## ImpactStatementDraft

- Affected layer: documentation and Aegis work records only.
- Architecture surface: implementation sequencing, phase ownership, verification planning, rollback planning.
- Compatibility boundary: no code behavior changes; current uncommitted implementation remains untouched.
- Risk: future developers could misread the older handoff as current state if this refresh is not explicit.
