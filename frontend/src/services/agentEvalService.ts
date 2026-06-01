import { api } from "@/services/api";

// ── 类型定义 ──

export interface AgentEvalSummary {
  summaryId?: string;
  tenantId?: string;
  agentId?: string;
  versionId?: string;
  evalType?: string;
  status?: string;
  score?: number;
  passThreshold?: number;
  warnThreshold?: number;
  caseCount?: number;
  datasetRef?: string;
  evalRunRef?: string;
  evidenceRefs?: string[];
  createdBy?: string;
  createdAt?: string;
}

export interface AgentEvalHistoryResult {
  records: AgentEvalSummary[];
  total: number;
  current: number;
  size: number;
  pages: number;
}

// ── API 调用 ──

export function saveEvalSummary(
  agentId: string,
  versionId: string,
  payload: AgentEvalSummary
) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/agents/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(versionId)}/eval-summaries`,
    payload
  );
}

export function getLatestEvalSummary(
  agentId: string,
  versionId: string,
  tenantId: string,
  evalType: string
) {
  return api.get<AgentEvalSummary>(
    `/api/agents/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(versionId)}/eval-summaries/latest`,
    { params: { tenantId, evalType } }
  );
}

export function listEvalHistory(
  agentId: string,
  versionId: string,
  params: { tenantId: string; evalType?: string; current?: number; size?: number }
) {
  return api.get<AgentEvalHistoryResult>(
    `/api/agents/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(versionId)}/eval-summaries`,
    { params }
  );
}
