import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ToolDetailPage } from "./ToolDetailPage";
import {
  getTool,
  getToolGateResult,
  listToolInvocations
} from "@/services/toolCatalogService";

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

vi.mock("@/config/productMode", () => ({
  ADVANCED_ADMIN_FEATURES: {
    TOOL_CATALOG_MANAGEMENT: "TOOL_CATALOG_MANAGEMENT"
  },
  getAdvancedFeatureState: () => ({ enabled: true })
}));

vi.mock("@/services/toolCatalogService", async () => {
  const actual = await vi.importActual<typeof import("@/services/toolCatalogService")>(
    "@/services/toolCatalogService"
  );
  return {
    ...actual,
    getTool: vi.fn(),
    getToolGateResult: vi.fn(),
    listToolInvocations: vi.fn(),
    enableTool: vi.fn(),
    disableTool: vi.fn()
  };
});

describe("ToolDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getTool).mockResolvedValue({
      toolId: "weather_query",
      name: "Weather Query",
      description: "Query weather",
      provider: "MCP",
      resourceType: "MCP",
      riskLevel: "HIGH",
      enabled: false,
      approvalRequired: false,
      parameterSchema: { type: "object" }
    });
    vi.mocked(getToolGateResult).mockResolvedValue({
      subjectType: "TOOL",
      subjectId: "weather_query",
      status: "FAIL",
      passed: false,
      blockingCodes: ["TOOL_ENABLED", "TOOL_HIGH_RISK_APPROVAL_REQUIRED"],
      checkedAt: "2026-07-06T00:00:00Z",
      sourceType: "ToolCatalogEntry",
      sourceId: "weather_query",
      items: [
        {
          code: "TOOL_ENABLED",
          status: "FAIL",
          message: "Disabled tools cannot be released for production use"
        },
        {
          code: "TOOL_HIGH_RISK_APPROVAL_REQUIRED",
          status: "FAIL",
          message: "High and critical risk tools must require approval"
        }
      ]
    });
    vi.mocked(listToolInvocations).mockResolvedValue({
      records: [],
      total: 0,
      current: 1,
      size: 20,
      pages: 0
    });
  });

  it("renders the tool GateResult evidence on the detail page", async () => {
    render(
      <MemoryRouter initialEntries={["/admin/tools/weather_query"]}>
        <Routes>
          <Route path="/admin/tools/:toolId" element={<ToolDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(getToolGateResult).toHaveBeenCalledWith("weather_query");
    });

    expect(await screen.findByText("Weather Query")).toBeInTheDocument();
    expect(screen.getByText("GateResult")).toBeInTheDocument();
    expect(screen.getByText("TOOL")).toBeInTheDocument();
    expect(screen.getAllByText("FAIL").length).toBeGreaterThan(0);
    expect(screen.getAllByText("TOOL_ENABLED").length).toBeGreaterThan(0);
    expect(screen.getAllByText("TOOL_HIGH_RISK_APPROVAL_REQUIRED").length).toBeGreaterThan(0);
    expect(screen.getByText("ToolCatalogEntry")).toBeInTheDocument();
    expect(screen.getByText("Disabled tools cannot be released for production use")).toBeInTheDocument();
  });
});
