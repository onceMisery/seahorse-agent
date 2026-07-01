import { api } from "@/services/api";
import { storage } from "@/utils/storage";

const DEFAULT_TENANT_ID = "default";
const DEFAULT_RUNTIME_TYPE = "CODE_INTERPRETER";

export interface SandboxSession {
  sessionId?: string;
  tenantId?: string;
  runId?: string;
  runtimeType?: string;
  status?: string;
  reasonCode?: string;
  agentId?: string;
  createTime?: string;
  closeTime?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SandboxExecution {
  executionId?: string;
  sessionId?: string;
  runtimeType?: string;
  status?: string;
  resultSummary?: string;
  reasonCode?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SandboxExecutionResult {
  execution?: SandboxExecution;
  artifacts?: SandboxArtifact[];
  reasonCode?: string;
}

export interface SandboxArtifact {
  artifactId?: string;
  sessionId?: string;
  executionId?: string;
  name?: string;
  mimeType?: string;
  mediaType?: string;
  sizeBytes?: number;
  content?: string;
  scanStatus?: string;
  sensitivity?: string;
  promptVisible?: boolean;
  createdAt?: string;
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
  return api.post<SandboxSession, SandboxSession>(
    `/api/sandbox/sessions/${encodeURIComponent(sessionId)}/close`
  );
}

export function listSandboxExecutions(sessionId: string) {
  return api.get<SandboxExecution[]>(
    `/api/sandbox/sessions/${encodeURIComponent(sessionId)}/executions`
  );
}

export function listSandboxArtifacts(sessionId: string) {
  return api.get<SandboxArtifact[]>(
    `/api/sandbox/sessions/${encodeURIComponent(sessionId)}/artifacts`
  );
}
