import { api } from "@/services/api";
import type { PageResult } from "@/services/metadataGovernanceService";
import { emptyPage, optionalGet } from "@/services/optionalEndpoint";

// ── 类型定义 ──

export interface ToolItem {
  toolId?: string;
  name?: string;
  description?: string;
  provider?: string;
  resourceType?: string;
  riskLevel?: string;
  enabled?: boolean;
  approvalRequired?: boolean;
  parameterSchema?: Record<string, unknown>;
  outputSchema?: Record<string, unknown>;
  affectedAgentCount?: number;
  createTime?: string;
  updateTime?: string;
}

export interface ToolInvocation {
  invocationId?: string;
  runId?: string;
  stepId?: string;
  agentId?: string;
  versionId?: string;
  rolloutId?: string;
  tenantId?: string;
  userId?: string;
  toolId?: string;
  idempotencyKey?: string;
  status?: string;
  policyDecisionId?: string;
  argumentsSummary?: string;
  resultSummary?: string;
  errorMessage?: string;
  startedAt?: string;
  finishedAt?: string;
}

export interface AgentToolBinding {
  toolId?: string;
  toolName?: string;
  permissionBoundary?: Record<string, unknown>;
  approvalPolicy?: Record<string, unknown>;
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

export interface UpdateToolBindingsPayload {
  tools: AgentToolBinding[];
}

// ── API 调用 ──

export function listTools(params: {
  current?: number;
  size?: number;
  keyword?: string;
  provider?: string;
  resourceType?: string;
  riskLevel?: string;
  enabled?: boolean;
}) {
  return optionalGet(
    api.get<PageResult<ToolItem>>("/api/tools", { params, suppressErrorToast: true }),
    emptyPage<ToolItem>(params.current, params.size)
  );
}

export function getTool(toolId: string) {
  return api.get<ToolItem>(`/api/tools/${encodeURIComponent(toolId)}`);
}

export function getToolGateResult(toolId: string) {
  return api.get<GateResult>(`/api/tools/${encodeURIComponent(toolId)}/gate-result`);
}

export function enableTool(toolId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/tools/${encodeURIComponent(toolId)}/enable`
  );
}

export function disableTool(toolId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/tools/${encodeURIComponent(toolId)}/disable`
  );
}

export function updateAgentToolBindings(agentId: string, versionId: string, payload: UpdateToolBindingsPayload) {
  return api.put<Record<string, unknown>, Record<string, unknown>>(
    `/api/agents/${encodeURIComponent(agentId)}/versions/${encodeURIComponent(versionId)}/tools`,
    payload
  );
}

export function listToolInvocations(params: {
  current?: number;
  size?: number;
  runId?: string;
  agentId?: string;
  versionId?: string;
  rolloutId?: string;
  toolId?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}) {
  return optionalGet(
    api.get<PageResult<ToolInvocation>>("/api/tool-invocations", { params, suppressErrorToast: true }),
    emptyPage<ToolInvocation>(params.current, params.size)
  );
}
