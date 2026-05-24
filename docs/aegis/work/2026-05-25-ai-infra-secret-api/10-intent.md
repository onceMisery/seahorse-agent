# AI Infra Secret API - Intent

## TaskIntentDraft

- Requested outcome: Close the Phase 5 credential vault persistence and secret creation API gap without repeating already merged approval/checkpoint/MCP credential work.
- Goal: Allow administrators to create encrypted secret references through a minimal API, store only ciphertext durably, and let existing SecretStorePort consumers resolve secrets by secretRef.
- Success evidence:
- Kernel, JDBC, Web, Starter, schema-alignment, and MCP credential regressions pass; API responses return SecretMetadata only; database stores encrypted_value rather than plaintext; branch is committed and merged to main.
- Stop condition: Done when the minimal secret write/read path, API, JDBC schema, auto-configuration, Aegis records, and focused verification are complete; blocked if current credential contracts conflict; scope-exceeded if OAuth, remote vault, rotation workflow, OpenAPI import, or sandbox runtime becomes required.
- Non-goals:
- No OAuth2 refresh or delegated token flow.
- No OpenAPI connector import.
- No sandbox runtime.
- No remote secret manager adapter.
- No secret rotation workflow beyond schema carrying rotated_at.
- Scope: Kernel credential management port/service, JDBC encrypted secret store adapter, Web POST /api/secrets, Spring auto-configuration, SQL schema alignment, focused tests, and Aegis work records.
- Change kinds:
- feature
- Risk hints:
- Cross-module public API, persistence schema, and credential security boundary change.

## BaselineReadSetHint

- docs/company-agent/ai-infra-phases/05-connectors-credentials-sandbox.md
- docs/aegis/work/2026-05-25-ai-infra-mcp-static-bearer/99-reflection.md

## ImpactStatementDraft

- Compatibility boundary: Existing SecretStorePort#getSecret contract remains read-only; existing MCP static bearer resolution continues through SecretStorePort; unauthenticated MCP remains default.
- Affected layers:
- kernel
- jdbc-repository-adapter
- web-adapter
- spring-auto-configuration
- database-schema
- Owners:
- SecretManagementInboundPort owns admin secret creation orchestration.
- SecretWritePort owns secret write persistence contract.
- SecretStorePort remains read-only lookup for runtime credential consumers.
- Invariants:
- Kernel depends on credential ports/domain only, not Spring/JDBC/Web.
- API responses and string rendering never include plaintext secrets.
- JDBC storage writes encrypted_value only when an AES-GCM key is configured.
- Non-goals:
- No OAuth2 refresh or delegated token flow.
- No OpenAPI connector import.
- No sandbox runtime.
- No remote secret manager adapter.
- No secret rotation workflow beyond schema carrying rotated_at.

These records are Method Pack drafts / hints, not authoritative runtime decisions.
