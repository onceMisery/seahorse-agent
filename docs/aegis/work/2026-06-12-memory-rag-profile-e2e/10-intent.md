# Memory, RAG, and Profile E2E Verification

## Requested Outcome
Analyze whether the deployed Seahorse Agent memory feature is effective, determine whether memory, RAG, and user profile form a complete closed loop, fix confirmed defects, and verify with real local Docker E2E tests.

## Scope
- Backend chat and RAG request path.
- Memory extraction, persistence, recall, governance/read APIs, and profile facts.
- Runtime adapter wiring in the currently deployed local Docker stack.
- Focused automated regression tests for confirmed defects.
- Real API-level E2E against `http://localhost:9090` and/or frontend proxy where needed.

## Non-Goals
- Redesigning the whole memory architecture.
- Replacing model providers or Docker infrastructure unless required to make the tested path work.
- Resetting user data or Docker volumes without explicit need.

## Success Evidence
- Code-level data-flow map identifies the owner of each loop step.
- Baseline Docker E2E demonstrates the current failure or confirms the current behavior.
- Confirmed defects have failing tests before repair.
- Repairs are made at the canonical owner.
- Final E2E demonstrates memory/profile/RAG behavior with persisted evidence.

## Stop Condition
- Done: all confirmed defects in scope are repaired and verified by focused tests plus real E2E.
- Needs verification: repair exists but a real dependency blocks final E2E.
- Blocked: required service, credential, or model behavior is unavailable after repeated attempts.
- Scope-exceeded: closing the loop requires an architectural/product decision outside this task.

## BaselineReadSetHint
- `CLAUDE.md`
- `README.md`
- `seahorse-architecture.md`
- `docker-compose.yml`
- `docker-compose.full.yml`
- `docs/aegis/specs/2026-05-20-memory-filtering-architecture.md`
- Memory package under `seahorse-agent-kernel/src/main/java/.../application/memory`
- Chat/RAG controllers under `seahorse-agent-adapter-web`
- JDBC memory repository adapters and database schema/migrations.

## ImpactStatementDraft
- Affected layers: web adapter, kernel chat/memory/RAG application services, repository/vector/search adapters, Docker runtime configuration.
- Compatibility boundary: existing REST APIs and database schema should remain compatible unless a confirmed schema defect requires a migration.
- Risk hints: asynchronous memory aggregation, noop/fallback adapters, model availability, tenant/user scoping, and frontend/backend path prefixes may hide broken loop edges.
