# AgentScope core boundary implementation - Checkpoint

- Task ID: `2026-06-30-agentscope-core-boundary-implementation`
- Current todo: Completion candidate after verification and documentation sync.
- Active slice: boundary implementation, live A2A hardening, and release-gate verification.
- Blocked on: none.
- Next step: user review, optional staging/commit, and future M4 physical Maven module split if the project wants dependency-level separation.

## Completed Todos

- Added Kernel-owned governed tool facade:
  - `GovernedToolExecutionPort`
  - `GovernedToolPermission`
  - `GovernedToolApproval`
  - `LocalGovernedToolExecutionPort`
- Registered the governed tool facade in Spring Kernel agent autoconfiguration.
- Refactored AgentScope tool bridge:
  - added `AgentScopeToolBridge`
  - kept `AgentScopeToolFactory` as a compatibility wrapper
  - removed direct AgentScope dependencies on tool policy, approval repository/query, and tool gateway ports.
- Split AgentScope autoconfiguration logically:
  - `AgentScopeObservationAutoConfiguration`
  - `AgentScopeCoreAutoConfiguration`
  - `AgentScopeNacosAutoConfiguration`
  - `AgentScopeA2aAutoConfiguration`
  - `AgentScopeConfigCenterAutoConfiguration`
  - `AgentScopeStudioAutoConfiguration`
  - `AgentScopeAutoConfigurationSupport`
  - retained `AgentScopeReActAutoConfiguration` as a compatibility aggregate.
- Updated Boot autoconfiguration imports to register the split configurations.
- Added Kernel-owned A2A/external invocation inbound contract:
  - `AgentExternalInvocationCommand`
  - `AgentExternalInvocationInboundPort`
  - `KernelAgentExternalInvocationInboundService`
- Refactored `AgentScopeA2aServerRunner` to call the Kernel inbound port rather than constructing `AgentLoopRequest` or invoking `ReActExecutorPort` directly.
- Repaired live A2A startup and execution gaps found in full-Docker validation:
  - forced `AgentScopeA2aAutoConfiguration` after `SeahorseAgentKernelChatAutoConfiguration` by class name so the external invocation inbound port exists before A2A beans are evaluated.
  - defaulted missing external users to `external-agent:<tenant>:<agent>` and propagated a synthetic `CurrentUser` through external invocation.
  - let `StreamChatCommand` carry optional `CurrentUser` so `AgentRunStartCommand` does not rely on an ambient Sa-Token context for external A2A calls.
  - separated protocol-level `agentName` from internal Seahorse `agentId`; internal agent ID is now only accepted from metadata keys `agentId` / `agent_id`.
- Extended `StreamChatCommand` with optional history, preferred executor engine, and tenant ID while keeping existing constructors.
- Updated `KernelChatInboundService` to honor supplied history, preferred executor engine, and tenant propagation through run/snapshot preparation.
- Preserved Kernel-selected executor behavior for A2A by not forcing `preferredExecutorEngine=agentscope` in the A2A adapter.
- Added focused tests for governed tool facade, core-only autoconfiguration, AgentScope tool bridge behavior, A2A inbound boundary, and existing AgentScope executor behavior.

## Evidence Refs

- See `90-evidence.md` for command-level evidence.
- Boundary scans:
  - AgentScope adapter has no direct policy/approval/gateway port dependency hits.
  - A2A runner/support have no direct `ReActExecutorPort`, `AgentLoopRequest`, `streamExecute`, or stale resolver hits.
- Release gate:
  - `scripts/agentscope-release-gate.ps1` reported `AGENTSCOPE_RELEASE_GATE=PASS`.
- Live A2A E2E:
  - shared-secret full-Docker flow reported `E2E_RESULT=PASS`.
  - tenant-signed full-Docker flow reported `E2E_RESULT=PASS`.
  - both covered main/remote Agent Cards, auth rejection, authenticated JSON-RPC execution, direct remote call, Nacos discovery, and connector smoke.

## DriftCheckDraft

- Does the current work still serve the original task intent? yes.
- Does the current work still serve the goal and stop condition? yes; M0-M3 are implemented and M4 remains a documented compatibility migration.
- Did the slice stay inside the compatibility boundary? yes; external API/SSE contracts and configuration keys were preserved.
- Did any new owner, fallback, adapter, or branch appear? yes; `GovernedToolExecutionPort` and `AgentExternalInvocationInboundPort` were introduced as Kernel-owned boundaries. Both are explicit owners, not duplicate AgentScope owners.
- Is the retirement track still explicit? yes; direct AgentScope ownership of approval/policy details and A2A direct loop construction were retired in this slice. Physical Maven module split remains future M4.
- Did the evidence bundle grow enough to support the next claim? yes; local full-Docker shared-secret and tenant-signed E2E are now included.
- Decision: `continue` to user review / optional commit.
