import axios from 'axios';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

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
  const response = await axios.get<{ code: string; data: AgentPageResponse }>(`${API_BASE}/api/agents`, {
    params: { current: 1, size: 100, ...params }
  });
  if (response.data.code === '0') {
    return response.data.data;
  }
  throw new Error('Failed to load agents');
}
