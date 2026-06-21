import { api } from "@/services/api";

export type RunProfileExecutorEngine = "kernel" | "agentscope" | string;

export interface RunProfileToolBinding {
  id?: number | string | null;
  tenantId?: string | null;
  profileId?: number | string | null;
  toolId: string;
  provider: string;
  enabled?: boolean | number | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface RunProfileVO {
  id: number | string;
  tenantId?: string | null;
  userId?: string | null;
  name: string;
  description?: string | null;
  roleCardId?: number | string | null;
  executorEngine: RunProfileExecutorEngine;
  executorConfigJson?: string | null;
  modelConfigJson?: string | null;
  memoryScopeJson?: string | null;
  guardrailConfigJson?: string | null;
  approvalStatus?: string | null;
  approvalOperator?: string | null;
  approvalComment?: string | null;
  approvalTime?: string | null;
  enabled?: boolean | number | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface RunProfileDetails {
  profile: RunProfileVO;
  toolBindings?: RunProfileToolBinding[];
}

export interface RunProfileResolvedPreview {
  runProfileId: number | string;
  roleCardId?: number | string | null;
  executorEngine: RunProfileExecutorEngine;
  executorConfigJson?: string | null;
  modelConfigJson?: string | null;
  memoryScopeJson?: string | null;
  guardrailConfigJson?: string | null;
  explicitToolAllowlist: boolean;
  toolIds: string[];
  mcpToolIds: string[];
  a2aAgentIds: string[];
}

export interface RunProfileRiskItem {
  code: string;
  level: "LOW" | "MEDIUM" | "HIGH" | string;
  message: string;
}

export interface RunProfileRiskSummary {
  runProfileId: number | string;
  riskLevel: "LOW" | "MEDIUM" | "HIGH" | string;
  riskCodes: string[];
  riskItems: RunProfileRiskItem[];
}

export interface RunProfileProductionGateItem {
  code: string;
  status: "PASS" | "WARN" | "BLOCK" | string;
  message: string;
}

export interface RunProfileProductionGateCheck {
  runProfileId: number | string;
  passed: boolean;
  riskLevel: "LOW" | "MEDIUM" | "HIGH" | string;
  blockingCodes: string[];
  checkItems: RunProfileProductionGateItem[];
}

export interface RunProfileAuditSummary {
  runProfileId: number | string;
  approvalStatus: string;
  riskLevel: "LOW" | "MEDIUM" | "HIGH" | string;
  runCount: number;
  failureCount: number;
  estimatedCost: number;
  enabledToolCount: number;
  highRiskToolCount: number;
  highRiskToolIds: string[];
}

export interface RunProfileRequest {
  name: string;
  description?: string | null;
  roleCardId?: number | string | null;
  executorEngine?: RunProfileExecutorEngine;
  executorConfig?: Record<string, unknown> | null;
  modelConfig?: Record<string, unknown> | null;
  memoryScope?: Record<string, unknown> | null;
  guardrailConfig?: Record<string, unknown> | null;
  toolBindings?: RunProfileToolBinding[];
}

export async function listRunProfiles(): Promise<RunProfileVO[]> {
  return api.get<RunProfileVO[], RunProfileVO[]>("/api/run-profiles");
}

export async function listRunProfileExecutorEngines(): Promise<RunProfileExecutorEngine[]> {
  return api.get<RunProfileExecutorEngine[], RunProfileExecutorEngine[]>("/api/run-profiles/executor-engines");
}

export async function getRunProfile(id: number | string): Promise<RunProfileDetails> {
  return api.get<RunProfileDetails, RunProfileDetails>(`/api/run-profiles/${encodeURIComponent(String(id))}`);
}

export async function getAppliedRunProfileForConversation(
  conversationId: number | string
): Promise<RunProfileDetails | undefined> {
  return api.get<RunProfileDetails | undefined, RunProfileDetails | undefined>(
    `/api/conversations/${encodeURIComponent(String(conversationId))}/run-profile`
  );
}

export async function resolveRunProfilePreview(id: number | string): Promise<RunProfileResolvedPreview> {
  return api.post<RunProfileResolvedPreview, RunProfileResolvedPreview>(
    `/api/run-profiles/${encodeURIComponent(String(id))}/resolve-preview`
  );
}

export async function getRunProfileRiskSummary(id: number | string): Promise<RunProfileRiskSummary> {
  return api.get<RunProfileRiskSummary, RunProfileRiskSummary>(
    `/api/run-profiles/${encodeURIComponent(String(id))}/risk-summary`
  );
}

export async function checkRunProfileProductionGate(
  id: number | string
): Promise<RunProfileProductionGateCheck> {
  return api.post<RunProfileProductionGateCheck, RunProfileProductionGateCheck>(
    `/api/run-profiles/${encodeURIComponent(String(id))}/production-gate/check`
  );
}

export async function submitRunProfileApproval(id: number | string, comment?: string): Promise<void> {
  return api.post<void, void>(`/api/run-profiles/${encodeURIComponent(String(id))}/submit-approval`, {
    comment
  });
}

export async function approveRunProfile(id: number | string, comment?: string): Promise<void> {
  return api.post<void, void>(`/api/run-profiles/${encodeURIComponent(String(id))}/approve`, {
    comment
  });
}

export async function rejectRunProfile(id: number | string, comment?: string): Promise<void> {
  return api.post<void, void>(`/api/run-profiles/${encodeURIComponent(String(id))}/reject`, {
    comment
  });
}

export async function getRunProfileAuditSummary(id: number | string): Promise<RunProfileAuditSummary> {
  return api.get<RunProfileAuditSummary, RunProfileAuditSummary>(
    `/api/run-profiles/${encodeURIComponent(String(id))}/audit-summary`
  );
}

export async function applyRunProfileToConversation(
  conversationId: number | string,
  id: number | string
): Promise<RunProfileResolvedPreview> {
  return api.post<RunProfileResolvedPreview, RunProfileResolvedPreview>(
    `/api/conversations/${encodeURIComponent(String(conversationId))}/run-profile/${encodeURIComponent(String(id))}/apply`
  );
}

export async function createRunProfile(request: RunProfileRequest): Promise<number | string> {
  return api.post<number | string, number | string>("/api/run-profiles", request);
}

export async function updateRunProfile(id: number | string, request: RunProfileRequest): Promise<number | string> {
  return api.put<number | string, number | string>(`/api/run-profiles/${encodeURIComponent(String(id))}`, request);
}

export async function activateRunProfile(id: number | string): Promise<void> {
  return api.post<void, void>(`/api/run-profiles/${encodeURIComponent(String(id))}/activate`);
}

export async function deleteRunProfile(id: number | string): Promise<void> {
  return api.delete<void, void>(`/api/run-profiles/${encodeURIComponent(String(id))}`);
}
