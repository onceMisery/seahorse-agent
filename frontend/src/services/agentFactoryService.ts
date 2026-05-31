import { api } from "@/services/api";

// ── 类型定义 ──

export interface AgentTemplate {
  templateId?: string;
  name?: string;
  description?: string;
  category?: string;
  instructions?: string;
  modelStrategy?: Record<string, unknown>;
  contextStrategy?: Record<string, unknown>;
  riskStrategy?: Record<string, unknown>;
  toolBindingSummary?: Record<string, unknown>;
  tags?: string[];
}

export interface AgentCatalogItem {
  agentId?: string;
  name?: string;
  description?: string;
  category?: string;
  status?: string;
  currentVersionNumber?: number;
  riskLevel?: string;
}

export interface CreateFromTemplatePayload {
  templateId: string;
  name?: string;
  description?: string;
  tenantId?: string;
}

// ── API 调用 ──

export function listAgentTemplates() {
  return api.get<AgentTemplate[], AgentTemplate[]>("/api/agent-templates");
}

export function createAgentFromTemplate(payload: CreateFromTemplatePayload) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    "/api/agents/from-template",
    payload
  );
}

export function getAgentCatalog() {
  return api.get<AgentCatalogItem[], AgentCatalogItem[]>("/api/agent-catalog");
}
