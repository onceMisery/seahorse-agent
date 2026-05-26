# AI Infra MCP OAuth Token Provider - Intent

## TaskIntentDraft

- Requested outcome: Implement Phase 5A MCP OAuth client credentials token provider with token cache and MCP bearer material injection.
- Goal: MCP servers can use CLIENT_CREDENTIALS credentials through kernel OAuth ports and MCP adapter wiring without exposing raw token or client secret.
- Success evidence:
- OAuth kernel tests, MCP adapter OAuth credential tests, starter credential auto-configuration tests, and git diff check pass.
- Stop condition: Done when Phase 5A focused regression passes; blocked if existing credential contract cannot be extended compatibly; scope-exceeded if authorization-code UI, dynamic client registration, scope auto-retry, or real connector execution is required.
- Non-goals:
- No browser authorization-code UI.
- No dynamic client registration.
- No automatic scope challenge retry.
- No real OpenAPI operation execution.
- Scope: Kernel credential/OAuth models and ports, OAuthCredentialProvider, MCP HTTP adapter properties and credential request mapping, starter credential wiring, focused tests, and Aegis records.
- Change kinds:
- feature
- Risk hints:
- Cross-module credential contract and adapter auto-configuration change; raw secret/token leakage risk.

## BaselineReadSetHint

- docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md#112-phase-5-开发卡mcp-oauth-token-provider-最小闭环
- docs/company-agent/ai-infra-phases/05-connectors-credentials-sandbox.md
- docs/aegis/work/2026-05-25-ai-infra-mcp-static-bearer/

## ImpactStatementDraft

- Compatibility boundary: Existing NONE and STATIC_BEARER MCP credentials continue to work; old StreamableHttpMcpClient constructors remain compatible; custom CredentialProviderPort beans override default composition.
- Affected layers:
- kernel
- mcp-http-adapter
- spring-auto-configuration
- Owners:
- CredentialProviderPort owns final material resolution; OAuthTokenPort owns token acquisition; OAuthTokenCachePort owns token cache semantics; MCP client only owns HTTP header injection.
- Invariants:
- Kernel depends only on domain objects and ports, not Spring, JDBC, Web, or OkHttp.
- Raw tokens and client secrets must not appear in toString, logs, trace, audit payload, ToolCatalog metadata, or prompt material.
- Non-goals:
- No browser authorization-code UI.
- No dynamic client registration.
- No automatic scope challenge retry.
- No real OpenAPI operation execution.

These records are Method Pack drafts / hints, not authoritative runtime decisions.
