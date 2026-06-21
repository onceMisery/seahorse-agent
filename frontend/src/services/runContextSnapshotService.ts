import { api } from "@/services/api";

export interface RunContextSnapshotVO {
  id: number | string;
  tenantId?: string | null;
  runId: string;
  conversationId?: number | string | null;
  branchLeafMessageId?: number | string | null;
  roleCardId?: number | string | null;
  runProfileId?: number | string | null;
  executorEngine: "kernel" | "agentscope" | string;
  executorConfigJson?: string | null;
  traceContextJson?: string | null;
  snapshotJson: string;
  createTime?: string | null;
  deleted?: number | boolean | null;
}

export async function getRunContextSnapshotByRunId(runId: string): Promise<RunContextSnapshotVO> {
  return api.get<RunContextSnapshotVO, RunContextSnapshotVO>(
    `/api/run-context-snapshots/by-run/${encodeURIComponent(runId)}`
  );
}

export async function getAgentRunContextSnapshot(runId: string): Promise<RunContextSnapshotVO> {
  return api.get<RunContextSnapshotVO, RunContextSnapshotVO>(
    `/api/agent-runs/${encodeURIComponent(runId)}/context-snapshot`
  );
}
