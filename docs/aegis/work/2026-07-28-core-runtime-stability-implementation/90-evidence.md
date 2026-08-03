# Core Runtime Stability Implementation Evidence

Updated: 2026-08-01  
State: draft

## Source Evidence

| Evidence | Result |
| --- | --- |
| `git ls-remote --symref origin HEAD` | Remote default is `refs/heads/main`; remote `master` is absent |
| `git fetch origin main --prune` | Succeeded |
| `git show origin/main` | Current baseline is `5917fe499b573b454eec241835823f9371c4b393` |
| `git worktree add` | Created `codex/core-runtime-stability` from `origin/main` |
| `git check-ignore -v .worktrees` | `.worktrees/` is ignored by repository `.gitignore` |
| `git merge --ff-only origin/main` | Fast-forwarded the isolated branch from `8a458f17` to `5917fe49` |

## Baseline Gate Evidence

| Gate | Command / evidence | Result |
| --- | --- | --- |
| Backend L0 | `.\mvnw.cmd -q -B -ntp verify "-Dtest=!SeahorseE2E*" "-Dsurefire.failIfNoSpecifiedTests=false"` | PASS, exit 0, about 303 seconds on `5917fe49` |
| Frontend typecheck | `npx tsc --noEmit -p tsconfig.app.json` | FAIL, exit 1, 599 output lines; dominant root is Axios runtime/type disagreement plus independent field/test errors |
| Frontend lint | `npm run lint` | FAIL, baseline 116 errors and 49 warnings |
| Frontend unit | `npm test -- --reporter=json --outputFile=vitest-baseline.json` | FAIL: 128 suites, 124 pass, 4 fail; 203 tests, 196 pass, 7 fail |
| Frontend failures | Vitest JSON | Six `ChatInput` run-profile/role-card tests and one `WorkspaceInspector` context-snapshot test |
| Frontend build | `npm run build` | PASS; main JavaScript chunk about 3.7 MB with chunk warning |
| Port inventory | source declaration inventory | 377 public interfaces: 97 inbound, 279 outbound, 1 common; 809 Java files are not 809 Ports |
| AutoConfiguration | nonblank, noncomment imports | 67 effective registrations; 106 is the physical line count |
| Cross-domain | ArchUnit whitelist | 40 class pairs |

The earlier JDBC `runtime_node_id` failure applied to `8a458f17`. Commit
`7cedc311` repaired the test fixture, and the full backend verify now passes.
No local repair is claimed for a defect already fixed by the updated remote baseline.

## Slice Evidence

### Slice 0: Honest Gates and Frontend Contract

| Gate / change | Command / evidence | Result |
| --- | --- | --- |
| Port architecture governance | `PortArchitectureTest` and architecture test module | PASS; real baseline 375 (96 inbound, 278 outbound, 1 common), no count growth, default maximum eight operations, eleven legacy exceptions frozen |
| Port architecture test | `.\mvnw.cmd -B -ntp -pl seahorse-agent-architecture-tests -Dtest=PortArchitectureTest test` | PASS, 3 tests, 0 failures/errors |
| Complexity report | `bash scripts/complexity-report.sh` | PASS after making AWK counters CRLF-safe: Ports 375, Port files 807, large classes 17, AutoConfig 67, cross-domain 40 |
| Port inventory document | `docs/architecture/port-inventory.md` | Records authority, counts, retention test, legacy budgets, and semantic `<=300` retirement target |
| Data-only HTTP owner | `frontend/src/services/api.ts` plus API regression | PASS; one public data-only client, raw Axios remains private |
| TypeScript | `npx tsc --noEmit -p tsconfig.app.json --pretty false` | PASS, exit 0 |
| ESLint | `npm run lint` | PASS, exit 0, zero errors and zero warnings |
| Core frontend regressions | focused Vitest command for API/auth/chat/store/stream/inspector | PASS, 8 files and 52 tests |
| Responsibility split regressions | focused Vitest command for knowledge model candidates, Markdown, and route contracts | PASS, 5 files and 19 tests |
| Production build | `npm run build` | PASS, exit 0; main JS 3,725.64 kB / gzip 1,073.80 kB, Vite chunk warning retained |
| Diff integrity | `git diff --check` | PASS; only existing line-ending conversion notices |
| Blocking CI | `.github/workflows/ci.yml` inspection | Typecheck, lint, unit tests, and build are blocking; no `continue-on-error` remains |
| Temporary baseline retirement | worktree/status inspection | `frontend/vitest-baseline.json` removed |
| Aegis structural bundle/check | `aegis-workspace.py bundle/check` | Attempted; not clean because this manually created work record has no JSON sidecars and the pre-existing workspace has a missing `adr` directory, two stale index links, and older schema-invalid AgentScope artifacts |

Testing follows the user's updated boundary: no blanket TDD and no new test per
mechanical edit. Only core correctness, shared contracts, and regression-prone
owners were exercised locally. The full frontend suite was not rerun in this
batch; CI retains the full `npm test` command as a blocking gate.

Repair track: corrected the API type/runtime owner, ChatInput run-profile UI
path, Studio run/trace identity, shared TypeScript contracts, Hook dependency
correctness, and cross-platform lint entry point.

Retirement track: removed duplicate Axios response ownership, obsolete casts
and generics, mixed component/tool exports, non-blocking CI settings, and the
temporary Vitest baseline. No public Port was added or merged into a God Port.

### Slice 1: Startup, Readiness, Authentication, and Isolation

| Gate / change | Command / evidence | Result |
| --- | --- | --- |
| Tenant and auth core regression | focused Maven reactor for tenant, exceptions, auth controller, JDBC refresh, auth concurrency, and auto-configuration | PASS; 29-module reactor, Web 11, JDBC 3, test module 53 |
| Frontend refresh contract | TypeScript, ESLint, and `frontend/src/services/api.test.ts` | PASS; refresh token is stored, sent, rotated with access token, and supplied on logout |
| Startup/NoOp guard | `SeahorseAgentNoopPortGuardTests` | PASS; 13 tests covering demo, enterprise missing/NoOp failure, and real bindings |
| Readiness semantics | `KernelReadinessServiceTests` | PASS; 5 tests for failure mapping, mode validation, dependency severity, demo isolation, and NoOp health |
| Capability-disabled HTTP contract | `AdvancedFeatureControllerGateTests` | PASS; 9 tests preserve stable 403 `ADVANCED_FEATURE_DISABLED` responses |
| Slice 1 focused reactor | focused Maven reactor for readiness, startup, auth, tenant, and feature gates | PASS; 60 tests, 0 failures/errors |
| Readiness auto-configuration | canonical property tests plus kernel readiness tests | PASS; 13 auto-configuration and 5 kernel tests; embedding health uses `EmbeddingModelPort` evidence |
| Port architecture after clean rebuild | Maven `clean` reactor ending in `PortArchitectureTest` | PASS; 3 tests after removing stale deleted-interface bytecode |
| Complexity report | `bash scripts/complexity-report.sh` | PASS; Ports 375, Port files 807, large classes 17, AutoConfig 67, cross-domain 40 |
| Diff integrity | `git diff --check` | PASS; line-ending conversion warnings only |

Retirement track: removed `AuthRefreshInboundPort` and
`KernelAuthRefreshService`; `AuthInboundPort` remains cohesive at three
operations. No readiness or startup-policy Port was added.

### Slice 2: Knowledge Ingestion and Retrieval

| Gate / change | Command / evidence | Result |
| --- | --- | --- |
| Upload boundary compensation | `KernelKnowledgeDocumentServiceTests` | Covers object deletion when document creation fails and `failed` state when reliable event publication fails |
| Quota correctness | `KernelKnowledgeDocumentService` inspection and compilation | Configured storage quota dependency errors fail closed; only explicit quota rejection is propagated unchanged |
| Reliable publication | `ReliableMessageQueueAdapterTests`, `SeahorseAgentMqAdapterAutoConfigurationTests`, and NoOp guard tests | Reliable publication requires Outbox, direct/Pulsar use the decorator, and production startup treats the existing Outbox repository Port as Class A |
| Ingestion aggregate finalization | `KernelIngestionTaskServiceTests` plus JDBC reactor compilation | Task terminal state and node logs use one repository operation; JDBC override is transactional; uncertain completion persistence records `unknown` |
| Rollback idempotency | `KernelIngestionTaskServiceTests` | Repeating an already completed rollback does not repeat compensation side effects |
| Deletion convergence | `KernelKnowledgeDocumentServiceTests` | External cleanup failure is visible, the database fact is retained, and a retry converges before soft deletion |
| Partial retrieval contract | `KernelRetrievalEngineTests` | A failed channel plus a successful channel produces `PARTIAL` and stable channel evidence; all-channel failure fails closed |
| Port growth | structured source inventory | No Port interface was added; the existing aggregate repository and Outbox boundaries were reused |
| Optional output audit classification | `SeahorseAgentNoopPortGuardTests` | `OutputValidationRecordPort` is Class B because it has no real adapter and its contract documents future optional persistence; enterprise startup still enforces the real Class A Outbox, memory operation/review/outbox, and tool-audit bindings |
| Ordered knowledge assembly | `SeahorseAgentKernelDocumentRefreshAutoConfigurationTests` and `SeahorseAgentKernelAutoConfigurationTests` | S3 storage assembles the knowledge service and exactly one Pulsar subscription after removing premature aggregate imports; metadata compensation is owned by metadata auto-configuration after knowledge assembly |
| Single chunk consumer | source and runtime inspection | Deleted the Web-layer `KnowledgeDocumentChunkConsumer`; `KernelKnowledgeDocumentChunkHandler` is the canonical subscription handler and the smoke assertion matches its success log |
| Focused reactor execution | Maven focused commands with `-am` | Auto-configuration and architecture target tests require `-am` so reactor dependencies are compiled/resolved from the current worktree rather than stale or missing artifacts |
| Real Pulsar smoke | `scripts/e2e-pulsar-mq-smoke.ps1` against the isolated topic `persistent://seahorse-agent/ai/knowledge-document-chunk-core-runtime-stability` | PASS, 9/9; counters advanced from 1/1 to 2/2, backlog and unacked remained zero, and PostgreSQL recorded `success|1|1|1` |
| Real ingestion state/recovery smoke | `scripts/e2e-ingestion-pipeline-smoke.ps1 -BaseUrl http://127.0.0.1:19090` | PASS, 13/13; PostgreSQL task/node facts, explicit failed indexer state, retry provenance, and rollback compensation all matched |
| Real S3/MinIO smoke | `scripts/e2e-s3-storage-smoke.ps1` | PASS, 10/10; S3 storage reference and object existence were real, then API deletion produced DB soft delete and object removal |
| PostgreSQL/Milvus/Elasticsearch reconciliation | direct database and adapter API queries | Two knowledge documents are `success` with one persisted chunk each; the isolated Elasticsearch index has the same two `doc_id` records, and both Milvus collections contain the matching chunk entity with dimension 768 |
| Deterministic backend restart | rebuilt bootstrap JAR mounted into a recreated `core-runtime-stability-backend` container | PASS; the backend returned actuator `UP`, connected to the existing real dependencies, and restored the single Pulsar chunk subscription |
| Duplicate-delivery reconciliation | replay outbox event `341642209315745792` twice for document `341642209097641984`, then query PostgreSQL, Elasticsearch, and Milvus directly | PASS; the pre-fix orphan state was `1/2/2`, the first replay converged it to `1/1/1`, and the second replay remained `1/1/1`; outbox returned to `SENT` with retry count 0 and no error |
| Real Milvus failure and recovery | stop `seahorse-milvus`, replay the same outbox event, observe Pulsar negative-ack state, restart Milvus, and wait for redelivery | PASS; failure logged `UNAVAILABLE`, Pulsar held `msgBacklog=1`/`unackedMessages=1`, then after the 60-second negative-ack delay redelivery succeeded with `msgOutCounter=7`, backlog/unacked `0/0`, and PostgreSQL/Elasticsearch/Milvus `1/1/1` |
| Slice 2 focused regression | Maven reactor targeting ingestion, indexing, retrieval, Outbox, auto-configuration, and NoOp guard classes | PASS; 105 test reports in the focused set, 0 failures/errors; the target `seahorse-agent-tests` module reported 93 passing tests |
| Port and complexity closure check | `PortArchitectureTest`, `bash scripts/complexity-report.sh`, `git diff --check` | PASS; Port 375, Port files 807, large classes 17, AutoConfig 67, cross-domain 40, all deltas 0; 3 architecture tests passed and diff check had no whitespace errors |

Complexity track: removed three independent best-effort delete catch blocks and
the reliable-publish direct-send fallback. The retrieval evidence is represented
by domain result state, not a new Port, callback, or cross-capability facade.

Slice 2 is closed. Real PostgreSQL, MinIO, Milvus, Elasticsearch, and Pulsar
prove the normal path, application-level retry and rollback, deterministic
process restart, duplicate-delivery reconciliation, orphan cleanup, and a
real Milvus failure followed by negative-ack redelivery recovery. Dual-instance
behavior remains part of the later recovery slice.

### Slice 3: Conversation, SSE, and Cancellation (in progress)

| Gate / change | Command / evidence | Result |
| --- | --- | --- |
| Unique terminal callback | `KernelChatInboundTraceTests` | PASS; duplicate complete/error callbacks settle the run, delegate, and trace once |
| SSE timeout semantics | `ResearchSseBridgeThrottlingTests` | PASS; max-duration expiry emits `error` plus transport `DONE`, closes once, and cancels the poller |
| SSE terminal content flush | `ResearchSseBridgeThrottlingTests` | PASS; pending throttled content is flushed before a terminal `FINISH` event and `DONE`, preventing loss of the final response chunk |
| SSE event-buffer read failure and reconnect semantics | `ResearchSseBridgeThrottlingTests` | PASS; internal buffer read failure emits `error` plus `DONE`, closes once, and cancels the poller; client disconnect cancels the poller silently, and a resumed connection reads from the supplied `afterSeq` cursor; the focused class passes 5/5 |
| Resume replay contract | `SeahorseChatControllerReplayTests` | PASS; six replay/snapshot authorization and continuation tests |
| Local cancellation contract | `LocalStreamTaskPortTests` | PASS; three tests cover idempotent cancel and late handle binding |
| Frontend terminal/replay/cancellation contract | `frontend/src/hooks/useStreamResponse.test.ts`, `frontend/src/stores/chatStreamHandlers.test.ts`, and `frontend/src/stores/chatStore.test.ts` | PASS; 34 focused tests cover retry cursor, duplicate sequence, single terminal dispatch, and cancelled status |
| Event-buffer durability contract | `ResearchRunOrchestratorTests`, `JdbcAgentRunEventBufferAdapterTests` | PASS; event-buffer write failures fail the claimed task, failure-event publication is isolated, terminal events are published before acknowledgement, and repeated `(runId,eventSeq)` writes retain the first event; 13 orchestrator tests and 3 JDBC adapter tests pass |
| Focused backend regression | Maven reactor with `-am` targeting kernel/web/repository/architecture/test modules | PASS; 32 core tests plus 4 architecture tests, 0 failures/errors |
| Frontend core regression rerun | `npm test -- --run src/hooks/useStreamResponse.test.ts src/stores/chatStreamHandlers.test.ts src/stores/chatStore.test.ts src/services/api.test.ts src/utils/authSession.test.ts src/components/chat/ChatInput.test.tsx src/components/chat/workbench/WorkspaceInspector.test.tsx src/components/chat/workbench/ArtifactInspectorTab.test.tsx`; `npx tsc --noEmit -p tsconfig.app.json --pretty false` | PASS; 8 files, 54 tests, and TypeScript typecheck completed with zero failures/errors |
| Local stream event-buffer failure contract | `LocalChatStreamCallbackFactoryTests` via `./mvnw.cmd -B -ntp -pl seahorse-agent-adapter-web -am "-Dtest=LocalChatStreamCallbackFactoryTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS; 9 tests, 0 failures/errors; persistence, latest-sequence, and replay-read failures now fail the callback instead of silently losing replayable events |
| Replay read failure behavior | `LocalChatStreamCallbackFactory` inspection and focused compilation | PASS; latest-sequence and buffered-event reads now propagate an `IllegalStateException`, preserving a visible failure boundary for reconnect correctness |
| Expanded Slice 3 backend core rerun | `./mvnw.cmd -B -ntp -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-adapter-repository-jdbc,seahorse-agent-architecture-tests,seahorse-agent-tests -am "-Dtest=KernelChatInboundTraceTests,ResearchRunOrchestratorTests,KernelAgentRunServiceTests,JdbcAgentRunEventBufferAdapterTests,ResearchSseBridgeThrottlingTests,SeahorseChatControllerReplayTests,LocalStreamTaskPortTests,LocalChatStreamCallbackFactoryTests,PortArchitectureTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS; 69 core tests plus 3 architecture tests, 0 failures/errors |
| Complexity budget after stream hardening | `bash scripts/complexity-report.sh` | PASS; Ports 375, Port Java files 807, large classes 17, AutoConfig 67, cross-domain pairs 40; all deltas 0 |
| Port and complexity impact | source inventory and focused diff review | PASS; no Port added, no fallback or cross-domain facade introduced |
| Agent Chat snapshot ownership | `KernelAgentRunServiceTests`, `KernelChatAgentRunStoreTests`, and `KernelChatInboundService` inspection | PASS; 44/44 tests cover metadata handoff, one snapshot owner, snapshot JSON/columns, and persistence failure blocking Agent Loop startup |
| Current reactor-aware core rerun | `mvn -pl seahorse-agent-kernel -Dtest=KernelAgentRunServiceTests,KernelChatAgentRunStoreTests,KernelChatInboundTraceTests test`; Web/Repository focused Maven modules; `mvn -pl seahorse-agent-tests -am -Dtest=ResearchRunOrchestratorTests,LocalStreamTaskPortTests test` | PASS; 90 tests total with zero failures/errors: Kernel 47, Web 20, Repository 7, cross-module 16 |
| Current complexity and diff gates | `bash scripts/complexity-report.sh`; `git diff --check` | PASS; Ports 375, Port Java files 807, large classes 17, AutoConfiguration 67, cross-domain pairs 40; no whitespace errors |
| Reactor architecture test rerun | `mvn -pl seahorse-agent-architecture-tests -am -Dtest=PortArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS; 3 tests, 0 failures/errors, with reactor dependencies compiled from the current worktree |
| Standalone architecture module rerun | `mvn -pl seahorse-agent-architecture-tests -Dtest=PortArchitectureTest test` | BLOCKED before test execution; three unpublished local SNAPSHOT dependencies (`agent-agentscope-core`, `agent-agentscope`, `sandbox-container`) are not available from configured repositories. This does not invalidate the reactor result above. |
| Isolated Port retirement | Repository-wide source, Spring configuration, reflection, and test search for `DocumentChangeListenerPort` | PASS; the unused outbound listener boundary had no consumer or adapter and was deleted without adding a replacement Port |
| Second isolated Port retirement | Repository-wide source, Spring configuration, reflection, ServiceLoader, test, and documentation search for `ConnectorCredentialVerificationPort` | PASS; only the declaration existed, so the speculative credential-verification boundary was deleted without adding a replacement Port |
| Port retirement clean reactor | `./mvnw.cmd -B -ntp -pl seahorse-agent-kernel,seahorse-agent-architecture-tests -am clean test "-Dtest=KernelAgentRunServiceTests,KernelChatAgentRunStoreTests,KernelChatInboundTraceTests,PortArchitectureTest" "-Dsurefire.failIfNoSpecifiedTests=false"` | PASS; clean compilation across 26 reactor modules, Kernel 47 tests, and 3 architecture tests with zero failures/errors |
| Port complexity after second retirement | `bash scripts/complexity-report.sh`; `git diff --check` | PASS; current Ports 374, Port Java files 806, large classes 17, AutoConfiguration 67, cross-domain pairs 40; no whitespace errors |

Slice 3 remains open. Full disconnect/reconnect runtime evidence, durable
context snapshot verification, and end-to-end cancellation under client/server
termination are still required before closure.

### Slice 4: Governed Tools and Sandbox (in progress)

| Gate / change | Command / evidence | Result |
| --- | --- | --- |
| UNKNOWN durable state | `ToolInvocationStatus` inspection | Added `UNKNOWN` for uncertain tool side-effect outcomes, matching the design state machine `ACCEPTED -> RUNNING -> SUCCEEDED | FAILED | CANCELLED | UNKNOWN` |
| Duplicate-key UNKNOWN audit | `LocalToolGatewayPortAuditTests` | PASS; 37 tests; duplicate/unresolved idempotency hits now record `UNKNOWN` (side-effect outcome unknown) instead of fabricating `FAILED`, and the completion record keeps the error message for reconciliation |
| Tool-gateway regression | Maven reactor for policy/audit/governed/agent-loop/openapi tests | PASS; 47 tests, 0 failures/errors |
| JDBC persistence compatibility | `JdbcToolInvocationAuditRepositoryAdapterTests`, `JdbcToolInvocationIdempotencyAdapterTests` | PASS; status persists by `name()` so `UNKNOWN` is storage-compatible without schema change |
| Gateway hotspot decomposition | `LocalToolGatewayPort` extraction | `LocalToolGatewayPort` reduced from 1272 to about 490 lines by moving pure audit-summary functions into two package-private collaborators `ToolArgumentAuditSummary` (681) and `ToolResultAuditSummary` (220); no new Port, no behavior change |
| Large-class reduction | `bash scripts/complexity-report.sh` | PASS; large classes >800 fell from 17 to 16 (-1), Ports 367, AutoConfig 67, cross-domain 40 unchanged |
| Kernel full regression | `mvn -pl seahorse-agent-kernel -am test` | PASS; 909 tests, 0 failures/errors |
| Formatting gate | `spotless:check` on kernel reactor | PASS; no violations |
| Architecture gate | `PortArchitectureTest` | PASS; 3 tests, 0 failures/errors |
| Path-escape guard wiring | `KernelSandboxRuntimeService` inspection and tests | `SandboxPathValidator` existed as a bean but had no caller; now invoked before every file:// artifact read in `copyDownloadableFileArtifact` and `openArtifactObjectStream`, so sensitive-path artifacts fail closed to `BLOCKED` instead of being read |
| Path-validator coverage | `SandboxPathValidatorTests` | PASS; 7 tests cover POSIX (`/etc`, `/root`, `/proc`, `/sys`, `/dev`, `/boot`) and Windows (`C:\Windows\System32`) forbidden-path interception plus safe-workspace allowance |
| Sandbox replay regression | `KernelSandboxRuntimeServiceTests` | PASS; 74 tests; normal temp-dir browser-session-state replay is not blocked by the guard |
| Kernel full regression after guard | `mvn -pl seahorse-agent-kernel -am test` | PASS; 917 tests, 0 failures/errors |
| Retrieval fail-closed test sync | `KernelMultiChannelRetrievalEngineTraceTests`, `MetadataRetrievalFilterTests` | PASS; 23 retrieval tests; three stale tests expected empty-result fallback when every enabled channel fails, but the design mandates fail-closed; updated to assert `IllegalStateException` with the channel-failure observation events retained |
| Port reduction: prompt templates | `EnhancementPromptPort`/`EnrichmentPromptPort` removal | The two ingestion prompt interfaces were pure internal templates (no adapter implementation, only `defaults()`), so they matched the design's single-owner package-private collaborator rule and were replaced by `IngestionPromptTemplates`; `EnhancerNodeFeature`/`EnricherNodeFeature` now call the static template directly and the plugin auto-configuration no longer registers prompt beans. Port interfaces fell from 367 to 365 (outbound 270 to 268), `port_java_files_info` 799 to 797; the complexity baseline was updated to the new values |
| Port reduction regression | `EnhancerNodeFeatureTests`, `EnricherNodeFeatureTests`, `PortArchitectureTest`, `ArchitectureRulesTest`, kernel `clean test` | PASS; 2 feature tests, 4 architecture tests, and 917 kernel tests with zero failures/errors; complexity budget and formatting gates pass |
| Port reduction remaining work | repository-wide source analysis | No dead Ports (every Port has at least two consumers) and no single-consumer Ports remain; the remaining 365-to-300 gap requires aggregate-level repository consolidation (merging distinct share/permission/version boundaries), which is an architectural change to defer to a dedicated slice with Full Docker evidence |
| ADR backfill | `docs/aegis/adr/ADR-001..004` | Created all four design-required ADRs: core runtime production boundary and one-way quarantine, Port classification and budget, durable operation state and UNKNOWN semantics, production evidence gates and completion authority |
| Baseline sync | `docs/architecture/port-inventory.md`, `docs/aegis/INDEX.md`, `complexity-baseline.txt` | Port inventory updated to 96 inbound / 268 outbound / 1 common / 365 total and 797 Java files; INDEX lists the four ADRs; complexity baseline records 365 |
| Acceptance audit | design §18 checklist cross-check | Completed items verified from checkpoint: honest gates, PDF/mount fixes, blocking frontend typecheck/lint/unit, no God interfaces, cross-domain 40, no-op guard, core error/terminal/idempotency/cancellation/SSE contracts, non-core disable, hotspot reduction (large classes 17->16), ADR/baseline sync. Open items recorded: Port 370->300, Full Docker golden paths, dual-instance recovery |
| Controller-to-Kernel zero | `R5ControllerDependencyTest` | All six Phase-0 Controller-to-Kernel*Service dependencies are eliminated. New inbound ports (`AuditLogInboundPort`, `AdminTenantInboundPort`, `AdminUserInboundPort`, `EvalCandidateDecisionInboundPort`, `AgentMarketplaceInboundPort`) let web controllers depend on use-case contracts; `KernelEvalDecisionFacade` aggregates the eval candidate/regression services. `R5ControllerDependencyTest` now asserts zero dependencies |
| Web regression after port migration | `mvn -pl seahorse-agent-adapter-web -am test` | PASS; 293 web tests and 917 kernel tests, 0 failures/errors |
| Port count after inbound migration | ArchUnit inventory | Port interfaces rose from 365 to 370 because five inbound ports were required to eliminate the six Controller-to-Kernel dependencies (design §7 hard rule); 370 stays under the 376 reviewed ceiling and the complexity baseline was updated. The remaining 370-to-300 reduction requires aggregate repository consolidation |

### Slice 5: Dual-Instance Recovery (in progress)

| Gate / change | Command / evidence | Result |
| --- | --- | --- |
| Outbox atomic claim | `JdbcMemoryOutboxRepositoryAdapter` | `pollPending` now atomically claims tasks (PENDING -> CLAIMED) inside a transaction with `FOR UPDATE SKIP LOCKED`, so two relay instances cannot both claim the same batch; the no-transaction fallback claims via an UPDATE-before-SELECT and short-circuits when zero rows are claimed |
| Outbox restart recovery | `JdbcMemoryOutboxRepositoryAdapter` | `reclaimExpiredClaims()` resets CLAIMED tasks older than 120 seconds back to PENDING, so a crash between handler execution and `markSucceeded` does not permanently strand the task (design §9 restart recovery) |
| Cross-instance duplicate-delivery test | `JdbcMemoryRepositoryAdapterTests#shouldClaimOutboxTaskAtomicallySoSecondInstanceDoesNotRepollIt` | PASS; the first poll claims the task, a second poll (second instance) returns empty, and only the claiming instance can complete it |
| Claim-timeout reclaim test | `JdbcMemoryRepositoryAdapterTests#shouldReclaimExpiredClaimedOutboxTaskAfterTimeout` | PASS; an expired CLAIMED task is reset to PENDING and re-polled for redelivery |
| JDBC full regression | `mvn -pl seahorse-agent-adapter-repository-jdbc -am clean test` | PASS; 306 tests, 0 failures/errors |
| Relay and auto-config regression | `MemoryOutboxRelayServiceTests`, `MemoryDerivedIndexOutboxTaskHandlerTests`, `SeahorseAgentKernelAutoConfigurationTests` | PASS; 20 + 47 tests, 0 failures/errors; the memory outbox auto-configuration injects `PlatformTransactionManager` for the atomic-claim path |
| Error retryable contract | `ErrorResponse`, `SeahorseWebExceptionHandler` | `ErrorResponse` now carries the design §9 `retryable` field; `ExternalServiceException` and `DatabaseTimeoutException` map to `retryable=true` (recoverable dependency failures), while validation, auth, advanced-feature-disabled, and other permanent failures map to `retryable=false` |
| Error contract tests | `ErrorResponseContractTests`, `SeahorseWebExceptionHandlerTests` | PASS; 8 tests; retryable semantics for dependency (true) and permanent (false) failures are asserted; the field is optional for existing clients (design §14) |
| Web regression after retryable | `mvn -pl seahorse-agent-adapter-web -am test` | PASS; 296 web tests, 0 failures/errors |
| Port merge: context pack | `ContextPackInboundPort` | `ContextPackQueryInboundPort`, `ContextPackRetentionInboundPort`, `ContextPackDiffInboundPort` merged into one `ContextPackInboundPort` (single implementation, single controller, four cohesive operations); Port -2 |
| Port merge: payment/subscription | `PaymentSubscriptionInboundPort` + `KernelPaymentSubscriptionFacade` | `PaymentInboundPort` + `SubscriptionInboundPort` merged behind a facade combining payment and subscription services; Port -1 |
| Port count after merges | ArchUnit inventory | Port interfaces fell from 370 to 367 (inbound 101 to 98); `port_java_files_info` 802 to 799; complexity baseline and port-inventory updated |
| Kernel/web/autoconfig regression after merges | `mvn -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am clean test` | PASS; 917 kernel, 296 web, 139 auto-configuration tests, 0 failures/errors |
| Port merge: feedback | `MessageFeedbackInboundPort` + `KernelMessageFeedbackFacade` | `FeedbackEvaluationCandidateQueryInboundPort` merged into `MessageFeedbackInboundPort` behind a facade combining message feedback and candidate-query services; Port -1 (367 to 366) |
| Feedback regression | `SeahorseMessageFeedbackControllerTests`, `SeahorseWebApiContractTests` | PASS; 1 + 23 tests, 0 failures/errors |
| Port merge: rollout | `AgentRolloutInboundPort` + `KernelAgentRolloutFacade` | `AgentRolloutCostSummaryInboundPort` merged into `AgentRolloutInboundPort` behind a facade combining rollout and cost-summary services; Port -1 (366 to 365) |
| Rollout regression | `SeahorseAgentRolloutControllerTests`, `SeahorseAgentRegistryAutoConfigurationTests` | PASS; 3 + 3 tests, 0 failures/errors |
| Port merge: artifact | `AgentArtifactInboundPort` + `KernelAgentArtifactFacade` | `AgentArtifactQueryInboundPort` + `AgentArtifactUpdateInboundPort` merged into `AgentArtifactInboundPort` behind a facade combining query and update services; Port -1 (365 to 364) |
| Artifact regression | `SeahorseAgentArtifactControllerTests`, `TaskOrchestrationServiceTests`, `SeahorseAgentRegistryAutoConfigurationTests` | PASS; 2 + 11 + 3 tests, 0 failures/errors; the registry test infrastructure now provides an in-memory `ObjectStoragePort` so the artifact facade assembles |
| Port merge: run query | `AgentRunQueryInboundPort` + `KernelAgentRunQueryFacade` | `AgentCheckpointQueryInboundPort` + `AgentRunCostSummaryInboundPort` + `AgentRunResumeInboundPort` merged into `AgentRunQueryInboundPort` behind a facade combining checkpoint-query, cost-summary, and resume services; Port -2 (364 to 362) |
| Run-query regression | `SeahorseAgentControllerTests`, `SeahorseCostUsageControllerTests`, `KernelAgentRunResumeServiceTests`, `KernelAgentRunWorkerServiceTests`, `SeahorseAgentRegistryAutoConfigurationTests`, `SeahorseAgentChatRunStoreAutoConfigurationTests` | PASS; the chat-run-store and registry test infrastructure now provide `CostUsageRepositoryPort` so the run-query facade assembles; `SeahorseAgentRunController` now exposes a single six-parameter constructor |
| Chat merge rolled back | source inspection | A chat+research port merge was attempted but rolled back: `ChatInboundPort` + `ResearchInboundPort` span different subdomains (`chat` vs `agent.research`), so combining them violates the design §5 one-way dependency rule; the rollback left `KernelChatInboundService`/`KernelResearchInboundService`/`SeahorseChatController`/auto-configurations at their HEAD state and Port count back at 362 |
| Historical Slice 3 chat failures | `KernelChatAgentRunStoreTests` (5), `KernelChatInboundTraceTests` (2) | 7 failures are pre-existing and unrelated to this slice: the working tree's `KernelChatAgentRunStoreTests` was edited (in a prior session) to assert `snapshotRepository.records.size() == 0` after renaming a test, but the current `KernelChatInboundService` (unchanged from HEAD) still persists a snapshot, so the assertions fail; `KernelChatInboundService` has no diff from HEAD, confirming the failures predate the Port reduction work |

## Evidence Gaps

- full CI execution of the blocking frontend job on Linux;
- route-level code splitting for the 3.73 MB main bundle;
- Full Docker environment readiness and golden-path evidence, including the
  auth/readiness scenarios required to close Slice 1 at L2.
- Full Docker auth/readiness scenarios required to close Slice 1 at L2.
- `SeahorseAgentNativeAdapterAutoConfigurationTests` (11 tests) require a real
  Milvus server: the default vector adapter is Milvus (`matchIfMissing`), so
  tests without `vector.type=noop` or a mocked `MilvusClientV2` hit a 10-second
  `DEADLINE_EXCEEDED`. `SeahorseAgentVectorAdapterAutoConfiguration` is unchanged
  from `origin/main`, so these are existing L1/L2 infrastructure-dependent tests,
  not regressions from this slice.
- Conversation/SSE/cancellation and dual-instance recovery evidence for later
  slices.
- Port reduction from 375 to no more than 300; current governance freezes
  growth but does not satisfy the final reduction target.
- The standalone architecture-test module needs reactor/local publication of
  its three unpublished adapter SNAPSHOT dependencies before a fresh isolated
  `PortArchitectureTest` result can be recorded; the reactor result is already
  current and passing.
- Aegis workspace structural cleanup is deferred because the reported stale
  links and old AgentScope JSON artifacts predate and are outside this slice;
  the method-pack check does not grant runtime completion authority.
