# AI Infra Version Model Routing Plan

## Goal

Close the Phase 1/2 runtime gap where published `AgentVersion.modelConfigJson` is stored but not replayed into Agent execution. The minimal slice routes a version-scoped chat model and sampling options into the existing `KernelAgentLoop` model request.

## Architecture

- Keep the version snapshot immutable in `AgentVersion`.
- Add a small kernel domain value object for chat model execution options.
- Keep JSON parsing in an application service, not in the domain object or Web adapter.
- Pass model selection through existing ports by extending `ChatRequest`; adapters remain replaceable.

## Tech Stack

- Java 17
- JUnit 5
- Maven module tests for kernel and AI adapter contracts

## Baseline/Authority Refs

- `docs/company-agent/ai-infra-phases/01-agent-registry-run-store.md`
- `docs/company-agent/ai-infra-phases/02-tool-gateway-policy-engine.md`
- `docs/company-agent/ai-infra-phases/03-durable-runtime-hitl.md`
- `docs/company-agent/ai-infra-phases/99-current-implementation-handoff.md` is stale, used only as negative guidance to avoid duplicate approval work.

## Compatibility Boundary

- Do not introduce a workflow engine, remote Agent mesh, or complex JSON persistence type.
- Do not change stored `modelConfigJson`; keep it as String.
- Do not make kernel depend on Spring, JDBC, or Web.
- Preserve legacy chat/agent behavior when no version model config is available.
- Preserve existing `StreamingChatModelPort` implementors; model selection is carried inside `ChatRequest`.

## Verification

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-tests,seahorse-agent-adapter-ai-openai-compatible,seahorse-agent-adapter-repository-jdbc,seahorse-agent-spring-boot-starter,seahorse-agent-adapter-web -am '-Dtest=KernelAgentRunServiceTests,KernelAgentDefinitionServiceTests,ChatRequestToolsTests,ChatModeTests,KernelAgentLoopTests,KernelChatAgentRunStoreTests,OpenAiCompatibleStreamingChatToolsTests,JdbcAgentDefinitionRepositoryAdapterTests,SeahorseWebApiContractTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## Tasks

- [x] Write RED tests for `ChatRequest` model id propagation into `KernelAgentLoop`.
- [x] Add `AgentModelExecutionConfig` parsing for minimal chat model JSON fields.
- [x] Wire version model config into `KernelChatInboundService` agent-mode request construction when an `AgentDefinitionRepositoryPort` is available.
- [x] Make OpenAI-compatible streaming calls honor `ChatRequest.modelId()`.
- [x] Run focused regressions and diff hygiene.
- [x] Tighten `AgentDefinitionRepositoryPort.findVersion` into an explicit immutable-version lookup after advisory review.

## Risks

- Existing agent-mode chat still defaults to `legacy-react-agent`; this slice only uses registered version config when the command/run identifies a registered agent.
- Resume from checkpoint currently uses fixed sampling. If model config needs resume replay, that should be a later checkpoint payload enhancement.
