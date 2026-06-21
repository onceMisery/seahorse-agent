import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    get: vi.fn()
  }
}));

import { api } from "@/services/api";
import {
  getAgentRunContextSnapshot,
  getRunContextSnapshotByRunId
} from "@/services/runContextSnapshotService";

describe("runContextSnapshotService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads run context snapshots by run id", async () => {
    vi.mocked(api.get).mockResolvedValueOnce({
      id: 1,
      runId: "run/alpha",
      executorEngine: "agentscope",
      snapshotJson: "{\"executorEngine\":\"agentscope\"}"
    });

    await expect(getRunContextSnapshotByRunId("run/alpha")).resolves.toEqual({
      id: 1,
      runId: "run/alpha",
      executorEngine: "agentscope",
      snapshotJson: "{\"executorEngine\":\"agentscope\"}"
    });
    expect(api.get).toHaveBeenCalledWith("/api/run-context-snapshots/by-run/run%2Falpha");
  });

  it("loads the context snapshot alias for an agent run", async () => {
    vi.mocked(api.get).mockResolvedValueOnce({
      id: 2,
      runId: "agent-run-1",
      executorEngine: "kernel",
      snapshotJson: "{}"
    });

    await expect(getAgentRunContextSnapshot("agent-run-1")).resolves.toEqual({
      id: 2,
      runId: "agent-run-1",
      executorEngine: "kernel",
      snapshotJson: "{}"
    });
    expect(api.get).toHaveBeenCalledWith("/api/agent-runs/agent-run-1/context-snapshot");
  });
});
