# AgentScope core boundary implementation - Intent

## TaskIntentDraft

- Requested outcome: implement `docs/aegis/specs/2026-06-30-agentscope-core-boundary-design.md` with focused tests only on high-risk boundary points.
- Goal: make AgentScope a focused execution backend for agent orchestration and loop execution, while Seahorse Kernel remains the owner of product governance.
- Success evidence:
  - AgentScope core-only startup registers the executor without A2A, Nacos config center, or Studio beans.
  - AgentScope tool execution routes through a Seahorse-owned governed tool facade.
  - AgentScope adapter no longer directly depends on tool policy, approval repository/query, or tool gateway ports.
  - A2A inbound requests enter Seahorse through a Kernel-owned external invocation inbound port.
  - `engine=kernel` remains the default rollback path, and `engine=agentscope` routing still works.
  - Targeted adapter, kernel, cross-module smoke, Spring autoconfigure, and release gate checks pass.
- Stop condition: stop when logical split, governed facade, A2A inbound boundary, compatibility aggregate, and verification evidence are complete; defer physical Maven module split as the documented M4 follow-up rather than forcing a high-risk packaging move in this slice.
- Non-goals:
  - Removing AgentScope support.
  - Removing A2A, Nacos, Config Center, or Studio.
  - Making AgentScope the default executor.
  - Replacing Seahorse memory, RAG, model routing, tool governance, approval, quota, audit, or billing.
  - Performing the M4 physical Maven module split in this compatibility slice.

## BaselineReadSetHint

- `docs/aegis/specs/2026-06-30-agentscope-core-boundary-design.md`
- `docs/design/agentscope-integration-and-loop-refactor.md`
- `docs/design/agentscope-production-integration-plan.md`
- `docs/ops/agentscope-release-gates.md`
- `seahorse-agent-adapter-agent-agentscope/src/main/java/com/miracle/ai/seahorse/agent/adapters/agent/agentscope`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/chat`
- `seahorse-agent-spring-boot-autoconfigure/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring`

## ImpactStatementDraft

- Compatibility boundary: existing external API/SSE contracts, `ReActExecutorPort`, `StreamCallback`, tool approval behavior, AgentScope configuration keys, and `engine=kernel` rollback behavior remain compatible.
- Affected layers:
  - AgentScope adapter auto-configuration and tool/A2A bridges.
  - Kernel agent tool governance facade.
  - Kernel chat inbound command and external invocation entry point.
  - Spring Boot autoconfiguration for Kernel agent/chat beans.
  - Focused tests and release gate evidence.
- Owners:
  - Seahorse Kernel owns tenant isolation, run profile resolution, model routing, tool governance, approval, audit, quota, memory, snapshots, and external invocation preparation.
  - AgentScope Core owns AgentScope ReAct execution, client invocation, event mapping, cancellation, and tool/model protocol conversion.
  - Optional AgentScope integrations own A2A, Nacos/config-center, Studio, and observation export surfaces.
- Invariants:
  - AgentScope is not a second product control plane.
  - Tool calls from AgentScope cannot bypass Seahorse governed tool execution.
  - A2A is an external protocol adapter and does not construct final `AgentLoopRequest` directly.
  - Physical module split remains a planned compatibility migration rather than an unverified packaging change in this slice.

These records are Method Pack drafts / hints, not authoritative runtime decisions.
