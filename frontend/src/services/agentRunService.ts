import { api } from "@/services/api";
import type { AgentRunCostSummary, AgentRunSnapshot, StreamEventEnvelope } from "@/types";
import {
  createAgentRun,
  getAgentRun,
  getAgentRunSteps,
  cancelAgentRun,
  retryAgentRunAction,
  resumeAgentRunAction,
  getAgentRunCheckpoints,
  getAgentRunHandoffs,
  getAgentHandoff,
  cancelAgentHandoff,
  getAgentRunArtifacts,
  getAgentArtifact,
  downloadAgentArtifact
} from "@/services/agentArtifactService";

// Re-export from agentArtifactService for unified facade
export {
  createAgentRun,
  getAgentRun,
  getAgentRunSteps,
  cancelAgentRun,
  retryAgentRunAction,
  resumeAgentRunAction,
  getAgentRunCheckpoints,
  getAgentRunHandoffs,
  getAgentHandoff,
  cancelAgentHandoff,
  getAgentRunArtifacts,
  getAgentArtifact,
  downloadAgentArtifact
};

const AGENT_RUNS_API_BASE = "/api/agent-runs";

export async function getAgentRunSnapshot(runId: string) {
  return api.get<AgentRunSnapshot, AgentRunSnapshot>(
    `${AGENT_RUNS_API_BASE}/${encodeURIComponent(runId)}/snapshot`
  );
}

export async function getAgentRunCostSummary(runId: string) {
  return api.get<AgentRunCostSummary, AgentRunCostSummary>(
    `${AGENT_RUNS_API_BASE}/${encodeURIComponent(runId)}/cost-summary`
  );
}

export async function resumeAgentRun(runId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `${AGENT_RUNS_API_BASE}/${encodeURIComponent(runId)}/resume`
  );
}

export async function retryAgentRun(runId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `${AGENT_RUNS_API_BASE}/${encodeURIComponent(runId)}/retry`
  );
}

export async function listAgentRunEvents(runId: string, afterSeq = 0) {
  return api.get<StreamEventEnvelope[], StreamEventEnvelope[]>(
    `${AGENT_RUNS_API_BASE}/${encodeURIComponent(runId)}/events`,
    { params: { afterSeq } }
  );
}

export async function listAgentRuns(params: {
  agentId?: string;
  status?: string;
  runId?: string;
  from?: string;
  to?: string;
  current?: number;
  size?: number;
}) {
  type PageResult = {
    records: Record<string, unknown>[];
    total: number;
    size: number;
    current: number;
    pages: number;
  };
  return api.get<PageResult, PageResult>("/api/agent-runs", { params });
}
