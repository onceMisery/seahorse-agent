import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";

vi.mock("@/services/agentRunService", () => ({
  getAgentRunSnapshot: vi.fn().mockResolvedValue(null),
  getAgentRunCostSummary: vi.fn().mockResolvedValue(null),
  listAgentRunEvents: vi.fn().mockResolvedValue([])
}));

import { AgentInspectorPage } from "@/pages/admin/agent-inspector/AgentInspectorPage";

describe("AgentInspectorPage", () => {
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
});
