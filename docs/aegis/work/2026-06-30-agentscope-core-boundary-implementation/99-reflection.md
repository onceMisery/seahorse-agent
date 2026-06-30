# AgentScope core boundary implementation - Reflection

## Goal Closure

- Goal status: satisfied for the approved compatibility slice.
- Success evidence: logical auto-configuration split, Kernel governed tool facade, AgentScope tool bridge refactor, A2A Kernel inbound boundary, live shared-secret and tenant-signed A2A E2E, focused tests, broader regression tests, boundary scans, and release gate pass.
- Stop state: done for M0-M3 plus compatibility aggregate; M4 physical Maven module split is deferred as a separate packaging migration.
- Non-goals respected: AgentScope support, A2A, Nacos, Config Center, Studio, default Kernel executor behavior, and Seahorse governance ownership were retained.

## Architecture Alignment

- Trigger: yes.
- Scope: AgentScope/Core boundary, Kernel governance ownership, A2A inbound ownership, and autoconfiguration ownership.
- Baseline checked:
  - `docs/aegis/specs/2026-06-30-agentscope-core-boundary-design.md`
  - `docs/design/agentscope-integration-and-loop-refactor.md`
  - `docs/design/agentscope-production-integration-plan.md`
  - `docs/ops/agentscope-release-gates.md`
- Result: aligned for the compatibility slice.
- Evidence: `90-evidence.md`, live full-Docker A2A shared-secret and tenant-signed E2E, release gate pass, and boundary scans.
- Residual architecture risk: physical dependency-level split is not complete and should remain tracked as M4; non-local A2A/Nacos deployments should still run the same E2E gate before rollout.

## ADR Backfill Check

- Trigger: yes.
- Suggested action: create or amend an ADR after user review if this implementation direction is accepted.
- Evidence source: this work record and `docs/aegis/specs/2026-06-30-agentscope-core-boundary-design.md`.
- Baseline sync: needed after M4 is scheduled or intentionally deferred for a release window.
- Skip reason: not skipped.
- Boundary: advisory method-pack signal only.

## Repair Track

- Repaired object: AgentScope adapter responsibilities.
- Action: moved tool governance details behind `GovernedToolExecutionPort`, split autoconfiguration into explicit core/optional integration owners, and moved A2A execution entry through `AgentExternalInvocationInboundPort`.
- Impact: AgentScope now focuses on execution mechanics while Seahorse Kernel retains policy, approval, tenant, run preparation, and routing ownership.
- Verification: see `90-evidence.md`.

- Repaired object: live A2A server bean registration.
- Action: ordered `AgentScopeA2aAutoConfiguration` after Kernel chat autoconfiguration so `AgentExternalInvocationInboundPort` is available when A2A server/controller beans are evaluated.
- Impact: `/a2a` is registered in the full Spring Boot Docker deployment instead of returning 404.
- Verification: full-Docker `/a2a` card smoke and both live A2A E2E modes passed.

- Repaired object: external A2A invocation identity and run context.
- Action: synthesized a stable external `CurrentUser` when the A2A caller does not provide a Seahorse user, propagated it through `StreamChatCommand`, and avoided treating protocol `agentName` as internal `agentId` unless explicit metadata carries `agentId` / `agent_id`.
- Impact: live A2A calls no longer fail on blank user IDs, missing Sa-Token context, or mismatched internal agent IDs.
- Verification: focused external invocation tests, shared-secret live E2E, tenant-signed live E2E, and release gate passed.

## Retirement Track

- Retired object: direct AgentScope dependency on tool policy/approval repository/query/gateway ports.
- Action: replaced with Kernel-owned governed tool facade.
- Retained boundary: `AgentScopeToolFactory` remains as a compatibility wrapper over `AgentScopeToolBridge`.
- Future trigger: remove compatibility wrapper only after downstream code no longer references `AgentScopeToolFactory` directly.

- Retired object: A2A direct construction of `AgentLoopRequest` and direct executor invocation.
- Action: replaced with Kernel-owned external invocation command/inbound port.
- Retained boundary: A2A still owns request authentication, AgentScope protocol conversion, and optional server/controller setup.
- Future trigger: add live A2A E2E before changing signing/tenant metadata or performing M4 packaging split.

- Retired object: monolithic `AgentScopeReActAutoConfiguration` as the sole owner of all AgentScope beans.
- Action: replaced implementation with split auto-configurations and retained old class as aggregate compatibility import.
- Retained boundary: old aggregate class stays for compatibility during the logical split.
- Future trigger: M4 physical Maven module split and one release-window deprecation plan.

## Residual Risk

- Unverified: non-local A2A/Nacos deployments, production network policy, and production secrets/signing rotation.
- Deferred: physical Maven module split into core/a2a/config-center/studio artifacts.
- Reduced: local full-Docker shared-secret and tenant-signed A2A E2E now both pass.

Method Pack output does not grant completion authority.
