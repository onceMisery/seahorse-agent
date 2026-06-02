# Frontend Backend Gap Remediation

Date: 2026-06-02

## Requested Outcome

Implement the remediation work described in `docs/aegis/plans/2026-06-02-frontend-backend-gap-remediation-plan.md`: align frontend and backend capabilities, remove broken or unreliable contracts, repair user-visible text, and verify the result.

## Scope

- P0 gaps from the plan are first-class scope: Chinese text/encoding, feature gates, frontend/backend endpoint contracts, and model configuration source alignment.
- P1 gaps are in scope after P0 evidence: knowledge operations, metadata governance service ownership, AI Infra tab-level feature degradation, and runtime health visibility.
- P2 gaps are in scope when they can be retired without destabilizing core flows, especially direct frontend calls to external GitHub APIs.

## Non-Goals

- Do not remove existing public admin routes in bulk.
- Do not remove backend legacy paths or `/api/**` aliases in this round.
- Do not introduce real secrets into docs, tests, or frontend responses.
- Do not fake ES, Milvus, pgvector, or provider runtime state in the frontend.

## Baseline Read Set

- `docs/aegis/plans/2026-06-02-frontend-backend-gap-remediation-plan.md`
- `frontend/src/router.tsx`
- `frontend/src/pages/admin/AdminLayout.tsx`
- `frontend/src/config/productMode.ts`
- `frontend/src/stores/featureStore.ts`
- `frontend/src/services/backendEndpointManifest.ts`
- `frontend/src/services/frontendCapabilityContracts.test.ts`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AdvancedFeature.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AdvancedFeatureGate.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseFeatureController.java`

## Impact Statement

The work touches frontend routing, admin navigation, endpoint contract tests, feature gating, and potentially backend controller/configuration surfaces. The compatibility boundary is to keep existing admin entry points available while making disabled or unavailable capabilities degrade clearly before protected calls are sent.
