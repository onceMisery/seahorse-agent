# Checkpoint

## TodoCheckpointDraft

Current todo:

- Optional follow-up: run authenticated browser checks against a real backend session.

Completed todos:

- Created isolated worktree on `codex/frontend-backend-gap-remediation`.
- Copied the plan document into the isolated worktree.
- Read initial router, admin layout, feature store, and backend feature enum.
- Fixed approval center route/menu/page feature ownership to `AGENT_RUN_MANAGEMENT`.
- Removed direct frontend GitHub API star fetches and corrected repository links.
- Removed unused `getAgentTemplate()` service method for missing `/api/agent-templates/{}` backend endpoint.
- Added remediation guard tests for feature mapping, GitHub API removal, metadata service ownership, and AI Infra child feature mapping.
- Routed metadata dictionary, extraction result, and quarantine components through `metadataGovernanceService`.
- Added AI Infra tab/data-source feature mapping and prevented disabled child features from firing protected API requests.
- Fixed backend endpoint extraction to include class-level `@RequestMapping`, no-path mappings, arrays, and normalized path variables.
- Regenerated `backendEndpointManifest.ts` with class-level admin AI config and dashboard mappings.
- Aligned model configuration UI with deployment/runtime adapter source-of-truth and stopped returning raw provider API keys from `/rag/settings`.
- Added knowledge document operations for refresh due documents, refresh single document, rebuild knowledge-base keyword index, and rebuild document keyword index.
- Added AI Infra SRE item rendering so backend `items` from `/api/sre/health` are shown as contributor/status/message/evidence rows.
- Added backend runtime adapter SRE contributors in the Spring Boot starter for `vector-store`, `keyword-search`, and `keyword-index`, based on configured adapter type plus actual port bean presence.
- Verified frontend remediation contracts, frontend capability contracts, full frontend test suite, frontend production build, and backend production compile with tests skipped.
- Repaired backend test-source `String`/`Long` fixture drift across web, kernel, JDBC, starter, and integration tests.
- Verified full backend test-source compilation for `seahorse-agent-tests` and its reactor dependencies.
- Verified the local frontend route `/admin/ai-infra` renders the admin shell with normal Chinese navigation and AI Infra/SRE text in the in-app browser.

Active slice:

- Completion audit and reporting.

Next step:

- Report verified completion with residual authenticated browser/full-deployment boundaries.

Blocked on:

- None for the implemented code and automated compile/test/build gates.

## DriftCheckDraft

- Serves original intent: yes.
- Serves goal/stop condition: yes.
- Inside compatibility boundary: yes.
- New owner/fallback/adapter/branch appeared: yes, backend SRE contributor auto-configuration was added as the canonical runtime-status producer for adapter visibility.
- Retirement track explicit: yes, retire direct GitHub API fetches, wrong approval feature ownership, unused agent template detail method, component-level metadata API calls, and frontend-only SRE inference.
- Evidence enough for next claim: enough for implemented slices and automated regression gates; not enough to claim full authenticated browser/manual deployment acceptance.
- Decision: report implemented work with residual browser-auth/full-deployment boundaries.

## ResumeStateHint

Resume from `D:\code\seahorse-agent\.worktrees\frontend-backend-gap-remediation` on branch `codex/frontend-backend-gap-remediation`. Re-read this checkpoint and the plan before continuing.
