# AI Infra MCP OAuth Token Provider - Checkpoint

- Task ID: 2026-05-26-ai-infra-mcp-oauth-token-provider
- Current todo: Phase 5A MCP OAuth Token Provider focused implementation completed and verified.
- Active slice: Phase 5A OAuth MCP adapter and starter wiring GREEN
- Completed todos:
  - Wrote kernel OAuth request/cache/material/provider tests in `seahorse-agent-kernel`.
  - Confirmed kernel OAuth RED before adding production OAuth credential model/provider code.
  - Implemented kernel OAuth credential request, token request, token cache key, in-memory cache, token port/cache port, TTL policy, and `OAuthCredentialProvider`.
  - Wrote MCP adapter OAuth tests in `McpHttpOAuthCredentialTests`.
  - Confirmed MCP adapter RED: `McpHttpAdapterProperties.Server` lacked client credentials fields.
  - Implemented MCP client credentials config fields, fail-closed validation, `CredentialRequest.clientCredentials(...)` mapping, and bearer material injection for OAuth-resolved credentials.
  - Extended starter credential auto-configuration with default `InMemoryOAuthTokenCachePort` and `OAuthCredentialProvider` composition.
  - Verified custom `CredentialProviderPort` still backs off default composition.
  - Ran Phase 5A focused regression and `git diff --check`.
- Evidence refs:
  - `docs/aegis/work/2026-05-26-ai-infra-mcp-oauth-token-provider/90-evidence.md`
- Blocked on: none
- Next step: Continue the AI Infra roadmap with the next unfinished slice; Phase 5A is not the full AI Infra completion.

## ResumeStateHint

- Current worktree: `D:\code\seahorse-agent\.worktrees\ai-infra-phase-design-plans`
- Current branch: `codex/ai-infra-phase-design-plans`
- Phase 5A status: focused implementation verified.
- Important caveat: one MCP test run failed with a truncated class file after Maven commands were run in parallel against the same module. The module `target` directory was safely removed and the same MCP command passed when rerun serially.
- Suggested next slice: inspect `docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md` section 7 and section 12, then select the next smallest TDD slice toward Phase 5C Sandbox Runtime or Phase 4 production hardening.

## DriftCheckDraft

- Scope status: Within Phase 5A; no authorization-code UI, dynamic client registration, automatic scope challenge retry, remote Agent mesh, or real OpenAPI operation execution added.
- Compatibility status: Existing `NONE` and `STATIC_BEARER` MCP credentials remain covered by existing regression tests; custom `CredentialProviderPort` override remains covered.
- Architecture status: Aligned with kernel-to-port dependency direction. Kernel owns credential/OAuth domain and ports; MCP adapter owns config mapping and HTTP header injection; starter owns conditional composition.
- Retirement status: Old static-bearer-only MCP injection is widened to bearer-material semantics; static bearer path is retained and covered.
- New risk signals:
  - A real OAuth HTTP token acquisition adapter is still absent; `OAuthTokenPort` is only a kernel abstraction in this slice.
  - Token cache is in-memory default; production Redis/distributed cache remains a later adapter concern.
- Advisory decision: continue
