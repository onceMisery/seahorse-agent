# AI Infra ContextPack Runtime Prompt Plan

## Goal

Make the existing prompt assembly path able to consume `ContextPack` as the preferred runtime context, while keeping the legacy `MemoryContext` path as a compatibility fallback.

## Architecture

Kernel domain objects carry `ContextPack`. `ContextWeaverPort` formats context through a small port method. Agent/RAG runtime orchestration composes through the port and does not depend on JDBC, Spring, or Web adapters.

## Tech Stack

Java 17, Maven, JUnit 5, Spring-compatible kernel ports.

## Baseline/Authority Refs

- `docs/company-agent/ai-infra-phases/00-architecture-baseline.md`
- `docs/company-agent/ai-infra-phases/04-context-db-resource-acl.md`
- `docs/aegis/plans/2026-05-24-ai-infra-contextpack-foundation.md`

## Compatibility Boundary

- Do not rewrite RAG retrieval or memory recall pipelines in this slice.
- Do not replace `MemoryContext`; use it only when no usable `ContextPack` is supplied.
- Do not add new persistence tables or ACL mutation APIs here.
- Keep `ContextWeaverPort` compatible with existing lambda implementations.

## Verification

- `mvn '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=DefaultContextWeaverContextPackTests,KernelContextPackBuilderServiceTests' test`
- `mvn '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=KernelAgentLoopTests,LocalRagPromptAdapterTests,MemoryWorkflowRoutingTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`

## Tasks

1. Write RED tests for formatting `ContextPack` items with source, ACL decision, citation, and secret filtering.
2. Write RED tests showing Agent loop first model turn prefers `ContextPack` over legacy `MemoryContext`.
3. Write RED tests showing RAG prompt assembly includes `ContextPack` content through `PromptContext`.
4. Extend context carrier objects and `ContextWeaverPort` without breaking existing memory-only implementations.
5. Implement `DefaultContextWeaver` formatting for `ContextPack`.
6. Update Agent loop and RAG prompt assembly to prefer `ContextPack`, then fall back to `MemoryContext`.

## Risks

This slice does not build ContextPack from RAG or Memory automatically. It only makes the runtime prompt layer ready to consume a built pack. Automatic builder integration remains a later slice because it needs explicit resource refs and ACL policy choices.

## Retirement

No legacy path is retired. `MemoryContext` remains the fallback until ContextPack builder integration covers RAG, Memory, tool result, and user input sources.
