# TodoCheckpointDraft

## Current Todo

- [x] Read Agent capability phase documents.
- [x] Inspect current worktree and existing Agent/RAG/Memory baseline.
- [x] Create Aegis work record for the Agent capability execution slice.
- [x] Implement Agent search and metadata tools.
- [x] Implement Agent memory tools.
- [x] Add focused tests.
- [x] Verify target regression and package.
- [x] Rebuild and restart Docker backend.
- [x] Run runtime smoke checks.
- [x] Update evidence and reflection.

## Active Slice

Completion candidate for a deployable vertical Agent tool ecosystem over the existing `KernelAgentLoop`, without replacing RAG or adding a parallel retrieval owner.

## Completed Todos

- Added built-in Agent tools: `search_knowledge_base`, `query_metadata`, `memory_read`, `memory_write`, and `memory_forget`.
- Registered built-in tools through Spring auto-configuration and feature switches.
- Preserved server-side user/conversation/question scope by injecting reserved `_seahorse*` arguments inside `KernelAgentLoop`.
- Loaded activated memory for Agent mode and injected it into the first model turn so cross-dialog profile memory does not depend only on voluntary tool use.
- Triggered memory governance after Agent `memory_write`, so explicit profile facts can promote to long-term and semantic memory in the same request.
- Normalized occupation/profile semantic keys and deduplicated loaded profile slots to avoid stale conflicting occupation memories polluting model context.
- Rebuilt and restarted the Docker backend with `SEAHORSE_AGENT_CHAT_AGENT_MODE_ENABLED=true`.
- Verified admin cross-dialog memory: one conversation stored "我是学生"; a separate conversation answered that the user's occupation is "学生".

## Evidence Refs

- `./mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelAgentLoopTests,KernelChatInboundServiceAgentModeTests,SeahorseAgentKernelAgentAutoConfigurationTests,AgentToolPortAdapterTests,KernelMemoryGovernanceServiceTests,DefaultMemoryEnginePortTests" test "-Dspotless.check.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"`: BUILD SUCCESS, 55 tests, 0 failures.
- `./mvnw.cmd -pl seahorse-agent-spring-boot-starter -am -DskipTests package "-Dspotless.check.skip=true"`: BUILD SUCCESS.
- `docker compose -f docker-compose.full.yml build backend`: image `seahorse-agent-backend:latest` built successfully.
- `docker compose -f docker-compose.full.yml up -d --no-deps backend`: `seahorse-backend` recreated and started on port 9090.
- Runtime SSE: admin login succeeded; conversation A called `memory_write`; conversation B called `memory_read` and answered "你的职业是学生".

## Blocked-On Items

- None for the deployable vertical slice.

## ResumeStateHint

If continuing beyond this slice, start from `git status --short --branch`, inspect the Agent tool package, then choose the next phase item from `docs/agent-capability-phases/`. Full multi-phase UI/HITL/enterprise governance remains outside this slice.

## DriftCheckDraft

- Serves original task intent: yes.
- Serves stop condition: yes for the requested deployable runtime slice and Docker verification.
- Inside compatibility boundary: yes; existing RAG pipeline remains the default path and retrieval still has one owner.
- New owner/fallback/adapter introduced: built-in Agent tool adapters over existing ports; no second retrieval owner.
- Retirement track explicit: no legacy path retired; stale occupation conflict is mitigated at activation/read time and by same-request governance promotion.
- Evidence enough for next claim: yes.
- Decision: continue-to-completion-response.
