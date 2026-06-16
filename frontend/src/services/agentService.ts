import { api } from "@/services/api";

export interface AgentDefinition {
  agentId: string;
  versionId: string;
  name: string;
  description?: string;
  agentType?: string;
  riskLevel?: string;
  status?: string;
}

export interface AgentPageResponse {
  records: AgentDefinition[];
  total: number;
  current: number;
  size: number;
}

export async function listAgents(params?: {
  current?: number;
  size?: number;
  keyword?: string;
}): Promise<AgentPageResponse> {
  return api.get<AgentPageResponse, AgentPageResponse>("/api/agents", {
    params: { current: 1, size: 100, ...params }
  });
}
