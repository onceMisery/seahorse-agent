# AI Infra Sandbox Runtime Integration - Intent

- Requested outcome: Continue the active AI Infra goal by implementing the Phase 5 Sandbox persistence/Web/starter slice from section 14.3 of `docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md`.
- Scope: Add sandbox repository ports, JDBC persistence, Web API, starter wiring, and focused tests while keeping the default runtime fail-closed.
- Non-goals:
  - Do not implement a real shell/browser/code interpreter runtime.
  - Do not implement remote Agent mesh or Phase 8 Audit Ledger in this slice.
  - Do not broaden OpenAPI connector behavior beyond existing enable path unless needed for sandbox integration.
- Baseline refs:
  - `docs/company-agent/ai-infra-phases/05-connectors-credentials-sandbox.md`
  - `docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md`, section 14.3
  - Existing sandbox kernel domain/application files under `seahorse-agent-kernel/.../agent/sandbox`
- Impact statement:
  - Kernel gains small sandbox persistence/query ports and orchestration hooks only.
  - JDBC/Web/starter add adapters around existing kernel contracts.
  - Default behavior remains unsupported/fail-closed unless a custom `SandboxRuntimePort` is provided.
