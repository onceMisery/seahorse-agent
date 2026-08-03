# ADR-004: Production Evidence Gates and Completion Authority

Status: Accepted  
Date: 2026-08-03  
Source: `docs/aegis/specs/2026-07-27-core-runtime-stability-complexity-design.md` §2, §3, §12, §13

## Context

Quality gates historically allowed false-green outcomes through temporary test
skipping, failure ignoring, wrapper-level zero-exit behavior, and broad test
exclusions. A green UI or CI did not prove correctness, and build-only success
was presented as runtime readiness.

## Decision

Four verification gates gate production promotion, and a release claim is valid
only when the complete gate passes without the historical failure-hiding
mechanisms:

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

Each slice produces an evidence card with commit and image digests, configuration
hash and infrastructure versions, scenario/expected/actual results, database and
index assertions, trace IDs and sanitized logs, failure-injection and recovery
results, remaining compatibility carriers and retirement triggers, and Port and
code complexity deltas.

Completion authority is held by the passing evidence, not by class presence,
green UI, or CI alone.

## Consequences

- Core frontend typecheck, lint, unit, and contract tests are blocking and pass.
- The frontend API response contract migrates once toward data-only semantics; a
  second long-lived response-unwrapping convention is forbidden.
- Normal feature iteration may resume only after L0-L3 pass for the core runtime
  and all remaining compatibility paths have owners and retirement triggers.

## Verification

- Frontend typecheck/lint/unit CI steps are blocking; no `continue-on-error`
  remains.
- The shared Axios client is data-only; duplicate response generics and consumer
  `.data` ownership are retired.
- Backend L0 verify passes on the remote baseline; sandbox and PDF
  active-content defects are resolved correctly.
- Aegis work records (`10-intent`, `20-checkpoint`, `90-evidence`) track
  evidence per slice.
