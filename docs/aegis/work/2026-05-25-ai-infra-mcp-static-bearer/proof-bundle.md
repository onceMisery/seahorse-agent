# Proof Bundle - 2026-05-25-ai-infra-mcp-static-bearer

## Method Pack Boundary

This proof bundle is an advisory Aegis Method Pack record. It does not determine evidence sufficiency, produce authoritative `GateDecision`, or grant `completion authority`.

## Task Intent

- Requested outcome: Close the Phase 5 MCP STATIC_BEARER credential gap with a minimal credential port/domain and MCP HTTP adapter integration.
- Scope: Kernel credential ports/domain, MCP HTTP adapter configuration, request header injection, Spring auto-configuration, focused tests, and Aegis work records.

## Impact

- Compatibility boundary: Existing MCP servers remain unauthenticated by default with CredentialAuthType.NONE; old StreamableHttpMcpClient constructor remains available.
- Non-goals:
- No OAuth2 refresh, challenge handling, client credentials, or user-delegated token flow.
- No OpenAPI connector import.
- No Sandbox runtime.
- No encrypted database vault table or remote secret manager adapter.
- No Tool Gateway or MCP protocol redesign.

## Evidence Bundle Refs

- docs/aegis/work/2026-05-25-ai-infra-mcp-static-bearer/evidence-bundle-draft-focused-regression.json

## Drift Check

- Scope status: Still within Phase 5 MCP static bearer minimal loop; OAuth/OpenAPI/Sandbox/Vault implementation not added.
- Compatibility status: Existing unauthenticated MCP configuration remains default; old MCP client constructor remains compatible; custom CredentialProviderPort beans override the default composition.
- Retirement status: No existing runtime path retired; this adds a credential-enabled path while preserving NONE default.
- Advisory decision: continue
