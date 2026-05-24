# AI Infra MCP static bearer - Checkpoint

- Task ID: 2026-05-25-ai-infra-mcp-static-bearer
- Current todo: Finalize MCP `STATIC_BEARER` minimal loop, update evidence, run verification, commit, and merge.
- Active slice: Phase 5 MCP static bearer credential resolution and header injection.
- Blocked on: none
- Next step: Run final diff checks, commit branch, merge back to root `main`, and rerun focused regression on `main`.

## DriftCheckDraft

- Scope status: Still within Phase 5 MCP static bearer minimal loop; OAuth/OpenAPI/Sandbox/Vault implementation not added.
- Compatibility status: Existing unauthenticated MCP configuration remains default; old MCP client constructor remains compatible; custom `CredentialProviderPort` beans override the default composition.
- Retirement status: No existing runtime path retired; this adds a credential-enabled path while preserving `NONE` default.
- New risk signals:
- New cross-module credential port types and MCP auto-configuration path; covered by kernel, client, and auto-configuration tests.
- Advisory decision: continue

## Checkpoint Update

- Current todo: Commit branch, merge back to root `main`, and rerun focused regression on `main`.
- Active slice: Phase 5 MCP static bearer credential resolution and header injection.
- Completed todos:
- Merged `main` into branch; wrote RED tests; added credential port/domain types; added MCP server `authType/clientSecretRef`; added bearer header injection; added default `SecretStorePort` to `CredentialProviderPort` composition; ran focused regression.
- Evidence refs:
- focused-regression: Maven focused regression exited 0 / BUILD SUCCESS.
- diff-check: `git diff --check` exited 0 with only LF/CRLF warnings.
- Blocked on: none
- Next step: Commit branch and merge to root `main`.
