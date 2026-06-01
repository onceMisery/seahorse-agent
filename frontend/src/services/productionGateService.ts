import { api } from "@/services/api";

// ── 类型定义 ──

export interface ProductionGateReport {
  agentId?: string;
  status?: string;
  checks?: ProductionGateCheckItem[];
  generatedAt?: string;
  generatedBy?: string;
}

export interface ProductionGateCheckItem {
  checkType?: string;
  passed?: boolean;
  message?: string;
  detail?: string;
}

// ── API 调用 ──

export function triggerProductionGate(agentId: string) {
  return api.post<ProductionGateReport, ProductionGateReport>(
    `/api/agents/${encodeURIComponent(agentId)}/production-gate`
  );
}

export function getLatestProductionGate(agentId: string) {
  return api.get<ProductionGateReport>(
    `/api/agents/${encodeURIComponent(agentId)}/production-gate/latest`
  );
}
