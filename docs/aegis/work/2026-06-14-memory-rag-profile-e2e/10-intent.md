# Memory/RAG/Profile E2E Intent

## Requested Outcome

Analyze whether the deployed Seahorse Agent memory, RAG, and user-profile loops work end to end, fix defects including the chat "login expired" failure, and verify against the local full Docker deployment.

## Scope

- Frontend chat stream authentication.
- Backend memory extraction, profile fact formation, keyword index, and recall behavior.
- Backend global RAG retrieval over configured knowledge bases and vector collections.
- Docker deployment rebuild/restart only for changed application services.

## Non-Goals

- Do not wipe Docker volumes or existing database/vector data.
- Do not revert unrelated dirty documentation changes.
- Do not replace the existing memory or RAG architecture unless evidence shows the loop cannot close safely with targeted changes.

## Baseline Read Set Hint

- `frontend/src/stores/chatStore.ts`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/ProfileSlotResolver.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/MemoryProfileValueNormalizer.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/feature/retrieval/VectorGlobalSearchFeature.java`
- `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcKnowledgeBaseQueryAdapter.java`

## Impact Statement Draft

The chat auth defect prevents any authenticated SSE conversation from completing. The memory/profile defect allows multi-slot Chinese profile facts to partially update, leaving stale active response-style facts. The RAG defect makes global retrieval sensitive to stale or empty collections and repeated embedding latency, preventing knowledge-base answers from surfacing in real conversations.
