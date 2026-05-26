# AI Infra Phase 6 Publish-ready - Checkpoint

- Task ID: 2026-05-26-ai-infra-phase6-publish-ready
- Current todo: Phase 6 Publish-ready focused acceptance passed; proceed to Phase 3 Worker hardening.
- Active slice: Phase 6 Publish-ready implementation complete within current scope.
- Completed todos:
  - Re-read section 15.3 of `09-unfinished-phase-design-development-plans.md`.
  - Re-read current Factory service, inbound port, JDBC adapters, Web controller, schema, and starter wiring.
  - Added JDBC activation and catalog adapters plus activation schema.
  - Added Web APIs for latest publish check, rollback, and catalog query.
  - Added starter repository beans and Kernel factory wiring for activation/catalog ports.
  - Preserved rollback invariant: activate target version and synchronize definition pointer without mutating `AgentVersion` snapshots.
- Evidence refs:
  - See `90-evidence.md` for RED/GREEN and focused acceptance commands.
- Blocked on: none
- Next step: Start Phase 3 Worker hardening with RED tests for worker tick semantics.

## DriftCheckDraft

- Scope status: Within Phase 6 Publish-ready; no frontend Studio or canary rollout.
- Compatibility status: Kernel will depend on ports only; JDBC/Web/Spring remain adapters.
- Retirement status: Existing Factory template/create/validate behavior remains active; this slice adds missing publish-ready surfaces.
- New risk signals:
  - Existing `AgentDefinition.latestVersionId` is a published pointer; rollback must not mutate `AgentVersion` snapshots.
  - Production Gate API already exists and should not be duplicated in Factory controller.
- Evidence status: sufficient for Phase 6 Publish-ready current slice, not full AI-Infra completion.
- Advisory decision: continue to Phase 3 Worker hardening
