# TodoCheckpointDraft

Current todo:

- [x] Use isolated worktree `D:\code\seahorse-agent\.worktrees\ai-infra-contextpack-runtime` on branch `codex/ai-infra-contextpack-runtime`.
- [x] Confirm Approval query/decision API was already implemented and avoid duplicating Phase 3.
- [x] Add runtime ContextPack prompt plan.
- [x] Add tests for ContextPack formatting, secret filtering, budget behavior, and legacy memory fallback.
- [x] Add tests for Agent loop first-turn prompt preference and Local RAG prompt preference.
- [x] Extend `ContextWeaverPort` without breaking existing memory-only lambdas.
- [x] Implement `DefaultContextWeaver` ContextPack formatting.
- [x] Carry `ContextPack` through `AgentLoopRequest`, `PromptContext`, and `StreamChatContext`.
- [x] Update Agent loop, chat response support, and local RAG prompt assembly to prefer ContextPack over legacy memory.
- [x] Re-run focused verification after aligning existing fragile test assertions to current stable tool-loop contracts.

Completed todos:

- ContextPack runtime consumer path is implemented.
- Legacy `MemoryContext` path remains active when ContextPack is absent or produces no prompt text.
- Existing Approval API/runtime work was not duplicated.

Active slice: completion candidate for ContextPack runtime prompt integration.

Evidence refs:

- `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=KernelAgentLoopTests,LocalRagPromptAdapterTests,MemoryWorkflowRoutingTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=DefaultContextWeaverContextPackTests,KernelContextPackBuilderServiceTests' test`
- `git diff --check`

Blocked-on items:

- External code-review subagent could not be spawned because the thread agent limit was reached. A local pre-merge review was performed instead.

ResumeStateHint:

- If resumed before merge, inspect `git status --short --branch`, re-run the two Maven commands above, run `git diff --check`, then commit branch `codex/ai-infra-contextpack-runtime`.
- Remaining AI-Infra work after this slice starts with automatic ContextPack builder integration into the chat/RAG/runtime producer path.

DriftCheckDraft:

- Original task still served: yes, this advances Phase 4 after stale handoff reconciliation.
- Compatibility boundary held: yes, runtime consumes supplied ContextPack but does not rewrite retrieval/memory producers.
- New owner/fallback introduced: no new owner; `ContextWeaverPort` remains the prompt formatting port, and `MemoryContext` remains a fallback.
- Retirement track explicit: `MemoryContext` fallback is retained until ContextPack builder integration covers RAG, memory, tool result, and user input sources.
- Decision: continue to commit and merge after verification.
