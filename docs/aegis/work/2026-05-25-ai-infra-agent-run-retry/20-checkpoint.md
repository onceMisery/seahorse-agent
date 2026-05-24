# TodoCheckpointDraft

Current todo:
- [x] Confirm `main` already contains the ContextPack producer merge.
- [x] Re-read Phase 3 and stale 99 handoff guidance.
- [x] Use subagents for read-only requirements/code evidence audit.
- [x] Identify next non-duplicate gap: Agent run retry API/state.
- [x] Create isolated worktree `D:\code\seahorse-agent\.worktrees\ai-infra-agent-run-retry`.
- [x] Write RED tests for service and Web API retry.
- [x] Implement minimal retry state/port/service/controller.
- [x] Merge latest root `main` into this worktree branch.
- [x] Run wider Phase 3 regression verification.
- [ ] Commit and merge slice back to root `main`.

Active slice: Phase 3 Agent run retry state/API seam.

Completed todos:
- Avoided repeating approval query/decision API, checkpoint repository, `WAITING_APPROVAL`, resume, lease, and ContextPack slices because current `main` already contains them.
- Added `AgentRun.retry()` domain invariant.
- Added `AgentRunStatus.RETRYING` as a non-terminal state.
- Added `AgentRunInboundPort.retry(String runId)`.
- Added `KernelAgentRunService.retry(String runId)`.
- Added Web mappings for both `/agent-runs/{runId}/retry` and `/api/agent-runs/{runId}/retry`.

Evidence refs:
- RED: combined Maven test run failed because `KernelAgentRunService.retry(String)` and `AgentRunStatus.RETRYING` did not exist.
- GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAgentRunServiceTests' test` -> `BUILD SUCCESS`, 9 tests.
- GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=SeahorseWebApiContractTests#shouldKeepAgentRegistryAndRunStoreApiContracts' '-Dsurefire.failIfNoSpecifiedTests=false' test` -> `BUILD SUCCESS`, 1 test.
- GREEN: `mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAgentRunServiceTests,KernelAgentRunLeaseServiceTests,KernelAgentRunResumeServiceTests,KernelAgentLoopToolGatewayTests,KernelChatAgentRunStoreTests' test` -> `BUILD SUCCESS`, 22 tests.
- GREEN: web contract rerun after helper transition update -> `BUILD SUCCESS`, 1 test.
- CHECK: `git diff --check` -> no whitespace errors; CRLF conversion warnings only.

Blocked-on items:
- None.

Next step:
- Commit this branch and merge it back into root `main`.

ResumeStateHint:
- Continue in `D:\code\seahorse-agent\.worktrees\ai-infra-agent-run-retry`.
- Do not edit unrelated root dirty files in `D:\code\seahorse-agent`.

DriftCheckDraft:
- Scope: still Phase 3 Durable Runtime / Human-in-the-Loop.
- Compatibility: approval resume still executes from checkpoint; retry API does not execute tools.
- New owner/adapter: no new owner; existing run domain/service/controller surfaces extended.
- Retirement: no old path retired; this exposes a missing documented transition.
- Decision: continue-to-commit.
