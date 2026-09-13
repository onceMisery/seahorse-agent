import { api } from "@/services/api";

export interface AgentTeamMember {
  memberId?: string;
  agentId?: string;
  role?: string;
  instruction?: string;
}

export interface AgentTeamEdge {
  sourceMemberId?: string;
  targetMemberId?: string;
  condition?: "ALWAYS" | "ON_SUCCESS" | "ON_FAILURE";
}

export type AgentTeamMode = "SUPERVISOR" | "WORKFLOW_DAG";
export type AgentTeamFailurePolicy = "FAIL_FAST" | "SKIP" | "RETRY";

export interface AgentTeamDefinition {
  teamId?: string;
  tenantId?: string;
  name?: string;
  mode?: AgentTeamMode;
  ownerTeam?: string;
  supervisorMemberId?: string;
  members?: AgentTeamMember[];
  edges?: AgentTeamEdge[];
  failurePolicy?: AgentTeamFailurePolicy;
  maxRetries?: number;
  active?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export type AgentTeamNodeStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";

export interface AgentTeamNodeRun {
  nodeRunId?: string;
  memberId?: string;
  agentId?: string;
  instruction?: string;
  status?: AgentTeamNodeStatus;
  handoffId?: string;
  childRunId?: string;
  outputSummary?: string;
  errorCode?: string;
  errorMessage?: string;
  startedAt?: string;
  finishedAt?: string;
}

export type AgentTeamRunStatus = "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";

export interface AgentTeamRun {
  teamRunId?: string;
  teamId?: string;
  tenantId?: string;
  userId?: string;
  mode?: AgentTeamMode;
  objective?: string;
  status?: AgentTeamRunStatus;
  parentRunId?: string;
  summary?: string;
  errorCode?: string;
  errorMessage?: string;
  nodeRuns?: AgentTeamNodeRun[];
  startedAt?: string;
  finishedAt?: string;
}

export interface AgentTeamCreateRequest {
  tenantId?: string;
  name: string;
  mode: AgentTeamMode;
  ownerTeam?: string;
  supervisorMemberId?: string;
  members: AgentTeamMember[];
  edges?: AgentTeamEdge[];
  failurePolicy?: AgentTeamFailurePolicy;
  maxRetries?: number;
}

export interface AgentTeamRunStartRequest {
  objective: string;
  userId?: string;
  traceId?: string;
}

export function listAgentTeams(tenantId = "default") {
  return api.get<AgentTeamDefinition[]>("/api/agent-teams", {
    params: { tenantId }
  });
}

export function getAgentTeam(teamId: string) {
  return api.get<AgentTeamDefinition>(`/api/agent-teams/${encodeURIComponent(teamId)}`);
}

export function createAgentTeam(request: AgentTeamCreateRequest) {
  return api.post<AgentTeamDefinition>("/api/agent-teams", request);
}

export function startAgentTeamRun(teamId: string, request: AgentTeamRunStartRequest) {
  return api.post<AgentTeamRun>(`/api/agent-teams/${encodeURIComponent(teamId)}/runs`, request);
}

export function getAgentTeamRun(teamRunId: string) {
  return api.get<AgentTeamRun>(`/api/agent-team-runs/${encodeURIComponent(teamRunId)}`);
}
