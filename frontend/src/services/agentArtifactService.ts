import { api } from "@/services/api";
import type { AgentArtifact } from "@/types";

const AGENT_ARTIFACTS_API_BASE = "/api/agent-artifacts";
const AGENT_RUNS_API_BASE = "/api/agent-runs";

export async function getAgentArtifact(artifactId: string) {
  return api.get<AgentArtifact, AgentArtifact>(
    `${AGENT_ARTIFACTS_API_BASE}/${encodeURIComponent(artifactId)}`
  );
}

export async function listAgentRunArtifacts(runId: string) {
  return api.get<AgentArtifact[], AgentArtifact[]>(
    `${AGENT_RUNS_API_BASE}/${encodeURIComponent(runId)}/artifacts`
  );
}

export async function downloadAgentArtifact(artifactId: string) {
  return api.get<Blob, Blob>(
    `${AGENT_ARTIFACTS_API_BASE}/${encodeURIComponent(artifactId)}/download`,
    { responseType: "blob" }
  );
}
