import { api } from "@/services/api";
import type { PageResult } from "@/services/metadataGovernanceService";

// ── 类型定义 ──

export interface AgentDefinition {
  agentId?: string;
  name?: string;
  description?: string;
  tenantId?: string;
  status?: string;
  latestVersionId?: string;
  latestVersionNumber?: number;
  latestVersionNo?: number;
  currentVersionId?: string;
  currentVersionNumber?: number;
  latestPublishedVersionId?: string;
  latestPublishedVersionNumber?: number;
  latestPublishStatus?: string;
  toolCount?: number;
  riskLevel?: string;
  owner?: string;
  instructions?: string;
  modelStrategy?: Record<string, unknown>;
  contextStrategy?: Record<string, unknown>;
  riskStrategy?: Record<string, unknown>;
  toolBindingSummary?: Record<string, unknown>;
  createTime?: string;
  updateTime?: string;
}

export interface AgentDefinitionDraft {
  agentId?: string;
  tenantId?: string;
  name?: string;
  description?: string;
  ownerUserId?: string;
  ownerTeam?: string;
  agentType?: string;
  baseAgentId?: string;
  riskLevel?: string;
  instructions?: string;
  modelStrategy?: Record<string, unknown>;
  contextStrategy?: Record<string, unknown>;
  riskStrategy?: Record<string, unknown>;
  toolBindingSummary?: Record<string, unknown>;
}

export interface AgentPublishCheck {
  checkId?: string;
  agentId?: string;
  versionId?: string;
  status?: string;
  checks?: AgentPublishCheckItem[];
  checkedAt?: string;
}

export interface AgentPublishCheckItem {
  checkType?: string;
  passed?: boolean;
  message?: string;
  detail?: string;
}

export interface AgentVersion {
  versionId?: string;
  versionNumber?: number;
  versionNo?: number;
  agentId?: string;
  status?: string;
  publishStatus?: string;
  instructions?: string;
  toolSetJson?: string;
  modelConfigJson?: string;
  memoryConfigJson?: string;
  guardrailConfigJson?: string;
  skillSetJson?: string;
  publishedAt?: string;
  publishedBy?: string;
  summary?: string;
  changeSummary?: string;
  createTime?: string;
}

export interface AgentPublishPayload {
  instructions: string;
  toolSetJson?: string;
  skillSetJson?: string;
  modelConfigJson?: string;
  memoryConfigJson?: string;
  guardrailConfigJson?: string;
  changeSummary: string;
}

export interface AgentRollbackPayload {
  tenantId: string;
  operator: string;
  reasonCode: string;
  comment?: string;
}

// ── API 调用 ──

export function listAgents(params: {
  current?: number;
  size?: number;
  keyword?: string;
  status?: string;
  tenantId?: string;
  owner?: string;
  riskLevel?: string;
}) {
  return api.get<PageResult<AgentDefinition>>("/api/agents", { params });
}

export function getAgent(agentId: string) {
  return api.get<AgentDefinition>(`/api/agents/${encodeURIComponent(agentId)}`);
}

export function getAgentVersion(agentId: string, versionId: string) {
  return api.get<AgentVersion, AgentVersion>(
    `/api/agents/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(versionId)}`
  );
}

export function createAgent(payload: AgentDefinitionDraft) {
  return api.post<AgentDefinition, AgentDefinition>("/api/agents", payload);
}

export function updateAgentDraft(agentId: string, payload: AgentDefinitionDraft) {
  return api.put<AgentDefinition, AgentDefinition>(
    `/api/agents/${encodeURIComponent(agentId)}/draft`,
    payload
  );
}

export function publishAgent(agentId: string, payload: AgentPublishPayload) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/agents/${encodeURIComponent(agentId)}/publish`,
    payload
  );
}

export function disableAgent(agentId: string, reason?: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/agents/${encodeURIComponent(agentId)}/disable`,
    { reason }
  );
}

export function getLatestPublishChecks(agentId: string) {
  return api.get<AgentPublishCheck>(
    `/api/agents/${encodeURIComponent(agentId)}/publish-checks/latest`
  );
}

export function validateAgent(agentId: string) {
  return api.post<AgentPublishCheck, AgentPublishCheck>(
    `/api/agents/${encodeURIComponent(agentId)}/validate`
  );
}

export function rollbackAgentVersion(agentId: string, versionId: string, payload: AgentRollbackPayload) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/agents/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(versionId)}/rollback`,
    payload
  );
}
