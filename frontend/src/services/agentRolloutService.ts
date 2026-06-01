import { api } from "@/services/api";

// ── 类型定义 ──

export interface AgentRollout {
  rolloutId?: string;
  agentId?: string;
  versionId?: string;
  tenantId?: string;
  canaryPercent?: number;
  status?: string;
  operator?: string;
  comment?: string;
  createTime?: string;
  updateTime?: string;
}

export interface RolloutAction {
  tenantId: string;
  operator: string;
  comment?: string;
}

// ── API 调用 ──

export function createCanaryRollout(
  agentId: string,
  versionId: string,
  payload: { tenantId: string; canaryPercent: number; operator: string }
) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/agents/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(versionId)}/rollouts/canary`,
    payload
  );
}

export function getLatestRollout(agentId: string, versionId: string, tenantId: string) {
  return api.get<AgentRollout>(
    `/api/agents/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(versionId)}/rollouts/latest`,
    { params: { tenantId } }
  );
}

export function pauseRollout(agentId: string, rolloutId: string, payload: RolloutAction) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/agents/${encodeURIComponent(agentId)}/rollouts/${encodeURIComponent(rolloutId)}/pause`,
    payload
  );
}

export function promoteRollout(agentId: string, rolloutId: string, payload: RolloutAction) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/agents/${encodeURIComponent(agentId)}/rollouts/${encodeURIComponent(rolloutId)}/promote`,
    payload
  );
}

export function rollbackRollout(
  agentId: string,
  rolloutId: string,
  payload: RolloutAction & { targetVersionId?: string }
) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/agents/${encodeURIComponent(agentId)}/rollouts/${encodeURIComponent(rolloutId)}/rollback`,
    payload
  );
}
