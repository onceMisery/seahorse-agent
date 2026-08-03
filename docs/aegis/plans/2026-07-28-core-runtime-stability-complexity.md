# Core Runtime Stability and Complexity Implementation Plan

Date: 2026-07-30  
Status: Active  
ArchitectureReviewRequired: yes

## Goal

Implement the approved core Agent/RAG stability design from current
`origin/main` (`5917fe499b573b454eec241835823f9371c4b393`). Restore honest blocking
gates, prove the core runtime under real failures and recovery, isolate
non-core features, and reduce actual public Port interfaces from 377 to at
most 300 without moving complexity into God interfaces or generic carriers.

## Architecture

The implementation proceeds as vertical stability slices. Each slice pins an
external behavior, keeps focused tests for core correctness and failure paths,
repairs the canonical owner, removes duplicate owners and internal Ports, and
records runtime plus complexity evidence. HTTP/SSE adapters depend on capability-level
inbound use cases; kernel capabilities depend only on domain contracts and
real external/runtime boundaries. Spring auto-configuration remains assembly,
not a second policy owner. Non-core capabilities may depend on stable core use
cases, while the core runtime must not depend on quarantined capabilities.

Port reduction is semantic. A public interface is retained only for an
external side effect, independently replaceable adapter, production SPI, or a
capability boundary with distinct ownership/transaction/failure semantics.
Single-implementation internal coordination becomes package-private; records,
enums, and value objects leave `ports` when touched; repository fragments for
one aggregate and transaction owner are consolidated. A merged interface may
not span capabilities, use `Map<String, Object>` as its primary contract, or
exceed eight abstract operations without an architecture review. The repository
Port count and each touched capability Port count may only decrease.

## Tech Stack

- Java 21, Maven Wrapper, Spring Boot 3.5, JUnit 5, ArchUnit.
- PostgreSQL/JDBC, Redis, Pulsar, Milvus, Elasticsearch, S3/MinIO, container sandbox.
- React 18, TypeScript 5.5, Axios, Zustand, Vitest, ESLint, Vite.
- Docker Compose Full for L2/L3 runtime evidence.

## Baseline / Authority Refs

- `docs/aegis/specs/2026-07-27-core-runtime-stability-complexity-design.md`
- `docs/aegis/baseline/2026-06-27-initial-baseline.md`
- `docs/architecture/current-code-architecture.md`
- `docs/design/architecture-complexity-reduction-plan.md`
- `docs/aegis/work/2026-07-28-core-runtime-stability-implementation/10-intent.md`
- Current source, tests, compose files, and CI are runtime authority where historical docs disagree.

Current verified baseline:

| Signal | Current evidence | Required end state |
| --- | ---: | ---: |
| Backend CI-equivalent verify | pass on `5917fe49` | pass, blocking |
| Frontend Vitest | 196/203 pass; 7 fail | all pass, blocking |
| Frontend typecheck | fail; 599 output lines | pass, blocking |
| Frontend lint | 116 errors, 49 warnings | zero, blocking |
| Frontend build | pass; main JS about 3.7 MB | pass; budgeted chunks |
| Actual public Port interfaces | 377 (97 inbound, 279 outbound, 1 common) | <=300 |
| Java files under `ports` | 809 | informational only |
| Cross-domain whitelist pairs | 40 | monotonically decrease |
| Controller-to-Kernel service edges | 6 | 0 |
| Effective AutoConfiguration registrations | 67 | monotonically decrease |

The historical `complexity-baseline.txt` values `ports=808` and
`autoconfig_imports=106` count files and physical lines, respectively. They are
not accepted as Port or registration counts and will be retired in Task 1.

## Compatibility Boundary

- Preserve core HTTP paths, request/response behavior, SSE event semantics,
  tenant isolation, security fail-closed behavior, and migratable business facts.
- New response fields remain optional to existing clients.
- Schema changes are additive and rollback-capable until migration evidence
  proves old formats unused.
- Internal Java Ports, Bean names, package layout, and module layout may change.
- Frontend HTTP services migrate once to data-only responses; no second
  response-unwrapping convention remains.
- Non-core endpoints may become explicit `FEATURE_DISABLED`, but core startup
  and core golden paths may not require their beans, schema, or dependencies.

## Verification

Tests are risk-based rather than blanket TDD. Add or update tests only for core
contracts, security/correctness invariants, state transitions, recovery, and
regression-prone shared owners. Mechanical type cleanup, file moves, formatting,
and package-private refactors do not require one test per edit.

L0 on every implementation batch:

```powershell
.\mvnw.cmd -B -ntp verify "-Dtest=!SeahorseE2E*" "-Dsurefire.failIfNoSpecifiedTests=false"
Set-Location frontend
npx tsc --noEmit -p tsconfig.app.json
npm run lint
npm test
npm run build
```

Architecture and inventory:

```powershell
.\mvnw.cmd -pl seahorse-agent-architecture-tests -am test
.\mvnw.cmd -pl seahorse-agent-architecture-tests -Dtest=PortArchitectureTest test
```

L1-L3 commands will be repository-owned scripts introduced by Tasks 4-8 and
must return non-zero for failed assertions. Full Docker evidence must record
compose configuration hash, image digests, scenario results, persisted state,
trace IDs, and cleanup assertions.

## Ripple Signal Triage

- Shared frontend API contract: owner is `frontend/src/services/api.ts`;
  downstream scope is every service, store, page, and mock consuming `api`.
- Port governance: owner is `seahorse-agent-architecture-tests`; downstream
  scope is kernel contracts, all adapters, auto-configuration, and controllers.
- Durable operation state: owner is the operation aggregate repository;
  downstream scope includes workers, SSE/cancellation, tool side effects,
  retries, restart recovery, and dual-instance execution.
- Required availability: backend readiness/capability state is canonical;
  frontend route/menu guesses and no-op health are duplicate owners to retire.

## Task 1: Replace False Complexity Metrics with Structured Port Governance

Files:

- Create `seahorse-agent-architecture-tests/src/test/java/com/miracle/ai/seahorse/agent/arch/PortArchitectureTest.java`.
- Modify `seahorse-agent-architecture-tests/src/test/java/com/miracle/ai/seahorse/agent/arch/ArchitectureRulesTest.java`.
- Modify `scripts/complexity-report.sh` and `complexity-baseline.txt`.
- Create `docs/architecture/port-inventory.md` with classification and retirement evidence.

Why: the current gate treats every Java value object as a Port and therefore
cannot detect real abstraction proliferation or God-interface consolidation.

Impact / compatibility: governance only; production behavior is unchanged.
The old file-count metric retires in the same change, so there is one owner.

Repair track: use ArchUnit imported classes to count public interfaces under
the canonical ports package, split directions, count abstract operations, and
reject new interfaces, count increases, cross-capability God Ports, and final
count above 300 once the retirement slices complete.

Retirement track: remove `ports=808` and physical imports count from the
blocking baseline. Keep Java-file count only as informational package hygiene.

- [ ] Add core architecture tests asserting the 377/97/279/1 Port baseline and
      retain the existing cross-domain/controller dependency gates.
- [ ] Replace the current 808/106 file/line metrics with structured inventories.
- [ ] Implement the structured inventory and report generation at the canonical owner.
- [ ] Run architecture tests and `scripts/complexity-report.sh`; verify
      on Windows and CI Linux without WSL assumptions.
- [ ] Commit the governance slice with its baseline evidence card.

## Task 2: Make the Frontend HTTP Contract Data-Only

Files:

- Modify `frontend/src/services/api.ts` and `frontend/src/services/api.test.ts`.
- Modify affected `frontend/src/services/*.ts`, stores, pages, and tests only to
  remove second-response generics, `.data` reads, and casts made obsolete by the owner fix.

Why: the interceptor returns payload data at runtime while Axios types promise
`AxiosResponse<T>`, creating hundreds of contradictory consumer patches.

Impact / compatibility: runtime payload behavior stays unchanged. Compile-time
types move once to data-only semantics. Raw Axios remains private to refresh and
transport implementation.

Repair track: expose a typed data-only client backed by a private Axios instance;
test success envelope unwrapping, raw payloads, business errors, 401 retry, and
the `Promise<T>` method signature.

Retirement track: remove consumer `.data`, `as unknown as`, and two-response-
generic conventions. Do not add a parallel `apiData` client.

- [ ] Keep one core contract assertion that `api.get<Payload>()` is
      `Promise<Payload>` and success envelopes resolve to payload.
- [ ] Implement the minimal private raw-client/public data-client typing.
- [ ] Run `npx vitest run src/services/api.test.ts` and typecheck for removal of
      API-contract errors.
- [ ] Remove now-redundant consumer patches mechanically, then fix only the
      remaining independently diagnosed type errors.
- [ ] Run full frontend typecheck and service/store tests; commit the contract slice.

## Task 3: Restore Frontend Behavior and Blocking Gates

Files:

- Diagnose and modify `frontend/src/components/chat/ChatInput.tsx` and its test.
- Diagnose and modify context snapshot/inspector owner and tests.
- Modify lint/type offenders under `frontend/src` without disabling rules.
- Modify `frontend/package.json`, `frontend/tsconfig.app.json`, and `.github/workflows/ci.yml`.

Why: seven behavior tests fail and CI explicitly ignores typecheck, lint, and tests.

Repair track: isolate each failing behavior, prove whether the canonical state
owner or stale test is wrong, then fix the owner and retain a focused regression. Fix Hook and
unsafe-control-flow findings before cosmetic lint issues.

Retirement track: remove all three frontend `continue-on-error` settings,
non-blocking labels, response casts, and obsolete run-profile/role-card state paths.

- [ ] Reproduce ChatInput and WorkspaceInspector failures independently with verbose output.
- [ ] Keep focused tests for the run-profile/role-card and context-snapshot invariants.
- [ ] Fix canonical state/render owners and verify the two target suites.
- [ ] Fix remaining type/lint findings without exclusions or lowered rules.
- [ ] Run typecheck, lint, all 203+ tests, build, and a deliberate-failure CI
      propagation check; commit Slice 0.

## Task 4: Startup, Readiness, Authentication, and Non-Core Isolation

Files: readiness/auth kernel services and inbound contracts, Web controllers,
feature API, Spring auto-configuration, frontend feature owner, and focused tests.

Why: required dependencies and feature availability need one fail-closed owner;
core startup must not depend on quarantined product breadth.

Repair track: test required dependency absence, explicit disabled capability,
login/refresh/concurrent refresh/logout, authorization, and tenant isolation.
Make backend capability state authoritative for frontend routes and menus.

Retirement track: delete no-op healthy implementations, frontend product-mode
guesses, redundant readiness marker Ports, and unused non-core root registrations.

- [ ] Add focused startup/readiness/auth/capability contract tests where coverage is missing.
- [ ] Verify silent no-op and duplicate feature-owner cases are rejected.
- [ ] Implement fail-closed required dependencies and explicit `FEATURE_DISABLED`.
- [ ] Consolidate or internalize touched readiness/auth Ports; Port count must decrease.
- [ ] Run L0 plus Full Docker auth/readiness scenarios and commit Slice 1.

## Task 5: Knowledge Ingestion and Retrieval Stability

Files: knowledge/ingestion/retrieval use cases, aggregate repositories, JDBC
schema ownership, object/vector/search adapters, controllers, and E2E scripts.

Why: upload-to-citation is a core production path whose retry and partial
failure behavior must preserve durable facts and avoid orphaned indexes.

Repair track: test upload, parsing, durable source, chunking, indexing,
retrieval, citations, idempotent retry, rollback, and partial-channel evidence
against PostgreSQL, MinIO, Milvus, and Elasticsearch.

Retirement track: merge same-aggregate CRUD repository fragments, internalize
single-consumer pipeline coordination, and remove duplicate schema owners.

- [ ] Add focused failure-injection tests for core external boundaries with missing coverage.
- [ ] Implement durable idempotency/rollback at aggregate owners.
- [ ] Consolidate touched inbound/outbound Ports without cross-capability facades.
- [ ] Run L0/L1 and Full Docker ingestion/retrieval normal plus recovery paths.
- [ ] Record data reconciliation and Port/complexity deltas; commit Slice 2.

## Task 6: Conversation, Context, SSE, and Cancellation Correctness

Files: `KernelChatInboundService`, chat pipeline/support classes, context
builders, conversation repositories, Web SSE bridge/controller, frontend chat
store/stream handlers, and contract/E2E tests.

Why: a core stream must produce exactly one terminal outcome, persist facts
once, propagate cancellation, and recover from durable state after disconnect.

Repair track: test normal completion, error, cancellation, disconnect, timeout,
overflow, duplicate completion, redaction, reconnect, and persisted reconciliation.

Retirement track: remove in-memory authoritative callbacks, duplicate context
builders, response parsing owners, and fine-grained internal chat Ports.

- [ ] Cover the terminal outcome matrix with core contract/state-transition tests.
- [ ] Move correctness to durable conversation/run owners and verify affected regressions.
- [ ] Reduce responsibilities/dependencies/size of touched chat hotspots.
- [ ] Run backend/frontend contracts and Full Docker SSE recovery scenarios.
- [ ] Record terminal-state, persistence, Port, and hotspot deltas; commit Slice 3.

## Task 7: Governed Tools and Sandbox Correctness

Files: tool gateway/loop, approval/policy/audit owners, durable invocation
repository, sandbox runtime/services/container adapter, artifact scanner/storage,
auto-configuration, and security/failure tests.

Why: external side effects require durable BEFORE_TOOL evidence, idempotency,
`UNKNOWN` handling, policy enforcement, and resource cleanup.

Repair track: test policy and approval, side-effect response loss, reconciliation,
malicious files, path escape, quotas, timeout, cancellation, and cleanup.

Retirement track: decompose `LocalToolGatewayPort` and sandbox hotspots by
policy/lifecycle/execution/artifact ownership while internalizing coordination
interfaces and removing executor-local retry authority.

- [ ] Cover durable invocation and sandbox failure/security invariants with core tests.
- [ ] Persist invocation intent before side effects and reconcile `UNKNOWN`.
- [ ] Simplify gateway/runtime owners and reduce touched Port count.
- [ ] Run L0/L1 plus Full Docker tool/sandbox fault scenarios.
- [ ] Record audit/artifact/cleanup and complexity evidence; commit Slice 4.

## Task 8: Dual-Instance Recovery and Fault Injection

Files: durable run/lease/outbox repositories, worker/resume services,
cross-instance cancellation, Docker compose profiles, and release-gate scripts.

Why: process-local success is not production recovery evidence.

Repair track: run two backend instances; restart either during core operations;
inject database, cache, MQ, model, vector, search, object-store, and response-loss
faults; assert lease exclusivity, idempotency, terminal reconciliation, and no leaks.

Retirement track: delete per-instance authoritative maps/callbacks and compatibility
paths with no observed consumer after dual-instance evidence passes.

- [ ] Add deterministic dual-instance/fault scenarios for process-local state risks.
- [ ] Repair durable owners and cross-instance coordination.
- [ ] Run L0-L3 and repeat recovery scenarios to exclude timing-only success.
- [ ] Record image/config digests, persisted assertions, trace IDs, and cleanup.
- [ ] Commit Slice 5.

## Task 9: Final Port Reduction, ADR Backfill, and Completion Audit

Files: remaining classified Port owners/consumers, ArchUnit baselines,
architecture docs, ADRs, Aegis baseline/work evidence, and CI/release scripts.

Why: completion requires repository-wide semantic simplification and direct
evidence for every acceptance item, not only green unit tests.

Repair track: finish classification-driven retirement until actual public
Ports are <=300, cross-domain entries never exceed 40, controller implementation
edges are zero, and touched hotspots materially shrink.

Retirement track: every retained compatibility carrier lists its consumer,
owner, deletion trigger, and required verification; unowned carriers are deleted.

- [ ] Work capability by capability from the inventory; delete/internalize at
      least 77 real interfaces while preserving external contracts.
- [ ] Run anti-God-Port, dependency, architecture, L0-L3, and deliberate-failure gates.
- [ ] Backfill ADRs for core boundary, Port rules, durable operation state, and evidence gates.
- [ ] Synchronize baselines and assemble the Aegis evidence bundle/reflection.
- [ ] Audit every design acceptance checkbox against current direct evidence;
      only then mark the goal complete and choose branch integration.

## Risks and Rollback

- Large internal contract changes can create a compile-wide blast radius.
  Work one capability at a time with consumer and implementation inventory.
- Count-driven consolidation can create God Ports. Reject cross-capability
  method groups, generic carriers, and interfaces above the reviewed operation budget.
- Additive schema compatibility can become permanent. Every dual read/write
  path requires a measured deletion trigger in the evidence card.
- Full Docker may expose environmental limitations. Missing credentials or
  capacity are recorded as missing evidence, never replaced by mock claims.
- Each commit is independently reviewable; rollback is by slice, while additive
  migrations remain compatible until retirement evidence exists.

## Plan Self-Review

- Approved scope: all Slice 0-5 requirements, Port <=300, Full Docker,
  dual-instance evidence, quarantine, ADR/baseline sync are mapped above.
- Placeholders: none; concrete owners, files, commands, and evidence are stated.
- Compatibility: core HTTP/SSE, security, tenant, and data boundaries are explicit.
- Verification: every major slice includes focused core regressions plus L0-L3 scope.
- Dual track: every task states repair and retirement.
- Decision hygiene: Port count cannot be gamed through file moves, God Ports,
  generic maps, test deletion, or hidden failures.
