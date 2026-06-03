import { api } from "@/services/api";

export type ApiRecord = Record<string, unknown>;

export type SreHealthStatus = "GREEN" | "WARN" | "RED";

export interface SreHealthItem {
  contributorName: string;
  status: SreHealthStatus;
  message: string;
  evidenceRef: string | null;
}

export interface SreHealthReport {
  reportId: string;
  status: SreHealthStatus;
  items: SreHealthItem[];
  checkedAt: string;
}

export type PageResult<T> = {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
};

export type ApprovalStatus = "PENDING" | "APPROVED" | "REJECTED" | "MODIFIED";

export type ApprovalPageParams = {
  tenantId?: string;
  status?: ApprovalStatus | "";
  current?: number;
  size?: number;
};

export type AgentPageParams = {
  tenantId?: string;
  keyword?: string;
  current?: number;
  size?: number;
};

export type ToolPageParams = {
  keyword?: string;
  enabled?: boolean;
  current?: number;
  size?: number;
};

export type CostUsageAggregateParams = {
  tenantId: string;
  agentId?: string;
  runId?: string;
  from?: string;
  to?: string;
};

export type FeedbackEvaluationCandidateParams = {
  userId?: string;
  runId?: string;
  reason?: string;
  current?: number;
  size?: number;
};

export type ReadinessRequest = {
  tenantId: string;
  agentId: string;
  versionId: string;
  operator: string;
};

export type RolloutRequest = ReadinessRequest & {
  canaryPercent: number;
};

export type RolloutActionRequest = {
  tenantId: string;
  agentId: string;
  rolloutId: string;
  operator: string;
  comment?: string;
  targetVersionId?: string;
};

export type EvalRegressionRequest = {
  datasetId: string;
  modelId?: string;
  baselinePassRate?: number;
};

const DEFAULT_PAGE_SIZE = 10;

function withPageDefaults<T extends { current?: number; size?: number }>(params: T) {
  return {
    current: 1,
    size: DEFAULT_PAGE_SIZE,
    ...params
  };
}

export async function getAiInfraAgents(params: AgentPageParams): Promise<PageResult<ApiRecord>> {
  return api.get<PageResult<ApiRecord>, PageResult<ApiRecord>>("/api/agents", {
    params: withPageDefaults(params)
  });
}

export async function getAiInfraApprovals(params: ApprovalPageParams): Promise<PageResult<ApiRecord>> {
  return api.get<PageResult<ApiRecord>, PageResult<ApiRecord>>("/api/approvals", {
    params: withPageDefaults({
      ...params,
      status: params.status || undefined
    })
  });
}

export async function approveAiInfraApproval(approvalId: string, decisionComment: string) {
  return api.post<ApiRecord, ApiRecord>(`/api/approvals/${encodeURIComponent(approvalId)}/approve`, {
    decisionComment
  });
}

export async function rejectAiInfraApproval(approvalId: string, decisionComment: string) {
  return api.post<ApiRecord, ApiRecord>(`/api/approvals/${encodeURIComponent(approvalId)}/reject`, {
    decisionComment
  });
}

export async function getAiInfraTools(params: ToolPageParams): Promise<PageResult<ApiRecord>> {
  return api.get<PageResult<ApiRecord>, PageResult<ApiRecord>>("/api/tools", {
    params: withPageDefaults(params)
  });
}

export async function getAiInfraSreHealth(): Promise<SreHealthReport> {
  return api.get<SreHealthReport, SreHealthReport>("/api/sre/health");
}

export async function getAiInfraCostUsageAggregate(params: CostUsageAggregateParams): Promise<ApiRecord> {
  return api.get<ApiRecord, ApiRecord>("/api/cost-usage:aggregate", { params });
}

export async function getFeedbackEvaluationCandidates(
  params: FeedbackEvaluationCandidateParams
): Promise<PageResult<ApiRecord>> {
  return api.get<PageResult<ApiRecord>, PageResult<ApiRecord>>("/api/feedback/evaluation-candidates", {
    params: withPageDefaults(params)
  });
}

export async function acceptEvalCandidate(candidateId: string, note?: string): Promise<ApiRecord> {
  return api.post<ApiRecord, ApiRecord>(
    `/api/eval-candidates/${encodeURIComponent(candidateId)}/accept`,
    { note: note ?? null }
  );
}

export async function rejectEvalCandidate(candidateId: string, note?: string): Promise<ApiRecord> {
  return api.post<ApiRecord, ApiRecord>(
    `/api/eval-candidates/${encodeURIComponent(candidateId)}/reject`,
    { note: note ?? null }
  );
}

export async function runEvalRegression(request: EvalRegressionRequest): Promise<ApiRecord> {
  return api.post<ApiRecord, ApiRecord>(
    `/api/eval-datasets/${encodeURIComponent(request.datasetId)}/regression`,
    {
      modelId: request.modelId ?? null,
      baselinePassRate: request.baselinePassRate ?? null
    }
  );
}

export async function generateAiInfraReadinessReport(request: ReadinessRequest): Promise<ApiRecord> {
  return api.post<ApiRecord, ApiRecord>(
    `/api/agents/${encodeURIComponent(request.agentId)}/versions/${encodeURIComponent(request.versionId)}/pilot-readiness/generate`,
    {
      tenantId: request.tenantId,
      operator: request.operator
    }
  );
}

export async function getLatestAiInfraReadinessReport(request: ReadinessRequest): Promise<ApiRecord> {
  return api.get<ApiRecord, ApiRecord>(
    `/api/agents/${encodeURIComponent(request.agentId)}/versions/${encodeURIComponent(request.versionId)}/pilot-readiness/latest`,
    {
      params: { tenantId: request.tenantId }
    }
  );
}

export async function createAiInfraCanaryRollout(request: RolloutRequest): Promise<ApiRecord> {
  return api.post<ApiRecord, ApiRecord>(
    `/api/agents/${encodeURIComponent(request.agentId)}/versions/${encodeURIComponent(request.versionId)}/rollouts/canary`,
    {
      tenantId: request.tenantId,
      canaryPercent: request.canaryPercent,
      operator: request.operator
    }
  );
}

export async function getLatestAiInfraRollout(request: ReadinessRequest): Promise<ApiRecord> {
  return api.get<ApiRecord, ApiRecord>(
    `/api/agents/${encodeURIComponent(request.agentId)}/versions/${encodeURIComponent(request.versionId)}/rollouts/latest`,
    {
      params: { tenantId: request.tenantId }
    }
  );
}

export async function pauseAiInfraRollout(request: RolloutActionRequest): Promise<ApiRecord> {
  return api.post<ApiRecord, ApiRecord>(
    `/api/agents/${encodeURIComponent(request.agentId)}/rollouts/${encodeURIComponent(request.rolloutId)}/pause`,
    {
      tenantId: request.tenantId,
      operator: request.operator,
      comment: request.comment
    }
  );
}

export async function promoteAiInfraRollout(request: RolloutActionRequest): Promise<ApiRecord> {
  return api.post<ApiRecord, ApiRecord>(
    `/api/agents/${encodeURIComponent(request.agentId)}/rollouts/${encodeURIComponent(request.rolloutId)}/promote`,
    {
      tenantId: request.tenantId,
      operator: request.operator,
      comment: request.comment
    }
  );
}

export async function rollbackAiInfraRollout(request: RolloutActionRequest): Promise<ApiRecord> {
  return api.post<ApiRecord, ApiRecord>(
    `/api/agents/${encodeURIComponent(request.agentId)}/rollouts/${encodeURIComponent(request.rolloutId)}/rollback`,
    {
      tenantId: request.tenantId,
      targetVersionId: request.targetVersionId,
      operator: request.operator,
      comment: request.comment
    }
  );
}
