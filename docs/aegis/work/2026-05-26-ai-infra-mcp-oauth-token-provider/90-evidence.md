# AI Infra MCP OAuth Token Provider - Evidence

## EvidenceBundleDraft

### RED Evidence

- Kernel OAuth RED:
  - Command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=OAuthTokenRequestTests,OAuthTokenCacheKeyTests,OAuthCredentialProviderTests' test`
  - Exit status: 1 before production OAuth classes existed.
  - Failure class: missing `OAuthTokenRequest`, `OAuthTokenCacheKey`, `OAuthCredentialProvider`, and related model/port classes.
- MCP adapter RED:
  - Command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-mcp-http -am '-Dtest=McpHttpOAuthCredentialTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 1 before MCP OAuth config fields existed.
  - Failure class: `McpHttpAdapterProperties.Server` missing `tenantId`, `clientId`, `scopes`, `audience`, `resource`, `authorizationServerMetadataUrl`, and `protectedResourceMetadataUrl` getters/setters.

### GREEN Evidence

- Kernel OAuth focused regression:
  - Command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=CredentialMaterialTests,OAuthTokenRequestTests,OAuthTokenCacheKeyTests,OAuthCredentialProviderTests' test`
  - Exit status: 0.
  - Result: 9 tests, 0 failures, 0 errors, 0 skipped.
  - Covered: credential material redaction/validation, OAuth token request required fields, normalized cache key, cache miss/hit behavior, missing secretRef fail-closed behavior.
- MCP adapter focused regression:
  - Command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-mcp-http -am '-Dtest=McpHttpOAuthCredentialTests,StreamableHttpMcpClientCredentialTests,McpHttpAutoConfigurationCredentialTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 0 after rerunning serially.
  - Result: 8 tests, 0 failures, 0 errors, 0 skipped.
  - Covered: client credentials config fields, server config to `CredentialRequest.clientCredentials(...)`, OAuth bearer material injection, fail-closed missing `clientId`, existing static bearer behavior, existing secret-store provider fallback.
- Starter credential auto-configuration regression:
  - Command: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentCredentialAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Exit status: 0.
  - Result: 5 tests, 0 failures, 0 errors, 0 skipped.
  - Covered: JDBC secret store setup, MCP credential ordering compatibility, default OAuth credential provider composition, custom `CredentialProviderPort` backoff, default in-memory OAuth token cache.
- Diff hygiene:
  - Command: `git diff --check`
  - Exit status: 0.
  - Output: Windows line-ending warnings only; no whitespace errors.

### Debug Evidence

- One MCP regression run failed with `StreamableHttpMcpClient.class` reported as truncated.
- Root cause evidence: a second Maven command was running against a reactor that also compiled `seahorse-agent-adapter-mcp-http`; the failure disappeared after deleting only `seahorse-agent-adapter-mcp-http\target` and rerunning the same MCP command serially.
- Safety boundary: only generated `target` output was removed after verifying the resolved path was inside the current worktree.

## Evidence Boundary

This evidence verifies Phase 5A MCP OAuth client credentials provider model, MCP adapter wiring, and starter composition. It does not verify a real OAuth HTTP token endpoint adapter, browser authorization-code UI, dynamic client registration, Redis/distributed token cache, OpenAPI operation execution, or Sandbox Runtime.
