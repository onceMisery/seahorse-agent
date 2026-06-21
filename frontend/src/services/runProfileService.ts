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
