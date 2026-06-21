import { api } from "@/services/api";

export interface RunExperimentVO {
  id: number | string;
  tenantId?: string | null;
  userId?: string | null;
  conversationId: number | string;
  baseLeafMessageId?: number | string | null;
  name: string;
  status: "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | string;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface RunExperimentTrialVO {
  id: number | string;
  tenantId?: string | null;
  experimentId: number | string;
  runProfileId: number | string;
  executorEngine?: "kernel" | "agentscope" | string | null;
  runId?: string | null;
  outputMessageId?: number | string | null;
  scoreJson?: string | null;
  metricJson?: string | null;
  status: "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | string;
  errorMessage?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface RunExperimentDetails {
  experiment: RunExperimentVO;
  trials: RunExperimentTrialVO[];
}

export interface RunExperimentForkResult {
  trialId: number | string;
  outputMessageId: number | string;
  branch: unknown[];
}

export interface RunExperimentRequest {
  conversationId: number | string;
  baseLeafMessageId?: number | string | null;
  name: string;
  runProfileIds: Array<number | string>;
}

export async function createRunExperiment(request: RunExperimentRequest): Promise<RunExperimentDetails> {
  return api.post<RunExperimentDetails, RunExperimentDetails>("/api/run-experiments", request);
}

export async function getRunExperiment(id: number | string): Promise<RunExperimentDetails> {
  return api.get<RunExperimentDetails, RunExperimentDetails>(
    `/api/run-experiments/${encodeURIComponent(String(id))}`
  );
}

export async function cancelRunExperiment(id: number | string): Promise<RunExperimentDetails> {
  return api.post<RunExperimentDetails, RunExperimentDetails>(
    `/api/run-experiments/${encodeURIComponent(String(id))}/cancel`
  );
}

export async function scoreRunExperimentTrial(
  experimentId: number | string,
  trialId: number | string,
  score: Record<string, unknown>
): Promise<RunExperimentDetails> {
  return api.post<RunExperimentDetails, RunExperimentDetails>(
    `/api/run-experiments/${encodeURIComponent(String(experimentId))}/trials/${encodeURIComponent(String(trialId))}/score`,
    { score }
  );
}

export async function forkRunExperimentTrialToBranch(
  experimentId: number | string,
  trialId: number | string
): Promise<RunExperimentForkResult> {
  return api.post<RunExperimentForkResult, RunExperimentForkResult>(
    `/api/run-experiments/${encodeURIComponent(String(experimentId))}/trials/${encodeURIComponent(String(trialId))}/fork-to-branch`
  );
}
