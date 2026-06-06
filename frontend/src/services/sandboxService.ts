import { api } from "@/services/api";
import { storage } from "@/utils/storage";

const DEFAULT_TENANT_ID = "default";
const DEFAULT_RUNTIME_TYPE = "CODE_INTERPRETER";

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

export interface SandboxSessionCreatePayload {
  tenantId?: string;
  runId?: string;
  runtimeType?: "CODE_INTERPRETER" | "BROWSER_AUTOMATION" | "SHELL" | "FILE_CONVERSION";
  networkRequested?: boolean;
  requestedHosts?: string[];
}

export interface SandboxExecutePayload {
  input?: string;
  argumentsJson?: string;
  toolId?: string;
  networkRequested?: boolean;
  requestedHosts?: string[];
}

function currentTenantId() {
  const user = storage.getUser() as ({ tenantId?: string | null } | null);
  return user?.tenantId?.trim() || DEFAULT_TENANT_ID;
}

function createRunId() {
  return `sandbox-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export function createSandboxSession(payload: SandboxSessionCreatePayload = {}) {
  return api.post<SandboxSession, SandboxSession>("/api/sandbox/sessions", {
    tenantId: payload.tenantId?.trim() || currentTenantId(),
    runId: payload.runId?.trim() || createRunId(),
    runtimeType: payload.runtimeType || DEFAULT_RUNTIME_TYPE,
    networkRequested: payload.networkRequested ?? false,
    requestedHosts: payload.requestedHosts || []
  });
}

export function executeInSandbox(sessionId: string, payload: SandboxExecutePayload) {
  const input = payload.input ?? payload.argumentsJson ?? "";
  return api.post<SandboxExecutionResult, SandboxExecutionResult>(
    `/api/sandbox/sessions/${encodeURIComponent(sessionId)}/execute`,
    {
      input,
      networkRequested: payload.networkRequested ?? false,
      requestedHosts: payload.requestedHosts || []
    }
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
