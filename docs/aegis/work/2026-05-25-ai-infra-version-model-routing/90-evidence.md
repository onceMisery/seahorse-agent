# EvidenceBundleDraft

## Current State

- Worktree: `D:\code\seahorse-agent\.worktrees\ai-infra-version-model-routing`
- Branch: `codex/ai-infra-version-model-routing`
- Base: root `main` after Agent run retry merge (`dc07f584`)

## Evidence To Add

## Implementation Evidence

- `StreamChatCommand` carries optional `agentId` and `versionId` while preserving legacy constructors.
- `ChatRequest` carries optional `modelId` and keeps existing sampling/tool accessors.
- `AgentLoopRequest` carries optional `modelId`; `KernelAgentLoop` forwards it to model requests.
- `KernelChatInboundService` resolves registered Agent versions through `AgentDefinitionRepositoryPort`, parses minimal `modelConfigJson`, and builds model-scoped `AgentLoopRequest`.
- `AgentDefinitionRepositoryPort.findVersion` is an explicit immutable version lookup contract; JDBC implements `agent_id + version_id` lookup.
- `KernelAgentRunService` rejects an explicit missing registered version before persisting a run.
- `OpenAiCompatibleModelAdapter` uses `ChatRequest.modelId` and conditionally writes `top_k` and `thinking`.
- Web chat API accepts optional `agentId` and `versionId` query parameters.

## Verification Commands

1. Main merge check:

```powershell
git merge main
```

Result: `Already up to date`; `HEAD` and `main` were both `dc07f584`.

2. Diff hygiene:

```powershell
git diff --check
```

Result: exit 0; Git reported only LF/CRLF conversion warnings for touched files.

3. Focused regression after implementation:

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-tests,seahorse-agent-adapter-ai-openai-compatible,seahorse-agent-adapter-repository-jdbc,seahorse-agent-spring-boot-starter,seahorse-agent-adapter-web -am '-Dtest=ChatRequestToolsTests,ChatModeTests,KernelAgentLoopTests,KernelChatAgentRunStoreTests,OpenAiCompatibleStreamingChatToolsTests,JdbcAgentDefinitionRepositoryAdapterTests,SeahorseWebApiContractTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD SUCCESS`.

4. Focused regression after review fix:

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-tests,seahorse-agent-adapter-ai-openai-compatible,seahorse-agent-adapter-repository-jdbc,seahorse-agent-spring-boot-starter,seahorse-agent-adapter-web -am '-Dtest=KernelAgentRunServiceTests,KernelAgentDefinitionServiceTests,ChatRequestToolsTests,ChatModeTests,KernelAgentLoopTests,KernelChatAgentRunStoreTests,OpenAiCompatibleStreamingChatToolsTests,JdbcAgentDefinitionRepositoryAdapterTests,SeahorseWebApiContractTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD SUCCESS`; reactor summary reported all selected modules successful.

## Code Review

- Advisory review found one P3 issue: default `AgentDefinitionRepositoryPort.findVersion()` only filtered `latestVersion()` and could reject explicit older immutable versions for custom repositories.
- Fix applied: removed default fallback and made `findVersion(agentId, versionId)` an explicit port method; updated JDBC and all test doubles.
- Reviewer found no blocking or important issues.

## DriftCheckDraft

- Original task intent still served: yes.
- Compatibility boundary: held; kernel still depends on ports/domain abstractions and framework-neutral libraries already present in kernel.
- New owner/fallback/adapter: repository port contract extended; no duplicate owner introduced.
- Retirement track: weak default version lookup fallback retired.
- Residual risk: invalid published `modelConfigJson` currently logs and falls back to defaults for compatibility; publish-time validation remains future work.
- Decision: continue-to-commit.
