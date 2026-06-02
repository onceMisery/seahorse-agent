# Evidence

## EvidenceBundleDraft

- `git status --short --branch` in isolated worktree showed `## codex/frontend-backend-gap-remediation`.
- `frontend/src/router.tsx` currently guards `/admin/approvals` with `AGENT_DEFINITION_MANAGEMENT`; plan expects `AGENT_RUN_MANAGEMENT`.
- `frontend/src/pages/admin/AdminLayout.tsx` currently shows `/admin/approvals` with `AGENT_DEFINITION_MANAGEMENT` and fetches `https://api.github.com/repos/onceMisery/seahorse-agent`.
- `frontend/src/stores/featureStore.ts` contains normal UTF-8 Chinese fallback strings.
- `AdvancedFeature.java` contains `AGENT_RUN_MANAGEMENT`, `AGENT_FACTORY_MANAGEMENT`, `COST_ANALYTICS`, and related features needed by the plan.
- `rg` for typical mojibake tokens in `frontend/src` returned no matches.
- RED: `vitest run src/services/frontendRemediationContracts.test.ts` initially failed for approval feature mapping, exported `getAgentTemplate`, and direct GitHub API calls.
- GREEN: `vitest run src/services/frontendRemediationContracts.test.ts --reporter=verbose --pool=forks` passed 5 tests after feature, GitHub, Agent Factory, metadata, and AI Infra guard fixes.
- RED: `vitest run src/services/frontendCapabilityContracts.test.ts --reporter=verbose --pool=forks` initially failed because manifest lacked `GET /admin/ai-config`.
- GREEN: after updating `scripts/extract-backend-mappings.js` and running `node scripts/extract-backend-mappings.js`, `frontendCapabilityContracts.test.ts` passed 8 tests.
- `node scripts/extract-backend-mappings.js` wrote 287 endpoints to `frontend/src/services/backendEndpointManifest.ts`.
- `npm run build` passed after AI Infra and metadata changes.
- RED/GREEN: `frontendRemediationContracts.test.ts` was extended for knowledge operations and SRE item rendering; final run passed 7 tests.
- `frontendCapabilityContracts.test.ts --reporter=verbose --pool=forks` passed 8 tests.
- `npm test -- --run --pool=forks` passed 12 files / 28 tests. npm emitted warnings that `--run` and `--pool` are unknown npm CLI configs, while Vitest still ran and passed.
- `npm run build` passed. Remaining warnings were old Browserslist data, large chunk size, and an existing `agentFactoryService.ts` static/dynamic import warning.
- `.\mvnw.cmd -pl seahorse-agent-spring-boot-starter -am -DskipTests '-Dmaven.test.skip=true' test-compile` passed. This compiled production code for kernel, web, adapters, and starter including `SeahorseAgentSreAdapterHealthAutoConfiguration`.
- RED: `.\mvnw.cmd -pl seahorse-agent-tests -am -DskipTests test-compile` initially failed in `seahorse-agent-tests` with `String`/`Long` fixture mismatches in metadata backfill/review and web contract tests.
- GREEN: after correcting test fixtures to match current record/port contracts, `.\mvnw.cmd -pl seahorse-agent-tests -am -DskipTests test-compile` passed for the 25-module reactor, including `seahorse-agent-tests`.
- GREEN: reran `.\mvnw.cmd -pl seahorse-agent-spring-boot-starter -am -DskipTests '-Dmaven.test.skip=true' test-compile`; the 23-module production compile reactor passed.
- Browser check: local frontend dev server on `127.0.0.1:5173` returned HTTP 200. The in-app browser loaded `/admin/ai-infra`, page title was `seahorse-agent AI 智能体`, and the DOM contained normal Chinese admin navigation plus `Agent 控制台`, `Runtime`, and `SRE` text. Authenticated backend data/API behavior was not covered.
- `rg -n "api\.github\.com|github\.com/.*/seahorse-agent|getAgentTemplate" frontend/src -S` found no direct `api.github.com` usage or exported `getAgentTemplate`; only local repo links and test assertions remain.
- `rg -n "@/services/api|api\.(get|post|put|delete|patch)" frontend/src/pages/admin/metadata-governance -S` returned no matches, confirming metadata governance page components no longer call the shared API client directly.
- `rg -n "vector-store|keyword-search|keyword-index|Runtime health|SreHealthItems|SreAdapterHealth" frontend/src seahorse-agent-spring-boot-starter/src -S` found the frontend SRE table and backend SRE adapter contributor registration/implementation.
- `git diff --check` exited 0; Git emitted only line-ending conversion warnings.
- Current work sidecars passed helper validation for `TaskIntentDraft`, `TodoCheckpointDraft`, `DriftCheckDraft`, and `EvidenceBundleDraft`.
- `python C:\Users\miracle\.codex\aegis\scripts\aegis-workspace.py bundle --root D:\code\seahorse-agent\.worktrees\frontend-backend-gap-remediation --work 2026-06-02-frontend-backend-gap-remediation` exited 0 and assembled `proof-bundle.md`.
- `python C:\Users\miracle\.codex\aegis\scripts\aegis-workspace.py check --root D:\code\seahorse-agent\.worktrees\frontend-backend-gap-remediation` exited 1 because historical `docs/aegis` records are missing `adr`/`baseline` directories, index entries, or current JSON sidecar schema fields. The current work records were validated separately.

## Not Yet Covered

- Authenticated browser checks with real backend data for `/admin/ai-infra`, `/admin/knowledge`, `/admin/model-config`, `/admin/settings`, and `/admin/metadata-governance` were not completed.
- The new SRE contributors report configured adapter type and actual Spring port bean presence. They do not perform real Elasticsearch, Milvus, or pgvector network health probes.
- P2-2 "gradual backend capability adoption" remains intentionally non-exhaustive; this work addressed direct broken/unreliable contracts and the P1/P2 items already selected in the plan.
- Full Aegis workspace check remains blocked by pre-existing historical workspace structure/index/schema drift outside this remediation slice.
