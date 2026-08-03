# Core Agent/RAG Runtime Stability and Complexity Reduction Design

Date: 2026-07-27  
Status: Review requested  
ArchitectureReviewRequired: yes

## 1. Purpose

This design stops feature expansion and makes the core Agent/RAG runtime the
only production commitment for the current governance cycle. Stability,
correctness, recoverability, and honest release evidence take priority over new
features. Code and architecture complexity must decrease as part of each
stability slice rather than through a separate cosmetic refactor.

The core runtime covers:

- authentication and tenant isolation;
- knowledge ingestion, durable source storage, indexing, retrieval, and citations;
- conversation persistence, model context construction, model execution, and SSE;
- governed tool execution, approval, sandboxing, artifacts, and audit evidence;
- cancellation, idempotency, restart recovery, and dual-instance behavior.

Marketplace, billing, experiments, advanced administration, optional
multi-agent integrations, and similar product expansion remain outside the
production completion claim for this cycle.

## 2. Evidence Baseline

The design is based on the current repository and the 2026-07-27 verification
run, not only on historical architecture documents.

### 2.1 Structural Evidence

- The repository contains 2,188 backend production Java files.
- `seahorse-agent-kernel` contains 1,569 production Java files.
- The `ports` source tree contains 809 Java files, including 377 top-level
  interfaces, 366 records, 16 enums, 49 classes, and one other declaration.
- The current direction split is 97 inbound interfaces, 279 outbound
  interfaces, and one common interface.
- The kernel application layer contains 34 top-level subdomains.
- The Phase 0 cross-domain baseline contains 40 allowed class pairs.
- Six Controller-to-`Kernel*Service` implementation dependencies are frozen in
  the current architecture test baseline.
- The Web adapter exposes 93 Controllers and approximately 429 mapped endpoints.
- The AutoConfiguration imports file contains 67 effective registrations; the
  previously documented value 106 counted comments and blank lines.

### 2.2 Verification Evidence

- The current architecture aggregation test passes kernel isolation, domain
  isolation, cross-domain baseline, adapter isolation, and Controller baseline checks.
- The CI-equivalent backend `verify` command fails in the sandbox adapter with
  two tests: one Windows/Docker Desktop mount-path portability assertion and
  one PDF active-content rejection failure.
- The PDF failure allows a security-sensitive input to reach `SUCCEEDED` when
  the test requires `FAILED`; it is a correctness defect, not merely a flaky test.
- Frontend Vitest reports 203 tests, 196 passing and 7 failing. Six failures are
  in ChatInput run-profile/role-card behavior and one is in WorkspaceInspector.
- Frontend type checking reports widespread response-contract errors. The
  shared Axios interceptor returns payload data at runtime while the exported
  client retains `AxiosResponse<T>` typing.
- Frontend lint reports 116 errors and 49 warnings, including Hook and unsafe
  control-flow findings.
- Recent history includes temporary test skipping, failure ignoring, and
  wrapper-level zero-exit behavior. The current worktree contains repairs, but
  a release claim remains invalid until the complete gate passes without those mechanisms.

### 2.3 Baseline Verdict

The physical Ports and Adapters direction still exists, but the application
surface has material architecture drift:

- too many public boundaries for internal coordination;
- duplicated runtime and frontend contract owners;
- feature breadth larger than the verified production surface;
- compatibility and no-op paths without retirement evidence;
- quality gates that historically allowed false-green outcomes.

## 3. Task Intent

### 3.1 Outcome

The core Agent/RAG runtime has a repeatable production release path backed by
real infrastructure, failure injection, restart recovery, and dual-instance evidence.

### 3.2 Success Evidence

- CI propagates real failures and all core blocking gates pass.
- Full Docker proves every core golden path and its failure/recovery variants.
- Required dependencies cannot silently become no-op implementations.
- Core requests have deterministic error, cancellation, idempotency, and terminal-state semantics.
- Port count, cross-domain edges, direct implementation dependencies, fallback
  paths, and touched hotspot complexity all decrease.

### 3.3 Stop Condition

Normal feature iteration may resume only after the L0-L3 production evidence
gates pass for the core runtime and all remaining compatibility paths have
owners and retirement triggers.

### 3.4 Non-Goals

- adding new product features;
- making every quarantined enterprise feature production-ready;
- Kubernetes-specific high-availability implementation;
- preserving internal Java Port, Bean name, or module-layout compatibility;
- rewriting the runtime around a new framework;
- reducing a metric through code movement, test deletion, or hidden failures.

## 4. First-Principles Decision

First-principles invariants:

- Non-negotiable goal: core requests remain correct under normal execution,
  dependency failure, cancellation, restart, and dual-instance execution.
- Non-negotiable constraints: core HTTP/SSE behavior, tenant isolation,
  security boundaries, and migratable business facts remain compatible.
- Historical assumptions to delete: class presence does not prove a feature is
  production-ready; green UI or CI does not prove correctness; adding a Port
  does not automatically improve architecture.

Owner and retirement matrix:

| Concern | Canonical owner | Old or duplicate owner | Retirement trigger |
| --- | --- | --- | --- |
| Core capability availability | Existing backend capability/feature source | Frontend route and menu guesses, no-op beans | Frontend consumes the backend source exclusively |
| HTTP response data shape | Shared frontend API client | Per-service Axios generic overrides and casts | Core services compile without second response generics or data casts |
| Core operation state | Durable repository for the operation aggregate | In-memory callbacks and per-instance maps | Restart and dual-instance evidence passes |
| Tool side-effect state | Durable invocation record | Executor-local retry assumptions | `UNKNOWN`, reconcile, and idempotency scenarios pass |
| Error classification | Core error contract mapped at adapter boundary | Controller-specific strings and swallowed exceptions | Core error contract tests cover all failure classes |
| Core use-case boundary | Capability-oriented use case owner | Fine-grained internal Ports and direct service dependencies | No production consumer remains for the old Port |

Smallest sufficient path:

1. restore honest gates;
2. quarantine non-core capabilities;
3. stabilize one complete core flow at a time;
4. remove duplicate owners and unnecessary Ports inside that flow;
5. collect Full Docker evidence before proceeding to the next flow.

## 5. Target Dependency Direction

```text
Core client
  -> HTTP / SSE adapter
  -> core inbound use cases
       -> knowledge ingestion and retrieval
       -> conversation and context
       -> governed tool and sandbox execution
       -> durable state and recovery
  -> necessary outbound Ports
  -> infrastructure adapters

Non-core quarantine
  -> may call stable core use cases
  -X must not be required by the core runtime

Bootstrap / AutoConfiguration
  -> assembly only
  -X must not become a second business-policy owner
```

Rules:

- Web depends on inbound use-case contracts, never concrete `Kernel*Service` implementations.
- Core application code does not depend on non-core application subdomains.
- Internal steps in one capability use package-private collaborators unless a
  real replaceable or external boundary exists.
- Cross-capability calls use an existing narrow capability contract or a
  durable event when transaction and failure semantics require decoupling.
- Every business fact, policy, operation state, and availability decision has one owner.

## 6. Port Reduction Design

Port proliferation is itself an architecture defect. The current `ports`
directory mixes actual abstractions with request/response records, enums,
helpers, repository fragments, readiness marker interfaces, and internal
coordination seams. Freezing the count is insufficient.

### 6.1 Canonical Port Definition

A Port is a public interface that crosses at least one of these boundaries:

1. an external side effect or infrastructure dependency;
2. an independently replaceable runtime adapter;
3. a plugin/SPI extension boundary used by production extensions;
4. a capability boundary with different ownership, transaction, or failure semantics.

Tests, mocking convenience, naming consistency, and a desire to split a large
class are not sufficient reasons for a public Port.

### 6.2 Classification and Disposition

Every Port in a touched capability is classified before refactoring:

| Classification | Disposition |
| --- | --- |
| External infrastructure Port | Keep narrow and behavior-oriented |
| Production plugin/SPI Port | Keep with compatibility and lifecycle tests |
| Capability boundary Port | Keep only when ownership/failure semantics differ |
| Single implementation and single production consumer | Convert to package-private collaborator by default |
| Repository fragment for the same aggregate and transaction | Merge under the aggregate repository owner |
| Marker interface used only to label evidence/readiness | Replace with one typed contributor contract |
| Request, response, record, enum, filter, or options object | Move to a contract/domain package when touched |
| Compatibility-only Port | Keep temporarily with consumer list and retirement trigger |
| Unused or test-only Port | Delete |

### 6.3 Consolidation Rules

- Inbound Ports align with user-observable use cases, not individual Controller methods.
- Outbound repository Ports align with aggregate and transaction boundaries,
  not one CRUD method per interface.
- Commands and queries remain separate only when their consistency, scaling,
  authorization, or failure semantics differ.
- A capability facade cannot expose unrelated operations merely to reduce the
  interface count.
- A merged Port must remain cohesive, independently testable, and owned by one capability.
- Implementations and callers migrate in the same slice; indefinite forwarding
  adapters are not accepted.
- A newly required Port must remove at least two obsolete/internal interfaces
  in the same capability unless an architecture review documents why this is impossible.

### 6.4 Port Budget

- Baseline: 377 actual Port interfaces.
- Core-stabilization target: no more than 300 actual Port interfaces repository-wide.
- Directional target: at least a 20% reduction, with reductions concentrated in
  touched core capabilities rather than achieved by renaming or moving interfaces.
- No slice may finish with a higher repository or touched-capability Port count.
- Records, enums, and classes moved out of `ports` do not count as Port reduction;
  they are reported separately as conceptual-boundary cleanup.
- The metric will be implemented using a structured Java/ArchUnit inventory,
  not raw directory line or file counts.

### 6.5 Anti-Gaming Checks

- no `CoreRuntimePort`, `RepositoryPort`, or similar God interface containing unrelated capabilities;
- no generic `Map<String, Object>` contract replacing typed business contracts;
- no public interface retained only because unit tests mock it;
- no interface deletion that moves adapter-specific types into the kernel;
- no reduction claim without consumer, implementation, and dependency-edge evidence.

## 7. Code Complexity Rules

Each vertical slice must produce a complexity delta covering public types,
dependency edges, cross-domain entries, direct implementation dependencies,
fallback paths, constructor dependencies, hotspot size, and production LOC.

Hard rules:

- the 40-entry cross-domain whitelist only decreases;
- the six Controller-to-Kernel implementation dependencies decrease to zero;
- no new production class exceeds 400 lines;
- a touched class above 800 lines must leave the slice with fewer
  responsibilities and dependencies and a material size reduction;
- no new AutoConfiguration root registration is added without retiring an old root;
- effective AutoConfiguration entries are counted structurally, excluding comments and blanks;
- no new catch-all exception path may return `null`, empty data, a default answer, or success;
- no new fallback or compatibility carrier exists without an owner and retirement trigger;
- test code growth is allowed; production code growth requires an irreducible business rule.

Priority hotspots include:

- `KernelChatInboundService`;
- `LocalToolGatewayPort`;
- `KernelSandboxRuntimeService`;
- `ContainerSandboxRuntimeAdapter`;
- `KernelIngestionTaskService`;
- memory/context builders and pipelines touched by the core conversation flow;
- the frontend API client and chat store response/state ownership.

Extraction must follow business stages and failure boundaries. Moving methods
into mutually coupled helper classes does not satisfy the rule.

## 8. Vertical Stability Slices

Every slice follows this sequence:

```text
pin external behavior
  -> add a failing test or failure-injection scenario
  -> repair the canonical owner
  -> remove duplicate paths and Ports
  -> reduce touched complexity
  -> collect Full Docker evidence
```

### 8.1 Slice 0: Honest Gates

- restore Maven, wrapper, test, and CI failure propagation;
- classify Unit, Contract, Integration, and Full-Docker E2E tests correctly;
- unify the frontend data-only API contract;
- repair the two current sandbox failures and seven frontend failures;
- make core typecheck, lint, unit, contract, and architecture checks blocking;
- repair PDF active-content rejection before broader sandbox restructuring.

### 8.2 Slice 1: Startup, Readiness, and Authentication

- required core dependencies fail startup or report `DOWN` with a stable reason;
- non-core capabilities report `FEATURE_DISABLED` rather than no-op health;
- login, refresh, concurrent refresh, logout, authorization, and tenant isolation pass;
- product-mode, feature, menu, route, and backend capability decisions converge on one backend source.

### 8.3 Slice 2: Knowledge Ingestion and Retrieval

- cover upload, parsing, durable source storage, chunking, indexing, retrieval, and citation;
- prove idempotent retries and absence of orphaned source, chunk, index, or outbox data;
- use real PostgreSQL, object storage, Milvus, and Elasticsearch;
- simplify ingestion, metadata, and JDBC schema ownership along the verified flow.

### 8.4 Slice 3: Conversation, Context, and SSE

- cover conversation creation, history, working context, model execution, and SSE termination;
- cover cancellation, disconnect, timeout, context overflow, duplicate completion, and redaction;
- keep raw facts separate from summaries, truncation, and provider working views;
- reduce chat orchestration, context-building, frontend store, and response-contract duplication.

### 8.5 Slice 4: Governed Tools and Sandbox

- cover native tool calls, policy, approval, durable invocation, sandbox, artifact, and audit;
- persist `BEFORE_TOOL` before side effects and use `UNKNOWN` for uncertain outcomes;
- cover malicious files, path escape, quota, timeout, cancellation, and container cleanup;
- decompose tool gateway and sandbox hotspots by policy, lifecycle, execution, and artifact boundaries.

### 8.6 Slice 5: Dual-Instance Recovery

- restart either backend during active core operations;
- cover cross-instance cancellation, lease competition, duplicate delivery, outbox, and idempotency;
- inject model, database, cache, MQ, vector, search, and object-storage faults;
- retire compatibility paths with no observed consumer after all core evidence passes.

## 9. Correctness and Failure Contract

Core adapter errors expose a stable, sanitized shape:

```json
{
  "code": "DEPENDENCY_UNAVAILABLE",
  "message": "Vector search is unavailable",
  "retryable": true,
  "traceId": "trace-id",
  "details": {}
}
```

Rules:

- `code` is the stable machine contract; `message` contains no secret,
  internal path, prompt, stack trace, or raw provider response.
- `retryable` is decided by server semantics, not guessed by clients.
- validation, authentication, authorization, policy rejection, conflict,
  throttling, dependency, timeout, cancellation, and internal defects remain distinct.
- `FEATURE_DISABLED` is explicit and not retryable.
- required correctness and security dependencies fail closed.
- partial retrieval continues only with a `PARTIAL` marker and evidence of the missing channel.

Durable operation states are:

```text
ACCEPTED -> RUNNING -> SUCCEEDED | FAILED | CANCELLED | UNKNOWN
UNKNOWN -> SUCCEEDED | FAILED only after reconciliation or compensation
```

- terminal states are immutable to ordinary retries;
- retries create a new attempt and retain prior evidence;
- automatic retry is allowed only for proven idempotent operations;
- idempotency identity binds tenant, operation type, and business object;
- in-memory callback state is never the authoritative recovery source.

## 10. SSE and Cancellation Contract

- one stream emits exactly one terminal outcome: done, error, or cancelled;
- disconnect does not imply model success and does not duplicate persistence;
- cancellation propagates from Web through retrieval, model, tool, and sandbox layers;
- reconnect reconciles with persisted conversation and run state rather than an in-memory stream;
- errors are protocol events, never appended to assistant text;
- raw facts persist independently from provider-specific working views.

## 11. Non-Core Quarantine

The quarantine strategy is isolate first, retire with evidence later.

- non-core capabilities default to disabled;
- core startup and golden paths do not require their beans, schema, routes, or external dependencies;
- core code cannot import non-core application packages;
- disabled endpoints return a stable feature-disabled result;
- quarantined code may receive security and isolation fixes but no feature growth;
- every compatibility-only endpoint or Port carries a consumer inventory and retirement trigger;
- deletion or repository extraction occurs only after usage and migration evidence exists.

## 12. Verification Gates

| Gate | Coverage | Trigger | Blocking |
| --- | --- | --- | --- |
| L0 | compile, formatting, core typecheck/lint, unit, contract, ArchUnit, complexity inventory | every PR | yes |
| L1 | PostgreSQL, storage, model mock server, container commands, persistence adapters | affected core changes | yes |
| L2 | Full Docker core golden paths and normal recovery | main and release candidate | yes |
| L3 | dual instance, restart, dependency faults, duplicate delivery, leaks, data reconciliation | release candidate | yes |

Forbidden gate behavior:

- `continue-on-error` for a blocking signal;
- `testFailureIgnore` or global test skipping;
- wrapper-level zero exit on failure;
- broad test exclusions used to hide unclassified integration tests;
- build-only success presented as runtime readiness.

## 13. Evidence Card

Every slice produces an evidence card with:

```text
commit and image digests
configuration hash and infrastructure versions
scenario, expected result, and actual result
database, index, and artifact assertions
trace ID, sanitized logs, and relevant metrics
failure injection and recovery result
remaining compatibility carrier and retirement trigger
Port and code complexity delta
```

Evidence must be reproducible from repository commands and must distinguish
unit evidence from real Full Docker evidence.

## 14. Compatibility and Migration

- core HTTP/SSE behavior remains compatible;
- newly exposed response fields are optional for existing clients;
- schema changes are additive and rollback-capable before old columns or formats are removed;
- core reads may temporarily understand old and new durable formats, with a measured retirement trigger;
- internal Java Ports, Bean names, and module layout may change;
- the frontend API response contract migrates once toward data-only semantics;
- a second long-lived response-unwrapping convention is forbidden.

## 15. Falsification Matrix

| Test | Design is rejected or revised when |
| --- | --- |
| Dependency-removal test | disabling a non-core capability prevents a core golden path from starting or completing |
| Port-reduction test | interface count falls only because interfaces were moved, renamed, or merged into a God Port |
| Failure-visibility test | a known backend, frontend, or security regression produces a green blocking gate |
| Restart test | authoritative state exists only in the failed process |
| Duplicate-side-effect test | retry or redelivery repeats an external side effect without detection |
| Contract test | the same API method returns payload data in one consumer and Axios metadata in another |
| Data-integrity test | failure leaves orphan source, chunk, index, artifact, invocation, or outbox records |
| Security test | required scanning, policy, or tenant checks degrade to allow or success |

## 16. Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Port consolidation creates God interfaces | aggregate/failure-semantics review and anti-gaming architecture tests |
| Refactoring destabilizes already weak gates | failing evidence first, one vertical slice at a time |
| Quarantine becomes permanent dead code | consumer inventory, usage observation, and retirement deadlines in each evidence card |
| Full Docker becomes too slow for PR feedback | L0/L1 remain focused; L2/L3 stay mandatory at their defined promotion gates |
| Additive migrations become permanent dual formats | explicit data migration counters and retirement trigger |
| Metrics drive code movement rather than simplification | require owner, consumer, dependency-edge, and behavior evidence with every delta |
| Existing dirty worktree changes overlap implementation | implementation plan inventories and preserves user changes before every slice |

## 17. Architecture and ADR Signals

ADR backfill is required for:

1. the core Agent/RAG runtime production boundary and one-way quarantine dependency;
2. Port classification, consolidation rules, and the public Port budget;
3. durable operation state and `UNKNOWN` side-effect semantics;
4. production evidence gates and completion authority.

Baseline synchronization is required after each implemented slice for owner,
contract, dependency, Port inventory, compatibility, and retirement changes.
The ADR and baseline documents record decisions and evidence; they do not
replace L2/L3 runtime proof.

## 18. Acceptance Checklist

- [ ] Maven, CI, frontend, and test commands propagate failures honestly.
- [ ] The PDF active-content defect and platform-specific mount behavior are resolved correctly.
- [ ] Core frontend typecheck, lint, unit, and contract tests are blocking and pass.
- [ ] All seven current frontend behavioral failures are resolved without weakening assertions.
- [ ] Port inventory uses structured interface classification.
- [ ] Actual Port interfaces fall from 377 to no more than 300.
- [ ] No Port-reduction change creates a God interface or generic untyped carrier.
- [ ] Cross-domain whitelist entries never increase.
- [ ] Controller-to-Kernel implementation dependencies reach zero.
- [ ] Touched core hotspots show material responsibility, dependency, and size reduction.
- [ ] Required dependencies cannot report healthy through no-op implementations.
- [ ] Core error, terminal state, retry, idempotency, cancellation, and SSE contracts pass.
- [ ] Non-core capabilities are disabled by default and cannot block core startup.
- [ ] Full Docker core golden paths pass on real infrastructure.
- [ ] Dual-instance restart and fault-injection evidence passes.
- [ ] Every remaining compatibility carrier has a consumer and retirement trigger.
- [ ] ADR and baseline records are synchronized with implemented behavior.

## 19. Working Artifacts

### TaskIntentDraft

- Outcome: a stable, usable, correct, and recoverable core Agent/RAG runtime.
- Success evidence: honest gates plus Full Docker normal, failure, restart, and dual-instance proof.
- Stop condition: L0-L3 pass and remaining compatibility paths have retirement evidence.
- Non-goals: new feature accumulation, full non-core completion, and internal compatibility preservation.

### BaselineReadSetHint

- `CONTEXT.md`
- `docs/aegis/BASELINE-GOVERNANCE.md`
- `docs/aegis/baseline/2026-06-27-initial-baseline.md`
- `docs/architecture/current-code-architecture.md`
- `docs/design/architecture-complexity-reduction-plan.md`
- root `pom.xml`, Maven wrapper, CI workflows, and complexity scripts
- `docker-compose.full.yml` and production deployment documentation
- kernel, core adapters, AutoConfiguration, bootstrap, frontend API/store, and current tests

### ImpactStatementDraft

- Affected layers: core kernel application/domain contracts, Web/SSE, model,
  persistence, retrieval, storage, sandbox, AutoConfiguration, bootstrap,
  frontend API/state, CI, and Full Docker evidence.
- Owners: capability-oriented core use cases and durable operation aggregates.
- Invariants: core external behavior, tenant isolation, security, facts, and migratable data.
- Compatibility: core external contracts preserved; internal Java boundaries may change.
- Non-goals: feature growth and making quarantined platform features production-complete.


