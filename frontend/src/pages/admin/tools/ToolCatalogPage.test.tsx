import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ToolCatalogPage } from "./ToolCatalogPage";
import { listTools } from "@/services/toolCatalogService";
import { listMcpServers } from "@/services/mcpServerService";

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
    listTools: vi.fn(),
    enableTool: vi.fn(),
    disableTool: vi.fn()
  };
});

vi.mock("@/services/mcpServerService", () => ({
  listMcpServers: vi.fn()
}));

describe("ToolCatalogPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listMcpServers).mockResolvedValue([]);
  });

  it("labels tool_search as deferred discovery instead of a normal eager tool", async () => {
    vi.mocked(listTools).mockResolvedValue({
      records: [
        {
          toolId: "tool_search",
          name: "Tool Search",
          provider: "BUILTIN",
          resourceType: "TOOL",
          riskLevel: "LOW",
          enabled: true,
          approvalRequired: false
        },
        {
          toolId: "web_search",
          name: "Web Search",
          provider: "BUILTIN",
          resourceType: "WEB",
          riskLevel: "MEDIUM",
          enabled: true,
          approvalRequired: false
        }
      ],
      total: 2,
      current: 1,
      pages: 1
    });

    render(
      <MemoryRouter>
        <ToolCatalogPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("Tool Search")).toBeInTheDocument();
    });
    expect(screen.getByText("延迟发现")).toBeInTheDocument();
    expect(screen.getByText("tool_search 只暴露已授权工具的元数据；普通工具仍由 Agent 绑定与运行时策略决定。")).toBeInTheDocument();
  });

  it("shows MCP server runtime status and diagnostics", async () => {
    vi.mocked(listTools).mockResolvedValue({
      records: [],
      total: 0,
      current: 1,
      pages: 1
    });
    vi.mocked(listMcpServers).mockResolvedValue([
      {
        name: "local-echo",
        transport: "STDIO",
        enabled: true,
        status: "READY",
        toolCount: 1,
        stderrTail: "ready on stdio",
        tools: [{ toolId: "echo", provider: "MCP", enabled: true }]
      }
    ]);

    render(
      <MemoryRouter>
        <ToolCatalogPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("local-echo")).toBeInTheDocument();
    });
    expect(screen.getByText("STDIO")).toBeInTheDocument();
    expect(screen.getByText("就绪")).toBeInTheDocument();
    expect(screen.getByText("1 个工具")).toBeInTheDocument();
    expect(screen.getByText("ready on stdio")).toBeInTheDocument();
  });
});
