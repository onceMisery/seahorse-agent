# Memory P4 retrieval outbox - Intent

## TaskIntentDraft

- Requested outcome: Implement P4 memory vector/business-document retrieval bridge and outbox fallback.
- Goal: Close the first usable retrieval loop after P3 Router/Context Weaver.
- Success evidence:
  - Accepted memory writes attempt vector indexing.
  - Vector indexing failure enqueues an outbox task and does not roll back durable memory/Profile writes.
  - Episodic questions can recall vector-hit memory IDs through existing memory stores.
  - Active Profile generation filters obsolete same-slot vector memories.
  - Business-rule questions can add business document candidates into the semantic/business memory zone.
- Stop condition: stop when targeted P4 tests, related memory regressions, Spring wiring tests, and starter package pass, or when live vector infrastructure is required.
- Non-goals:
  - Full pgvector ANN implementation if the current repo lacks a concrete embedding/vector store configuration.
  - P5 lifecycle state machine and GC workers.
  - P6 dynamic metrics and strategy UI.
- Scope:
  - memory outbox port and JDBC adapter/table
  - memory vector indexing/recall integration
  - business document retriever port integration
  - generation filtering in memory read path
  - Spring wiring and tests

## BaselineReadSetHint

- `docs/Seahorse Agent记忆系统差距分析与Gemini对齐改进方案.md`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/memory/MemoryVectorPort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/retrieval/KernelRetrievalEngine.java`
- `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcChatSchemaUpgrade.java`
- `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelMemoryAutoConfiguration.java`

## ImpactStatementDraft

- Compatibility boundary:
  - Existing memory engine constructors remain available.
  - Existing store ports remain the durable memory source of record.
  - Existing knowledge/RAG retrieval remains the business document source of record.
- Owners:
  - `DefaultMemoryEnginePort` owns memory read/write orchestration.
  - `MemoryVectorPort` owns memory vector index/search capability.
  - `MemoryOutboxPort` owns deferred retry tasks.
  - `MemoryBusinessDocumentRetrieverPort` owns business document candidates for memory context.
- Invariants:
  - Profile KV remains the strong fact source.
  - Correction Ledger remains highest priority in prompt weaving.
  - Vector/index failure must not block accepted deterministic memory writes.

These records are Method Pack drafts / hints, not authoritative runtime decisions.
