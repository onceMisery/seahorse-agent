# AI Infra ContextPack Producer Plan

## Goal

Implement the next Phase 4 Context DB slice: production chat/runtime paths should build a `ContextPack` from user input, loaded memory, and retrieved RAG chunks before prompts are assembled. This continues after the already merged Approval API, ContextPack foundation, and runtime prompt consumption slices.

## Architecture

- Keep `ContextPack` invariants and ACL selection in the existing `KernelContextPackBuilderService`.
- Add a small chat application assembler that maps existing runtime facts into `ContextBuildItemCandidate` values.
- Wire the assembler through `ContextPackBuilderInboundPort`; kernel code depends only on ports/domain contracts.
- Preserve legacy `MemoryContext` fallback if no builder is configured or build fails.

## Tech Stack

- Java 17 records/classes in `seahorse-agent-kernel`.
- Existing Spring Boot auto-configuration in `seahorse-agent-spring-boot-starter`.
- Existing Maven/JUnit/Mockito/AssertJ tests under `seahorse-agent-tests`.

## Baseline/Authority Refs

- `docs/company-agent/ai-infra-phases/04-context-db-resource-acl.md`
- `docs/aegis/plans/2026-05-24-ai-infra-contextpack-foundation.md`
- `docs/aegis/plans/2026-05-24-ai-infra-contextpack-runtime.md`
- Started from `main` at `2b73c0e4`, including ContextPack prompt consumption.
- Merged current local `main` (`9df495cc`) into `codex/ai-infra-contextpack-producer` before completion checks.

## Compatibility Boundary

- Do not rewrite retrieval, memory recall, or the ContextPack builder/persistence invariants.
- Keep existing constructors working where practical.
- If ContextPack assembly is unavailable or fails, continue with existing memory/RAG behavior.
- Do not introduce a workflow engine, JSON abstraction library, remote mesh, or new persistence table in this slice.

## Verification

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=KernelChatPipelineTests,KernelChatInboundServiceTests,KernelChatInboundServiceAgentModeTests,LocalRagPromptAdapterTests,KernelAgentLoopTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=DefaultContextWeaverContextPackTests,KernelContextPackBuilderServiceTests' test
git diff --check
```

## Tasks

- [x] Write failing tests proving RAG and Agent mode produce a `ContextPack`.
- [x] Add the chat runtime ContextPack assembler.
- [x] Wire the assembler into `KernelChatPipeline`, `KernelChatInboundService`, and Spring chat auto-configuration.
- [x] Verify focused tests and update evidence/checkpoint records.

## Risks

- The default resource policy denies everything; tests should use a recording builder, not depend on default ACL allowing items.
- Existing RAG prompt fallback must remain operational if the builder is missing or throws.

## Retirement

- This slice does not remove `MemoryContext`; it narrows its role to compatibility fallback while production chat paths prefer `ContextPack`.
