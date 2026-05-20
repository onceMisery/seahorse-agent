# Memory P6 observability policy - Intent

## TaskIntentDraft

- Requested outcome: Implement P6 observability and dynamic policy controls for the Seahorse memory system.
- Goal: Expose user/tenant memory health, make capture thresholds configurable without code changes, and surface alert signals for schema failures and outbox backlog.
- Success evidence:
  - Memory management can return a user/tenant health report with Profile, Correction, operation, conflict, outbox, quality, and alert fields.
  - Memory capture thresholds are supplied through `MemoryPolicyConfigPort` instead of fixed constants.
  - Spring properties initialize runtime policy config for thresholds, token budget, review flag, alert thresholds, and grey-release key.
  - Web APIs expose memory health and policy config read/update endpoints.
  - Existing memory constructors and management ports remain source-compatible through default policy ports.
- Stop condition: stop when P6 targeted tests, broader memory regression tests, starter packaging, and diff checks pass.
- Non-goals:
  - Durable database persistence for policy config.
  - Micrometer metric emission for every memory KPI.
  - Full recall hit-rate and prompt-token accounting across all prompt paths.
  - GC worker metrics beyond current lifecycle/outbox signals.
- Scope:
  - policy config domain model and port
  - in-memory runtime policy adapter
  - capture value assessor dynamic thresholds
  - management health aggregation
  - web management endpoints
  - Spring property wiring
  - targeted and contract tests

## BaselineReadSetHint

- `docs/Seahorse Agent记忆系统差距分析与Gemini对齐改进方案.md`
- `docs/Gemini Agent记忆系统完整设计方案.md`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/MemoryValueAssessor.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/KernelMemoryManagementService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/MemoryManagementServicePorts.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/memory/MemoryManagementInboundPort.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseMemoryController.java`
- `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelMemoryAutoConfiguration.java`

## ImpactStatementDraft

- Compatibility boundary:
  - Existing memory store schema is not replaced.
  - Existing constructors remain available and use default policy config.
  - Existing management API methods remain available.
  - Runtime policy config is in-memory by default and can be replaced by another `MemoryPolicyConfigPort`.
- Owners:
  - `MemoryPolicyConfigPort` owns runtime memory policy config.
  - `MemoryValueAssessor` consumes policy thresholds for deterministic capture decisions.
  - `KernelMemoryManagementService` owns health-report aggregation and policy config management surface.
  - `SeahorseMemoryController` exposes management endpoints.
  - Spring auto-configuration owns default property-backed policy adapter wiring.
- Invariants:
  - Profile KV remains the strong fact source.
  - Correction Ledger remains highest-priority memory track.
  - The write workflow remains the final authority for actual writes.
  - Dynamic policy changes must not give LLM direct database-write authority.

These records are Method Pack drafts / hints, not authoritative runtime decisions.
