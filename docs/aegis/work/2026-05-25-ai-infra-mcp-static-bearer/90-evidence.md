# AI Infra MCP static bearer - Evidence

## EvidenceBundleDraft

Verification:
- Initial RED check failed as expected because credential abstractions did not exist:
  `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-adapter-mcp-http -am '-Dtest=CredentialMaterialTests,StreamableHttpMcpClientCredentialTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- Focused credential/client regression passed:
  `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-adapter-mcp-http -am '-Dtest=CredentialMaterialTests,StreamableHttpMcpClientCredentialTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- Focused MCP regression passed:
  `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-adapter-mcp-http -am '-Dtest=CredentialMaterialTests,StreamableHttpMcpClientCredentialTests,McpHttpAutoConfigurationCredentialTests,NativeMcpEnabledConditionTests,NativeMcpToolRegistryTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- Broader related regression passed:
  `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-adapter-mcp-http,seahorse-agent-spring-boot-starter -am '-Dtest=CredentialMaterialTests,StreamableHttpMcpClientCredentialTests,McpHttpAutoConfigurationCredentialTests,NativeMcpEnabledConditionTests,NativeMcpToolRegistryTests,SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- Root `main` focused regression passed after fast-forward merge:
  `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-adapter-mcp-http,seahorse-agent-spring-boot-starter -am '-Dtest=CredentialMaterialTests,StreamableHttpMcpClientCredentialTests,McpHttpAutoConfigurationCredentialTests,NativeMcpEnabledConditionTests,NativeMcpToolRegistryTests,SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `git diff --check` exited 0. Git only reported LF/CRLF conversion warnings.
- Aegis proof bundle assembled:
  `docs/aegis/work/2026-05-25-ai-infra-mcp-static-bearer/proof-bundle.md`

Workspace check:
- `python C:\Users\miracle\.codex\aegis\scripts\aegis-workspace.py check --root D:\code\seahorse-agent\.worktrees\ai-infra-mcp-static-bearer` exited 1 because pre-existing historical `docs/aegis` records are not indexed or use older JSON sidecar shapes.
- The failing paths are outside this MCP static bearer work directory.

Changed production surfaces:
- Kernel credential ports/domain:
  `CredentialAuthType`, `SecretValue`, `CredentialMaterial`, `CredentialRequest`, `SecretStorePort`, `CredentialProviderPort`, `SecretStoreCredentialProvider`.
- MCP HTTP adapter:
  `McpHttpAdapterProperties.Server.authType`, `McpHttpAdapterProperties.Server.clientSecretRef`.
- MCP HTTP client:
  Optional `CredentialMaterial` constructor and `Authorization: Bearer ...` injection for `STATIC_BEARER`.
- MCP HTTP auto-configuration:
  default `SecretStoreCredentialProvider` when a `SecretStorePort` exists and no custom `CredentialProviderPort` is present; per-server credential material resolution during remote discovery.

Changed tests:
- `CredentialMaterialTests`
- `StreamableHttpMcpClientCredentialTests`
- `McpHttpAutoConfigurationCredentialTests`

Safety evidence:
- `SecretValue.toString()` and `CredentialMaterial.toString()` do not contain the raw token.
- Production logs for credential resolution skip/failure include server name, auth type, and named reason only.
- Raw token is revealed only at the HTTP request header injection boundary.

Completion evidence:
- Feature branch commit: `06cab7d8 feat(mcp): add static bearer credentials`.
- Feature branch merged latest `main`: `01610084 merge: main into mcp static bearer`.
- Root `main` fast-forwarded to `01610084`.
- Root `main` focused regression exited 0 / BUILD SUCCESS.
