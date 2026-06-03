import { api } from "@/services/api";

export type SkillCategory = "PUBLIC" | "CUSTOM";
export type SkillStatus = "ACTIVE" | "DELETED";
export type SkillInjectMode = "METADATA_ONLY" | "METADATA_AND_BODY";

export interface AgentSkill {
  name: string;
  tenantId?: string;
  category: SkillCategory;
  source?: string;
  status: SkillStatus;
  enabled: boolean;
  latestRevisionId?: string;
  description: string;
  tags?: string[];
  allowedTools?: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface AgentSkillRevision {
  revisionId: string;
  skillName: string;
  tenantId?: string;
  revisionNo: number;
  contentHash: string;
  content: string;
  scanDecision: string;
  createdBy?: string;
  createdAt?: string;
}

export interface AgentSkillBinding {
  agentId?: string;
  tenantId?: string;
  skillName: string;
  revisionId: string;
  injectMode: SkillInjectMode;
  createdAt?: string;
}

export interface SkillPage {
  records?: AgentSkill[];
  total?: number;
  size?: number;
  current?: number;
  pages?: number;
}

export function listSkills(params?: { tenantId?: string; current?: number; size?: number; keyword?: string }) {
  return api.get<SkillPage>("/api/skills", { params });
}

export function getSkill(name: string, tenantId?: string) {
  return api.get<AgentSkill>(`/api/skills/${encodeURIComponent(name)}`, { params: { tenantId } });
}

export function createCustomSkill(payload: { tenantId?: string; content: string }) {
  return api.post<AgentSkill, AgentSkill>("/api/skills/custom", payload);
}

export function updateCustomSkill(name: string, payload: { tenantId?: string; content: string }) {
  return api.put<AgentSkill, AgentSkill>(`/api/skills/custom/${encodeURIComponent(name)}`, payload);
}

export function installSkill(payload: { tenantId?: string; content: string }) {
  return api.post<AgentSkill, AgentSkill>("/api/skills/install", payload);
}

export function enableSkill(name: string, tenantId?: string) {
  return api.post<AgentSkill, AgentSkill>(`/api/skills/${encodeURIComponent(name)}/enable`, { tenantId });
}

export function disableSkill(name: string, tenantId?: string) {
  return api.post<AgentSkill, AgentSkill>(`/api/skills/${encodeURIComponent(name)}/disable`, { tenantId });
}

export function deleteCustomSkill(name: string, tenantId?: string) {
  return api.delete<AgentSkill>(`/api/skills/custom/${encodeURIComponent(name)}`, { params: { tenantId } });
}

export function listSkillHistory(name: string, tenantId?: string) {
  return api.get<AgentSkillRevision[]>(`/api/skills/custom/${encodeURIComponent(name)}/history`, {
    params: { tenantId }
  });
}

export function rollbackCustomSkill(name: string, payload: { tenantId?: string; revisionId: string }) {
  return api.post<AgentSkill, AgentSkill>(`/api/skills/custom/${encodeURIComponent(name)}/rollback`, payload);
}

export function listAgentSkillBindings(agentId: string, tenantId?: string) {
  return api.get<AgentSkillBinding[]>(`/api/agents/${encodeURIComponent(agentId)}/skills`, { params: { tenantId } });
}

export function replaceAgentSkillBindings(
  agentId: string,
  payload: { tenantId?: string; bindings: AgentSkillBinding[] }
) {
  return api.put<AgentSkillBinding[], AgentSkillBinding[]>(
    `/api/agents/${encodeURIComponent(agentId)}/skills`,
    payload
  );
}

export function getAgentSkillSnapshot(agentId: string, tenantId?: string) {
  return api.get<string>(`/api/agents/${encodeURIComponent(agentId)}/skills/snapshot`, { params: { tenantId } });
}

export function buildSkillMarkdown(input: {
  name: string;
  description: string;
  tags?: string[];
  allowedTools?: string[];
  body: string;
}) {
  const list = (items?: string[]) => (items && items.length > 0 ? items.map((item) => `  - ${item}`).join("\n") : "");
  const tags = list(input.tags);
  const tools = list(input.allowedTools);
  return [
    "---",
    `name: ${input.name.trim()}`,
    `description: ${input.description.trim()}`,
    tags ? "tags:" : "tags: []",
    tags,
    tools ? "allowed_tools:" : "allowed_tools: []",
    tools,
    "---",
    "",
    input.body.trim()
  ]
    .filter((line) => line !== "")
    .join("\n");
}
