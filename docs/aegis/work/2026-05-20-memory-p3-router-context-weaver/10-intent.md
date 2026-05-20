# Memory P3 router context weaver - Intent

## TaskIntentDraft

- Requested outcome: Implement P3 memory read Router + Context Weaver behavior.
- Goal: Make memory reads question-aware and make prompt injection use ContextWeaver rather than the flat formatter directly.
- Success evidence:
  - Profile questions activate Correction/Profile/Short Window without pulling broad episodic memories.
  - Business rule/document questions activate Business Document and relevant episodic support.
  - General chat keeps only the short window plus correction guardrails.
  - ContextWeaver emits ordered zones with Correction before Profile and enforces item/character budget.
  - Chat system responses and AgentLoop memory injection use `ContextWeaverPort`.
- Stop condition: Stop when targeted P3 tests and package compile pass, or when live infrastructure is required.
- Non-goals:
  - Vector/BM25/business document retriever implementation from P4.
  - Read feedback events and lifecycle governance from P5.
  - Metrics/dynamic strategy configuration from P6.
- Scope: kernel memory router, context weaver, chat response prompt injection, agent prompt injection, Spring wiring, tests.
- Change kinds:
  - architecture
  - behavior

## BaselineReadSetHint

- `docs/Seahorse Agent记忆系统差距分析与Gemini对齐改进方案.md`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryRouter.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultContextWeaver.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/chat/KernelChatResponseSupport.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/KernelAgentLoop.java`

## ImpactStatementDraft

- Compatibility boundary: keep existing `MemoryEnginePort.loadMemory()` and `MemoryPromptFormatter` compatibility surface, while routing new prompt construction through `ContextWeaverPort`.
- Affected layers:
  - Memory router
  - Context weaver
  - Chat system prompt construction
  - Agent loop prompt construction
  - Spring auto-configuration
  - Memory routing tests
- Owners:
  - `DefaultMemoryRouter` owns deterministic track activation.
  - `DefaultContextWeaver` owns memory prompt zoning and budget.
  - `MemoryPromptFormatter` remains a compatibility facade.
- Invariants:
  - Correction Ledger must stay before Profile and all legacy memories.
  - Profile questions must not inject broad unrelated episodic memories.
  - Budget pressure must drop lower-priority zones before dropping Correction/Profile.

These records are Method Pack drafts / hints, not authoritative runtime decisions.
