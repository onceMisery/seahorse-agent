# AI Infra MCP static bearer - Checkpoint

- Task ID: 2026-05-25-ai-infra-mcp-static-bearer
- Current todo: MCP `STATIC_BEARER` minimal loop is merged to root `main`; continue with the next AI-Infra gap in a new slice.
- Active slice: Phase 5 MCP static bearer credential resolution and header injection.
- Blocked on: none
- Next step: Continue with the next AI-Infra gap in a new isolated slice.

## DriftCheckDraft

- Scope status: Still within Phase 5 MCP static bearer minimal loop; OAuth/OpenAPI/Sandbox/Vault implementation not added.
- Compatibility status: Existing unauthenticated MCP configuration remains default; old MCP client constructor remains compatible; custom `CredentialProviderPort` beans override the default composition.
- Retirement status: No existing runtime path retired; this adds a credential-enabled path while preserving `NONE` default.
- New risk signals:
- New cross-module credential port types and MCP auto-configuration path; covered by kernel, client, and auto-configuration tests.
- Advisory decision: continue

## Checkpoint Update

- Current todo: MCP `STATIC_BEARER` minimal loop is merged and verified on root `main`.
- Active slice: Phase 5 MCP static bearer credential resolution and header injection.
- Completed todos:
- Merged `main` into branch; wrote RED tests; added credential port/domain types; added MCP server `authType/clientSecretRef`; added bearer header injection; added default `SecretStorePort` to `CredentialProviderPort` composition; ran focused regression.
- Evidence refs:
- focused-regression: Maven focused and broader related regressions exited 0 / BUILD SUCCESS in the feature worktree and on root `main`.
- diff-check: `git diff --check` exited 0 with only LF/CRLF warnings.
- Blocked on: none
- Next step: Continue with the next AI-Infra gap in a new isolated slice.
