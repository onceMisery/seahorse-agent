import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { CostQuotaInspectorTab } from "@/components/chat/workbench/CostQuotaInspectorTab";
import { resumeAgentRun, retryAgentRun } from "@/services/agentRunService";

vi.mock("@/services/agentRunService", () => ({
  resumeAgentRun: vi.fn().mockResolvedValue({}),
  retryAgentRun: vi.fn().mockResolvedValue({})
}));

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

describe("CostQuotaInspectorTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders cost, quota pressure, resume and retry controls with readable Chinese labels", () => {
    render(
      <CostQuotaInspectorTab
        agentRunId="run-1"
        canResume
        canRetry
        costSummary={{
          tenantId: "default",
          runId: "run-1",
          totalTokens: 1234,
          totalCalls: 3,
          totalCost: 0.125,
          recordCount: 2
        }}
        quota={[{
          id: "quota-1",
          label: "每日运行额度",
          used: 90,
          limit: 100,
          unit: "次"
        }]}
      />
    );

    expect(screen.getByText("运行成本")).toBeInTheDocument();
    expect(screen.getByText("调用次数")).toBeInTheDocument();
    expect(screen.getByText("费用")).toBeInTheDocument();
    expect(screen.getByText("配额")).toBeInTheDocument();
    expect(screen.getByText("每日运行额度")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "恢复运行" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重试" })).toBeInTheDocument();
  });

  it("calls Agent Run actions from the visible controls", async () => {
    render(
      <CostQuotaInspectorTab
        agentRunId="run-1"
        canResume
        canRetry
        costSummary={{
          tenantId: "default",
          runId: "run-1",
          totalTokens: 100,
          totalCalls: 1,
          totalCost: 0.01,
          recordCount: 1
        }}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: "恢复运行" }));
    await waitFor(() => expect(resumeAgentRun).toHaveBeenCalledWith("run-1"));

    fireEvent.click(screen.getByRole("button", { name: "重试" }));
    await waitFor(() => expect(retryAgentRun).toHaveBeenCalledWith("run-1"));
  });
});
