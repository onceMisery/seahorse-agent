# AgentScope Core Boundary Design

## 1. Purpose

This design makes AgentScope a focused execution backend for Seahorse Agent.
AgentScope should provide agent orchestration and loop execution capabilities,
while Seahorse remains the product control plane and the owner of all business
governance.

The target outcome is:

- AgentScope Core owns ReAct orchestration, AgentScope client invocation, stream
  event mapping, cancellation, and protocol conversion.
- Seahorse Kernel owns tenant isolation, run profile resolution, model routing,
  tool policy, approval, quota, audit, memory governance, cost recording,
  snapshots, and external API/SSE contracts.
- A2A, Nacos, Config Center, and Studio remain available, but as optional
  integration modules rather than responsibilities of AgentScope Core.

This design is a specification for implementation planning. It does not change
runtime behavior by itself.

## 2. Source Context

Primary design references:

- `docs/design/agentscope-integration-and-loop-refactor.md`
- `docs/design/agentscope-production-integration-plan.md`

Relevant current implementation:

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/ReActExecutorPort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/ReActExecutorRouter.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/chat/KernelChatInboundService.java`
- `seahorse-agent-adapter-agent-agentscope/src/main/java/com/miracle/ai/seahorse/agent/adapters/agent/agentscope/AgentScopeReActAutoConfiguration.java`
- `seahorse-agent-adapter-agent-agentscope/src/main/java/com/miracle/ai/seahorse/agent/adapters/agent/agentscope/AgentScopeReActExecutor.java`
- `seahorse-agent-adapter-agent-agentscope/src/main/java/com/miracle/ai/seahorse/agent/adapters/agent/agentscope/AgentScopeModelBridge.java`
- `seahorse-agent-adapter-agent-agentscope/src/main/java/com/miracle/ai/seahorse/agent/adapters/agent/agentscope/AgentScopeToolFactory.java`
- `seahorse-agent-adapter-agent-agentscope/src/main/java/com/miracle/ai/seahorse/agent/adapters/agent/agentscope/AgentScopeA2aServerRunner.java`
- `seahorse-agent-adapter-agent-agentscope/pom.xml`

## 3. Current State

### 3.1 Existing Architecture Intent

The existing design documents already define the desired high-level boundary:

- Seahorse should not be replaced by AgentScope.
- `ReActExecutorPort` is the only execution backend selection point.
- `engine=kernel` remains default and must remain the immediate rollback path.
- `engine=agentscope` is a gray-release execution backend.
- Tool approval, quota, audit, model governance, memory governance, and SSE/API
  compatibility stay in Seahorse.

This design keeps those decisions and makes them enforceable at module and code
boundaries.

### 3.2 Current AgentScope Adapter Responsibilities

The current `seahorse-agent-adapter-agent-agentscope` module contains:

- AgentScope ReAct executor and client.
- Model bridge from AgentScope `Model` to Seahorse `StreamingChatModelPort`.
- Tool bridge from AgentScope tool calls to Seahorse tool ports.
- A2A client, server, controller, request signing, authentication, discovery,
  registration, and remote invocation.
- Nacos registry and config center integration.
- Prompt and skill repository integration.
- Studio lifecycle integration.
- Observation support for AgentScope and A2A events.
- Run metadata contributor for AgentScope config center source snapshots.

This is operationally useful, but the module currently mixes core loop
execution with product-adjacent integration and governance-adjacent code.

### 3.3 Current Boundary Risks

The main risks are:

- `AgentScopeReActAutoConfiguration` wires core executor, A2A, Nacos config
  center, Studio, metadata contributors, and remote agent tooling in one class.
- `AgentScopeToolFactory` directly depends on tool policy and approval
  repository/query ports. That makes the AgentScope adapter aware of approval
  lifecycle details instead of only consuming a governed tool execution contract.
- `AgentScopeA2aServerRunner` builds a simplified `AgentLoopRequest` directly
  from A2A messages. That bypasses parts of the normal Seahorse request
  preparation pipeline unless kept carefully constrained.
- The Maven module pulls core AgentScope, Nacos A2A, A2A server, Nacos prompt,
  Nacos skill, and Studio dependencies together, so consumers cannot choose only
  the loop executor without also carrying the integration surface.

## 4. First-Principles Boundary

First-principles invariants:

- Non-negotiable goal: AgentScope must be a pluggable execution backend, not a
  second Seahorse control plane.
- Non-negotiable constraints: existing `ReActExecutorPort`, `StreamCallback`,
  cancellation, usage recording, tool approval, and `engine=kernel` rollback
  behavior must remain compatible.
- Historical assumptions to drop: AgentScope integrations do not all have to
  live in the same adapter module just because they share the upstream
  AgentScope dependency.

Owner matrix:

| Concern | Canonical Owner | AgentScope Core Role |
| --- | --- | --- |
| Executor selection | Seahorse Kernel | Exposes `engineId=agentscope` |
| Agent loop orchestration | AgentScope Core for `engine=agentscope`, Seahorse Kernel for `engine=kernel` | Owns AgentScope ReAct execution |
| Chat/API/SSE contract | Seahorse Kernel and Web adapters | Maps AgentScope events to `StreamCallback` only |
| Tenant isolation | Seahorse Kernel | Carries tenant ID in requests, does not decide tenant access |
| Model routing and credentials | Seahorse model adapters | Calls `StreamingChatModelPort` through bridge |
| Tool catalog | Seahorse tool registry | Converts AgentScope tool schema to Seahorse tool calls |
| Tool policy and approval | Seahorse Kernel | Receives allow/deny/approval result from governed tool facade |
| Quota and cost | Seahorse billing/cost services | Emits usage back to Seahorse callback |
| Audit | Seahorse audit/tool/run services | Emits enough execution events for Seahorse to record |
| Memory and RAG | Seahorse memory/retrieval services | Consumes prepared context and history |
| A2A discovery and invocation | Optional AgentScope A2A integration module | Not part of Core |
| Nacos config center | Optional AgentScope config center module | Not part of Core |
| Studio/Trace integration | Optional AgentScope Studio/observation module | Core emits events, optional module exports diagnostics |

## 5. Target Architecture

```text
Web / SSE / API
  -> KernelChatInboundService
  -> Seahorse context preparation
       - tenant and user context
       - Run Profile
       - role card
       - model execution config
       - memory and RAG context
       - tool allowlist and policies
       - run metadata and snapshot
  -> ReActExecutorRouter
       -> KernelAgentLoop
       -> AgentScopeCoreExecutor
            -> AgentScopeAgentClient
            -> AgentScopeModelBridge -> StreamingChatModelPort
            -> AgentScopeToolBridge  -> GovernedToolExecutionPort
            -> AgentScopeEventBridge -> StreamCallback

Optional integrations:
  -> AgentScope A2A module
  -> AgentScope Nacos module
  -> AgentScope Config Center module
  -> AgentScope Studio module
```

## 6. Target Module Structure

### 6.1 Phase 1 Logical Split

Keep the existing Maven module, but split auto-configuration and internal
responsibilities:

- `AgentScopeCoreAutoConfiguration`
  - `AgentScopeModelFactory`
  - `AgentScopeToolBridge`
  - `AgentScopeAgentClient`
  - `AgentScopeReActExecutor`
- `AgentScopeA2aAutoConfiguration`
  - A2A resolver, registry, remote invoker, tool adapter, server runner,
    controller, registrar, signing, authentication
- `AgentScopeConfigCenterAutoConfiguration`
  - Nacos prompt listener, prompt config center, skill repository, strict
    startup validator, run metadata contributor
- `AgentScopeStudioAutoConfiguration`
  - Studio hook and lifecycle
- `AgentScopeObservationAutoConfiguration`
  - Observation support for AgentScope and A2A integration, still backed by
    Seahorse `ObservationPort`

This phase reduces behavioral risk because package names and the Maven artifact
can stay stable while bean ownership becomes explicit.

### 6.2 Phase 2 Physical Split

After Phase 1 tests pass, split Maven artifacts:

- `seahorse-agent-adapter-agent-agentscope-core`
- `seahorse-agent-adapter-agent-agentscope-a2a`
- `seahorse-agent-adapter-agent-agentscope-config-center`
- `seahorse-agent-adapter-agent-agentscope-studio`
- `seahorse-agent-adapter-agent-agentscope`
  - Compatibility aggregate artifact for one release window.

Dependency direction:

```text
agentscope-core
  -> seahorse-agent-kernel
  -> io.agentscope:agentscope

agentscope-a2a
  -> agentscope-core
  -> AgentScope A2A / Nacos A2A extensions

agentscope-config-center
  -> agentscope-core
  -> AgentScope Nacos prompt / skill extensions

agentscope-studio
  -> agentscope-core
  -> AgentScope Studio extension

aggregate agentscope module
  -> core
  -> a2a
  -> config-center
  -> studio
```

## 7. Configuration Model

Default behavior:

```properties
seahorse.agent.executor.engine=kernel
seahorse.agentscope.executor.enabled=false
seahorse.agentscope.a2a.enabled=false
seahorse.agentscope.config-center.enabled=false
seahorse.agentscope.studio.enabled=false
```

Register AgentScope executor for Run Profile or targeted routing:

```properties
seahorse.agentscope.executor.enabled=true
seahorse.agent.executor.engine=kernel
```

Make AgentScope the global default executor:

```properties
seahorse.agentscope.executor.enabled=true
seahorse.agent.executor.engine=agentscope
```

Enable optional integration surfaces:

```properties
seahorse.agentscope.a2a.enabled=true
seahorse.agentscope.a2a.register-enabled=true
seahorse.agentscope.config-center.enabled=true
seahorse.agentscope.studio.enabled=true
```

Immediate rollback:

```properties
seahorse.agent.executor.engine=kernel
```

Disable optional integration surfaces without disabling the core executor:

```properties
seahorse.agentscope.a2a.enabled=false
seahorse.agentscope.config-center.enabled=false
seahorse.agentscope.studio.enabled=false
```

## 8. Core Contract

### 8.1 `ReActExecutorPort`

`ReActExecutorPort` remains the only execution backend abstraction:

- `KernelAgentLoop` keeps `engineId=kernel`.
- `AgentScopeReActExecutor` keeps `engineId=agentscope`.
- `ReActExecutorRouter` continues routing by `AgentLoopRequest.executorEngine()`.
- Unknown executor values must fail clearly and must not silently fall back to a
  different engine.

### 8.2 AgentScope Core Executor Responsibilities

`AgentScopeReActExecutor` may:

- Call `AgentScopeAgentClient`.
- Convert `AgentLoopRequest.history()` and `question()` to AgentScope messages.
- Convert AgentScope streaming events to `StreamCallback`.
- Map content, thinking, usage, final result, recoverable error, approval wait,
  and complete events.
- Bind cancellation to the AgentScope subscription.
- Preserve `TraceRunScope` only as an event/trace bridge, not as a business
  governance owner.

`AgentScopeReActExecutor` must not:

- Resolve tenant authorization.
- Choose model credentials or providers.
- Decide tool policy.
- Create approval records directly.
- Persist cost, quota, memory, or audit records directly.
- Read or write long-term memory.
- Rewrite Seahorse SSE/API contracts.

## 9. Tool Governance Boundary

### 9.1 Problem

The current `AgentScopeToolFactory` directly depends on:

- `ToolRegistryPort`
- `ToolGatewayPort`
- `ToolPolicyPort`
- `ToolApprovalRequestRepositoryPort`
- `ApprovalRequestQueryPort`

This makes AgentScope adapter code aware of Seahorse policy and approval
lifecycle internals.

### 9.2 Target Port

Introduce a Seahorse-owned facade:

```java
public interface GovernedToolExecutionPort {

    GovernedToolPermission preflight(ToolInvocationRequest request);

    ToolInvocationResult invoke(ToolInvocationRequest request);
}
```

Suggested permission result:

```java
public record GovernedToolPermission(
        Effect effect,
        String approvalId,
        String reasonCode,
        String reasonMessage) {

    public enum Effect {
        ALLOW,
        APPROVAL_REQUIRED,
        DENY
    }
}
```

The implementation lives in Seahorse Kernel or Spring autoconfigure, not in
AgentScope Core. It may internally use:

- `ToolRegistryPort`
- `ToolGatewayPort`
- `ToolPolicyPort`
- `ToolApprovalRequestRepositoryPort`
- `ApprovalRequestQueryPort`
- tool audit and usage ports

### 9.3 AgentScope Tool Bridge

`AgentScopeToolBridge` should:

- Convert AgentScope tool schemas into Seahorse `ToolDescriptor` views.
- Convert AgentScope tool inputs into `ToolInvocationRequest`.
- Call `GovernedToolExecutionPort.preflight(...)`.
- Map `ALLOW` to AgentScope `PermissionDecision.allow`.
- Map `APPROVAL_REQUIRED` to AgentScope `PermissionDecision.ask`.
- Map `DENY` to AgentScope `PermissionDecision.deny`.
- Call `GovernedToolExecutionPort.invoke(...)` for execution.
- Convert `ToolInvocationResult` into AgentScope `ToolResultBlock`.

It must not create approval records or decide policy itself.

## 10. Model Governance Boundary

AgentScope Core may keep `AgentScopeModelBridge`, but the bridge must stay thin:

- Use `StreamingChatModelPort` only.
- Use model ID and sampling options resolved by Seahorse.
- Pass tool schemas as model-call metadata.
- Convert model stream content, thinking, tool calls, and usage to AgentScope
  response blocks.
- Propagate cancellation.

AgentScope Core must not:

- Read API keys.
- Resolve model provider.
- Enforce model quotas.
- Persist usage or cost.
- Own retry, circuit breaker, or rate limit policies except those delegated
  through the Seahorse model port.

## 11. Memory Boundary

Seahorse owns memory and RAG:

- `KernelChatInboundService` and related kernel services prepare history,
  context pack, memory context, skills, and RAG inputs before entering the
  executor.
- AgentScope Core receives prepared context as part of `AgentLoopRequest`.
- AgentScope Core may map conversation history to AgentScope messages.
- AgentScope Core must not write long-term memory.
- AgentScope Core must not independently retrieve RAG documents.
- Any future AgentScope memory feature must be treated as session-local
  execution context unless explicitly wrapped by Seahorse memory governance.

## 12. Tenant Boundary

Seahorse owns tenant isolation:

- Request entry points set tenant context.
- Kernel services resolve tenant-scoped run profiles, agents, tools, memory,
  billing, and snapshots.
- AgentScope Core receives tenant ID as part of `AgentLoopRequest`.

AgentScope Core may:

- Carry tenant ID into tool invocation requests.
- Preserve tenant context across async execution when invoking Seahorse ports.

AgentScope Core must not:

- Authorize cross-tenant access.
- Select fallback candidates across tenants.
- Decide tenant routing policy.

A2A tenant signing and Nacos tenant metadata belong to the optional A2A module,
not Core.

## 13. A2A Boundary

### 13.1 Current Risk

`AgentScopeA2aServerRunner` currently converts incoming A2A messages directly
to `AgentLoopRequest`. This is acceptable as a transitional adapter but should
not become a second public execution pipeline.

### 13.2 Target Inbound Flow

Introduce or reuse a Seahorse-owned external invocation entry point:

```java
public interface AgentExternalInvocationInboundPort {

    StreamCancellationHandle streamInvoke(
            AgentExternalInvocationCommand command,
            StreamCallback callback);
}
```

Suggested command:

```java
public record AgentExternalInvocationCommand(
        String tenantId,
        String userId,
        String agentName,
        String question,
        List<ChatMessage> history,
        Map<String, String> metadata,
        String preferredExecutorEngine) {
}
```

A2A server runner should:

- Authenticate and parse A2A request in the A2A module.
- Build `AgentExternalInvocationCommand`.
- Call Seahorse inbound port.
- Let Seahorse Kernel build the canonical `AgentLoopRequest`.
- Let `ReActExecutorRouter` pick `kernel` or `agentscope`.

This keeps A2A as an external protocol adapter and keeps run preparation in
Seahorse.

## 14. Config Center Boundary

Nacos config center is not part of Core.

The optional config center module may:

- Load prompt and skill metadata from Nacos.
- Provide prompt source snapshots.
- Provide strict/fallback startup validation.
- Contribute metadata through `AgentRunMetadataContributor`.

It must not:

- Replace Seahorse Run Profile ownership.
- Rewrite role card resolution.
- Grant tool access.
- Write memory.
- Bypass Seahorse snapshot creation.

## 15. Studio and Observation Boundary

Core may emit AgentScope execution events. Exporting those events to Studio,
OTEL, or infrastructure tracing belongs to optional integration.

Rules:

- Observation is best-effort and must not change execution behavior.
- Studio is disabled unless explicitly configured.
- Studio failure must not fail agent execution.
- Seahorse `ObservationPort` remains the owner of metrics/tracing integration.
- AgentScope Core should not depend directly on infrastructure-specific tracing
  exporters.

## 16. Implementation Plan

### M0: Baseline and Guard Tests

Purpose: make the intended boundary executable before refactoring.

Changes:

- Add auto-configuration tests proving core-only startup creates only core beans.
- Add tests proving A2A, Config Center, and Studio beans are absent unless their
  switches are explicitly enabled.
- Add tests around `ReActExecutorRouter` routing for `kernel`, `agentscope`, and
  unsupported engines.
- Add dependency-boundary checks if the project already has an architecture test
  pattern. If no pattern exists, add focused Spring auto-configuration tests
  first.

Acceptance:

- With `seahorse.agentscope.executor.enabled=true` and all optional integration
  flags false, the application context contains `AgentScopeReActExecutor` but no
  A2A server/controller, Nacos resolver/registry, config center validator, or
  Studio lifecycle.
- Existing AgentScope adapter tests still pass.
- `engine=kernel` remains default.

### M1: Logical Auto-Configuration Split

Purpose: separate responsibilities without moving Maven artifacts yet.

Changes:

- Replace the broad `AgentScopeReActAutoConfiguration` with:
  - `AgentScopeCoreAutoConfiguration`
  - `AgentScopeA2aAutoConfiguration`
  - `AgentScopeConfigCenterAutoConfiguration`
  - `AgentScopeStudioAutoConfiguration`
  - `AgentScopeObservationAutoConfiguration`, if useful
- Keep old bean names where practical to reduce downstream churn.
- Keep existing properties compatible.
- Keep existing `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  updated with the new auto-configuration classes.

Acceptance:

- Existing AgentScope tests pass.
- Application smoke for `engine=agentscope` passes.
- A2A tests pass when A2A flags are enabled.
- Config center tests pass when config center flags are enabled.
- Studio remains disabled by default.

### M2: Governed Tool Execution Facade

Purpose: remove approval and policy ownership from AgentScope Core.

Changes:

- Add `GovernedToolExecutionPort` or equivalent Seahorse-owned facade.
- Implement default facade using existing Seahorse tool registry, policy,
  approval repository/query, gateway, audit, and usage ports.
- Refactor `AgentScopeToolFactory` into `AgentScopeToolBridge` that depends on
  the facade, not individual policy/approval ports.
- Preserve existing approval behavior:
  - pending approval maps to `WAITING_APPROVAL`
  - approved or modified approval allows execution
  - rejected approval denies execution
  - policy denial denies execution
  - tool invocation failure maps to failed tool result

Acceptance:

- AgentScope tool approval tests still pass.
- Kernel tool gateway and approval tests still pass.
- AgentScope Core no longer directly injects approval repository/query ports.
- No tool can execute through AgentScope without Seahorse tool gateway.

### M3: A2A Inbound Through Seahorse Kernel

Purpose: avoid A2A becoming a second execution pipeline.

Changes:

- Add `AgentExternalInvocationInboundPort` or reuse an existing inbound service
  if one already represents external agent invocation cleanly.
- Refactor `AgentScopeA2aServerRunner` to call the inbound port rather than
  constructing a final `AgentLoopRequest` directly.
- Keep authentication, signing, Nacos discovery, and JSON-RPC concerns in the
  A2A module.
- Ensure inbound kernel flow records run context and metadata consistently.

Acceptance:

- A2A E2E still passes.
- A2A requests create the same kind of run metadata/snapshot as normal agent
  execution where applicable.
- Cross-tenant A2A calls remain rejected.
- `engine=kernel` rollback still works for A2A-triggered execution.

### M4: Physical Maven Module Split

Purpose: make the boundary visible to dependency management.

Changes:

- Create `seahorse-agent-adapter-agent-agentscope-core`.
- Move core executor/client/model/tool/event bridge classes into core module.
- Create `seahorse-agent-adapter-agent-agentscope-a2a`.
- Move A2A classes and Nacos A2A dependencies into A2A module.
- Create `seahorse-agent-adapter-agent-agentscope-config-center`.
- Move prompt/skill/config-center classes and dependencies into config module.
- Create `seahorse-agent-adapter-agent-agentscope-studio`.
- Move Studio lifecycle and Studio dependencies into studio module.
- Keep existing `seahorse-agent-adapter-agent-agentscope` as aggregate
  compatibility artifact for one release window.
- Update starter-all dependencies deliberately.

Acceptance:

- Core module builds and tests without Nacos A2A, Nacos prompt/skill, A2A server,
  or Studio dependencies.
- Aggregate module preserves current starter behavior.
- Optional modules can be included independently.
- Docker and starter smoke tests pass.

### M5: Documentation and Release Gates

Purpose: prevent boundary regression.

Changes:

- Update `docs/design/agentscope-production-integration-plan.md` with the new
  module structure after implementation.
- Update `docs/ops/agentscope-release-gates.md` with core-only and integration
  checks.
- Add release-gate script entries for:
  - core-only context test
  - `engine=agentscope` route smoke
  - A2A E2E when optional profile is enabled
  - config center strict/fallback tests

Acceptance:

- Release gate can fail if optional integration beans appear in core-only mode.
- Release gate can fail if AgentScope bypasses Seahorse approval.
- Release gate can fail if `engine=kernel` rollback is broken.

## 17. Verification Commands

Focused AgentScope adapter tests:

```powershell
mvn -pl seahorse-agent-adapter-agent-agentscope -am test
```

Application-level AgentScope route smoke:

```powershell
mvn -pl seahorse-agent-tests -am "-Dtest=KernelChatInboundServiceAgentScopeEngineSmokeTests" "-DfailIfNoTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Kernel agent and tool approval regression:

```powershell
mvn -pl seahorse-agent-kernel -am "-Dtest=KernelChatAgentRunStoreTests,KernelAgentLoopToolGatewayTests" "-DfailIfNoTests=false" test
```

Release gate:

```powershell
.\scripts\agentscope-release-gate.ps1
```

Optional live A2A E2E:

```powershell
.\scripts\agentscope-a2a-e2e.ps1 `
  -MainUrl http://127.0.0.1:9090/a2a `
  -RemotePort 9092 `
  -NacosServer 127.0.0.1:8848 `
  -TenantId tenant-a `
  -SharedSecret seahorse-local-a2a-token `
  -AuthMode tenant-signed `
  -MainAgentName seahorse-a
```

## 18. Compatibility Requirements

The implementation must preserve:

- Default `engine=kernel`.
- `seahorse.agent.executor.engine=agentscope` global routing.
- Run Profile `executorEngine=agentscope` routing.
- `seahorse.agentscope.executor.enabled=true` registration for targeted routing.
- Existing `StreamCallback` content/thinking/usage/error/complete semantics.
- Existing tool approval `WAITING_APPROVAL` behavior.
- Existing cost usage persistence through Seahorse callbacks.
- Existing A2A shared-secret and tenant-signed behavior when A2A is enabled.
- Existing config center strict and fallback semantics when config center is
  enabled.
- Existing Studio best-effort behavior when Studio is enabled.

## 19. Non-Goals

This design does not:

- Remove AgentScope support.
- Remove A2A, Nacos, Config Center, or Studio.
- Make AgentScope the default executor.
- Replace Seahorse memory, RAG, model routing, tool governance, approval, quota,
  audit, or billing.
- Introduce a second SSE/API contract for AgentScope.
- Require all module splitting to happen in the first implementation slice.

## 20. Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Auto-configuration split changes bean ordering | AgentScope executor not registered or wrong beans created | Add context tests for core-only and full integration profiles |
| Tool facade changes approval behavior | Tools may bypass approval or block incorrectly | Preserve current approval tests and add AgentScope facade tests |
| A2A inbound refactor changes behavior | Remote agent calls may fail or lose metadata | Keep A2A E2E and add snapshot/metadata assertions |
| Physical module split breaks starter-all | Runtime packaging misses optional dependencies | Keep aggregate module for one release window |
| Observation split loses traces | Debuggability regression | Keep `ObservationPort` contract and focused observation tests |
| AgentScope/core dependency drift returns | Core regains business logic | Add module/dependency boundary tests and release gate checks |

## 21. Acceptance Checklist

The design is fully implemented when:

- AgentScope Core can run `engine=agentscope` without A2A, Nacos config center,
  or Studio enabled.
- AgentScope Core has no direct dependency on approval repositories, billing
  repositories, quota services, audit services, or long-term memory writers.
- All tool execution from AgentScope goes through Seahorse governed tool facade.
- All model calls from AgentScope go through Seahorse model ports.
- A2A is an optional protocol adapter and enters Seahorse through a kernel-owned
  inbound contract.
- Nacos config center is optional and only contributes prompt/skill source
  metadata and config loading.
- Studio is optional and best-effort.
- `engine=kernel` remains the default and immediate rollback path.
- Current AgentScope smoke, adapter tests, kernel tool approval tests, and release
  gates pass.

## 22. Working Artifacts

TaskIntentDraft:

- Outcome: AgentScope becomes a focused execution backend and optional
  integrations are separated from core loop execution.
- Goal: prevent AgentScope from becoming a second product control plane.
- Success evidence: core-only startup works, optional integrations are gated,
  tool/model/memory/governance remain Seahorse-owned, current smoke tests pass.
- Stop condition: design is implemented through logical split, tool facade,
  A2A inbound cleanup, and module split or a documented compatibility aggregate.
- Non-goals: removing AgentScope, disabling A2A/Nacos/Studio, or changing
  default executor behavior.

BaselineReadSetHint:

- `docs/design/agentscope-integration-and-loop-refactor.md`
- `docs/design/agentscope-production-integration-plan.md`
- `docs/ops/agentscope-release-gates.md`
- `seahorse-agent-adapter-agent-agentscope`
- `seahorse-agent-kernel/application/agent`
- `seahorse-agent-kernel/application/chat`
- `seahorse-agent-spring-boot-autoconfigure`

ImpactStatementDraft:

- Affected layers: AgentScope adapter, kernel execution ports, Spring
  autoconfiguration, optional A2A/Nacos/Studio integrations, tests, release gate
  docs.
- Invariants: Seahorse owns product governance; AgentScope owns execution
  mechanics only; `engine=kernel` rollback remains immediate.
- Compatibility boundary: external API/SSE contracts and existing configuration
  keys remain compatible during the logical split.
- ADR signal: module ownership, execution boundary, optional integration
  dependency direction, and compatibility aggregate lifetime should be captured
  after implementation is approved.
