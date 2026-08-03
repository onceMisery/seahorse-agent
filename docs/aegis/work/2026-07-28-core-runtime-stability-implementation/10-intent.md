# Core Runtime Stability Implementation Intent

Date: 2026-07-28  
State: active  
ArchitectureReviewRequired: yes

## Requested Outcome

Implement `docs/aegis/specs/2026-07-27-core-runtime-stability-complexity-design.md`
from the latest remote primary branch. The remote has no `master`; its default
primary branch is `main`, and the implementation branch starts from
`origin/main` commit `8a458f17434f908d3f53a8970ff7a0f268518d11`.

## Scope

- restore honest backend and frontend quality gates;
- stabilize authentication, readiness, knowledge ingestion/retrieval,
  conversation/SSE/context, governed tools/sandbox, and durable recovery;
- isolate non-core capabilities from the core runtime;
- reduce actual Port interfaces from 377 to no more than 300 without God Ports;
- reduce cross-domain edges, direct implementation dependencies, swallowed
  failures, compatibility carriers, and touched hotspot complexity;
- collect Full Docker, dual-instance, restart, and failure-injection evidence;
- synchronize ADR, architecture baseline, and retirement records.

## Success Evidence

- L0-L3 gates defined by the approved design pass without ignored failures.
- Core golden paths pass against real Full Docker dependencies.
- Restart, duplicate delivery, cancellation, and dual-instance scenarios reconcile correctly.
- Port inventory is structured and proves a repository-wide count of at most 300.
- Every remaining compatibility carrier has a consumer and retirement trigger.

## Stop Conditions

- `done`: every approved acceptance item has direct current-state evidence.
- `needs-verification`: implementation exists but required runtime evidence is missing.
- `scope-exceeded`: a required change would alter the approved core external contract.
- `blocked`: an external dependency or user decision prevents meaningful progress after the required blocked audit.

## Non-Goals

- new product features;
- production completion of quarantined enterprise features;
- Kubernetes-specific implementation;
- preservation of internal Java Port, Bean-name, or module-layout compatibility;
- complexity reduction through test removal, code movement, or hidden failures.

## BaselineReadSetHint

- `CONTEXT.md`
- `docs/aegis/BASELINE-GOVERNANCE.md`
- `docs/aegis/baseline/2026-06-27-initial-baseline.md`
- `docs/aegis/specs/2026-07-27-core-runtime-stability-complexity-design.md`
- `docs/architecture/current-code-architecture.md`
- `docs/design/architecture-complexity-reduction-plan.md`
- root Maven build, CI workflows, complexity scripts, Full Docker compose, and deployment docs
- core kernel, Web, persistence, model, sandbox, AutoConfiguration, bootstrap, frontend API/state, and tests

## ImpactStatementDraft

- Affected layers: kernel application/domain contracts, HTTP/SSE, model,
  persistence, retrieval, storage, sandbox, AutoConfiguration, bootstrap,
  frontend API/state, CI, and Docker evidence.
- Canonical owners: capability-oriented core use cases and durable operation aggregates.
- Compatibility: core HTTP/SSE behavior and migratable facts remain stable;
  internal Java boundaries may change.
- Retirement: single-consumer internal Ports, duplicate response owners,
  no-op health paths, direct implementation dependencies, and unobserved
  compatibility carriers must shrink or be removed.

## Risk Hints

- Remote baseline already contains known test failures and prior false-green CI history.
- The original workspace is dirty; all implementation remains isolated in
  `.worktrees/core-runtime-stability`.
- Full Docker dependencies may require local capacity and model credentials;
  missing runtime evidence must remain explicit rather than being replaced by mocks.
- Port reduction can create God interfaces if based only on counts.

