import { api } from "@/services/api";

// ── 类型定义 ──

export interface SandboxSession {
  sessionId?: string;
  status?: string;
  agentId?: string;
  createTime?: string;
  closeTime?: string;
}

export interface SandboxExecutionResult {
  executionId?: string;
  sessionId?: string;
  status?: string;
  output?: string;
  error?: string;
  durationMs?: number;
  artifacts?: Array<{
    artifactId?: string;
    name?: string;
    mimeType?: string;
    sizeBytes?: number;
  }>;
}

export interface SandboxArtifact {
  artifactId?: string;
  name?: string;
  mimeType?: string;
  sizeBytes?: number;
  content?: string;
}

// ── API 调用 ──

export function createSandboxSession(payload?: { agentId?: string; config?: Record<string, unknown> }) {
  return api.post<SandboxSession, SandboxSession>("/api/sandbox/sessions", payload || {});
}

export function executeInSandbox(sessionId: string, payload: { toolId?: string; argumentsJson?: string }) {
  return api.post<SandboxExecutionResult, SandboxExecutionResult>(
    `/api/sandbox/sessions/${encodeURIComponent(sessionId)}/execute`,
    payload
  );
}

export function closeSandboxSession(sessionId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/sandbox/sessions/${encodeURIComponent(sessionId)}/close`
  );
}

export function listSandboxArtifacts(sessionId: string) {
  return api.get<SandboxArtifact[]>(
    `/api/sandbox/sessions/${encodeURIComponent(sessionId)}/artifacts`
  );
}
