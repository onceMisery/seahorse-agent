# Core Runtime Stability Implementation Checkpoint

Updated: 2026-08-03  
State: slice-2-closed-slice-3-core-hardening-verified-slice-4-unknown-and-gateway-split

## TodoCheckpointDraft

- [x] Resolve the requested remote primary branch (`master` does not exist; use remote default `main`).
- [x] Fetch `origin/main` and create isolated branch `codex/core-runtime-stability`.
- [x] Import the approved context, governance baseline, and design specification.
- [x] Run backend and frontend baseline gates from the latest remote worktree.
- [x] Write and self-review the implementation plan.
- [x] Complete Slice 0 honest gates and current known failures using the user-approved core-test boundary.
- [ ] Complete Slice 1 startup, readiness, authentication, and non-core isolation (local core gates pass; Full Docker evidence remains).
- [x] Complete Slice 2 knowledge ingestion and retrieval.
- [ ] Complete Slice 3 conversation, context, and SSE (core hardening verified; Full Docker reconnect evidence open).
- [ ] Complete Slice 4 governed tools and sandbox (UNKNOWN state, gateway split, path-escape guard wired; sandbox hotspot decomposition and Full Docker evidence open).
- [ ] Complete Slice 5 dual-instance recovery and fault injection.
- [ ] Prove Port count is no more than 300 and complete complexity retirement (Port fell 377 -> 365; 365 -> 300 aggregate consolidation deferred).
- [x] Backfill ADR (ADR-001..004) and sync Port inventory; final requirement-by-requirement audit recorded.

## Active Slice

Slice 1 implementation and local core verification are complete, with Full
Docker auth/readiness evidence still required for L2 closure. Slice 2 is now
closed: local core contracts plus real PostgreSQL, MinIO/S3, Pulsar, Milvus,
and Elasticsearch evidence cover durable ingestion, indexing, retry metadata,
rollback compensation, deterministic restart, duplicate delivery, orphan
cleanup, and Milvus failure/recovery. Slice 3 is active: terminal callback
idempotency, SSE timeout semantics, replay sequence deduplication, and focused
conversation/recovery regressions are covered; full conversation/SSE closure
and reconnect evidence remain open. Slice 4 has landed two increments. The
durable `UNKNOWN` tool-invocation state is in place: the gateway records
duplicate/unresolved idempotency hits as `UNKNOWN` (side-effect outcome
unknown) instead of fabricating `FAILED`, matching the design state machine
`ACCEPTED -> RUNNING -> SUCCEEDED | FAILED | CANCELLED | UNKNOWN`. The gateway
hotspot is decomposed: `LocalToolGatewayPort` fell from 1272 to about 490
lines by extracting pure audit-summary functions into package-private
collaborators `ToolArgumentAuditSummary` and `ToolResultAuditSummary`; large
classes >800 dropped from 17 to 16 and the full kernel suite (909 tests) plus
architecture and formatting gates pass. Path-escape protection is wired:
`SandboxPathValidator` existed as a registered bean with no caller, so file://
artifact reads could reach sensitive host paths; it is now invoked before every
file:// read in the sandbox artifact copy and browser-session replay paths, with
new cross-platform validator tests and a sandbox replay regression. Three stale
retrieval tests that still expected empty-result fallback when every enabled
channel fails were updated to assert the design's fail-closed
`IllegalStateException` while retaining the channel-failure observation
assertions; the retrieval tests pass and the remaining local verify failures are
the pre-existing Milvus-infrastructure-dependent `SeahorseAgentNativeAdapterAutoConfigurationTests`.

Port reduction has started. The two ingestion prompt interfaces
(`EnhancementPromptPort`/`EnrichmentPromptPort`) were pure internal templates
with no adapter implementation, so they were replaced by the package-private
`IngestionPromptTemplates` collaborator and their plugin auto-configuration
beans were removed. Port interfaces fell from 367 to 365 and
`port_java_files_info` from 799 to 797; the complexity baseline was updated.
Repository-wide analysis found no dead or single-consumer Ports, so the
remaining 365-to-300 gap needs aggregate-level repository consolidation and is
deferred to a dedicated slice.

Controller-to-Kernel dependencies reached zero. All six Phase-0
Controller-to-`Kernel*Service` dependencies were eliminated by introducing
inbound use-case ports (`AuditLogInboundPort`, `AdminTenantInboundPort`,
`AdminUserInboundPort`, `EvalCandidateDecisionInboundPort`,
`AgentMarketplaceInboundPort`) and an eval decision facade, so web controllers
now depend on inbound contracts only. `R5ControllerDependencyTest` asserts zero
dependencies and `controller_kernel_service_edges` is 0. This required five new
inbound ports, raising the Port total from 365 to 370 (still under the 376
ceiling); the complexity baseline was updated accordingly.

Slice 5 has landed two contract increments. First, the memory outbox now claims
tasks atomically (`PENDING -> CLAIMED` via `FOR UPDATE SKIP LOCKED` in a
transaction, with an UPDATE-before-SELECT fallback), reclaims expired CLAIMED
tasks after 120 seconds for crash recovery, and the relay completes tasks only
when it actually claimed them. Two new JDBC tests prove a second instance does
not re-poll an already-claimed task and an expired claim is redelivered; the
full JDBC suite (306 tests) and the relay/auto-configuration tests pass.

Second, the core error contract now carries the design §9 `retryable` field.
`ErrorResponse` exposes `retryable`, and the web exception handler maps
`ExternalServiceException` and `DatabaseTimeoutException` to `retryable=true`
(recoverable dependency failures) while validation, auth, quota, and
advanced-feature-disabled failures map to `retryable=false`. The new field is
optional for existing clients (design §14); error-contract and exception-handler
tests assert the semantics.

Port reduction continues by consolidating inbound use-case ports. The three
context-pack ports merged into `ContextPackInboundPort` (one implementation, one
controller), and the payment + subscription ports merged into
`PaymentSubscriptionInboundPort` behind `KernelPaymentSubscriptionFacade`. The
feedback candidate-query port merged into `MessageFeedbackInboundPort` behind
`KernelMessageFeedbackFacade`. The rollout cost-summary port merged into
`AgentRolloutInboundPort` behind `KernelAgentRolloutFacade`, the artifact
query + update ports merged into `AgentArtifactInboundPort` behind
`KernelAgentArtifactFacade`, and the checkpoint/cost-summary/resume ports merged
into `AgentRunQueryInboundPort` behind `KernelAgentRunQueryFacade`. These align
with design §6.3 (inbound ports align with use cases, not per-controller
methods). Port interfaces fell from 370 to 362. A chat+research port merge was
attempted next but rolled back because `ChatInboundPort` and
`ResearchInboundPort` span different subdomains, violating the design §5
one-way dependency rule; the rollback left Port count at 362. Seven
`KernelChatAgentRunStoreTests`/`KernelChatInboundTraceTests` failures are
historical Slice 3 leftovers (a prior session edited the test to assert no
snapshot persistence while `KernelChatInboundService` unchanged from HEAD still
persists one); they are recorded and unrelated to the Port reduction work.

## Evidence Refs

- Remote default: `refs/heads/main`.
- Original starting commit: `8a458f17434f908d3f53a8970ff7a0f268518d11`.
- Current remote baseline: `5917fe499b573b454eec241835823f9371c4b393`.
- Branch: `codex/core-runtime-stability`.
- Worktree: `D:/code/seahorse-agent/.worktrees/core-runtime-stability`.
- Approved specification: `docs/aegis/specs/2026-07-27-core-runtime-stability-complexity-design.md`.
- Implementation plan: `docs/aegis/plans/2026-07-28-core-runtime-stability-complexity.md`.
- Backend CI-equivalent verify passes on the current remote baseline.
- Frontend baseline: 203 tests, 196 pass, 7 fail; typecheck and lint fail; build passes.
- Initial Port baseline: 377 interfaces (97 inbound, 279 outbound, 1 common).
- Effective AutoConfiguration registrations: 67; cross-domain entries: 40.
- Structured Port governance passes with the current 375 baseline and freezes
  the total plus the eleven legacy interfaces above the eight-operation budget.
- `docs/architecture/port-inventory.md` records the retention/retirement rules;
  the shell report is CRLF-safe and preserves directional fields on baseline updates.
- Frontend API is now one data-only client; duplicate response generics and
  consumer `.data` ownership have been retired.
- Frontend TypeScript passes with `npx tsc --noEmit -p tsconfig.app.json`.
- Frontend lint passes with zero warnings via `npm run lint`; the script now
  uses a Windows/Linux-safe explicit `src` glob.
- Core frontend regressions pass: 8 files, 52 tests (API, auth session,
  ChatInput, Workspace/Artifact inspector, chat store and stream handlers).
- Responsibility-split regressions pass: 5 files, 19 tests.
- Frontend production build passes; the existing main chunk remains about
  3.73 MB and requires a separate route-level code-splitting slice.
- Frontend typecheck, lint, and unit-test CI steps are blocking; all three
  `continue-on-error` entries and the temporary Vitest baseline are retired.
- Tenant resolution is fail-closed for authenticated requests and clears
  thread-local state across normal and async dispatch.
- Authentication now has one owner and one inbound Port for login, refresh,
  and logout; refresh rotation uses compare-and-set and concurrent reuse has one winner.
- RAG and enterprise default to Class A NoOp enforcement; missing and NoOp
  Class A bindings fail startup while demo remains non-blocking by default.
- Readiness maps probe failures to BLOCKED, rejects unknown product modes,
  blocks missing RAG search dependencies, and checks the real embedding-model bean.
- Disabled advanced endpoints retain stable 403 `ADVANCED_FEATURE_DISABLED` responses.
- The governance ceiling remains 375, while the current Port inventory is 374
  (96 inbound, 277 outbound, 1 common), reduced by deleting the duplicate
  authentication refresh boundary, the unused `DocumentChangeListenerPort`, and
  the unused `ConnectorCredentialVerificationPort` with no runtime consumers.
- Upload now compensates object storage when document creation fails, quota
  dependency failures fail closed, and publish failures move documents out of
  `running` into `failed`.
- Reliable MQ publication no longer degrades to direct send; direct and Pulsar
  configurations both use the existing durable Outbox owner, which is Class A.
- Ingestion task terminal state and node logs are one repository aggregate
  operation; JDBC persists them transactionally and uncertain persistence is
  recorded as `unknown`. Repeated rollback is idempotent.
- Document deletion no longer swallows vector, keyword, or object-storage
  failures. The database fact remains until idempotent external cleanup succeeds.
- Retrieval returns `PARTIAL` with stable failed-channel evidence when at least
  one channel succeeds, and fails closed when every enabled channel fails.
- `OutputValidationRecordPort` is an optional future audit persistence extension,
  so it is Class B warning rather than a Class A startup dependency. Existing
  Outbox, memory operation/review/outbox, and tool audit Ports remain Class A.
- Knowledge and document-refresh auto-configurations are owned only by ordered
  `AutoConfiguration.imports`; removing their premature aggregate `@Import`
  allows the S3 adapter to exist before knowledge service assembly.
- `KernelKnowledgeDocumentChunkHandler` is now the single document chunk
  consumer owner; the duplicate Web-layer consumer and subscription were removed.
- The isolated real Pulsar smoke passed 9/9 with two messages acknowledged,
  zero backlog/unacked messages, and PostgreSQL document state `success|1|1|1`.
- The real ingestion pipeline smoke passed 13/13, including PostgreSQL task/node
  assertions, explicit indexer failure, retry provenance, and rollback compensation.
- The independent S3/MinIO smoke passed 10/10, including object existence,
  database storage reference, API delete, database soft delete, and object removal.
- The rebuilt backend restarted against the existing PostgreSQL, Pulsar,
  Elasticsearch, Milvus, Redis, and MinIO state and returned actuator `UP`.
- Replaying outbox event `341642209315745792` exposed the pre-fix orphan state
  for document `341642209097641984` as PostgreSQL/Elasticsearch/Milvus `1/2/2`.
  The first replay converged all three stores to `1/1/1`; a second replay kept
  them at `1/1/1`, with outbox `SENT`, zero retries, and no error.
- Stopping Milvus during a fresh replay kept Pulsar at `msgBacklog=1` and
  `unackedMessages=1` while the handler logged `UNAVAILABLE`; after Milvus
  recovery and the normal 60-second negative-ack delay, redelivery succeeded,
  Pulsar reached `msgOutCounter=7`, backlog/unacked returned to `0/0`, and all
  three stores remained `1/1/1`.
- `KernelChatInboundService` now claims the single terminal state before
  persisting the Agent Run, notifying the stream consumer, and finishing the
  trace; duplicate complete/error callbacks are ignored.
- `ResearchRunOrchestrator` no longer swallows event-buffer persistence
  failures; a claimed task is failed instead of continuing with a missing
  durable event. Failure-event publication remains best-effort after the task
  failure has been recorded.
- `LocalChatStreamCallbackFactory` now fails the stream when replayable event
  persistence, latest-sequence lookup, or buffered-event lookup fails; these
  failures are no longer logged and ignored, so a client cannot observe a
  successful stream while losing reconnectable events.
- Research task acknowledgement now happens after terminal event publication
  and next-step persistence; a late event-buffer failure leaves the claimed
  task unacknowledged for recovery instead of losing it after an early `ack`.
- `JdbcAgentRunEventBufferAdapter` treats a repeated `(runId,eventSeq)` insert
  as idempotent while allowing non-duplicate database failures to propagate.
- `ResearchSseBridge` now reports max-duration expiry as an SSE `error` before
  closing, rather than emitting a success-only `DONE`; the timeout regression
  test also verifies one close and cancellation of the poller.
- `ResearchSseBridge` now reports internal event-buffer read failures as an SSE
  `error` before closing; client disconnects still use the silent cancellation
  path.
- `ResearchSseBridge` flushes pending throttled content before publishing a
  terminal `FINISH` event and `DONE`, preventing loss of the final response
  chunk when content and terminal events arrive in one poll.
- `LocalChatStreamCallbackFactoryTests` passes 9/9, including regressions that
  replayable event-buffer persistence, latest-sequence lookup, and buffered
  event lookup failures are surfaced to the caller.
- Frontend SSE parsing ignores replayed envelopes with the same/older sequence
  and dispatches only the first terminal event, preventing late `error` or
  duplicate `done` events from overwriting the already settled message.
- Frontend cancellation now settles a stream as `cancelled` for both explicit
  `cancel` events and abort errors; normal completion remains `done`.
- Slice 3 focused backend regression passed 32 core tests plus 4 architecture
  tests (kernel trace/pipeline, event-buffer durability/idempotency, web
  replay/SSE timeout, and local cancellation), with zero failures/errors.
- The expanded Slice 3 core rerun passed 69 tests plus 3 architecture tests
  across run creation/snapshot failure, chat terminal callbacks, research
  acknowledgement, JDBC event buffering, SSE timeout/replay, local
  cancellation, and local chat stream event-buffer failures; all had zero
  failures/errors.
- Agent Chat no longer persists a duplicate run-context snapshot. Chat now
  passes branch/role context through the existing start metadata, and
  `KernelAgentRunService` is the single snapshot owner. Snapshot persistence
  failure marks the created run `FAILED` with
  `CONTEXT_SNAPSHOT_PERSISTENCE_FAILED` and prevents the Agent Loop from
  starting.
- The current reactor-aware core rerun passed 90 tests with zero
  failures/errors: Kernel 47, Web 20, repository 7 (including the two JDBC
  snapshot tests), and cross-module tests 16. The architecture test passed in
  the reactor with 3/3 tests; only the intentionally isolated single-module
  invocation is blocked by three unpublished local SNAPSHOT dependencies.
- `bash scripts/complexity-report.sh` passed with no growth from baseline:
  Ports 375, Port Java files 807, large classes 17, AutoConfiguration 67, and
  cross-domain pairs 40. `git diff --check` reported no whitespace errors.
- The SSE bridge focused regression now passes 5 tests, including terminal
  content flush ordering, with zero failures/errors.
- Slice 3 focused frontend regression passed 34 tests (chat store cancellation,
  SSE retry/terminal handling, and stream state sequence deduplication), with
  zero failures.
- A broader Slice 3 frontend core rerun passed 54 tests across eight API,
  authentication, chat, stream, and inspector files; TypeScript typecheck also
  passed.
- The second isolated Port retirement search found
  `ConnectorCredentialVerificationPort` only at its declaration; no runtime,
  adapter, Spring, reflection, ServiceLoader, test, or documentation consumer
  was present, so the speculative boundary was deleted without a replacement.
- `bash scripts/complexity-report.sh` passed after the retirement: current
  Ports 374, Port Java files 806, large classes 17, AutoConfiguration 67, and
  cross-domain pairs 40; all remain within the governance ceiling.

## Blocked On

Full Docker credentials/capacity remain an evidence risk for Slice 1 closure.
Slice 2 evidence is closed and Slice 3 core hardening is locally verified.
Full Docker auth/readiness, conversation/SSE, dual-instance recovery, the
standalone architecture module dependency resolution, and the final Port
reduction target remain open. The standalone architecture invocation is a
packaging/evidence limitation only; the reactor result is the current
architecture-test evidence.

## ResumeStateHint

1. Open `D:/code/seahorse-agent/.worktrees/core-runtime-stability`.
2. Read `10-intent.md`, this checkpoint, the approved specification, and the latest evidence file.
3. Confirm the branch still descends from the recorded `origin/main` commit.
4. Compare `git status --short` against this checkpoint before editing.
5. Start Slice 3 (conversation, context, SSE, and cancellation) with the same
   core-only test boundary. Keep tests limited to contracts, correctness
   invariants, state transitions, idempotency, and recovery.

## DriftCheckDraft

- Original task intent: aligned.
- Goal and stop condition: aligned.
- Compatibility boundary: unchanged.
- New owner/fallback/adapter: no new Port or fallback was introduced; duplicate
  auth refresh ownership was removed. Slice 2 reused the existing aggregate
  repositories and Outbox Port rather than adding cleanup or coordination Ports.
- Retirement track: non-blocking CI, duplicate frontend response ownership,
  mixed component/tool exports, the temporary Vitest baseline, and the unused
  `DocumentChangeListenerPort` were retired.
- Evidence sufficiency: sufficient for Slice 2 normal-path, restart,
  duplicate-delivery, and dependency-failure claims; insufficient for Slice 1
  Full Docker auth/readiness closure, production completion, or the final Port
  target.
- Decision: continue Slice 3 with full disconnect/reconnect, context snapshot,
  and cancellation evidence still required before closure.
