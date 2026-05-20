# Memory P2 ingestion workflow - Intent

## TaskIntentDraft

- Requested outcome: Implement P2 deterministic memory ingestion workflow with operation log, filtering, and entrypoint convergence
- Goal: Implement P2 deterministic memory ingestion workflow with operation log, filtering, and entrypoint convergence
- Success evidence:
- Deterministic ingestion workflow is available through `MemoryIngestionWorkflowPort`.
- Chat completion capture and `memory_write` tool submit ingestion commands instead of owning final writes.
- Sanitizer, pre-filter, semantic classifier, and schema validator gate durable writes.
- Operation log persists idempotency and terminal decisions through JDBC.
- Sensitive content, low-value chat, explicit preferences, and explicit occupation corrections follow distinct decision paths.
- Existing P1 Profile KV and Correction Ledger remain the strong fact and highest-priority correction sources.
- Compatibility is preserved for existing `MemoryEnginePort.writeMemory()` and `memory_write` response contract.
- Stop condition: Stop when success evidence is satisfied or a blocker/risk requires pause.
- Non-goals:
- LLM refiner implementation.
- Memory outbox/vector/BM25 fanout.
- P3 read-path router and context weaving beyond existing P1 skeleton.
- Scope: Seahorse Agent memory write path, operation log persistence, sanitizer/pre-filter/classifier/schema validator, tests
- Change kinds:
- architecture
- Risk hints:
- Public tool response compatibility.
- Database migration compatibility for existing volumes.
- Keeping LLM/tool calls as candidates only, with deterministic code owning final persistence.

## BaselineReadSetHint

- `docs/Seahorse Agent记忆系统差距分析与Gemini对齐改进方案.md`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/chat/MemoryCaptureStage.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/tool/MemoryWriteToolPortAdapter.java`
- `resources/database/seahorse_init.sql`

## ImpactStatementDraft

- Compatibility boundary: Existing layered memory tables and `MemoryEnginePort` signatures remain valid. New workflow wraps write decisions and operation logging without requiring callers to bypass existing APIs.
- Affected layers:
- Kernel memory write path.
- Agent memory tool entrypoint.
- Chat completion memory capture stage.
- JDBC repository adapter and schema upgrade.
- Spring auto-configuration.
- Memory workflow tests.
- Owners:
- `DefaultMemoryEnginePort` owns deterministic write decisions.
- `MemoryOperationLogPort` owns ingestion idempotency persistence.
- Profile KV remains strong profile fact source.
- Correction Ledger remains highest-priority correction source.
- Invariants:
- Sensitive content must not be durably stored by the memory workflow.
- Duplicate operation id must not create duplicate durable writes.
- LLM/tool calls submit candidates only; deterministic code owns final writes.
- P1 read priority for Correction Ledger and Profile KV is retained.
- Non-goals:
- LLM refiner.
- Memory outbox.
- P3 retrieval routing/context weaving.

These records are Method Pack drafts / hints, not authoritative runtime decisions.
