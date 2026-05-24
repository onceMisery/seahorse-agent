# TodoCheckpointDraft

Current todo:
- [x] Merge latest `main` before new worktree creation.
- [x] Create isolated worktree `D:\code\seahorse-agent\.worktrees\ai-infra-version-model-routing`.
- [x] Identify next non-duplicate gap: published version model config is not replayed into runtime model calls.
- [x] Save plan and intent records.
- [x] Write RED tests.
- [x] Implement minimal model config routing.
- [x] Run focused regression.
- [ ] Commit and merge slice back to root `main`.

Active slice: Phase 1/2 version-scoped model routing into Agent runtime.

Completed todos:
- Avoided repeating approval management, checkpoint, resume, ContextPack, and retry work already in `main`.
- Confirmed `AgentVersion.modelConfigJson` is persisted as String.
- Confirmed `KernelAgentLoop` builds `ChatRequest` without a model id today.
- Confirmed OpenAI-compatible streaming calls currently pass `null` as model id.
- Added version-scoped `agentId/versionId/modelId` flow from Web command to kernel agent loop and OpenAI-compatible payloads.
- Tightened `AgentDefinitionRepositoryPort.findVersion` to an explicit immutable version lookup contract after review feedback.
- Direct Agent run start now rejects an explicit missing registered version before creating a run.

Evidence refs:
- Code read: `AgentVersion`, `KernelChatInboundService`, `KernelAgentLoop`, `ChatRequest`, `OpenAiCompatibleModelAdapter`.
- `git merge main` in the worktree returned `Already up to date`; `HEAD` and `main` were both `dc07f584`.
- `git diff --check` exited 0; Git only reported LF/CRLF conversion warnings.
- Focused Maven regression exited 0 after the review fix:
  `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-tests,seahorse-agent-adapter-ai-openai-compatible,seahorse-agent-adapter-repository-jdbc,seahorse-agent-spring-boot-starter,seahorse-agent-adapter-web -am '-Dtest=KernelAgentRunServiceTests,KernelAgentDefinitionServiceTests,ChatRequestToolsTests,ChatModeTests,KernelAgentLoopTests,KernelChatAgentRunStoreTests,OpenAiCompatibleStreamingChatToolsTests,JdbcAgentDefinitionRepositoryAdapterTests,SeahorseWebApiContractTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`.
- Code review agent finding: default `findVersion` fallback could reject older immutable versions for non-JDBC adapters; fixed by making `findVersion` an explicit port method.

Blocked-on items:
- None.

Next step:
- Commit the feature branch, then inspect root `main` dirty state before any merge back.

ResumeStateHint:
- Continue in `D:\code\seahorse-agent\.worktrees\ai-infra-version-model-routing`.
- Do not edit unrelated root dirty files in `D:\code\seahorse-agent`.

DriftCheckDraft:
- Scope: still AI-Infra Phase 1/2 runtime replay of published version metadata.
- Compatibility: existing ports remain replaceable; kernel still depends on ports/domain/Jackson already present in kernel, not Spring/JDBC/Web.
- New owner/adapter: no new owner; existing model request contract and repository port contract extended.
- Retirement: weak default `findVersion` fallback removed in favor of explicit adapter contract.
- Decision: continue-to-commit.
