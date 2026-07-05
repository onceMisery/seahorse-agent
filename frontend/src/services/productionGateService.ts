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

export interface GateResultItem {
  code: string;
  status: string;
  message?: string;
}

export interface GateResult {
  subjectType: string;
  subjectId: string;
  status: string;
  passed: boolean;
  blockingCodes?: string[];
  items?: GateResultItem[];
  checkedAt?: string;
  sourceType?: string;
  sourceId?: string;
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

export function getAgentGateResult(agentId: string) {
  return api.get<GateResult, GateResult>(
    `/api/agents/${encodeURIComponent(agentId)}/production-gate/gate-result`
  );
}
