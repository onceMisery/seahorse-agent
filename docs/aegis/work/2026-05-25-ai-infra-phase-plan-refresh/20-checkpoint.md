# AI Infra Phase Plan Refresh - Checkpoint

- Task ID: 2026-05-25-ai-infra-phase-plan-refresh
- Current todo: Documentation refresh self-check completed for the latest 2026-05-26 worktree state, including post-Phase-4/5/8A calibration, section 15 refined remaining-stage plans, and section 16 execution-ready blueprints.
- Active slice: documentation refresh
- Completed todos:
  - Read `docs/company-agent/` and `docs/company-agent/ai-infra-phases/` with UTF-8 output to avoid mojibake.
  - Reconciled `09-unfinished-phase-design-development-plans.md` with the current worktree state: Phase 5B OpenAPI Web/Starter closure is now implemented and focused-regression verified.
  - Updated the recommended execution order to start from Phase 5A MCP OAuth Token Provider.
  - Added section `11. 2026-05-26 未完成阶段下一步开发卡`.
  - Added one concrete next development card each for Phase 4, Phase 5, Phase 6, Phase 7, and Phase 8.
  - Re-read Phase 4-8 source phase docs and top-level gap/risk material.
  - Added section `12. 2026-05-26 深读后的阶段级详细补充方案`.
  - Added one deeper phase-level design/development plan each for Phase 4, Phase 5, Phase 6, Phase 7, and Phase 8.
  - Re-read the latest Sandbox Runtime foundation and Agent Factory kernel foundation Aegis checkpoints.
  - Added section `13. 2026-05-26 当前实现后的未完成阶段详细方案`.
  - Added one current-state detailed design/development plan each for Phase 4, Phase 5, Phase 6, Phase 7, and Phase 8, reflecting that Approval API, Sandbox kernel foundation, and Agent Factory kernel foundation now exist.
  - Re-read current worktree evidence after Phase 6 Agent Factory integration and newer Phase 3 runtime progress.
  - Added section `14. 2026-05-26 深读与当前 worktree 校准后的未完成阶段执行方案`.
  - Added one more specific design/development plan each for Phase 3, Phase 4, Phase 5, Phase 6, Phase 7, and Phase 8.
  - Updated latest recommended execution order to start from Phase 5 Sandbox persistence/Web/starter, then Phase 8A Audit Ledger.
  - Re-read current worktree evidence after Phase 5 Sandbox integration, Phase 8A Audit Ledger foundation, and Phase 4 Resource ACL dry-run/provenance.
  - Added section `15. 2026-05-26 当前完成进度后的剩余阶段精细设计开发方案`.
  - Added refined remaining-stage plans for Phase 3 worker hardening, Phase 5 connector residuals, Phase 6 publish-ready/rollback/catalog, Phase 7 local Agent-as-Tool, and Phase 8B/C/D.
  - Updated the latest remaining execution order to start from Phase 6 Publish-ready, then Phase 3 worker hardening, then Phase 5 connector residuals.
  - Re-read the current worktree code surface for Phase 3/5/6/7/8 owner files and confirmed Phase 6 kernel interfaces exist while outer JDBC/Web/starter closure remains the next concrete gap.
  - Added section `16. 2026-05-26 深读后的未完成阶段开发执行蓝图`.
  - Added one execution-ready design/development blueprint each for Phase 6 Publish-ready, Phase 3 Worker hardening, Phase 5 Connector residuals, Phase 7 Local Agent-as-Tool, and Phase 8B/C/D.
  - Ran heading, unresolved-marker, and whitespace checks.
  - Re-read `docs/company-agent/` and `docs/company-agent/ai-infra-phases/` again after Phase 3 worker and Phase 6 publish-ready implementation evidence appeared in the worktree.
  - Added section `17. 2026-05-26 Phase 3/6 完成后的未完成阶段设计开发方案`.
  - Calibrated the latest unfinished-stage entry point: Phase 3 and Phase 6 are no longer the next main planning targets; Phase 4 is a closeout/hardening slice; Phase 5, Phase 7, and Phase 8B/C/D remain the main unfinished implementation areas.
  - Updated `docs/company-agent/ai-infra-phases/README.md` to point readers to section 17 as the latest unfinished-stage plan entry.
  - Ran section 17 heading scan and targeted whitespace checks.
- Evidence refs:
  - Self-check commands recorded in `90-evidence.md`.
- Blocked on: none
- Next step: Continue implementation from section 17.5 order: Phase 5 connector residuals, Phase 4 audit/DB hardening, Phase 7 local Agent-as-Tool, then Phase 8B/C/D.

## DriftCheckDraft

- Scope status: Within documentation refresh request; no new production code implementation added in this slice.
- Compatibility status: Plans preserve kernel-to-port dependency direction and adapter boundaries.
- Retirement status: Older Phase 4/OpenAPI/Sandbox/Factory assumptions are superseded in sections 13, 14, 15, and 16, not deleted.
- New risk signals:
  - Full AI Infra remains incomplete; this slice only improves design/development planning.
  - Phase 5 connector residuals, Phase 4 audit/DB hardening, Phase 7 local handoff, and Phase 8B/C/D remain implementation work.
- Advisory decision: continue

## 2026-05-26 Section 18 Refresh Checkpoint

- Current todo: User requested another deep read of `docs/company-agent/` and `docs/company-agent/ai-infra-phases/`, then one more detailed concrete design/development plan for each unfinished phase.
- Active slice: documentation refresh only; no production code implementation in this slice.
- Completed todos:
  - Re-read Aegis `using-aegis`, `long-task-continuation`, `writing-plans`, and `brainstorming` guidance for routing.
  - Confirmed current isolated worktree: `D:\code\seahorse-agent\.worktrees\ai-infra-phase-design-plans`.
  - Re-read architecture baseline, test baseline, staged development plan, gap analysis, Phase 0-8 phase docs, `99-current-implementation-handoff.md`, and the two enterprise Agent background articles with UTF-8 output.
  - Re-read latest existing `09-unfinished-phase-design-development-plans.md` section 17 and the previous phase-plan checkpoint.
  - Added section `18. 2026-05-26 深入研读后的未完成阶段执行级设计开发方案`.
  - Added one execution-level design/development plan for Phase 4, Phase 5, Phase 7, and Phase 8B/C/D.
  - Confirmed Phase 0-3 and Phase 6 no longer need another unfinished-phase plan in this slice; they remain regression/composition dependencies.
  - Updated `docs/company-agent/ai-infra-phases/README.md` so the current remaining-plan entry points to section 18.
  - Ran heading scan and whitespace check for the updated docs.
- Evidence refs:
  - Section 18 heading scan and README pointer scan recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Continue implementation from section 18.6 order: Phase 5 connector residuals, Phase 4 audit/DB hardening, Phase 7 local Agent-as-Tool, then Phase 8B/C/D.

## Section 18 DriftCheckDraft

- Scope status: Within the user's documentation-planning request; production code was not changed.
- Compatibility status: The new plans preserve kernel-to-port dependency direction, small ISP ports, adapter boundaries, append-only audit/cost records, and enum/constant rules.
- Retirement status: Section 18 supersedes section 17 as the latest execution entry while keeping older sections as history.
- New risk signals:
  - Full AI Infra remains incomplete; section 18 defines the remaining implementation plan, not completion evidence.
  - Phase 5 connector residuals and Phase 8B/C/D remain the largest implementation surface.
- Advisory decision: continue.

## 2026-05-26 Section 19 Execution Card Checkpoint

- Current todo: User requested a deeper read of `docs/company-agent/` and `docs/company-agent/ai-infra-phases/`, then one more detailed and concrete design/development plan for each unfinished phase.
- Active slice: documentation refresh only; no production code implementation in this slice.
- Completed todos:
  - Re-read the latest phase-plan state and current code surface for Resource ACL, connector credential binding, sandbox, audit event types, Web controllers, JDBC adapters, and starter wiring.
  - Added section `19. 2026-05-26 未完成阶段执行卡补充方案` to `docs/company-agent/ai-infra-phases/09-unfinished-phase-design-development-plans.md`.
  - Added a latest execution order that starts with Phase 5 Connector/Sandbox Security Closure, then Phase 4 Resource ACL Import Commit + Audit Closure, Phase 7 Local Agent-as-Tool, Phase 8B Eval Gate, Phase 8C Quota/Cost/SRE, and Phase 8D Canary/Pilot Gate.
  - Added one execution-card-level design/development plan each for Phase 4, Phase 5, Phase 7, Phase 8B, Phase 8C, and Phase 8D.
  - Each execution card now includes target outcomes, file boundaries, domain/port/API/table design, TDD red/green steps, exact focused verification commands, non-goals, and rollback notes.
  - Confirmed Phase 0-3 and Phase 6 are not re-planned as unfinished stages in this slice; they remain dependencies and regression scopes.
  - Updated `docs/company-agent/ai-infra-phases/README.md` so the current remaining-plan entry points to section 19.
  - Ran heading scan, unresolved-marker scan, tail readback, and whitespace check for the updated docs.
- Evidence refs:
  - Section 19 heading scan, README pointer scan, unresolved-marker scan, tail readback, and whitespace check recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Implementation should continue from section 19.1 order, starting with Phase 5 Connector/Sandbox Security Closure, then Phase 4 Resource ACL Import Commit + Audit Closure.

## Section 19 DriftCheckDraft

- Scope status: Within the user's documentation-planning request; production code was not changed.
- Compatibility status: The execution cards preserve kernel-to-port dependency direction, small ISP ports, adapter boundaries, enum/constant status semantics, append-only audit/cost records, and no new workflow engine or remote mesh.
- Retirement status: Section 19 supersedes section 18 as the latest execution entry while preserving older sections as historical planning context.
- New risk signals:
  - Full AI Infra remains incomplete; section 19 is a design/development plan, not completion evidence.
  - Phase 7 and Phase 8B/C/D remain substantial implementation surfaces.
  - `09-unfinished-phase-design-development-plans.md` is an untracked file in the current dirty worktree, so diff statistics do not include it unless it is staged or added later.
- Advisory decision: continue.

## 2026-05-26 Section 20 Implementation Pack Checkpoint

- Current todo: Continue toward full AI Infra completion by keeping the latest unfinished-stage implementation entry current, then resume implementation from Phase 5.
- Active slice: documentation refresh and implementation handoff alignment; no production code implementation in this slice.
- Completed todos:
  - Re-read the latest `README.md` pointer and confirmed `09-unfinished-phase-design-development-plans.md` still ended at section 19.
  - Added `docs/company-agent/ai-infra-phases/20-unfinished-phase-implementation-pack.md` as the latest implementation entry.
  - Added one concrete implementation package each for Phase 5, Phase 4, Phase 7, Phase 8B, Phase 8C, and Phase 8D.
  - Each package includes goals, file boundaries, first RED tests, minimal GREEN order or API/table contracts, verification commands, rollback notes, and explicit non-goals.
  - Updated `docs/company-agent/ai-infra-phases/README.md` to point the current remaining plan to section 20's standalone implementation pack while keeping section 19 as historical planning context.
  - Ran heading/readback scan, unresolved-marker scan, tail readback, and whitespace check for the new entry and README.
- Evidence refs:
  - Section 20 scans and whitespace checks recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Resume implementation from `20-unfinished-phase-implementation-pack.md` order, starting with Phase 5 Connector/Sandbox Security Closure.

## Section 20 DriftCheckDraft

- Scope status: Within the user's documentation-planning request and active AI Infra goal; production code was not changed in this slice.
- Compatibility status: The implementation pack preserves kernel-to-port dependency direction, adapter boundaries, small ISP ports, enum/constant status semantics, append-only evidence records, and no workflow engine or remote mesh.
- Retirement status: `20-unfinished-phase-implementation-pack.md` supersedes section 19 as the latest execution entry while preserving `09-unfinished-phase-design-development-plans.md` as historical analysis.
- New risk signals:
  - Full AI Infra remains incomplete; the new implementation pack is a plan and handoff artifact, not completion evidence.
  - The next implementation slice must produce RED/GREEN evidence for Phase 5 sandbox/connector audit closure.
- Advisory decision: continue.

## 2026-05-26 Section 9/10 Deep-Read Supplement Checkpoint

- Current todo: User requested a deeper read of `docs/company-agent/` and `docs/company-agent/ai-infra-phases/`, then one more detailed concrete design/development plan for each unfinished phase.
- Active slice: documentation planning only; no production code implementation in this slice.
- Completed todos:
  - Re-read the current standalone implementation pack, Phase 4-8 original phase docs, architecture baseline, test baseline, enterprise Agent gap analysis, enterprise Agent background article, historical unfinished-stage plan headings, and Aegis checkpoint/evidence tails.
  - Confirmed the current unfinished stages remain Phase 5, Phase 4, Phase 7, Phase 8B, Phase 8C, and Phase 8D.
  - Appended section `9. 2026-05-26 深读补充：每个未完成阶段的具体开发方案` to `docs/company-agent/ai-infra-phases/20-unfinished-phase-implementation-pack.md`.
  - Added one more detailed implementation plan each for Phase 5 Connector/Sandbox security closure, Phase 4 Resource ACL import/audit/DB hardening, Phase 7 Governed Local Agent-as-Tool, Phase 8B Eval Summary Gate, Phase 8C Quota/Cost/SRE Health, and Phase 8D Canary/Pilot Gate.
  - Added section `10. 深读补充后的执行判定` to keep the latest implementation order explicit.
  - Ran heading scan, unresolved-marker scan, tail readback, and whitespace check for the updated implementation pack.
- Evidence refs:
  - Section 9/10 heading scan, unresolved-marker scan, whitespace check, and tail readback are recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Resume implementation from section 10 order, starting with Phase 5 Connector/Sandbox Security Closure, then Phase 4 Resource ACL Import Commit + Audit Closure.

## Section 9/10 DriftCheckDraft

- Scope status: Within the user's documentation-planning request; no production code changed.
- Compatibility status: The supplement preserves kernel-to-port dependency direction, small ISP ports, adapter boundaries, enum/constant status semantics, append-only audit/cost/eval/readiness records, and no workflow engine or remote mesh.
- Retirement status: Section 9/10 refines the standalone section 20 implementation pack; it does not replace the pack or delete older historical planning records.
- New risk signals:
  - Full AI Infra remains incomplete; this supplement is a design/development plan, not implementation evidence.
  - Phase 7 and Phase 8B/C/D remain substantial implementation surfaces.
  - Aegis evidence must be updated again when RED/GREEN implementation starts.
- Advisory decision: continue.

## 2026-05-26 Phase 5 Security Closure Checkpoint

- Current todo: Continue the active AI Infra implementation goal from `20-unfinished-phase-implementation-pack.md`, starting with Phase 5 Connector/Sandbox Security Closure.
- Active slice: Phase 5 verification and evidence capture. No new production code was needed in this slice because the current worktree already contains connector/sandbox audit, credential binding rotation, enable/disable gates, MCP OAuth credential handling, Web contracts, JDBC adapters, and starter wiring.
- Completed todos:
  - Re-read current connector, sandbox, audit, Web, JDBC, and starter code surfaces.
  - Confirmed existing tests cover connector import default-disabled behavior, high-risk enable approval policy, active credential binding requirement, binding rotation, operation disable idempotency, connector audit redaction, sandbox session/execution audit redaction, and starter audit-ledger wiring.
  - Ran Phase 5 focused regression across kernel, MCP OAuth adapter, JDBC adapters, Web contracts, and starter wiring.
- Evidence refs:
  - Phase 5 focused regression commands and pass counts are recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Verify Phase 4 Resource ACL Import Commit + Audit Closure, then proceed to Phase 7 Local Agent-as-Tool if Phase 4 is already green.

## Phase 5 DriftCheckDraft

- Scope status: Within the active AI Infra goal and the latest implementation pack order.
- Compatibility status: Evidence covers current Phase 5 closure without adding new dependencies or widening kernel boundaries.
- Retirement status: Phase 5 remains as implemented code plus focused regression evidence; no rollback or deletion performed.
- New risk signals:
  - Phase 5 evidence is focused, not a full repository build.
  - Full AI Infra remains incomplete; Phase 4, Phase 7, and Phase 8B/C/D remain.
- Advisory decision: continue.

## 2026-05-26 Phase 4 ACL Audit/DB Closure Checkpoint

- Current todo: Verify Phase 4 Resource ACL Import Commit + Audit Closure after Phase 5 focused regression passed.
- Active slice: Phase 4 verification and evidence capture. No new production code was needed in this slice because the current worktree already contains Resource ACL dry-run/import commit, audit wiring, access audit wrapper, JDBC enum/constraint coverage, Web endpoint, and starter wiring.
- Completed todos:
  - Ran Phase 4 focused kernel regression for Resource ACL domain, management service, ACL-backed policy, audited access policy, and dry-run/import behavior.
  - Ran Phase 4 JDBC regression for Resource ACL repository and audit event repository.
  - Ran Phase 4 starter regression for registry/audit auto-configuration.
  - Ran Phase 4 Web regression. The first Web run failed with `NoClassDefFoundError` while multiple Maven commands were running in parallel against the same module output. Confirmed source and class files existed, then reran the same Web command serially; the serial run passed.
- Evidence refs:
  - Phase 4 focused regression commands, pass counts, and Web rerun diagnosis are recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Start Phase 7 Governed Local Agent-as-Tool with RED tests for handoff domain, context policy, mesh policy, local tool adapter, repository, and query/cancel API.

## Phase 4 DriftCheckDraft

- Scope status: Within the active AI Infra goal and latest implementation pack order.
- Compatibility status: Evidence supports current Phase 4 closure without adding new dependencies or changing ContextPack semantics.
- Retirement status: Resource ACL history remains append/disable based; no physical deletion or rollback was performed.
- New risk signals:
  - Phase 4 Web verification should be run serially when other Maven builds target `seahorse-agent-adapter-web`.
  - Full AI Infra remains incomplete; Phase 7 and Phase 8B/C/D remain.
- Advisory decision: continue.

## 2026-05-26 Section 11/12 Current-State Plan Checkpoint

- Current todo: User requested a deep read of `docs/company-agent/` and `docs/company-agent/ai-infra-phases/`, then one more detailed concrete design/development plan for each unfinished phase.
- Active slice: documentation planning only; no production code implementation in this slice.
- Completed todos:
  - Re-read the current objective, Aegis `using-aegis`, `long-task-continuation`, `writing-plans`, and `brainstorming` routing guidance.
  - Confirmed active isolated worktree `D:\code\seahorse-agent\.worktrees\ai-infra-phase-design-plans`.
  - Re-read `docs/company-agent/` and `docs/company-agent/ai-infra-phases/` with UTF-8 heading and keyword scans, including architecture baseline, test baseline, staged plan, gap analysis, Phase 0-8 docs, `99-current-implementation-handoff.md`, `09-unfinished-phase-design-development-plans.md`, and `20-unfinished-phase-implementation-pack.md`.
  - Re-read current evidence showing Phase 5 Connector/Sandbox Security Closure and Phase 4 Resource ACL Import Commit + Audit Closure have focused regression evidence.
  - Scanned current code surface for handoff, eval, quota, cost, health, rollout, readiness, gate, audit, sandbox, connector, and resource ACL owners.
  - Added section `11. 2026-05-26 当前实现校准后的剩余阶段设计开发方案` to `docs/company-agent/ai-infra-phases/20-unfinished-phase-implementation-pack.md`.
  - Added one detailed design/development plan each for Phase 7 Handoff starter/tool-registration closure, Phase 8B Agent Eval Summary Gate, Phase 8C Quota/Cost/SRE Health, and Phase 8D Canary/Pilot Readiness.
  - Added section `12. 当前最新执行判定`, explicitly moving the latest main implementation order to Phase 7, Phase 8B, Phase 8C, and Phase 8D while keeping Phase 4/5 as regression dependencies.
  - Updated `docs/company-agent/ai-infra-phases/README.md` so `当前剩余方案` points readers to section 11/12 as the latest current-state entry.
  - Ran heading scan, unresolved-marker scan, tail readback, README pointer scan, and whitespace check.
- Evidence refs:
  - Section 11/12 heading scan, README pointer scan, unresolved-marker scan, and whitespace check recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Resume implementation from section 12 order, starting with Phase 7 Handoff starter/tool-registration closure.

## Section 11/12 DriftCheckDraft

- Scope status: Within the user's documentation-planning request; production code was not changed in this slice.
- Compatibility status: The new plans preserve kernel-to-port dependency direction, small ISP ports, adapter boundaries, enum/constant state semantics, append-only audit/eval/cost/readiness records, and no workflow engine or remote mesh.
- Retirement status: Section 11/12 supersedes section 9/10 as the latest current-state implementation entry; older sections remain historical planning context.
- New risk signals:
  - Full AI Infra remains incomplete; Phase 7 starter/tool registration and Phase 8B/C/D still require implementation and RED/GREEN evidence.
  - Phase 4/5 are no longer main implementation entries but must remain regression scopes for later phases.
- Advisory decision: continue.

## 2026-05-26 Phase 7 Handoff Starter Closure Checkpoint

- Current todo: Close Phase 7 Handoff starter/tool-registration gap before moving to Phase 8B.
- Active slice: Phase 7 focused implementation and verification.
- Completed todos:
  - Reproduced RED: `SeahorseAgentChatRunStoreAutoConfigurationTests.shouldWireLocalAgentAsToolPortWhenHandoffDependenciesExist` failed because no `LocalAgentAsToolPort` bean existed.
  - Diagnosed root cause: the handoff starter test context provided `AgentHandoffRepositoryPort`, `AgentRunRepositoryPort`, `CurrentUserPort`, and `MeshPolicyPort`, but did not provide `AgentDefinitionRepositoryPort`; therefore the correct production condition for `AgentRunInboundPort` was not satisfied and `KernelAgentHandoffService` could not be created in that test context.
  - Kept production auto-configuration boundaries unchanged. Did not weaken the requirement that handoff create child runs through `AgentRunInboundPort`.
  - Added `AgentDefinitionRepositoryPort` to `TestApprovalRuntimeConfiguration` in `SeahorseAgentChatRunStoreAutoConfigurationTests`.
  - Re-ran starter GREEN: registry/chat run-store auto-configuration tests passed.
  - Ran Phase 7 focused kernel, JDBC, Web, and starter regressions serially.
  - Ran kernel dependency scan and diff whitespace check.
- Evidence refs:
  - Phase 7 RED/GREEN and focused regression commands recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Start Phase 8B Agent Eval Summary Gate with RED tests for eval domain, eval query service, production gate eval integration, JDBC latest isolation, and Web contract.

## Phase 7 Handoff Starter Closure DriftCheckDraft

- Scope status: Within active AI Infra goal and section 12 execution order.
- Compatibility status: Production conditions still require `AgentHandoffRepositoryPort`, `AgentRunInboundPort`, and `MeshPolicyPort`; no controller create path or repository-owned child run creation was introduced.
- Retirement status: No old path added or retained. The test fixture now mirrors the real dependency graph needed for governed local handoff.
- New risk signals:
  - Evidence is focused, not a full repository build.
  - Full AI Infra remains incomplete; Phase 8B/C/D remain.
- Advisory decision: continue.

## 2026-05-26 Section 13/14 Phase 7-Cleared Plan Checkpoint

- Current todo: User requested a deep read of `docs/company-agent/` and `docs/company-agent/ai-infra-phases/`, then one more detailed concrete design/development plan for each unfinished phase.
- Active slice: documentation planning only; no production code implementation in this slice.
- Completed todos:
  - Re-read the active goal and Aegis routing skills for using Aegis, long-task continuation, and writing plans.
  - Confirmed active isolated worktree `D:\code\seahorse-agent\.worktrees\ai-infra-phase-design-plans`.
  - Re-read `docs/company-agent/` and `docs/company-agent/ai-infra-phases/` with UTF-8 scans, including architecture baseline, test baseline, staged plan, gap analysis, Phase 0-8 docs, the old handoff file, the historical unfinished-stage plan, and the current standalone implementation pack.
  - Re-read current Aegis checkpoint/evidence and confirmed Phase 5, Phase 4, and Phase 7 have focused regression evidence.
  - Scanned current code surface for Production Gate, Eval, Quota, Cost, SRE, Rollout, and Readiness owners.
  - Added section `13. 2026-05-26 Phase 7 收口后的剩余阶段执行级设计开发方案` to `docs/company-agent/ai-infra-phases/20-unfinished-phase-implementation-pack.md`.
  - Added one execution-level design/development plan each for Phase 8B Agent Eval Summary Gate, Phase 8C Quota/Cost/SRE Health, and Phase 8D Canary/Pilot Readiness.
  - Added section `14. Phase 7 收口后的当前最新执行判定`, explicitly moving the latest main implementation order to Phase 8B, Phase 8C, and Phase 8D while keeping Phase 4/5/7 as regression dependencies.
  - Updated `docs/company-agent/ai-infra-phases/README.md` so `当前剩余方案` points readers to section 13/14 as the latest current-state entry.
- Evidence refs:
  - Section 13/14 heading scan, README pointer scan, unresolved-marker scan, tail readback, and whitespace check are recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Resume implementation from section 14 order, starting with Phase 8B Agent Eval Summary Gate.

## Section 13/14 DriftCheckDraft

- Scope status: Within the user's documentation-planning request; production code was not changed in this slice.
- Compatibility status: The new plans preserve kernel-to-port dependency direction, small ISP ports, adapter boundaries, enum/constant state semantics, append-only eval/cost/rollout/readiness records, and no workflow engine, remote mesh, real billing, or real traffic router.
- Retirement status: Section 13/14 supersedes section 11/12 as the latest current-state implementation entry; older sections remain historical planning context.
- New risk signals:
  - Full AI Infra remains incomplete; Phase 8B/C/D still require implementation and RED/GREEN evidence.
  - Phase 4/5/7 are no longer main implementation entries but must remain regression scopes for later Phase 8 slices.
- Advisory decision: continue.

## 2026-05-26 Section 15/16 Deep-Read Refinement Checkpoint

- Current todo: User requested a deep read of `docs/company-agent/` and `docs/company-agent/ai-infra-phases/`, then one more detailed and concrete design/development plan for each unfinished phase.
- Active slice: documentation planning only; no production code implementation in this slice.
- Completed todos:
  - Re-read the active goal and Aegis routing guidance for using Aegis, long-task continuation, writing plans, and brainstorming.
  - Confirmed active isolated worktree `D:\code\seahorse-agent\.worktrees\ai-infra-phase-design-plans`.
  - Re-read `docs/company-agent/` and `docs/company-agent/ai-infra-phases/` with UTF-8 scans, including architecture baseline, test baseline, Phase 8 hardening doc, the old handoff file, the historical unfinished-stage plan, and the current standalone implementation pack.
  - Re-read current Aegis checkpoint/evidence and confirmed Phase 4, Phase 5, and Phase 7 already have focused regression evidence and should remain regression dependencies.
  - Scanned current code surface and confirmed Phase 8B has kernel-level eval summary implementation traces while JDBC/Web/starter closure is still pending; Phase 8C and Phase 8D remain the larger absent implementation surfaces.
  - Added section `15. 2026-05-26 再次深读后的剩余阶段精细开发方案` to `docs/company-agent/ai-infra-phases/20-unfinished-phase-implementation-pack.md`.
  - Added one more concrete design/development plan each for Phase 8B Eval Summary outer-layer closure, Phase 8C Quota/Cost/SRE evidence chains, and Phase 8D Rollout/Readiness.
  - Added section `16. 再次深读后的当前最新执行判定`, explicitly preserving Phase 8B, Phase 8C, and Phase 8D as the only remaining main implementation phases.
  - Updated `docs/company-agent/ai-infra-phases/README.md` so `当前剩余方案` points readers to section 15/16 as the latest entry.
  - Ran heading scan, unresolved-marker scan, tail readback, and whitespace check.
- Evidence refs:
  - Section 15/16 heading scan, README pointer scan, unresolved-marker scan, tail readback, and whitespace check are recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Resume implementation from section 16 order, starting with Phase 8B Eval Summary Gate outer-layer closure: explicit gate severity, JDBC adapter/schema, Web controller, starter wiring, and focused regression.

## Section 15/16 DriftCheckDraft

- Scope status: Within the user's documentation-planning request; production code was not changed in this slice.
- Compatibility status: The refined plans preserve kernel-to-port dependency direction, small ISP ports, adapter boundaries, enum/constant state semantics, append-only eval/cost/rollout/readiness records, and no workflow engine, remote mesh, real billing, or real traffic router.
- Retirement status: Section 15/16 supersedes section 13/14 as the latest current-state implementation entry; older sections remain historical planning context.
- New risk signals:
  - Full AI Infra remains incomplete; Phase 8B/C/D still require implementation and RED/GREEN evidence.
  - Phase 8B has partial kernel implementation but still lacks JDBC/Web/starter closure evidence.
  - Phase 4/5/7 remain mandatory focused regression dependencies for later Phase 8 slices.
- Advisory decision: continue.

## 2026-05-26 Phase 8B Eval Summary Gate Checkpoint

- Current todo: Close Phase 8B Eval Summary Gate outer-layer implementation before starting Phase 8C.
- Active slice: Phase 8B focused implementation and verification.
- Completed todos:
  - Re-read Phase 8B checkpoint/evidence, starter auto-configuration, eval query service, production gate service, JDBC eval repository, and starter auto-configuration test.
  - Reproduced RED: `SeahorseAgentRegistryAutoConfigurationTests.shouldCreatePhaseOneRegistryAndRunStoreBeans` failed because no `AgentEvalSummaryRepositoryPort` bean existed.
  - Added `JdbcAgentEvalSummaryRepositoryAdapter` auto-configuration under the existing JDBC repository property boundary.
  - Added `KernelAgentEvalQueryService` auto-configuration behind `AgentEvalSummaryRepositoryPort`.
  - Injected optional `AgentEvalSummaryRepositoryPort` into `KernelProductionGateService` auto-configuration without weakening the existing `ProductionGateRepositoryPort` and `AgentDefinitionRepositoryPort` requirements.
  - Ran starter GREEN and Phase 8B focused regressions across kernel, JDBC, Web, and starter.
  - Ran kernel dependency scan, raw eval/secret field scan, and whitespace check.
- Evidence refs:
  - Phase 8B RED/GREEN and focused regression commands are recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Start Phase 8C Quota/Cost/SRE with RED tests for quota policy/decision, cost usage append/aggregate, SRE health contributor aggregation, JDBC/Web/starter wiring, and production gate integration.

## Phase 8B DriftCheckDraft

- Scope status: Within active AI Infra goal and section 16 execution order.
- Compatibility status: Kernel dependency direction is preserved; eval summary remains a small repository/inbound port surface; production gate consumes eval through the port abstraction.
- Retirement status: The previous fixed foundation warning remains only for missing eval repository fallback; when repository is configured, gate uses latest eval summary evidence.
- New risk signals:
  - Evidence is focused, not a full repository build.
  - Phase 8C and Phase 8D remain incomplete.
  - Raw-field scan produced an unrelated existing `rawCases` variable in the memory golden-case loader; Phase 8B eval/web/JDBC surfaces did not introduce raw eval sample storage.
- Advisory decision: continue.

## 2026-05-26 Section 17/18 Current Implementation Calibration Checkpoint

- Current todo: User requested another deep read of `docs/company-agent/` and `docs/company-agent/ai-infra-phases/`, then one more detailed and concrete design/development plan for each unfinished phase.
- Active slice: documentation planning only; no production code implementation in this slice.
- Completed todos:
  - Re-read the active goal and latest Aegis checkpoint/evidence state.
  - Confirmed active isolated worktree `D:\code\seahorse-agent\.worktrees\ai-infra-phase-design-plans`.
  - Re-read `docs/company-agent/` and `docs/company-agent/ai-infra-phases/` with UTF-8 output, including architecture baseline, test baseline, staged plan, gap analysis, Phase 0-8 docs, historical `09` plan, old `99` handoff, and current standalone implementation pack.
  - Used a read-only explorer subagent to independently inspect the same document set and report current unfinished-stage signals; no files were modified by the subagent.
  - Re-read current code surface for quota, cost, SRE, rollout, readiness, production gate, eval summary, ACL, connector, sandbox, and handoff owners.
  - Confirmed Phase 4, Phase 5, Phase 7, and Phase 8B now have focused regression evidence and should be treated as regression dependencies, not current main implementation stages.
  - Confirmed Phase 8C has kernel/domain/port/service traces and kernel focused regression evidence, while JDBC/Web/starter closure remains missing.
  - Confirmed Phase 8D rollout/readiness owner files are not present and remain the next large implementation stage after Phase 8C.
  - Added section `17. 2026-05-26 当前实现校准后的未完成阶段精细开发方案` to `docs/company-agent/ai-infra-phases/20-unfinished-phase-implementation-pack.md`.
  - Added one detailed design/development plan for Phase 8C Quota/Cost/SRE outer-layer closure and one for Phase 8D Rollout/Readiness.
  - Added section `18. 当前最新执行判定`, updating the current main implementation order to Phase 8C, then Phase 8D, then final completion audit.
  - Updated `docs/company-agent/ai-infra-phases/README.md` so `当前剩余方案` points readers to section 17/18 as the latest current-state entry.
- Evidence refs:
  - Section 17/18 heading scan, README pointer scan, unresolved-marker scan, tail readback, and whitespace check are recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Resume implementation from section 18 order, starting with Phase 8C JDBC/Web/starter closure.

## Section 17/18 DriftCheckDraft

- Scope status: Within the user's documentation-planning request; production code was not changed in this slice.
- Compatibility status: The new plans preserve kernel-to-port dependency direction, small ISP ports, adapter boundaries, enum/constant state semantics, append-only eval/cost/readiness records, and no workflow engine, remote mesh, real billing, or real traffic router.
- Retirement status: Section 17/18 supersedes section 15/16 as the latest current-state implementation entry; older sections remain historical planning context.
- New risk signals:
  - Full AI Infra remains incomplete; Phase 8C JDBC/Web/starter and Phase 8D full implementation still require RED/GREEN evidence.
  - Phase 8C is partially implemented in kernel only; outer adapters and auto-configuration remain the immediate gap.
  - Phase 4/5/7/8B remain mandatory focused regression dependencies for final completion.
- Advisory decision: continue.

## 2026-05-26 Section 19 RED Test Calibration Checkpoint

- Current todo: User requested another deep read of `docs/company-agent/` and `docs/company-agent/ai-infra-phases/`, then one more detailed concrete design/development plan for every unfinished phase.
- Active slice: documentation planning only; no production code implementation in this slice.
- Completed todos:
  - Re-read Aegis `using-aegis`, `long-task-continuation`, `writing-plans`, and `brainstorming` guidance for routing.
  - Confirmed active isolated worktree `D:\code\seahorse-agent\.worktrees\ai-infra-phase-design-plans`.
  - Re-read `docs/company-agent/` and `docs/company-agent/ai-infra-phases/` with UTF-8 heading/key-signal scans, including Phase 8 production hardening, architecture baseline, old handoff, historical unfinished plans, and current standalone implementation pack.
  - Re-read current Phase 8C kernel contracts and RED tests for quota, cost usage, and SRE health.
  - Confirmed current code facts: `CostUsageSource` is `MODEL`, `TOOL`, `SANDBOX`, `MANUAL_ADJUSTMENT`; `QuotaScope`, `QuotaPolicyStatus`, `QuotaDecisionEffect`, and `SreHealthStatus` already exist in kernel and should be treated as implementation authority.
  - Confirmed Phase 8C has JDBC/Web RED tests already written, while starter RED still needs to be added before GREEN implementation.
  - Confirmed Phase 8D rollout/readiness owner files are still absent and must start from domain RED tests.
  - Added section `19. 2026-05-26 RED 测试校准后的未完成阶段精细开发方案` to `docs/company-agent/ai-infra-phases/20-unfinished-phase-implementation-pack.md`.
  - Added one more detailed implementation plan for Phase 8C Quota/Cost/SRE outer-layer closure, aligned to current RED tests and real enum/port names.
  - Added one more detailed implementation plan for Phase 8D Rollout/Readiness from-zero closure, including domain, port, JDBC, Web, starter, and readiness evidence contracts.
  - Added section `19.4 最新执行判定`, preserving the latest implementation order: Phase 8C, Phase 8D, then final completion audit.
  - Updated `docs/company-agent/ai-infra-phases/README.md` so `当前剩余方案` points readers to section 19 as the current entry.
  - Ran section 19 heading scan, README pointer scan, unresolved-marker scan, tail readback, and whitespace check.
- Evidence refs:
  - Section 19 heading scan, README pointer scan, unresolved-marker scan, tail readback, and whitespace check are recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Resume implementation from section 19.4 order, starting with Phase 8C by running the existing JDBC/Web RED tests, adding starter RED, then implementing JDBC/Web/starter GREEN.

## Section 19 DriftCheckDraft

- Scope status: Within the user's documentation-planning request; production code was not changed in this slice.
- Compatibility status: The new plans preserve kernel-to-port dependency direction, small ISP ports, adapter boundaries, enum/constant state semantics, append-only cost/eval/audit/readiness records, and no workflow engine, remote mesh, real billing, real traffic router, or duplicate active-version owner.
- Retirement status: Section 19 supersedes section 17/18 as the latest current-state implementation entry; older sections remain historical planning context.
- New risk signals:
  - Full AI Infra remains incomplete; Phase 8C JDBC/Web/starter and Phase 8D full implementation still require RED/GREEN evidence.
  - Phase 8C test files exist but the RED commands still need to be run serially before implementation.
  - Phase 8D has no owner files yet and will require a full domain-to-starter implementation slice.
  - Phase 4/5/7/8B remain mandatory focused regression dependencies for final completion.
- Advisory decision: continue.

## 2026-05-26 Section 20 Phase 8C GREEN Calibration Checkpoint

- Current todo: User requested a deeper read of `docs/company-agent/` and `docs/company-agent/ai-infra-phases/`, then one more detailed and concrete design/development plan for each unfinished phase.
- Active slice: documentation planning only; no production code implementation in this slice.
- Completed todos:
  - Re-read Aegis `using-aegis`, `long-task-continuation`, and `writing-plans` guidance for routing.
  - Confirmed active isolated worktree `D:\code\seahorse-agent\.worktrees\ai-infra-phase-design-plans`.
  - Re-read `docs/company-agent/` and `docs/company-agent/ai-infra-phases/` with UTF-8, including architecture baseline, test baseline, Phase 0-8 documents, current `README.md`, current implementation pack, and old `99-current-implementation-handoff.md`.
  - Re-read Phase 8 production hardening goals for eval, audit, quota/cost, SRE, rollout, and enterprise pilot readiness.
  - Confirmed the old Approval query/decision API order is historical and not the current main line.
  - Confirmed current unfinished main work should be split into Phase 8C evidence closure and Phase 8D rollout/readiness implementation.
  - Confirmed Phase 4, Phase 5, Phase 7, and Phase 8B remain mandatory focused regression dependencies, not current main implementation stages.
  - Added section `20. 2026-05-26 Phase 8C GREEN 后的未完成阶段详细开发方案` to `docs/company-agent/ai-infra-phases/20-unfinished-phase-implementation-pack.md`.
  - Added one detailed concrete plan for Phase 8C evidence closure, including focused regression commands, dependency scan, raw evidence scan, and completion criteria.
  - Added one detailed concrete plan for Phase 8D rollout/readiness, including domain objects, ports, services, JDBC tables, Web APIs, TDD order, and completion criteria.
  - Updated `docs/company-agent/ai-infra-phases/README.md` so `当前剩余方案` points readers to section 20 as the latest entry.
  - Ran section 20 heading scan, README pointer scan, unresolved-marker scan, tail readback, code-surface scan, rollout/readiness absence scan, whitespace check, and manual trailing-whitespace scan.
- Evidence refs:
  - Section 20 heading scan, README pointer scan, unresolved-marker scan, tail readback, code-surface scan, rollout/readiness absence scan, whitespace check, and manual trailing-whitespace scan are recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Resume implementation from section 20.4 order, starting with Phase 8C evidence closure, then Phase 8D TDD implementation.

## Section 20 DriftCheckDraft

- Scope status: Within the user's documentation-planning request; production code was not changed in this slice.
- Compatibility status: The new plans preserve kernel-to-port dependency direction, small ISP ports, adapter boundaries, enum/constant state semantics, append-only cost/eval/audit/readiness records, and no workflow engine, remote mesh, real billing, real traffic router, or duplicate active-version owner.
- Retirement status: Section 20 supersedes section 19 as the latest current-state implementation entry; older sections remain historical planning context.
- New risk signals:
  - Full AI Infra remains incomplete; Phase 8C still needs evidence closure and Phase 8D still needs implementation.
  - Phase 8C code exists but still requires serial focused regression, dependency scan, raw evidence scan, and Aegis evidence closure before focused completion can be claimed.
  - Phase 8D rollout/readiness owner files are still absent and require full domain-to-starter TDD.
  - Phase 4/5/7/8B remain mandatory focused regression dependencies for final completion.
- Advisory decision: continue.

## 2026-05-26 Phase 8C Evidence Closure Checkpoint

- Current todo: Close Phase 8C Quota/Cost/SRE evidence per section 20.2 before starting Phase 8D implementation.
- Active slice: verification/evidence closure; no production code edits in this slice.
- Completed todos:
  - Confirmed current worktree and section 20 latest implementation order.
  - Confirmed Phase 8C production and test files exist, while Phase 8D rollout/readiness owner files are absent.
  - Ran Phase 8C kernel focused regression: 15 tests, 0 failures.
  - Ran Phase 8C JDBC focused regression: 5 tests, 0 failures.
  - Ran Phase 8C Web focused regression: 4 tests, 0 failures.
  - Ran Phase 8C starter focused regression: 2 tests, 0 failures.
  - Ran kernel forbidden dependency scan; no matches.
  - Ran raw evidence scan across kernel/Web/JDBC production paths; no matches.
  - Ran diff hygiene check; only existing CRLF warnings were emitted, with no whitespace errors.
- Evidence refs:
  - Exact commands, exit status, and pass counts are recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Start Phase 8D from domain RED tests for rollout/readiness.

## Phase 8C Evidence Closure DriftCheckDraft

- Scope status: Within the section 20.2 Phase 8C evidence-closure scope.
- Compatibility status: Kernel dependency direction is preserved; repository/Web/starter remain adapter concerns; quota/cost/SRE stay enum/port driven.
- Retirement status: Phase 8C fixed foundation warning path is now covered by focused regression when repository/provider wiring exists; no new fallback or duplicate owner was added in this slice.
- New risk signals:
  - Phase 8C evidence is focused, not a full repository build.
  - Phase 8D remains unimplemented.
  - Final AI Infra completion still requires Phase 8D and final Phase 4/5/7/8B/8C/8D regression bundle.
- Advisory decision: continue to Phase 8D.

## 2026-05-26 Section 21 Current Code-Surface Calibration Checkpoint

- Current todo: User requested a deep read of `docs/company-agent/` and `docs/company-agent/ai-infra-phases/`, then one more detailed concrete design/development plan for unfinished phases, noting the previous handoff is outdated.
- Active slice: documentation planning only; no production code implementation in this slice.
- Completed todos:
  - Re-read Aegis `using-aegis`, `long-task-continuation`, `writing-plans`, and `brainstorming` guidance for routing.
  - Confirmed active isolated worktree `D:\code\seahorse-agent\.worktrees\ai-infra-phase-design-plans`.
  - Re-read `docs/company-agent/` and `docs/company-agent/ai-infra-phases/` with UTF-8 scans, including architecture baseline, test baseline, staged planning, gap analysis, Phase 0-8 documents, current `README.md`, current implementation pack, historical `09` plan, and old `99-current-implementation-handoff.md`.
  - Re-read the company Agent background articles for governance, memory, cost, security, HITL, audit, rollout, and Agent Mesh signals.
  - Re-read current Aegis checkpoint/evidence and confirmed Phase 8C evidence closure has focused regression evidence.
  - Scanned current code surface and corrected the latest unfinished-state model: Phase 8D rollout/readiness kernel domain, ports, services, and tests now exist; JDBC/Web/starter rollout/readiness owners are still absent; service GREEN evidence has not been recorded yet.
  - Added section `21. 2026-05-26 当前代码面校准后的未完成阶段详细开发方案` to `docs/company-agent/ai-infra-phases/20-unfinished-phase-implementation-pack.md`.
  - Added concrete execution plans for Phase 8D kernel service GREEN calibration, JDBC persistence, Web API, starter wiring/evidence fallbacks, Phase 8D focused evidence closure, and final completion audit.
  - Updated `docs/company-agent/ai-infra-phases/README.md` so `当前剩余方案` points readers to section 21 as the latest current-state entry.
  - Ran section 21 heading scan, README pointer scan, unresolved-marker scan, tail readback, and whitespace check.
- Evidence refs:
  - Section 21 heading scan, README pointer scan, unresolved-marker scan, tail readback, code-surface scans, and whitespace check are recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Resume implementation from section 21.8 order, starting with Phase 8D-1 by running the kernel service GREEN candidate and fixing only proven failures.

## Section 21 DriftCheckDraft

- Scope status: Within the user's documentation-planning request; production code was not changed in this slice.
- Compatibility status: The new plan preserves kernel-to-port dependency direction, small ISP ports, adapter boundaries, enum/constant state semantics, no duplicate active-version owner, and no workflow engine, real traffic router, remote A2A mesh, real billing, or real secret vault.
- Retirement status: Section 21 supersedes section 20 as the latest current-state implementation entry. Section 20 remains historical because its Phase 8D from-zero assumption is now stale after kernel rollout/readiness files appeared.
- New risk signals:
  - Full AI Infra remains incomplete.
  - Phase 8D service GREEN evidence still needs to be run and recorded.
  - Phase 8D JDBC/Web/starter owners remain missing.
  - Final AI Infra completion still requires a serial Phase 4/5/7/8B/8C/8D regression bundle plus dependency/raw scans.
- Advisory decision: continue.

## 2026-05-26 Phase 8D Starter And Focused Evidence Checkpoint

- Current todo: Close Phase 8D rollout/readiness starter wiring and focused evidence before final completion audit.
- Active slice: Phase 8D starter TDD GREEN plus focused regression/scans.
- Completed todos:
  - Re-read current goal, Aegis routing guidance, latest section 21 checkpoint/evidence, starter auto-configuration, rollout service, readiness service, readiness domain enums, and readiness ports.
  - Added starter RED assertions for `AgentRolloutRepositoryPort`, `EnterprisePilotReadinessRepositoryPort`, rollout/readiness inbound ports, `KernelAgentRolloutService`, `KernelEnterprisePilotReadinessService`, and the small readiness evidence ports.
  - Reproduced RED: `SeahorseAgentRegistryAutoConfigurationTests.shouldCreatePhaseOneRegistryAndRunStoreBeans` failed because no `AgentRolloutRepositoryPort` bean existed.
  - Added JDBC repository auto-configuration for `JdbcAgentRolloutRepositoryAdapter` and `JdbcEnterprisePilotReadinessRepositoryAdapter`.
  - Added conservative readiness evidence adapters in starter code. These use enum status/reason codes and safe `readiness:*` evidence refs, and they fail closed or warn conservatively instead of faking PASS for unknown evidence.
  - Added `KernelAgentRolloutService` and `KernelEnterprisePilotReadinessService` auto-configuration behind the existing small ports.
  - Ran starter GREEN, Phase 8D kernel/JDBC/Web focused regressions, kernel dependency scan, raw-sensitive-evidence scans, and diff hygiene.
- Evidence refs:
  - Exact commands, exit status, pass counts, RED failure, GREEN pass, and scan boundaries are recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Run the final serial Phase 4/5/7/8B/8C/8D completion audit and update evidence before making any completion claim.

## Phase 8D Starter And Focused Evidence DriftCheckDraft

- Scope status: Within section 21.8 Phase 8D order and the active AI Infra goal.
- Compatibility status: Kernel still depends only on domain/ports/JDK; JDBC and Spring remain adapter/starter concerns; readiness evidence remains split by small ISP ports rather than a broad agent service.
- Retirement status: Phase 8D starter gap is closed by real bean wiring. The default readiness providers are explicit conservative fallbacks and should be replaced by richer adapters in later work without changing kernel invariants.
- New risk signals:
  - Evidence is focused, not yet the final cross-phase completion bundle.
  - The strict raw-sensitive scan intentionally reports existing security-filter constants and credential-provider comments; after excluding those safety-only paths, no production raw-sensitive payload matches remain.
- Advisory decision: continue to final completion audit.

## 2026-05-26 Final AI Infra Completion Audit Checkpoint

- Current todo: Finish the serial final completion audit from section 21.7/21.8 before making any AI Infra completion claim.
- Active slice: Verification and evidence closure only; no production code edits in this slice.
- Completed todos:
  - Re-read the active goal, latest section 21 implementation order, Aegis routing guidance, checkpoint, and evidence boundary.
  - Continued the final serial regression bundle after the already recorded Phase 5 and Phase 4 kernel results.
  - Ran Phase 4 JDBC regression: 4 tests, 0 failures.
  - Ran Phase 4 starter regression: 4 tests, 0 failures.
  - Ran Phase 4 Web regression: 13 tests, 0 failures. `SeahorseResourceAclControllerTests` is not a standalone class; Resource ACL Web coverage is inside `SeahorseAgentControllerTests#shouldExposeResourceAclManagementApi`.
  - Ran Phase 7 kernel/JDBC/Web/starter regressions: 6 + 2 + 1 + 12 tests, all 0 failures.
  - Ran Phase 8B kernel/JDBC/Web regressions: 10 + 3 + 2 tests, all 0 failures.
  - Ran Phase 8C kernel/JDBC/Web/starter regressions: 15 + 5 + 4 + 2 tests, all 0 failures.
  - Ran Phase 8D kernel/JDBC/Web regressions: 10 + 3 + 3 tests, all 0 failures. Phase 8D starter regression had already passed in the prior focused evidence slice and was also covered by the Phase 8C/final starter registry regression.
  - Ran the final kernel forbidden dependency scan; no matches.
  - Ran the final strict raw-sensitive scan; only safety-only credential-provider comments and readiness forbidden-fragment constants matched.
  - Ran the final raw-sensitive scan excluding those safety-only paths; no matches.
  - Ran final `git diff --check`; exit status 0 with only existing CRLF warnings and no whitespace errors.
- Evidence refs:
  - Exact final audit commands, exit statuses, pass counts, scan outputs, and coverage note are recorded in `90-evidence.md`.
- Blocked on: none.
- Next step: Run Aegis structural bundle/check where possible, then report the AI Infra goal completion with evidence and residual non-goals.

## Final AI Infra Completion Audit DriftCheckDraft

- Scope status: Within the active AI Infra goal and section 21 final audit scope.
- Compatibility status: Kernel dependency direction remains clean; final scan found no Spring/JDBC/Web/HTTP client dependencies in kernel/ports. The implementation keeps small ports and adapter/starter wiring boundaries instead of a broad `AgentService`.
- Retirement status: The old Approval query/decision API handoff is no longer the current implementation authority. Section 21 remains the current implementation-pack entry, and the final audit evidence now supersedes the prior "not yet complete" evidence boundary for Phase 4/5/7/8B/8C/8D.
- New risk signals:
  - The final audit is a focused regression bundle, not a full `mvn test` across every repository module.
  - Remaining items are explicit non-goals from section 21.7: real traffic percentage routing, remote A2A mesh, real secret vault, real sandbox container runtime, Prometheus exporter, frontend publish wizard, distributed rate limiting, and real billing.
  - The working tree is intentionally very large and dirty because the AI Infra feature work is still uncommitted.
- Advisory decision: completion evidence is sufficient for the scoped AI Infra implementation goal; report verified completion with residual risk boundaries.

## Checkpoint Update

- Current todo: Final AI Infra completion audit completed for current section 21 scope.
- Active slice: final completion audit evidence closure
- Completed todos:
- Phase 4/5/7/8B/8C/8D focused regression bundle completed with all selected tests passing.
- Kernel forbidden dependency scan completed with no matches.
- Raw-sensitive scan completed; only safety-only comments/constants matched, and excluded scan had no matches.
- Diff hygiene completed with no whitespace errors.
- Evidence refs:
- docs/aegis/work/2026-05-25-ai-infra-phase-plan-refresh/90-evidence.md#2026-05-26-final-ai-infra-completion-audit-evidence
- Blocked on: none
- Next step: Report scoped AI Infra implementation completion and residual non-goals.

## DriftCheckDraft

- Scope status: within active section 21 final AI Infra completion audit scope
- Compatibility status: kernel remains isolated from Spring/JDBC/Web/HTTP clients; adapter and starter boundaries remain intact
- Retirement status: older Approval API handoff and pre-section-21 incomplete markers are superseded by final audit evidence for the scoped implementation
- New risk signals:
- Final audit is focused regression plus scans, not a full repository-wide mvn test.
- Explicit non-goals remain outside completion: real traffic routing, remote A2A mesh, real secret vault, real container sandbox runtime, Prometheus exporter, frontend publish wizard, distributed rate limiting, and real billing.
- Advisory decision: continue
