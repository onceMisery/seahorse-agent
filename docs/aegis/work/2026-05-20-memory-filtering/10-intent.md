# Task Intent: Memory Filtering and Cross-Session Recall

## Requested Outcome

将记忆系统完善方案落地成 Aegis markdown 文档，并按计划推进第一批改造，使用户画像记忆能被正确捕获、归属登录用户，并在跨会话回答中可被模型消费。

## Scope

In scope:

- Aegis spec and implementation plan.
- Identity resolution for chat/conversation/feedback Web adapters.
- Robust rule extraction for high-value user memory.
- Shared memory prompt injection for RAG and generic fallback paths.
- Regression tests where feasible.
- Production module compile verification.

Out of scope for this slice:

- New database tables.
- Full knowledge-base candidate review queue.
- LLM-based memory extraction.
- Milvus vector memory retrieval.
- Repairing unrelated agent test baseline compile failures.

## Non-Goals

- Do not make all user statements memorable.
- Do not use assistant guesses as user facts.
- Do not remove `default` fallback entirely.
- Do not change public `MemoryEnginePort` methods.

## BaselineReadSetHint

- `docs/memory-system-improvement-plan.md`
- `docs/Agent_Memory_系统改进设计方案.md`
- `docs/aegis/plans/2026-05-19-phase-e-memory-loop.md`
- `KernelChatPipeline`
- `DefaultMemoryEnginePort`
- `SeahorseChatController`
- `LocalRagPromptAdapter`

## ImpactStatementDraft

ArchitectureReviewRequired: yes.

Shared behavior affected:

- user identity source-of-truth
- memory write ownership
- prompt assembly ownership
- cross-session answer grounding
