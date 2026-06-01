# Frontend Backend Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the frontend admin surface with backend capabilities so users only see usable features, deployment mode is consistent, auth failures are safe, and future frontend/backend API drift is caught by tests.

**Architecture:** Backend owns feature capability truth through a new `/api/features` contract. Frontend loads that contract at startup and uses it for routes, menus, and unavailable states. Existing endpoints remain compatible while high-value missing UI entry points are added incrementally behind capability checks.

**Tech Stack:** Spring Boot 3.5, Java 17, Sa-Token, React 18, TypeScript, Vite, Axios, Vitest, Docker Compose.

---

## 1. Background

This plan is based on the frontend/backend alignment review in `docs/frontend-backend-alignment-review.md` and the runtime issues observed in the local browser:

- Expired or invalid login state can redirect to `/login?...&reason=token invalid:<raw-token>`, leaking token-like values into the URL.
- Frontend feature visibility is mainly controlled by `VITE_SEAHORSE_PRODUCT_MODE` and `VITE_SEAHORSE_ENABLE_ADVANCED_ADMIN`.
- Backend feature access is controlled by `seahorse-agent.product-mode` and `seahorse-agent.advanced.*` flags through `AdvancedFeatureGate`.
- Some backend feature flags are not fully bound in configuration, so controller-level features can exist but never become visible/configurable in a predictable way.
- Full Docker deployment does not clearly force enterprise-mode frontend build arguments and matching backend advanced flags.
- Backend exposes more product capabilities than the frontend currently surfaces, especially around knowledge maintenance, metadata governance, agent readiness, plugin health, memory evaluation, and context pack features.
- API path style is mixed across `/agents`, `/agent-runs`, `/api/agent-runs`, and `/api/agents/...`.

## 2. Scope

In scope:

- Sanitize auth-expired messages on frontend and backend.
- Add backend `/api/features` as the capability source of truth.
- Complete backend advanced feature flag binding.
- Make frontend menus, routes, and unavailable states consume backend capabilities.
- Align full Docker deployment with enterprise mode.
- Add high-value missing frontend entry points for existing backend capabilities.
- Add contract tests to detect frontend/backend API drift.

Out of scope:

- Changing database primary-key strategy.
- Removing legacy backend paths in the same release.
- Building every possible backend-only screen at once.
- Reworking page visual design beyond what is needed for feature alignment.

## 3. File Map

Backend:

- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AdvancedFeature.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AdvancedFeatureGate.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseWebGovernanceConfiguration.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseFeatureController.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseWebExceptionHandler.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentDefinitionController.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentRunController.java`

Frontend:

- `frontend/src/utils/authSession.ts`
- `frontend/src/services/api.ts`
- `frontend/src/config/productMode.ts`
- `frontend/src/services/featureService.ts`
- `frontend/src/stores/featureStore.ts`
- `frontend/src/main.tsx`
- `frontend/src/router.tsx`
- `frontend/src/pages/admin/AdminLayout.tsx`
- `frontend/src/services/knowledgeService.ts`
- `frontend/src/pages/admin/knowledge/KnowledgeDocumentsPage.tsx`
- `frontend/src/pages/admin/knowledge/KnowledgeChunksPage.tsx`
- `frontend/src/services/metadataGovernanceService.ts`
- `frontend/src/pages/admin/metadata-governance/MetadataGovernancePage.tsx`
- `frontend/src/services/agentDefinitionService.ts`
- `frontend/src/pages/admin/agents/*`
- `frontend/src/services/aiInfraService.ts`
- `frontend/src/pages/admin/ai-infra/AiInfraConsolePage.tsx`
- `frontend/src/services/frontendCapabilityContracts.test.ts`

Deployment and tooling:

- `frontend/Dockerfile.frontend`
- `docker-compose.full.yml`
- `.env.full.example`
- `scripts/extract-backend-mappings.js`
- `docs/deployment/enterprise-mode.md`

## 4. Milestone A: Auth Failure Sanitization

### Task A1: Sanitize frontend login redirect reason

Files:

- Modify `frontend/src/utils/authSession.ts`
- Create or modify `frontend/src/utils/authSession.test.ts`

Steps:

- [ ] Add a Vitest case that calls `handleUnauthorizedSession("token invalid: 2f04865b-95e4-40ac-90b1-078f5c6ec671")`.
- [ ] Assert `storage.clearAuth()` is called.
- [ ] Assert `window.location.replace()` points to `/login`.
- [ ] Assert decoded query contains a fixed safe message such as `登录已过期，请重新登录`.
- [ ] Assert redirect URL does not contain `2f04865b`, `token invalid`, or the original backend message.
- [ ] Implement a local sanitizer in `authSession.ts` that always converts auth/session/token/not-login errors into `登录已过期，请重新登录`.
- [ ] Run `cd frontend && npm test -- authSession`.
- [ ] Commit with `fix: sanitize frontend auth redirect reason`.

Acceptance:

- Expired sessions never place token-like content in the URL.
- Redirect behavior still preserves the original `redirect` path.

### Task A2: Sanitize backend `NotLoginException`

Files:

- Modify `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseWebExceptionHandler.java`
- Create or modify `seahorse-agent-adapter-web/src/test/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseWebExceptionHandlerTests.java`

Steps:

- [ ] Add a unit test for `new NotLoginException("token invalid: abc-raw-token")`.
- [ ] Assert response status/code remains unauthorized according to current project convention.
- [ ] Assert response message is exactly `登录已过期，请重新登录`.
- [ ] Assert serialized response does not contain `abc-raw-token`.
- [ ] Change the `NotLoginException` handler to return the fixed safe message instead of `ex.getMessage()`.
- [ ] Run `mvn -pl seahorse-agent-adapter-web -Dtest=SeahorseWebExceptionHandlerTests test`.
- [ ] Commit with `fix: sanitize backend auth errors`.

Acceptance:

- Frontend receives a safe auth error even if Sa-Token includes raw token details.

## 5. Milestone B: Backend Capability Contract

### Task B1: Complete advanced feature configuration binding

Files:

- Modify `AdvancedFeature.java`
- Modify `AdvancedFeatureGate.java`
- Modify `SeahorseWebGovernanceConfiguration.java`
- Create or modify `SeahorseWebGovernanceConfigurationTests.java`

Steps:

- [ ] List every `AdvancedFeature` referenced by backend controllers.
- [ ] List every frontend advanced feature in `frontend/src/config/productMode.ts`.
- [ ] Ensure the backend enum has canonical keys for all controllable advanced admin modules. Add missing enum values for frontend-only advanced modules that should be backend-governed.
- [ ] Add `@Value("${seahorse-agent.advanced.<feature-name>-enabled:false}")` binding for each backend advanced feature.
- [ ] Build the gate from an `EnumMap<AdvancedFeature, Boolean>` so missing entries are obvious during review.
- [ ] Add a test that enables every advanced feature and asserts `gate.isEnabled(feature)` is true for every enum value.
- [ ] Add a test for consumer mode that confirms enterprise-only features are disabled or invisible according to current product-mode rules.
- [ ] Run `mvn -pl seahorse-agent-adapter-web -Dtest=SeahorseWebGovernanceConfigurationTests test`.
- [ ] Commit with `feat: complete advanced feature configuration`.

Acceptance:

- Every controller-gated feature can be controlled from configuration.
- Backend enum names become the canonical feature keys for frontend usage.

### Task B2: Add `/api/features`

Files:

- Create `SeahorseFeatureController.java`
- Create `SeahorseFeatureControllerTests.java`

Contract:

```json
{
  "productMode": "ENTERPRISE_PLATFORM",
  "features": {
    "AGENT_DEFINITION_MANAGEMENT": {
      "enabled": true,
      "visible": true,
      "reason": ""
    }
  }
}
```

Steps:

- [ ] Implement `GET /api/features`.
- [ ] Return `productMode` from `AdvancedFeatureGate`.
- [ ] Return one `features` entry per `AdvancedFeature`.
- [ ] Set `enabled` from `advancedFeatureGate.isEnabled(feature)`.
- [ ] Set `visible` from product-mode rules; consumer mode should not expose enterprise-only modules.
- [ ] Set `reason` to an empty string when enabled and a human-readable backend reason when disabled.
- [ ] Add a controller test for enterprise mode with enabled features.
- [ ] Add a controller test for disabled feature reason.
- [ ] Run `mvn -pl seahorse-agent-adapter-web -Dtest=SeahorseFeatureControllerTests test`.
- [ ] Commit with `feat: add feature capability endpoint`.

Acceptance:

- Frontend can determine route/menu visibility without relying only on build-time env.

## 6. Milestone C: Frontend Capability Consumption

### Task C1: Add feature service and store

Files:

- Create `frontend/src/services/featureService.ts`
- Create `frontend/src/stores/featureStore.ts`
- Modify `frontend/src/main.tsx`
- Create `frontend/src/services/featureService.test.ts`

Steps:

- [ ] Add TypeScript interfaces `BackendFeatureState` and `BackendFeatureResponse`.
- [ ] Add `getBackendFeatures()` calling `api.get("/api/features")`.
- [ ] Add a Zustand store with `capabilities`, `isLoading`, `loadCapabilities()`, and `getFeatureState(featureKey)`.
- [ ] Default missing features to `{ enabled: false, visible: false, reason: "功能未启用" }`.
- [ ] Load capabilities during app startup after auth initialization.
- [ ] Add a service test that asserts `/api/features` is requested.
- [ ] Run `cd frontend && npm test -- featureService`.
- [ ] Commit with `feat: load backend feature capabilities`.

Acceptance:

- Feature capabilities are available to routing, layout, and pages from one frontend store.

### Task C2: Replace build-time feature checks in menus and routes

Files:

- Modify `frontend/src/config/productMode.ts`
- Modify `frontend/src/router.tsx`
- Modify `frontend/src/pages/admin/AdminLayout.tsx`
- Modify advanced admin pages currently using `getAdvancedFeatureState(...)`

Steps:

- [ ] Map frontend feature constants to backend enum keys.
- [ ] Keep Vite env only as a fallback before `/api/features` has loaded.
- [ ] In `AdminLayout`, hide consumer-mode-only unavailable entries and show disabled enterprise entries with backend `reason` where useful.
- [ ] In routes, either lazily guard page rendering or keep route registration and render a shared `FeatureUnavailableState`.
- [ ] Update each advanced page to read backend capability before making protected API calls.
- [ ] Add or update tests for menu visibility and disabled feature state.
- [ ] Run `cd frontend && npm test -- productMode featureService`.
- [ ] Run `cd frontend && npm run build`.
- [ ] Commit with `feat: align frontend navigation with backend capabilities`.

Acceptance:

- Users do not see routes that backend cannot support in the current product mode.
- Disabled enterprise features show a clear state instead of API errors.

## 7. Milestone D: Full Docker Enterprise Deployment

### Task D1: Align compose and frontend build arguments

Files:

- Modify `frontend/Dockerfile.frontend`
- Modify `docker-compose.full.yml`
- Modify `.env.full.example`
- Create `docs/deployment/enterprise-mode.md`

Steps:

- [ ] Add Docker build args for `VITE_API_BASE_URL`, `VITE_SEAHORSE_PRODUCT_MODE`, and `VITE_SEAHORSE_ENABLE_ADVANCED_ADMIN`.
- [ ] Set full deployment defaults to `enterprise-platform` and advanced admin enabled.
- [ ] Add backend env values for every `seahorse-agent.advanced.*-enabled` feature needed by the full deployment.
- [ ] Document which env variables must match between frontend build and backend runtime.
- [ ] Run `docker compose -f docker-compose.full.yml config`.
- [ ] Rebuild with `docker compose -f docker-compose.full.yml up -d --build backend frontend`.
- [ ] Smoke test `http://localhost/api/features`.
- [ ] Commit with `chore: align full docker feature mode`.

Acceptance:

- A full local Docker deployment exposes the intended enterprise admin surface after login.

## 8. Milestone E: API Path Compatibility and Standardization

### Task E1: Add `/api` aliases for agent endpoints

Files:

- Modify `SeahorseAgentDefinitionController.java`
- Modify `SeahorseAgentRunController.java`
- Modify `frontend/src/services/agentDefinitionService.ts`
- Modify `frontend/src/services/agentRunService.ts`

Steps:

- [ ] Add `/api/agents` aliases while keeping existing `/agents` mappings.
- [ ] Add `/api/agent-runs` aliases while keeping existing non-API mappings.
- [ ] Migrate frontend services to prefer `/api/**`.
- [ ] Add backend tests for one legacy path and one `/api` path per controller.
- [ ] Run `mvn -pl seahorse-agent-adapter-web -Dtest='*AgentDefinition*,*AgentRun*' test`.
- [ ] Run `cd frontend && npm test -- frontendCapabilityContracts`.
- [ ] Commit with `refactor: add api aliases for agent endpoints`.

Acceptance:

- Frontend moves toward `/api/**` without breaking existing clients.

## 9. Milestone F: High-Value Missing UI Entry Points

### Task F1: Knowledge document maintenance operations

Files:

- Modify `frontend/src/services/knowledgeService.ts`
- Modify `frontend/src/pages/admin/knowledge/KnowledgeDocumentsPage.tsx`
- Modify `frontend/src/pages/admin/knowledge/KnowledgeChunksPage.tsx`

Steps:

- [ ] Add service methods for single-document refresh, due-document refresh, and document chunk logs using existing backend endpoints.
- [ ] Add row actions on the document list: refresh, rebuild index if backend endpoint exists, and view chunk logs.
- [ ] Add a drawer or modal for chunk-log history with status, chunk id, timing, and error detail.
- [ ] Add a page-level action for refreshing due documents.
- [ ] Add loading, success, and error states using existing toast/message patterns.
- [ ] Run `cd frontend && npm run build`.
- [ ] Commit with `feat: expose knowledge maintenance operations`.

Acceptance:

- Admin users can operate existing knowledge maintenance capabilities without leaving the UI.

### Task F2: Metadata governance operations

Files:

- Modify `frontend/src/services/metadataGovernanceService.ts`
- Modify `frontend/src/pages/admin/metadata-governance/MetadataGovernancePage.tsx`
- Modify existing metadata governance components under `frontend/src/pages/admin/metadata-governance/`

Steps:

- [ ] Add service wrappers for metadata dictionary list/create/update/delete.
- [ ] Add service wrappers for quarantine detail and review audit history.
- [ ] Add service wrappers for backfill job run-next, pause, resume, and cancel if backend endpoints exist.
- [ ] Add tabs for schema fields, review queue, quarantine, dictionary, and backfill jobs.
- [ ] Add drawers/dialogs for dictionary edit, review audit trail, quarantine detail, and backfill actions.
- [ ] Gate the page and actions through backend feature capabilities.
- [ ] Run `cd frontend && npm run build`.
- [ ] Commit with `feat: complete metadata governance console`.

Acceptance:

- Existing backend governance operations have visible and actionable frontend entry points.

### Task F3: Agent production readiness operations

Files:

- Modify `frontend/src/services/agentDefinitionService.ts`
- Modify `frontend/src/services/aiInfraService.ts`
- Modify `frontend/src/pages/admin/agents/*`
- Modify `frontend/src/pages/admin/ai-infra/AiInfraConsolePage.tsx`

Steps:

- [ ] Add service wrappers for production gate generation and latest gate result.
- [ ] Add service wrappers for evaluation summary and pilot readiness endpoints that already exist in backend.
- [ ] Add an agent detail panel showing publish checks, production gate status, evaluation status, and readiness result.
- [ ] Add actions to rerun readiness checks where backend supports mutation.
- [ ] Show feature-disabled states using `/api/features` reason.
- [ ] Run `cd frontend && npm run build`.
- [ ] Commit with `feat: surface agent readiness operations`.

Acceptance:

- Admin users can tell whether an agent is production-ready from the agent UI instead of calling backend APIs manually.

### Task F4: Plugin, memory, and context-pack backlog screens

Files:

- Modify or create services and pages according to existing backend controller ownership.
- Update `frontend/src/pages/admin/AdminLayout.tsx`.
- Update `frontend/src/router.tsx`.

Steps:

- [ ] Inventory backend endpoints for plugin health/status/registry.
- [ ] Inventory backend endpoints for memory recall golden harness and evaluation.
- [ ] Inventory backend endpoints for context pack management.
- [ ] Add minimal read-only overview screens for each area when backend data exists.
- [ ] Add capability-gated menu entries only after service calls are implemented.
- [ ] Run `cd frontend && npm run build`.
- [ ] Commit with `feat: add remaining capability overview entries`.

Acceptance:

- Backend-only areas that are important to operators have at least a discoverable overview page.

## 10. Milestone G: Contract Tests

### Task G1: Generate backend endpoint manifest

Files:

- Create `scripts/extract-backend-mappings.js`
- Create `frontend/src/services/backendEndpointManifest.ts`

Steps:

- [ ] Implement a Node script that scans `seahorse-agent-adapter-web/src/main/java`.
- [ ] Extract `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, and `@PatchMapping`.
- [ ] Normalize path variables to `{}`.
- [ ] Write a TypeScript manifest exporting `{ method, path }[]`.
- [ ] Run `node scripts/extract-backend-mappings.js`.
- [ ] Commit with `test: generate backend endpoint manifest`.

Acceptance:

- Frontend tests can compare service calls against backend mappings without manually maintaining a large list.

### Task G2: Detect frontend/backend endpoint drift

Files:

- Modify `frontend/src/services/frontendCapabilityContracts.test.ts`

Steps:

- [ ] Import `backendEndpointManifest`.
- [ ] Normalize frontend service paths and backend manifest paths the same way.
- [ ] Assert every internal frontend service endpoint has a matching backend endpoint.
- [ ] Add a small documented allowlist for intentionally legacy or external calls.
- [ ] Fail tests when a service points to an endpoint that backend does not expose.
- [ ] Run `cd frontend && npm test -- frontendCapabilityContracts`.
- [ ] Commit with `test: detect frontend backend endpoint drift`.

Acceptance:

- Future feature work cannot silently add frontend calls to nonexistent backend endpoints.

## 11. Milestone H: Full Verification

Steps:

- [ ] Run backend focused tests:

```bash
mvn -pl seahorse-agent-adapter-web -Dtest='SeahorseFeatureControllerTests,SeahorseWebExceptionHandlerTests,SeahorseWebGovernanceConfigurationTests' test
```

- [ ] Run affected backend compile:

```bash
mvn -pl seahorse-agent-adapter-web,seahorse-agent-bootstrap -am -DskipTests compile
```

- [ ] Run frontend focused tests and build:

```bash
cd frontend
npm test -- featureService productMode frontendCapabilityContracts authSession
npm run build
```

- [ ] Package backend:

```bash
mvn -pl seahorse-agent-bootstrap -am -DskipTests "-Dspotless.check.skip=true" package
```

- [ ] Rebuild local full deployment:

```bash
docker compose -f docker-compose.full.yml up -d --build backend frontend
```

- [ ] Browser smoke test:

```text
http://localhost/login
http://localhost/admin/knowledge
http://localhost/admin/model-config
http://localhost/admin/settings
```

Smoke-test expectations:

- Login expiration redirects do not expose raw token values.
- `/api/features` returns enterprise product mode in full deployment.
- Admin menu matches backend capability states.
- Disabled features show clear unavailable states.
- Knowledge, metadata, and agent readiness pages load without backend capability mismatch errors.

## 12. Recommended Commit Order

1. `fix: sanitize frontend auth redirect reason`
2. `fix: sanitize backend auth errors`
3. `feat: complete advanced feature configuration`
4. `feat: add feature capability endpoint`
5. `feat: load backend feature capabilities`
6. `feat: align frontend navigation with backend capabilities`
7. `chore: align full docker feature mode`
8. `refactor: add api aliases for agent endpoints`
9. `feat: expose knowledge maintenance operations`
10. `feat: complete metadata governance console`
11. `feat: surface agent readiness operations`
12. `feat: add remaining capability overview entries`
13. `test: generate backend endpoint manifest`
14. `test: detect frontend backend endpoint drift`

## 13. Final Acceptance Criteria

- No login redirect URL contains raw token/session identifiers.
- Backend exposes `/api/features` with product mode and per-feature states.
- Frontend menus, routes, and protected pages consume backend feature capabilities.
- Full Docker deployment has aligned frontend build args and backend runtime flags.
- Agent endpoint usage moves toward `/api/**` while preserving compatibility.
- Knowledge, metadata governance, and agent readiness backend functions have practical frontend entry points.
- Contract tests catch endpoint drift between frontend services and backend mappings.
- Local full deployment passes smoke testing on `/admin/knowledge`, `/admin/model-config`, and `/admin/settings`.

## 14. Risk Controls

- Keep legacy endpoints until at least one release after frontend migration.
- Make `/api/features` additive so older frontends are not broken.
- Gate new UI entry points behind backend capabilities.
- Prefer focused UI panels over broad page rewrites.
- Commit after each task group so regressions can be bisected.
- Keep the endpoint drift allowlist small and documented.

## 15. Self Review

- Review finding coverage: auth leakage, feature flag mismatch, Docker mode mismatch, missing UI entry points, path inconsistency, and contract-test absence are each mapped to milestones.
- Execution granularity: every task has files, steps, commands, and acceptance criteria.
- Compatibility: no task removes existing public endpoints.
- Verification: backend tests, frontend tests, build, Docker config validation, and browser smoke checks are all included.
