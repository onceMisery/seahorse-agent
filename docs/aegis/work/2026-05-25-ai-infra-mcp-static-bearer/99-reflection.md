# AI Infra MCP static bearer - Reflection

## Reflection

Goal:
- Close the Phase 5 MCP `STATIC_BEARER` credential gap without expanding into OAuth, OpenAPI, Sandbox, or durable vault implementation.

Repair track:
- Root issue: Phase 5 required bearer token injection and secretRef-based credential usage, but the MCP HTTP adapter only supported unauthenticated requests and had no credential abstraction boundary.
- Canonical owner: `SecretStorePort` owns secret lookup; `CredentialProviderPort` owns credential material resolution; MCP HTTP adapter owns request header application for resolved material.
- Minimal change: add credential value objects and ports in kernel, compose a default static bearer provider from `SecretStorePort`, add MCP server auth configuration, and inject bearer headers in `StreamableHttpMcpClient`.
- Compatibility boundary: existing MCP configs default to `CredentialAuthType.NONE`; existing client constructor is preserved; custom credential providers override the default composition.

Retirement track:
- No existing behavior was retired.
- Direct credential-in-config remains unsupported for this slice; only `clientSecretRef` is introduced for MCP server configuration.
- OAuth2, OpenAPI connector import, Sandbox runtime, and durable vault storage remain deferred Phase 5 slices.

Risk / Unknown:
- This slice does not implement token refresh or OAuth challenges.
- No concrete production secret store adapter is introduced; applications must provide `SecretStorePort` or a custom `CredentialProviderPort`.
- Aegis workspace global check is known to fail on historical records outside this work directory with older index/JSON shapes.

Decision:
- Focused and broader related regressions passed in the feature worktree and after fast-forward merge on root `main`.
- This MCP static bearer slice is complete; continue with the next AI-Infra gap in a separate isolated slice.

Method Pack output does not grant completion authority.
