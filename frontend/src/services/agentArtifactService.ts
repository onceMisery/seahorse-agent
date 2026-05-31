import { api } from "@/services/api";

// ── 类型定义 ──

export interface AgentRunStep {
  stepId?: string;
  stepNo?: number;
  stepType?: string;
  status?: string;
  summary?: string;
  errorCode?: string;
  errorMessage?: string;
  startedAt?: string;
  finishedAt?: string;
}

export interface AgentCheckpoint {
  checkpointId?: string;
  runId?: string;
  stepId?: string;
  checkpointType?: string;
  data?: Record<string, unknown>;
  createTime?: string;
}

export interface AgentHandoff {
  handoffId?: string;
  runId?: string;
  fromAgentId?: string;
  toAgentId?: string;
  status?: string;
  summary?: string;
  createTime?: string;
}

export interface AgentArtifactItem {
  artifactId?: string;
  runId?: string;
  title?: string;
  artifactType?: string;
  mimeType?: string;
  sizeBytes?: number;
  previewText?: string;
  disposition?: string;
  createTime?: string;
}

// ── Agent Run 管理 ──

export function createAgentRun(agentId: string, payload: { input?: string; context?: Record<string, unknown> }) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/agents/${encodeURIComponent(agentId)}/runs`,
    payload
  );
}

export function getAgentRun(runId: string) {
  return api.get<Record<string, unknown>>(`/agent-runs/${encodeURIComponent(runId)}`);
}

export function getAgentRunSteps(runId: string) {
  return api.get<AgentRunStep[]>(`/agent-runs/${encodeURIComponent(runId)}/steps`);
}

export function cancelAgentRun(runId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/agent-runs/${encodeURIComponent(runId)}/cancel`
  );
}

export function retryAgentRunAction(runId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/agent-runs/${encodeURIComponent(runId)}/retry`
  );
}

export function resumeAgentRunAction(runId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/agent-runs/${encodeURIComponent(runId)}/resume`
  );
}

// ── Checkpoints ──

export function getAgentRunCheckpoints(runId: string) {
  return api.get<AgentCheckpoint[]>(`/agent-runs/${encodeURIComponent(runId)}/checkpoints`);
}

// ── Handoffs ──

export function getAgentRunHandoffs(runId: string) {
  return api.get<AgentHandoff[]>(`/api/agent-runs/${encodeURIComponent(runId)}/handoffs`);
}

export function getAgentHandoff(handoffId: string) {
  return api.get<AgentHandoff>(`/api/agent-handoffs/${encodeURIComponent(handoffId)}`);
}

export function cancelAgentHandoff(handoffId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/agent-handoffs/${encodeURIComponent(handoffId)}/cancel`
  );
}

// ── Artifacts ──

export function getAgentRunArtifacts(runId: string) {
  return api.get<AgentArtifactItem[]>(`/api/agent-runs/${encodeURIComponent(runId)}/artifacts`);
}

export function getAgentArtifact(artifactId: string) {
  return api.get<AgentArtifactItem>(`/api/agent-artifacts/${encodeURIComponent(artifactId)}`);
}

export function downloadAgentArtifact(artifactId: string) {
  return api.get(`/api/agent-artifacts/${encodeURIComponent(artifactId)}/download`, {
    responseType: "blob"
  });
}
