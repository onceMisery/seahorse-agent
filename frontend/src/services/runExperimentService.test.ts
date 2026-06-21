import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    get: vi.fn(),
    post: vi.fn()
  }
}));

import { api } from "@/services/api";
import {
  cancelRunExperiment,
  createRunExperiment,
  forkRunExperimentTrialToBranch,
  getRunExperiment,
  scoreRunExperimentTrial
} from "@/services/runExperimentService";

describe("runExperimentService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("creates and gets run experiments from the API endpoints", async () => {
    const details = {
      experiment: {
        id: 1,
        userId: "100",
        conversationId: 101,
        baseLeafMessageId: 202,
        name: "Profile compare",
        status: "PENDING"
      },
      trials: [
        { id: 10, experimentId: 1, runProfileId: 12, status: "PENDING" },
        { id: 11, experimentId: 1, runProfileId: 13, status: "PENDING" }
      ]
    };
    const request = {
      conversationId: 101,
      baseLeafMessageId: 202,
      name: "Profile compare",
      runProfileIds: [12, 13]
    };
    vi.mocked(api.post).mockResolvedValueOnce(details);
    vi.mocked(api.get).mockResolvedValueOnce(details);

    await expect(createRunExperiment(request)).resolves.toEqual(details);
    await expect(getRunExperiment(1)).resolves.toEqual(details);

    expect(api.post).toHaveBeenCalledWith("/api/run-experiments", request);
    expect(api.get).toHaveBeenCalledWith("/api/run-experiments/1");
  });

  it("cancels experiments and scores trials through the API endpoints", async () => {
    const cancelled = {
      experiment: { id: 1, conversationId: 101, name: "Profile compare", status: "CANCELLED" },
      trials: [{ id: 10, experimentId: 1, runProfileId: 12, status: "CANCELLED" }]
    };
    const scored = {
      experiment: { id: 1, conversationId: 101, name: "Profile compare", status: "PENDING" },
      trials: [{ id: 10, experimentId: 1, runProfileId: 12, status: "PENDING", scoreJson: "{\"rating\":5}" }]
    };
    vi.mocked(api.post).mockResolvedValueOnce(cancelled).mockResolvedValueOnce(scored);

    await expect(cancelRunExperiment(1)).resolves.toEqual(cancelled);
    await expect(scoreRunExperimentTrial(1, 10, { rating: 5 })).resolves.toEqual(scored);

    expect(api.post).toHaveBeenCalledWith("/api/run-experiments/1/cancel");
    expect(api.post).toHaveBeenCalledWith("/api/run-experiments/1/trials/10/score", { score: { rating: 5 } });
  });

  it("forks a trial result to the active conversation branch", async () => {
    const result = {
      trialId: 10,
      outputMessageId: 301,
      branch: [{ id: 301, parentId: 202, role: "assistant", content: "trial output", active: true }]
    };
    vi.mocked(api.post).mockResolvedValueOnce(result);

    await expect(forkRunExperimentTrialToBranch(1, 10)).resolves.toEqual(result);

    expect(api.post).toHaveBeenCalledWith("/api/run-experiments/1/trials/10/fork-to-branch");
  });
});
