# AI Infra Secret API - Checkpoint

- Task ID: 2026-05-25-ai-infra-secret-api
- Current todo: Finish Phase 5 minimal Credential Vault persistence and POST /api/secrets slice.
- Active slice: Credential Vault minimal persistence and secret creation API.
- Blocked on: none
- Next step: Record evidence after focused regression and diff checks.

## DriftCheckDraft

- Scope status: Still inside Phase 5 minimal Credential Vault persistence and POST /api/secrets; OAuth, OpenAPI import, sandbox runtime, remote vault, and rotation workflow remain non-goals.
- Compatibility status: SecretStorePort remains read-only; SecretWritePort adds write contract separately; existing MCP SecretStorePort -> CredentialProviderPort static bearer path remains covered by regression.
- Retirement status: No existing runtime path retired; this adds a durable JDBC default only when an AES-GCM key is configured and backs off for custom stores/providers.
- New risk signals:
- Credential encryption key management remains an application deployment responsibility.
- Advisory decision: continue

## Checkpoint Update

- Current todo: Complete final verification, review feedback, commit, and merge the Phase 5 Secret API slice.
- Active slice: Final verification and integration.
- Completed todos:
- Created isolated worktree codex/ai-infra-secret-api and confirmed main is merged.
- Added kernel SecretMetadata, SecretManagementInboundPort, SecretCreateCommand, SecretWritePort, and KernelSecretManagementService.
- Added JDBC AES-GCM cipher and JdbcSecretStoreAdapter implementing SecretStorePort and SecretWritePort.
- Added POST /api/secrets Web API returning metadata only.
- Added Spring credential auto-configuration gated by seahorse-agent.credentials.jdbc.aes-key-base64.
- Added sa_secret_ref to runtime SQL and resources/database/seahorse_init.sql with schema-alignment regression.
- Focused credential and MCP credential regressions passed.
- Evidence refs:
- docs/aegis/work/2026-05-25-ai-infra-secret-api/evidence-bundle-draft-focused-regression.json
- docs/aegis/work/2026-05-25-ai-infra-secret-api/evidence-bundle-draft-mcp-credential-regression.json
- Blocked on: none
- Next step: Run diff checks, workspace bundle/check, review feedback, then commit and merge to main.

## Checkpoint Update

- Current todo: Review independent feedback, run final diff checks, commit, and merge the Phase 5 Secret API slice.
- Active slice: Final review and integration.
- Completed todos:
- Created isolated worktree codex/ai-infra-secret-api and confirmed main is merged.
- Added kernel SecretMetadata, SecretManagementInboundPort, SecretCreateCommand, SecretWritePort, and KernelSecretManagementService.
- Added JDBC AES-GCM cipher and JdbcSecretStoreAdapter implementing SecretStorePort and SecretWritePort.
- Added POST /api/secrets Web API returning metadata only.
- Added Spring credential auto-configuration gated by seahorse-agent.credentials.jdbc.aes-key-base64.
- Added sa_secret_ref to runtime SQL and resources/database/seahorse_init.sql with schema-alignment regression.
- Focused credential and MCP credential regressions passed.
- Affected module regression passed across kernel, JDBC, Web, Starter, and -am adapter modules.
- Evidence refs:
- docs/aegis/work/2026-05-25-ai-infra-secret-api/evidence-bundle-draft-focused-regression.json
- docs/aegis/work/2026-05-25-ai-infra-secret-api/evidence-bundle-draft-mcp-credential-regression.json
- docs/aegis/work/2026-05-25-ai-infra-secret-api/evidence-bundle-draft-affected-modules-regression.json
- Blocked on: none
- Next step: Review independent feedback, rerun diff checks and proof bundle, then commit and merge to main.

## DriftCheckDraft

- Scope status: Still inside Phase 5 minimal Credential Vault and secret creation API; no OAuth, remote vault, workflow engine, OpenAPI import, sandbox runtime, or rotation workflow added.
- Compatibility status: Kernel still depends only on ports/domain; SecretStorePort remains read-only; SecretWritePort remains separate; MCP static bearer path resolves through SecretStorePort with auto-configuration order regression covered.
- Retirement status: No existing runtime path retired; JDBC credential vault is enabled only when aes-key-base64 is configured and custom SecretStorePort/SecretWritePort beans still back off the default.
- New risk signals:
- Aegis workspace global check may still report historical unrelated records outside this work item.
- Advisory decision: continue

## Checkpoint Update

- Current todo: Commit the Phase 5 Secret API slice, merge to main, and verify main.
- Active slice: Final integration and main-branch verification.
- Completed todos:
- Confirmed git merge main is already up to date in codex/ai-infra-secret-api.
- Implemented shared metadata plaintext guard in SecretCreateCommand and SecretWriteCommand.
- Fixed MCP HTTP auto-configuration ordering after credential auto-configuration.
- Added Web regression for rejecting metadata that contains the plaintext secret.
- Affected module regression passed for kernel, JDBC, Web, MCP HTTP, starter, and dependencies.
- Evidence refs:
- review-fix-affected-modules-regression
- Blocked on: none
- Next step: Run final diff/Aegis checks, stage, commit, merge to main, and rerun focused main verification.
