# AI Infra MCP static bearer - Intent

## TaskIntentDraft

- Requested outcome: Close the Phase 5 MCP `STATIC_BEARER` credential gap with a minimal credential port/domain and MCP HTTP adapter integration.
- Goal: Allow configured MCP HTTP servers to resolve a bearer token by `clientSecretRef` and inject it into outbound MCP requests without logging or rendering the raw token.
- Success evidence:
- Credential value objects redact raw token string rendering.
- MCP HTTP client sends `Authorization: Bearer <token>` when supplied with `CredentialMaterial.STATIC_BEARER`.
- MCP HTTP auto-configuration composes `SecretStorePort -> CredentialProviderPort` and backs off for custom credential providers.
- Focused kernel/MCP/starter regressions pass; diff check passes; branch is committed and merged back to root `main`.
- Stop condition: Done when the minimal static bearer loop is implemented, verified, committed, merged to `main`, and no token-leak path is found in production logging/string rendering; blocked if existing MCP adapter contracts conflict; scope-exceeded if OAuth, OpenAPI import, Sandbox runtime, or database vault implementation becomes required.
- Non-goals:
- No OAuth2 refresh, challenge handling, client credentials, or user-delegated token flow.
- No OpenAPI connector import.
- No Sandbox runtime.
- No encrypted database vault table or remote secret manager adapter.
- No Tool Gateway or MCP protocol redesign.
- Scope: Kernel credential ports/domain, MCP HTTP adapter configuration, request header injection, Spring auto-configuration, focused tests, and Aegis work records.
- Change kinds:
- feature
- Risk hints:
- Cross-module outbound port contract and adapter auto-configuration change.

## BaselineReadSetHint

- docs/company-agent/ai-infra-phases/05-connectors-credentials-sandbox.md
- docs/company-agent/ai-infra-phases/99-current-implementation-handoff.md
- Existing MCP HTTP adapter code under `seahorse-agent-adapter-mcp-http`.

## ImpactStatementDraft

- Compatibility boundary: Existing MCP servers remain unauthenticated by default with `CredentialAuthType.NONE`; old `StreamableHttpMcpClient` constructor remains available.
- Affected layers:
- kernel
- mcp-http-adapter
- spring-auto-configuration
- Owners:
- `SecretStorePort` owns secret lookup contract.
- `CredentialProviderPort` owns credential material resolution.
- `StreamableHttpMcpClient` owns HTTP header injection for already-resolved credential material.
- Invariants:
- Kernel depends only on credential abstractions and domain value objects.
- MCP adapter depends on kernel ports, not on JDBC, Web, or concrete secret storage.
- Raw token rendering is explicit through `SecretValue.reveal()` and is not included in `toString()`.
- Non-goals:
- No OAuth2 refresh, challenge handling, client credentials, or user-delegated token flow.
- No OpenAPI connector import.
- No Sandbox runtime.
- No encrypted database vault table or remote secret manager adapter.
- No Tool Gateway or MCP protocol redesign.

These records are Method Pack drafts / hints, not authoritative runtime decisions.
