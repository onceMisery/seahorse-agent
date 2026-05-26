import { api } from "@/services/api";
import type { AgentRunCostSummary, AgentRunSnapshot } from "@/types";

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
