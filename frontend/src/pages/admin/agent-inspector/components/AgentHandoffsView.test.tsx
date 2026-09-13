import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";

import { AgentHandoffsView } from "@/pages/admin/agent-inspector/components/AgentHandoffsView";
import { getAgentRunHandoffs } from "@/services/agentArtifactService";

vi.mock("@/services/agentArtifactService", () => ({
  getAgentRunHandoffs: vi.fn(),
  cancelAgentHandoff: vi.fn()
}));

function handoff(overrides: Record<string, string | undefined>) {
  return {
    handoffId: "handoff-root-1",
    tenantId: "tenant-1",
    parentRunId: "parent-run-1",
    childRunId: "child-run-1",
    sourceAgentId: "agent-supervisor",
    targetAgentId: "agent-researcher",
    status: "SUCCEEDED",
    failureCode: undefined,
    handoffReason: "TEAM_DISPATCH",
    ...overrides
  };
}

describe("AgentHandoffsView", () => {
  beforeEach(() => {
    vi.mocked(getAgentRunHandoffs).mockReset();
  });

  it("renders empty state without handoffs", async () => {
    vi.mocked(getAgentRunHandoffs).mockResolvedValue([]);
    render(
      <MemoryRouter>
        <AgentHandoffsView runId="parent-run-1" />
      </MemoryRouter>
    );
    expect(await screen.findByText("No handoffs")).toBeInTheDocument();
    expect(getAgentRunHandoffs).toHaveBeenCalledWith("parent-run-1");
  });

  it("renders handoff with failure code and child run link", async () => {
    vi.mocked(getAgentRunHandoffs).mockResolvedValue([
      handoff({ status: "FAILED", failureCode: "MEMBER_RUN_FAILED" })
    ]);
    render(
      <MemoryRouter>
        <AgentHandoffsView runId="parent-run-1" />
      </MemoryRouter>
    );
    expect(await screen.findByText("FAILED")).toBeInTheDocument();
    expect(screen.getByText("MEMBER_RUN_FAILED")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: /child run/i });
    expect(link).toHaveAttribute("href", "/admin/agent-inspector/child-run-1");
  });

  it("expands a node to lazily load the child run's handoff tree", async () => {
    const user = userEvent.setup();
    vi.mocked(getAgentRunHandoffs).mockImplementation((runId: string) => {
      if (runId === "parent-run-1") {
        return Promise.resolve([handoff({})]);
      }
      if (runId === "child-run-1") {
        return Promise.resolve([
          handoff({
            handoffId: "handoff-nested-1",
            parentRunId: "child-run-1",
            childRunId: "grandchild-run-1",
            sourceAgentId: "agent-researcher",
            targetAgentId: "agent-writer"
          })
        ]);
      }
      return Promise.resolve([]);
    });

    render(
      <MemoryRouter>
        <AgentHandoffsView runId="parent-run-1" />
      </MemoryRouter>
    );

    // 根节点先渲染（agentId 截断为前 12 字符），嵌套层未加载
    expect(await screen.findByText(/agent-superv →/)).toBeInTheDocument();
    expect(screen.queryByText(/agent-writ/)).not.toBeInTheDocument();

    // 展开后懒加载 child run 的 handoff
    await user.click(screen.getByRole("button", { name: /expand child handoffs/i }));
    expect(await screen.findByText(/agent-resear → agent-writer/)).toBeInTheDocument();
    expect(getAgentRunHandoffs).toHaveBeenCalledWith("child-run-1");
  });
});
