# TodoCheckpointDraft

Current todo:
- [x] Confirm current `main` includes ContextPack foundation/runtime prompt consumption.
- [x] Confirm `codex/ai-infra-contextpack-producer` worktree is clean and based on `2b73c0e4`.
- [x] Read Phase 4 requirements and relevant chat/runtime code.
- [x] Write RED tests for ContextPack production in RAG and Agent mode.
- [x] Implement assembler and wiring.
- [x] Merge current local `main` (`9df495cc`) into the producer worktree.
- [x] Run focused and related regression verification.

Active slice: ContextPack production wiring completion candidate.

Completed todos:
- Avoided `codex/ai-infra-phases-gap` because it has no ahead commits and would delete already merged ContextPack files.
- Added `ContextPackRuntimeAssembler` to map user input, loaded memory, and RAG chunks into `ContextBuildItemCandidate` values.
- Wired optional `ContextPackBuilderInboundPort` into `KernelChatPipeline`, `KernelChatInboundService`, and Spring chat auto-configuration.
- Preserved fallback behavior when no builder exists or builder invocation fails.
- Added `ContextResourceType` enum and `ResourceRef` overload to avoid scattering resource-type literals in application assembly.

Evidence refs:
- `git status --short --branch` in producer worktree: clean at start.
- `git diff --name-status main..codex/ai-infra-phases-gap`: branch would remove current ContextPack foundation files.
- RED evidence before implementation: `KernelChatPipelineTests#shouldExposeProducedContextPackToRagPromptAssembly` failed with `PromptContext.contextPack == null`.
- `git merge main` fast-forwarded producer branch from `2b73c0e4` to `9df495cc`; `docs/aegis/INDEX.md` conflict resolved by retaining both entries.
- Focused verification: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=KernelChatPipelineTests#shouldStreamRagResponseWithMcpSamplingParameters+shouldExposeProducedContextPackToRagPromptAssembly+shouldFallbackToLegacyPromptContextWhenContextPackBuilderFails,KernelChatInboundServiceAgentModeTests#shouldRouteAgentModeToKernelAgentLoopWithoutExecutingRagPipeline+shouldBuildContextPackForAgentModeBeforeExecutingAgentLoop+shouldExecuteAgentLoopWithLegacyMemoryContextWhenContextPackBuilderFails' '-Dsurefire.failIfNoSpecifiedTests=false' test` -> `BUILD SUCCESS`, 6 tests.
- Chat/runtime regression: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=KernelChatPipelineTests,KernelChatInboundServiceAgentModeTests,KernelChatInboundServiceTests,LocalRagPromptAdapterTests,KernelAgentLoopTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` -> `BUILD SUCCESS`, 37 tests.
- Kernel ContextPack regression: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=DefaultContextWeaverContextPackTests,KernelContextPackBuilderServiceTests' test` -> `BUILD SUCCESS`, 5 tests.
- Spring auto-config regression: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests,seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentKernelAutoConfigurationTests,SeahorseAgentKernelAgentAutoConfigurationTests,SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` -> `BUILD SUCCESS`, 65 tests.
- `git diff --check` -> clean.

Blocked-on items:
- None.

Next step:
- Run final diff review, stage untracked files, commit producer branch, then merge back to root `main` while preserving unrelated root dirty files.

ResumeStateHint:
- Continue in `D:\code\seahorse-agent\.worktrees\ai-infra-contextpack-producer`.
- Do not touch unrelated dirty files in root `D:\code\seahorse-agent`.

DriftCheckDraft:
- Scope: still Phase 4 Context DB / Resource ACL integration.
- Compatibility: preserve `MemoryContext` fallback and existing constructors where practical.
- Retirement: `MemoryContext` remains compatibility fallback; new production path prefers `ContextPack`.
- New owner/adapter: added app-layer assembler only; builder still owns ACL, budget, and persistence invariants.
- Evidence bundle grew enough to support this slice's completion claim.
- Decision: continue-to-commit.
