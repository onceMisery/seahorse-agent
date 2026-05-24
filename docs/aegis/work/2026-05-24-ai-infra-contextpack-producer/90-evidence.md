# EvidenceBundleDraft

## Current State

- Worktree: `D:\code\seahorse-agent\.worktrees\ai-infra-contextpack-producer`
- Branch: `codex/ai-infra-contextpack-producer`
- Base after user-requested merge: local `main` at `9df495cc`

## Implementation Evidence

- `ContextPackRuntimeAssembler` builds `ContextBuildRequest` candidates from user input, loaded memory, and RAG chunks.
- `KernelChatPipeline` builds a `ContextPack` after retrieval and before prompt assembly.
- `KernelChatInboundService` builds a `ContextPack` before invoking `KernelAgentLoop` in Agent mode.
- `SeahorseAgentKernelChatAutoConfiguration` injects optional `ContextPackBuilderInboundPort` into both chat runtime paths.
- `ContextResourceType` centralizes ContextPack resource-type constants used by runtime assembly.
- Builder absence or builder failure keeps legacy `MemoryContext` fallback behavior.

## Verification Commands

1. Focused producer/fallback tests:

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=KernelChatPipelineTests#shouldStreamRagResponseWithMcpSamplingParameters+shouldExposeProducedContextPackToRagPromptAssembly+shouldFallbackToLegacyPromptContextWhenContextPackBuilderFails,KernelChatInboundServiceAgentModeTests#shouldRouteAgentModeToKernelAgentLoopWithoutExecutingRagPipeline+shouldBuildContextPackForAgentModeBeforeExecutingAgentLoop+shouldExecuteAgentLoopWithLegacyMemoryContextWhenContextPackBuilderFails' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD SUCCESS`, 6 tests, 0 failures/errors.

Fresh rerun after merging `main` and constructor cleanup on 2026-05-24T23:42+08:00:
`BUILD SUCCESS`, 6 tests, 0 failures/errors.

2. Chat/runtime regression:

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=KernelChatPipelineTests,KernelChatInboundServiceAgentModeTests,KernelChatInboundServiceTests,LocalRagPromptAdapterTests,KernelAgentLoopTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD SUCCESS`, 37 tests, 0 failures/errors.

3. Kernel ContextPack regression:

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=DefaultContextWeaverContextPackTests,KernelContextPackBuilderServiceTests' test
```

Result: `BUILD SUCCESS`, 5 tests, 0 failures/errors.

4. Spring auto-configuration regression:

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests,seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentKernelAutoConfigurationTests,SeahorseAgentKernelAgentAutoConfigurationTests,SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD SUCCESS`, 65 tests, 0 failures/errors.

5. Whitespace/conflict check:

```powershell
git diff --check
```

Result: clean.

## DriftCheckDraft

- Original task intent still served: yes, this closes the Phase 4 production ContextPack construction gap without duplicating foundation/runtime consumption work.
- Compatibility boundary: maintained; `MemoryContext` fallback remains when no builder exists or builder fails.
- New owner/fallback/adapter: one app-layer assembler added; `KernelContextPackBuilderService` remains the owner of ACL, budget, and persistence invariants.
- Retirement track: no removal in this slice; future work can reduce direct `MemoryContext` prompt paths after all production paths emit `ContextPack`.
- Decision: continue-to-commit.
