import { api } from "@/services/api";
import type { PageResult } from "@/services/metadataGovernanceService";

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
  agentId?: string;
  toolId?: string;
  toolName?: string;
  status?: string;
  argumentsSummary?: string;
  resultStatus?: string;
  durationMs?: number;
  approvalId?: string;
  createTime?: string;
}

export interface AgentToolBinding {
  toolId?: string;
  toolName?: string;
  permissionBoundary?: Record<string, unknown>;
  approvalPolicy?: Record<string, unknown>;
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
  return api.get<PageResult<ToolItem>>("/api/tools", { params });
}

export function getTool(toolId: string) {
  return api.get<ToolItem>(`/api/tools/${encodeURIComponent(toolId)}`);
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
  toolId?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}) {
  return api.get<PageResult<ToolInvocation>>("/api/tool-invocations", { params });
}
