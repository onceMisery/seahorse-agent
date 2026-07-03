import { api } from "@/services/api";
import { storage } from "@/utils/storage";

const DEFAULT_TENANT_ID = "default";
const DEFAULT_RUNTIME_TYPE = "CODE_INTERPRETER";
const SANDBOX_API_PREFIX = "/api/api/sandbox";

export interface SandboxSession {
  sessionId?: string;
  tenantId?: string;
  runId?: string;
  runtimeType?: string;
  status?: string;
  reasonCode?: string;
  profileId?: string;
  expiresAt?: string;
  agentId?: string;
  createTime?: string;
  closeTime?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SandboxSessionSweepResult {
  tenantId?: string;
  sweptAt?: string;
  matchedCount?: number;
  closedCount?: number;
  failedCount?: number;
  closedSessions?: SandboxSession[];
}

export interface SandboxRuntimeCleanupResult {
  sweptAt?: string;
  activeSessionCount?: number;
  inspectedWorkspaceCount?: number;
  skippedActiveWorkspaceCount?: number;
  skippedRecentWorkspaceCount?: number;
  removedWorkspaceCount?: number;
  failedWorkspaceCount?: number;
  removedWorkspaceNames?: string[];
  failedWorkspaceNames?: string[];
  inspectedContainerCount?: number;
  activeContainerCount?: number;
  orphanContainerCount?: number;
  failedContainerInspectionCount?: number;
  activeContainerNames?: string[];
  orphanContainerNames?: string[];
  failedContainerInspectionMessages?: string[];
}

export interface SandboxRuntimeHealth {
  checkedAt?: string;
  runtime?: string;
  engine?: string;
  status?: string;
  engineAvailable?: boolean;
  workspaceAvailable?: boolean;
  workspaceFreeBytes?: number;
  workspaceMinFreeBytes?: number;
  workspaceDiskAvailable?: boolean;
  workspaceDiskStatus?: string;
  activeSessionCount?: number;
  activeSessionLimit?: number;
  activeSessionRemaining?: number;
  activeSessionCapacityAvailable?: boolean;
  capacityStatus?: string;
  inspectedContainerCount?: number;
  activeContainerCount?: number;
  orphanContainerCount?: number;
  failedContainerInspectionCount?: number;
  activeContainerNames?: string[];
  orphanContainerNames?: string[];
  failureMessages?: string[];
}

export interface SandboxRuntimeProfile {
  runtimeType?: string;
  profileId?: string;
  supportedByContainerRuntime?: boolean;
  networkAllowed?: boolean;
  status?: string;
  policyId?: string;
  policyStatus?: "ACTIVE" | "DISABLED" | string;
  sessionTtlSeconds?: number;
}

export interface SandboxRuntimeProfilesResponse {
  profiles?: SandboxRuntimeProfile[];
  defaultNetworkPolicy?: string;
  allowlistedHosts?: string[];
  defaultTtlSeconds?: number;
}

export interface SandboxArtifactScannerPolicy {
  scannerId?: string;
  scannerMode?: string;
  failClosed?: boolean;
  rawFindingValuesPersisted?: boolean;
  maxContentScanBytes?: number;
  maxBinarySignatureScanBytes?: number;
  maxArchiveScanEntries?: number;
  maxArchiveEntryScanBytes?: number;
  promptSafeMediaTypes?: string[];
  downloadOnlyMediaTypes?: string[];
  contentScannedMediaTypes?: string[];
  binarySignatureScannedMediaTypes?: string[];
  archiveScannedMediaTypes?: string[];
  blockedCategories?: string[];
  redactedCategories?: string[];
  unsupportedCapabilities?: string[];
}

export interface SandboxToolQuotaPolicyPayload {
  policyId?: string;
  tenantId: string;
  toolId: "sandbox_python" | "sandbox_file_convert" | "sandbox_browser" | string;
  status?: "ACTIVE" | "DISABLED" | string;
  tokenLimit?: number;
  callLimit?: number;
  costLimit?: number;
  warnRatio?: number;
}

export interface SandboxRuntimeProfilePolicyPayload {
  policyId?: string;
  tenantId: string;
  runtimeType: "CODE_INTERPRETER" | "FILE_CONVERSION" | "BROWSER_AUTOMATION" | "SHELL" | string;
  profileId?: string;
  status?: "ACTIVE" | "DISABLED" | string;
  sessionTtlSeconds?: number;
  networkAllowed?: boolean;
}

export interface SandboxRuntimeProfilePolicy {
  policyId?: string;
  tenantId?: string;
  runtimeType?: string;
  profileId?: string;
  status?: string;
  sessionTtlSeconds?: number;
  networkAllowed?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface SandboxToolQuotaPolicy {
  policyId?: string;
  tenantId?: string;
  scope?: string;
  subjectId?: string;
  status?: string;
  tokenLimit?: number;
  callLimit?: number;
  costLimit?: number;
  warnRatio?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface SandboxRuntimeContainerReapResult {
  reapedAt?: string;
  dryRun?: boolean;
  activeSessionCount?: number;
  inspectedContainerCount?: number;
  activeContainerCount?: number;
  orphanContainerCount?: number;
  failedContainerInspectionCount?: number;
  reapedContainerCount?: number;
  failedContainerCount?: number;
  activeContainerNames?: string[];
  orphanContainerNames?: string[];
  reapedContainerNames?: string[];
  failedContainerNames?: string[];
  failureMessages?: string[];
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
  contentType?: string;
  filename?: string;
  sizeBytes?: number;
  content?: string;
  scanStatus?: string;
  sensitivity?: string;
  scanSummary?: string;
  redactionSummaryJson?: string;
  promptVisible?: boolean;
  downloadable?: boolean;
  downloadBlockedReason?: string | null;
  createdAt?: string;
}

export type SandboxArtifactDetail = SandboxArtifact;

export interface SandboxSessionCreatePayload {
  tenantId?: string;
  runId?: string;
  runtimeType?: "CODE_INTERPRETER" | "BROWSER_AUTOMATION" | "SHELL" | "FILE_CONVERSION";
  networkRequested?: boolean;
  requestedHosts?: string[];
  profileId?: string;
  expiresAt?: string;
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

export function currentSandboxTenantId() {
  return currentTenantId();
}

function createRunId() {
  return `sandbox-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export function createSandboxSession(payload: SandboxSessionCreatePayload = {}) {
  const request: SandboxSessionCreatePayload = {
    tenantId: payload.tenantId?.trim() || currentTenantId(),
    runId: payload.runId?.trim() || createRunId(),
    runtimeType: payload.runtimeType || DEFAULT_RUNTIME_TYPE,
    networkRequested: payload.networkRequested ?? false,
    requestedHosts: payload.requestedHosts || []
  };
  if (payload.profileId?.trim()) {
    request.profileId = payload.profileId.trim();
  }
  if (payload.expiresAt?.trim()) {
    request.expiresAt = payload.expiresAt.trim();
  }
  return api.post<SandboxSession, SandboxSession>(`${SANDBOX_API_PREFIX}/sessions`, request);
}

export function listSandboxSessions(tenantId = currentTenantId(), limit = 20) {
  return api.get<SandboxSession[]>(`${SANDBOX_API_PREFIX}/sessions`, {
    params: {
      tenantId,
      limit
    }
  });
}

export function sweepExpiredSandboxSessions(tenantId = currentTenantId(), limit = 20) {
  return api.post<SandboxSessionSweepResult, SandboxSessionSweepResult>(
    `${SANDBOX_API_PREFIX}/sessions/expired:sweep`,
    undefined,
    {
      params: {
        tenantId,
        limit
      }
    }
  );
}

export function sweepOrphanedSandboxRuntimeResources() {
  return api.post<SandboxRuntimeCleanupResult, SandboxRuntimeCleanupResult>(
    `${SANDBOX_API_PREFIX}/runtime/orphans:sweep`
  );
}

export function getSandboxRuntimeHealth() {
  return api.get<SandboxRuntimeHealth>(`${SANDBOX_API_PREFIX}/runtime/health`);
}

export function getSandboxArtifactScannerPolicy() {
  return api.get<SandboxArtifactScannerPolicy>(`${SANDBOX_API_PREFIX}/runtime/artifact-scanner-policy`);
}

export function getSandboxRuntimeProfiles(tenantId = currentTenantId()) {
  return api.get<SandboxRuntimeProfilesResponse>(`${SANDBOX_API_PREFIX}/runtime/profiles`, {
    params: { tenantId }
  });
}

export function upsertSandboxRuntimeProfilePolicy(payload: SandboxRuntimeProfilePolicyPayload) {
  return api.post<SandboxRuntimeProfilePolicy, SandboxRuntimeProfilePolicy>(
    `${SANDBOX_API_PREFIX}/runtime/profile-policies`,
    payload
  );
}

export function upsertSandboxToolQuotaPolicy(payload: SandboxToolQuotaPolicyPayload) {
  return api.post<SandboxToolQuotaPolicy, SandboxToolQuotaPolicy>(
    `${SANDBOX_API_PREFIX}/runtime/tool-quota-policies`,
    payload
  );
}

export function reapOrphanedSandboxRuntimeContainers(dryRun = true) {
  return api.post<SandboxRuntimeContainerReapResult, SandboxRuntimeContainerReapResult>(
    `${SANDBOX_API_PREFIX}/runtime/orphan-containers:reap`,
    undefined,
    {
      params: { dryRun }
    }
  );
}

export function executeInSandbox(sessionId: string, payload: SandboxExecutePayload) {
  const input = payload.input ?? payload.argumentsJson ?? "";
  return api.post<SandboxExecutionResult, SandboxExecutionResult>(
    `${SANDBOX_API_PREFIX}/sessions/${encodeURIComponent(sessionId)}/execute`,
    {
      input,
      networkRequested: payload.networkRequested ?? false,
      requestedHosts: payload.requestedHosts || []
    }
  );
}

export function closeSandboxSession(sessionId: string) {
  return api.post<SandboxSession, SandboxSession>(
    `${SANDBOX_API_PREFIX}/sessions/${encodeURIComponent(sessionId)}/close`
  );
}

export function listSandboxExecutions(sessionId: string) {
  return api.get<SandboxExecution[]>(
    `${SANDBOX_API_PREFIX}/sessions/${encodeURIComponent(sessionId)}/executions`
  );
}

export function listSandboxArtifacts(sessionId: string) {
  return api.get<SandboxArtifact[]>(
    `${SANDBOX_API_PREFIX}/sessions/${encodeURIComponent(sessionId)}/artifacts`
  );
}

export function getSandboxArtifact(artifactId: string) {
  return api.get<SandboxArtifactDetail>(
    `${SANDBOX_API_PREFIX}/artifacts/${encodeURIComponent(artifactId)}`
  );
}

export function downloadSandboxArtifact(artifactId: string) {
  return api.get(`${SANDBOX_API_PREFIX}/artifacts/${encodeURIComponent(artifactId)}/download`, {
    responseType: "blob"
  });
}
