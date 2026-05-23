# TaskIntentDraft

Requested outcome: continue the AI-Infra implementation from `docs/company-agent/ai-infra-phases/99-current-implementation-handoff.md`, using an isolated worktree and preserving the existing hexagonal architecture.

Scope: implement the next recommended Approval query/decision API slice first, then continue through the remaining handoff items only after the preceding slice is verified.

Non-goals for the active Approval slice: no workflow engine, no remote agent mesh, no direct tool execution from approval APIs, and no cleartext secret persistence.

Baseline refs:

- `docs/company-agent/ai-infra-phases/99-current-implementation-handoff.md`
- `docs/company-agent/ai-infra-phases/02-tool-gateway-policy-engine.md`
- `docs/company-agent/ai-infra-phases/03-durable-runtime-hitl.md`
- `CLAUDE.md`
- Existing Approval request domain, JDBC repository, Web controller, and Spring auto-configuration patterns.

ImpactStatementDraft: adds isolated Approval management ports/services/adapters/controllers while keeping kernel dependent on ports only and JDBC/Web/Spring wiring outside kernel.

