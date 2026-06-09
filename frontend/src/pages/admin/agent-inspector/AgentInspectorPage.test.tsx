import { render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { listAgentRunEvents } from "@/services/agentRunService";

vi.mock("@/services/agentRunService", () => ({
  getAgentRunSnapshot: vi.fn().mockResolvedValue(null),
  getAgentRunCostSummary: vi.fn().mockResolvedValue(null),
  listAgentRunEvents: vi.fn().mockResolvedValue([])
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
    expect(screen.getByText("Events")).toBeInTheDocument();
    expect(screen.getByText("State")).toBeInTheDocument();
    expect(screen.getByText("Context")).toBeInTheDocument();
    expect(screen.getByText("Tools")).toBeInTheDocument();
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
});
