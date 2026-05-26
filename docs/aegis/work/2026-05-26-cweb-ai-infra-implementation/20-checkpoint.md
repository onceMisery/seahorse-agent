# C Web AI Infra Implementation - Checkpoint

- Task ID: 2026-05-26-cweb-ai-infra-implementation
- Current todo: commit and merge to the actual trunk branch.
- Active slice: branch integration.
- Completed todos:
  - Created and used isolated worktree `D:\code\seahorse-agent\.worktrees\cweb-ai-infra` on branch `codex/cweb-ai-infra`.
  - Added C Web AI Infra docs and marked old enterprise/local/mesh phases as historical or advanced-extension references.
  - Added `ProductMode`, `AdvancedFeature`, and `AdvancedFeatureGate` in web adapter; `consumer-web` forces advanced features disabled even if an advanced flag is misconfigured.
  - Added controller guards for sandbox, connector, secret, handoff, intent tree, ingestion, tool catalog, rollout, quota/resource ACL management, production gate, enterprise readiness, agent definition/factory/tool-binding/eval, and global approval management APIs.
  - Added `A2A` run trigger guard so default Web run API cannot start A2A/mesh-style runs.
  - Added minimal structured stream events, run snapshot query, pending approval query, user/admin approval decision boundary, and frontend timeline/source/artifact/approval/quota/memory rendering.
  - Added server-side `AgentArtifact` domain/query/API/persistence/download policy and frontend artifact service/rendering path.
  - Added server-side task template, quota summary, user memory privacy, and conversation attachment APIs.
  - Added conversation attachment domain, repository port/JDBC adapter/schema, upload/list/delete controller, and `StreamChatCommand.attachmentIds` passthrough.
  - Added frontend attachment upload in chat input, including first-message pending conversation id support, attachment chips, status display, delete, and repeated `attachmentIds` query params on send.
  - Added frontend task template selector, quota summary display, quota-exceeded blocking, and high-cost confirmation.
  - Added frontend dislike reason/comment dialog wired to existing feedback `reason/comment` backend contract.
  - Added backend run cost summary query/API and frontend trace-panel cost summary rendering.
  - Added backend feedback evaluation candidate query/API and frontend AI Infra Console feedback tab.
  - Persisted and read back `t_message.agent_run_id` so message history and feedback can link user-visible messages to agent runs.
  - Added user-side Memory center page with memory list, delete, and privacy mode toggle; added protected route and sidebar entry.
  - Replaced the welcome screen's separate input path with the shared `ChatInput`, so first-message chat uses the same attachment/template/quota path as later messages.
  - Ran targeted backend tests and frontend production build.
- Blocked on: none for the current C Web minimal closure.
- Known deferred work:
  - Full SSE event replay by sequence after `lastEventSeq`; current resume path returns snapshot/done.
  - Attachment content parsing into model context; current loop persists uploads and passes attachment IDs through the chat contract.
  - Full research Web Agent orchestration with bounded plan/search/fetch/read/synthesize/write/verify steps; current work includes web search/fetch adapters and templates, not full Deer Flow style orchestration.
  - Backend source/citation event emission from every RAG/Web source into live source cards; frontend and snapshot structures exist.
  - Complete feedback-to-evaluation candidate pipeline; current loop captures vote/reason/comment in the existing message feedback store.
  - Paid billing/product packaging; current work is quota/cost transparency and guardrails only.
- ResumeStateHint: Continue from `D:\code\seahorse-agent\.worktrees\cweb-ai-infra`; do not use the root working tree for this branch.
- Next step: commit `codex/cweb-ai-infra`, merge into the actual local trunk branch, then run post-merge verification.

## DriftCheckDraft

- Does the current work still serve the original task intent? yes.
- Does the current work still serve the goal and stop condition? yes; the implemented scope is the C-side Web AI Infra minimal closure, not enterprise/local/mesh future scope.
- Did the slice stay inside the compatibility boundary? yes; consumer-web default path remains Web-only and advanced/local/mesh APIs are gated.
- Did any new owner, fallback, adapter, or branch appear? yes; `AdvancedFeatureGate` owns web exposure policy, and conversation attachments use a kernel port plus JDBC/storage adapters. Kernel still depends on ports, not Spring/JDBC/Web.
- Is the retirement track still explicit? yes; local agent, host shell, sandbox, connectors, A2A/mesh, and arbitrary MCP are retained only as advanced/enterprise references and are not C Web baseline.
- Did the evidence bundle grow enough to support the next claim? yes for the C Web minimal closure and branch integration; no for claiming all future research orchestration/billing/mesh capabilities are complete.
- Decision: continue to commit and merge.
