# Architecture Roadmap Implementation Intent

## Requested Outcome

Read the current architecture baseline and roadmap documents, then implement practical near-term and mid-term roadmap slices with evidence.

## Goal

Move existing Seahorse Agent capabilities from skeleton or partial governance toward verifiable, auditable, recoverable daily-development and production-readiness loops.

## Success Evidence

- Each implemented slice has focused tests that fail before implementation and pass afterward.
- Changed runtime contracts have controller, port, service, repository, migration, or auto-configuration coverage as appropriate.
- Broad regression covering the touched slices passes.
- Remaining roadmap gaps are named instead of being implied complete.

## Stop Condition

- `done`: implemented slices have passing tests and evidence.
- `blocked`: a slice needs external service/data or product choices that cannot be inferred safely.
- `needs-verification`: code exists but fresh verification is missing.
- `scope-exceeded`: continuing would require implementing the whole multi-month roadmap in one pass.

## Non-Goals

- Do not claim the whole roadmap is complete.
- Do not wipe local data, rewrite architecture, or replace established ports/adapters.
- Do not fake retry-from-node behavior by skipping upstream context restoration.
- Do not silently auto-delete memory or automatically roll back production behavior without manual confirmation.

## Baseline Read Set Hint

- `docs/architecture/current-code-architecture.md`
- `docs/roadmap/architecture-roadmap-and-vision.md`
- Retrieval evaluation, strategy template, ingestion task, memory governance, production gate, rollout, audit, and auto-configuration tests.

## Impact Statement Draft

The roadmap calls for evidence-first governance across RAG, ingestion, memory, and Agent release flows. The implemented slices strengthen existing contracts without inventing parallel subsystems: failed ingestion can be retried and inspected, rollout decisions are audited, production gates include publish evidence, memory cleanup remains advisory, and retrieval strategy recommendation now requires a passing saved comparison plus audit.
