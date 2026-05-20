# Memory P5 lifecycle management - Intent

## TaskIntentDraft

- Requested outcome: Implement P5 lifecycle governance and management surface for the Seahorse memory system.
- Goal: Make obsolete or corrected memory fragments read-invisible, record read feedback, and expose lifecycle-related memory state through management APIs.
- Success evidence:
  - Layered memory tables carry lifecycle columns without breaking existing records.
  - Active reads exclude `OBSOLETE`, `DELETED`, and `PHYSICAL_DELETED` records.
  - Profile slot updates can mark old same-slot fragments obsolete across short-term, long-term, and semantic stores.
  - Loaded memory records produce read feedback for lifecycle tuning.
  - Management APIs expose Profile facts, Correction rules, operation records, and memory outbox tasks.
- Stop condition: stop when lifecycle repository tests, kernel lifecycle tests, management API contract tests, Spring wiring tests, starter packaging, and diff checks pass.
- Non-goals:
  - Full offline compaction job.
  - Physical vector/BM25 GC worker.
  - Human REVIEW queue mutation workflow.
  - P6 dynamic observability and policy tuning.
- Scope:
  - layered memory lifecycle columns and indexes
  - lifecycle outbound port and JDBC adapter
  - read filtering and read feedback
  - Profile generation invalidation for compatible layered stores
  - management query surface and contract tests
  - Spring auto-configuration wiring

## BaselineReadSetHint

- `docs/Seahorse Agent记忆系统差距分析与Gemini对齐改进方案.md`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/MemoryManagementServicePorts.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/KernelMemoryManagementService.java`
- `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcChatSchemaUpgrade.java`
- `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/Jdbc*MemoryRepositoryAdapter.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseMemoryController.java`
- `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelMemoryAutoConfiguration.java`
- `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentMemoryRepositoryAutoConfiguration.java`

## ImpactStatementDraft

- Compatibility boundary:
  - Existing layered memory tables remain readable and writable.
  - New lifecycle columns are nullable or defaulted.
  - Existing management API methods and constructors remain available.
  - Missing lifecycle infrastructure degrades through no-op ports.
- Owners:
  - `MemoryLifecyclePort` owns lifecycle invalidation and read feedback.
  - `DefaultMemoryEnginePort` owns lifecycle calls during write/read orchestration.
  - JDBC memory adapters own lifecycle persistence and active-read filtering.
  - `MemoryManagementInboundPort` owns management query contracts.
- Invariants:
  - Correction Ledger remains the highest-priority memory track.
  - Profile KV remains the strong fact source.
  - Obsolete layered fragments must not be injected into prompts through active reads.
  - Lifecycle metadata is additive and must not corrupt old memory records.

These records are Method Pack drafts / hints, not authoritative runtime decisions.
