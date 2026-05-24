# AI Infra Agent Run Retry Plan

## Goal

Close the Phase 3 retry API gap by adding a minimal `AgentRun` retry transition and Web API endpoint. This continues after the already merged approval management, checkpoint, `WAITING_APPROVAL`, resume, worker lease, and ContextPack slices.

## Architecture

- Keep run state invariants in the `AgentRun` domain object.
- Keep `KernelAgentRunService` responsible for authentication, loading, and persistence orchestration.
- Expose retry through the existing small `AgentRunInboundPort` instead of introducing a broad runtime service.
- Keep Web as an adapter that delegates to the inbound port.

## Compatibility Boundary

- Do not introduce a workflow engine or background worker scheduler in this slice.
- Do not execute retry immediately; retry marks the run as `RETRYING` for later worker/orchestrator pickup.
- Do not change approval resume semantics.
- Do not touch unrelated dirty files in the root worktree.

## Verification

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAgentRunServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=SeahorseWebApiContractTests#shouldKeepAgentRegistryAndRunStoreApiContracts' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## Tasks

- [x] Add RED tests for service-level retry and Web retry API.
- [x] Add `RETRYING` run status.
- [x] Add `AgentRun.retry()` invariant: only `FAILED` can transition to `RETRYING`; repeated retry is idempotent.
- [x] Add `AgentRunInboundPort.retry(String runId)`.
- [x] Wire `KernelAgentRunService.retry`.
- [x] Expose `POST /agent-runs/{runId}/retry` and `POST /api/agent-runs/{runId}/retry`.

## Risks

- `RETRYING` is not yet picked up by a production worker; this slice intentionally only closes the API/state seam.
- A broader version-driven execution orchestrator remains a later slice.

