# EvidenceBundleDraft

## Current State

- Worktree: `D:\code\seahorse-agent\.worktrees\ai-infra-agent-run-retry`
- Branch: `codex/ai-infra-agent-run-retry`
- Base: root `main` after merging latest `main` into the retry worktree (`c578a6f1`)

## Implementation Evidence

- `AgentRunStatus.RETRYING` added as a non-terminal runtime state.
- State predicates separate finality from retry eligibility:
  - `isTerminal()` excludes `FAILED` because Phase 3 supports retrying failed runs.
  - `isFinished()` treats `FAILED` as finished for history/resume timestamps.
  - `isWorkerRunnable()` includes `CREATED`, `RUNNING`, and `RETRYING` for lease/succeed/fail rules.
- `AgentRun.retry()` owns retry invariants:
  - `FAILED` transitions to `RETRYING`.
  - repeated retry on `RETRYING` is idempotent.
  - non-`FAILED` non-`RETRYING` states are rejected.
- `AgentRunInboundPort.retry(String runId)` exposes the state transition through the run port.
- `KernelAgentRunService.retry(String runId)` authenticates, loads, transitions, and persists.
- `SeahorseAgentRunController` exposes retry at `/agent-runs/{runId}/retry` and `/api/agent-runs/{runId}/retry`.

## Verification Commands

1. RED verification:

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel,seahorse-agent-tests -am '-Dtest=KernelAgentRunServiceTests,SeahorseWebApiContractTests#shouldKeepAgentRegistryAndRunStoreApiContracts' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD FAILURE`; expected compile failures for missing `KernelAgentRunService.retry(String)` and `AgentRunStatus.RETRYING`.

2. Service GREEN verification:

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAgentRunServiceTests' test
```

Result: `BUILD SUCCESS`, 9 tests, 0 failures/errors.

3. Web contract GREEN verification:

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=SeahorseWebApiContractTests#shouldKeepAgentRegistryAndRunStoreApiContracts' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD SUCCESS`, 1 test, 0 failures/errors.

4. Focused Phase 3 regression verification:

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAgentRunServiceTests,KernelAgentRunLeaseServiceTests,KernelAgentRunResumeServiceTests,KernelAgentLoopToolGatewayTests,KernelChatAgentRunStoreTests' test
```

Result: `BUILD SUCCESS`, 22 tests, 0 failures/errors.

5. Web contract rerun after helper update:

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=SeahorseWebApiContractTests#shouldKeepAgentRegistryAndRunStoreApiContracts' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD SUCCESS`, 1 test, 0 failures/errors.

6. Diff hygiene:

```powershell
git diff --check
```

Result: no whitespace errors; Git reported only CRLF conversion warnings for touched files.

## DriftCheckDraft

- Original task intent still served: yes, this implements a documented Phase 3 API/state gap without repeating stale handoff work.
- Compatibility boundary: held; retry marks state only and does not bypass checkpoint resume or Tool Gateway.
- New owner/fallback/adapter: no new owner; existing domain/service/Web adapter extended.
- Retirement track: no old path retired.
- Decision: continue-to-commit.
