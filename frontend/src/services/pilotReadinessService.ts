import { api } from "@/services/api";

// ── 类型定义 ──

export interface PilotReadinessReport {
  agentId?: string;
  versionId?: string;
  status?: string;
  sections?: Record<string, unknown>[];
  generatedAt?: string;
  generatedBy?: string;
}

// ── API 调用 ──

export function generateReadiness(
  agentId: string,
  versionId: string,
  payload: { tenantId: string; operator: string }
) {
  return api.post<PilotReadinessReport, PilotReadinessReport>(
    `/api/agents/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(versionId)}/pilot-readiness/generate`,
    payload
  );
}

export function getLatestReadiness(
  agentId: string,
  versionId: string,
  tenantId: string
) {
  return api.get<PilotReadinessReport>(
    `/api/agents/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(versionId)}/pilot-readiness/latest`,
    { params: { tenantId } }
  );
}
