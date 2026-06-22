import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    post: vi.fn()
  }
}));

import { api } from "@/services/api";
import { createAgentRun, type AgentRunStartPayload } from "@/services/agentArtifactService";

describe("agentArtifactService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("starts agent runs with the backend run profile execution contract", async () => {
    const payload: AgentRunStartPayload = {
      versionId: "version-1",
      rolloutId: "rollout-1",
      tenantId: "tenant-a",
      conversationId: "conversation/1",
      triggerType: "MANUAL",
      inputSummary: "Compare this profile",
      traceId: "trace-1",
      runProfileId: 12,
      executorEngine: "agentscope",
      executorConfig: {
        nacosNamespace: "public",
        studioTraceEnabled: true
      }
    };
    const response = { runId: "run-1" };
    vi.mocked(api.post).mockResolvedValueOnce(response);

    await expect(createAgentRun("agent/1", payload)).resolves.toEqual(response);

    expect(api.post).toHaveBeenCalledWith("/agents/agent%2F1/runs", payload);
  });
});
