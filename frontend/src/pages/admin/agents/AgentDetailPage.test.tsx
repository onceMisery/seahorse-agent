import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AgentDetailPage } from "./AgentDetailPage";
import {
  getAgent,
  getAgentVersion,
  getLatestPublishChecks
} from "@/services/agentDefinitionService";
import { listAgentRuns } from "@/services/agentRunService";
import { getAgentSkillSnapshot } from "@/services/skillService";
import { getAgentGateResult } from "@/services/productionGateService";

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn()
  }
}));

vi.mock("@/config/productMode", () => ({
  ADVANCED_ADMIN_FEATURES: {
    AGENT_DEFINITION_MANAGEMENT: "AGENT_DEFINITION_MANAGEMENT"
  },
  getAdvancedFeatureState: () => ({ enabled: true })
}));

vi.mock("@/pages/admin/tools/components/AgentToolBindingPanel", () => ({
  AgentToolBindingPanel: () => <div>Agent tool bindings</div>
}));

vi.mock("./components/AgentPublishDialog", () => ({
  AgentPublishDialog: () => null
}));

vi.mock("./components/AgentRollbackDialog", () => ({
  AgentRollbackDialog: () => null
}));

vi.mock("@/services/agentDefinitionService", async () => {
  const actual = await vi.importActual<typeof import("@/services/agentDefinitionService")>(
    "@/services/agentDefinitionService"
  );
  return {
    ...actual,
    getAgent: vi.fn(),
    getAgentVersion: vi.fn(),
    getLatestPublishChecks: vi.fn(),
    validateAgent: vi.fn()
  };
});

vi.mock("@/services/agentRunService", () => ({
  listAgentRuns: vi.fn()
}));

vi.mock("@/services/skillService", () => ({
  getAgentSkillSnapshot: vi.fn()
}));

vi.mock("@/services/productionGateService", async () => {
  const actual = await vi.importActual<typeof import("@/services/productionGateService")>(
    "@/services/productionGateService"
  );
  return {
    ...actual,
    getAgentGateResult: vi.fn()
  };
});

describe("AgentDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAgent).mockResolvedValue({
      agentId: "agent-1",
      name: "Research Agent",
      description: "Research workflow",
      status: "PUBLISHED",
      riskLevel: "HIGH",
      currentVersionId: "version-1",
      currentVersionNumber: 3,
      toolCount: 2
    });
    vi.mocked(getAgentVersion).mockResolvedValue({
      versionId: "version-1",
      versionNumber: 3,
      instructions: "Research carefully",
      toolSetJson: JSON.stringify({ tools: ["search", "browser"] })
    });
    vi.mocked(getLatestPublishChecks).mockResolvedValue({
      agentId: "agent-1",
      status: "BLOCKED",
      checks: []
    });
    vi.mocked(getAgentSkillSnapshot).mockResolvedValue("{}");
    vi.mocked(listAgentRuns).mockResolvedValue({
      records: [],
      total: 0,
      current: 1,
      size: 5,
      pages: 0
    });
    vi.mocked(getAgentGateResult).mockResolvedValue({
      subjectType: "AGENT",
      subjectId: "agent-1",
      status: "BLOCKED",
      passed: false,
      blockingCodes: ["AGENT_PRODUCTION_GATE_FAILED"],
      sourceType: "AGENT_PRODUCTION_GATE",
      sourceId: "agent-1",
      checkedAt: "2026-07-06T05:10:00Z",
      items: [
        {
          code: "AGENT_PRODUCTION_GATE_FAILED",
          status: "FAIL",
          message: "Agent production gate must pass before release"
        }
      ]
    });
  });

  it("renders unified Agent GateResult evidence on the detail page", async () => {
    render(
      <MemoryRouter initialEntries={["/admin/agents/agent-1"]}>
        <Routes>
          <Route path="/admin/agents/:agentId" element={<AgentDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(getAgentGateResult).toHaveBeenCalledWith("agent-1");
    });

    expect(await screen.findByText("Research Agent")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "GateResult" }));
    expect(await screen.findByText("Agent GateResult")).toBeInTheDocument();
    expect(screen.getByText("AGENT:agent-1")).toBeInTheDocument();
    expect(screen.getByText("AGENT_PRODUCTION_GATE")).toBeInTheDocument();
    expect(screen.getAllByText("AGENT_PRODUCTION_GATE_FAILED").length).toBeGreaterThan(0);
    expect(screen.getByText("Agent production gate must pass before release")).toBeInTheDocument();
  });
});
