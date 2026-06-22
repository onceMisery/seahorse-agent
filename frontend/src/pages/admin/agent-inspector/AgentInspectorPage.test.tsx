import { render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { listAgentRunEvents } from "@/services/agentRunService";
import { getAgentRunContextSnapshot } from "@/services/runContextSnapshotService";

vi.mock("@/services/agentRunService", () => ({
  getAgentRunSnapshot: vi.fn().mockResolvedValue(null),
  getAgentRunCostSummary: vi.fn().mockResolvedValue(null),
  listAgentRunEvents: vi.fn().mockResolvedValue([])
}));

vi.mock("@/services/runContextSnapshotService", () => ({
  getAgentRunContextSnapshot: vi.fn().mockResolvedValue(null)
}));

import { AgentInspectorPage } from "@/pages/admin/agent-inspector/AgentInspectorPage";

describe("AgentInspectorPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAgentRunEvents).mockResolvedValue([]);
  });

  it("renders tabs", () => {
    render(
      <MemoryRouter>
        <AgentInspectorPage />
      </MemoryRouter>
    );
    expect(screen.getByText("事件")).toBeInTheDocument();
    expect(screen.getByText("状态")).toBeInTheDocument();
    expect(screen.getByText("上下文")).toBeInTheDocument();
    expect(screen.getByText("工具")).toBeInTheDocument();
  });

  it("renders replayed events in sequence order without duplicates", async () => {
    vi.mocked(listAgentRunEvents).mockResolvedValue([
      {
        eventId: "evt-3",
        eventSeq: "3" as unknown as number,
        eventType: "agent.step.finished",
        runId: "run-1",
        timestamp: "2026-06-08T00:00:03Z",
        typedPayload: { stepId: "step-2" }
      },
      {
        eventId: "evt-2",
        eventSeq: 2,
        eventType: "agent.step.started",
        runId: "run-1",
        timestamp: "2026-06-08T00:00:02Z",
        typedPayload: { stepId: "step-1" }
      },
      {
        eventId: "evt-2-duplicate",
        eventSeq: 2,
        eventType: "agent.step.started",
        runId: "run-1",
        timestamp: "2026-06-08T00:00:02Z",
        typedPayload: { stepId: "step-1" }
      },
      {
        eventId: "evt-invalid",
        eventSeq: "" as unknown as number,
        eventType: "agent.step.started",
        runId: "run-1",
        timestamp: "2026-06-08T00:00:01Z",
        typedPayload: { stepId: "step-invalid" }
      }
    ]);

    render(
      <MemoryRouter initialEntries={["/admin/agent-inspector/run-1"]}>
        <Routes>
          <Route path="/admin/agent-inspector/:runId" element={<AgentInspectorPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => expect(listAgentRunEvents).toHaveBeenCalledWith("run-1", 0));

    const eventNumbers = await screen.findAllByText(/^#\d+$/);
    expect(eventNumbers.map((node) => node.textContent)).toEqual(["#2", "#3"]);
    expect(screen.getAllByText("agent.step.started")).toHaveLength(1);
    expect(within(eventNumbers[0].closest("div")?.parentElement as HTMLElement)
      .getByText("agent.step.started")).toBeInTheDocument();
  });

  it("loads the run context snapshot on the context tab", async () => {
    vi.mocked(getAgentRunContextSnapshot).mockResolvedValue({
      id: "snapshot-1",
      runId: "run-agentscope",
      executorEngine: "agentscope",
      snapshotJson: JSON.stringify({
        executorEngine: "agentscope",
        toolIds: ["github_repository_reader"],
        a2aAgentIds: ["planner-agent"],
        agentScope: {
          nacosNamespace: "public",
          nacosGroup: "SEAHORSE_A2A"
        }
      })
    });

    render(
      <MemoryRouter initialEntries={["/admin/agent-inspector/run-agentscope?tab=context"]}>
        <Routes>
          <Route path="/admin/agent-inspector/:runId" element={<AgentInspectorPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => expect(getAgentRunContextSnapshot).toHaveBeenCalledWith("run-agentscope"));
    expect((await screen.findAllByText("agentscope")).length).toBeGreaterThan(0);
    expect(screen.getByText("planner-agent")).toBeInTheDocument();
    expect(screen.getByText("SEAHORSE_A2A")).toBeInTheDocument();
  });
});
