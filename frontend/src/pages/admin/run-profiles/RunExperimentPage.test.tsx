import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RunExperimentPage } from "@/pages/admin/run-profiles/RunExperimentPage";

const experimentMocks = vi.hoisted(() => ({
  cancelRunExperiment: vi.fn(),
  createRunExperiment: vi.fn(),
  forkRunExperimentTrialToBranch: vi.fn(),
  scoreRunExperimentTrial: vi.fn()
}));

const profileMocks = vi.hoisted(() => ({
  listRunProfiles: vi.fn()
}));

vi.mock("@/services/runExperimentService", () => ({
  cancelRunExperiment: experimentMocks.cancelRunExperiment,
  createRunExperiment: experimentMocks.createRunExperiment,
  forkRunExperimentTrialToBranch: experimentMocks.forkRunExperimentTrialToBranch,
  scoreRunExperimentTrial: experimentMocks.scoreRunExperimentTrial
}));

vi.mock("@/services/runProfileService", () => ({
  listRunProfiles: profileMocks.listRunProfiles
}));

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

describe("RunExperimentPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    profileMocks.listRunProfiles.mockResolvedValue([
      { id: 12, name: "AgentScope Research", executorEngine: "agentscope", enabled: true },
      { id: 13, name: "Kernel Baseline", executorEngine: "kernel", enabled: false }
    ]);
    experimentMocks.createRunExperiment.mockResolvedValue(details("PENDING"));
    experimentMocks.cancelRunExperiment.mockResolvedValue(details("CANCELLED"));
    experimentMocks.scoreRunExperimentTrial.mockResolvedValue(scoredDetails());
    experimentMocks.forkRunExperimentTrialToBranch.mockResolvedValue({
      trialId: 10,
      outputMessageId: 301,
      branch: []
    });
  });

  it("creates an experiment from selected run profiles and renders trial rows", async () => {
    render(<RunExperimentPage />);

    expect(await screen.findByText("AgentScope Research")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("实验名称"), { target: { value: "Profile compare" } });
    fireEvent.change(screen.getByLabelText("会话 ID"), { target: { value: "101" } });
    fireEvent.change(screen.getByLabelText("基准消息 ID"), { target: { value: "202" } });
    fireEvent.click(screen.getByLabelText("AgentScope Research"));
    fireEvent.click(screen.getByLabelText("Kernel Baseline"));
    fireEvent.click(screen.getByRole("button", { name: "发起实验" }));

    await waitFor(() => {
      expect(experimentMocks.createRunExperiment).toHaveBeenCalledWith({
        conversationId: 101,
        baseLeafMessageId: 202,
        name: "Profile compare",
        runProfileIds: [12, 13]
      });
    });
    expect(await screen.findByText("实验 #1")).toBeInTheDocument();
    expect(screen.getAllByText("AgentScope Research").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("PENDING").length).toBeGreaterThan(0);
    expect(screen.getByText("run-exp-1-trial-10")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "检视 run-exp-1-trial-10" })).toHaveAttribute(
      "href",
      "/admin/agent-inspector/run-exp-1-trial-10"
    );
    expect(screen.getByLabelText("trial-engine-10")).toHaveTextContent("agentscope");
    expect(screen.getByLabelText("trial-engine-11")).toHaveTextContent("kernel");
    expect(screen.getByLabelText("trial-metrics-10")).toHaveTextContent("steps 2");
    expect(screen.getByLabelText("trial-metrics-10")).toHaveTextContent("tools 1");
    expect(screen.getByLabelText("trial-metrics-10")).toHaveTextContent("chars 16");
  });

  it("cancels, scores, and forks a trial result from the experiment table", async () => {
    render(<RunExperimentPage />);

    await screen.findByText("AgentScope Research");
    fireEvent.change(screen.getByLabelText("实验名称"), { target: { value: "Profile compare" } });
    fireEvent.change(screen.getByLabelText("会话 ID"), { target: { value: "101" } });
    fireEvent.click(screen.getByLabelText("AgentScope Research"));
    fireEvent.click(screen.getByLabelText("Kernel Baseline"));
    fireEvent.click(screen.getByRole("button", { name: "发起实验" }));

    await screen.findByText("实验 #1");
    fireEvent.change(screen.getByLabelText("评分 JSON"), { target: { value: "{\"rating\":4}" } });
    fireEvent.click(screen.getByRole("button", { name: "保存评分" }));
    await waitFor(() => {
      expect(experimentMocks.scoreRunExperimentTrial).toHaveBeenCalledWith(1, 10, { rating: 4 });
    });

    fireEvent.click(screen.getByRole("button", { name: "Fork 分支" }));
    await waitFor(() => {
      expect(experimentMocks.forkRunExperimentTrialToBranch).toHaveBeenCalledWith(1, 10);
    });

    fireEvent.click(screen.getByRole("button", { name: "取消实验" }));
    await waitFor(() => {
      expect(experimentMocks.cancelRunExperiment).toHaveBeenCalledWith(1);
    });
  });
});

function details(status: string) {
  return {
    experiment: {
      id: 1,
      conversationId: 101,
      baseLeafMessageId: 202,
      name: "Profile compare",
      status
    },
    trials: [
      {
        id: 10,
        experimentId: 1,
        runProfileId: 12,
        runId: "run-exp-1-trial-10",
        outputMessageId: 301,
        metricJson: "{\"executorEngine\":\"agentscope\",\"truncated\":false,\"stepCount\":2,\"toolCallCount\":1,\"outputChars\":16}",
        status
      },
      {
        id: 11,
        experimentId: 1,
        runProfileId: 13,
        status
      }
    ]
  };
}

function scoredDetails() {
  const value = details("PENDING");
  value.trials[0].scoreJson = "{\"rating\":4}";
  return value;
}
