import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ToolCatalogPage } from "./ToolCatalogPage";
import { listTools } from "@/services/toolCatalogService";
import { listMcpServers, refreshMcpServerTools, restartMcpServer, testMcpServer } from "@/services/mcpServerService";

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
  listMcpServers: vi.fn(),
  refreshMcpServerTools: vi.fn(),
  restartMcpServer: vi.fn(),
  testMcpServer: vi.fn()
}));

describe("ToolCatalogPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(HTMLElement.prototype, "hasPointerCapture", {
      configurable: true,
      value: () => false
    });
    Object.defineProperty(HTMLElement.prototype, "setPointerCapture", {
      configurable: true,
      value: () => undefined
    });
    Object.defineProperty(HTMLElement.prototype, "releasePointerCapture", {
      configurable: true,
      value: () => undefined
    });
    Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {
      configurable: true,
      value: () => undefined
    });
    vi.mocked(listMcpServers).mockResolvedValue([]);
    vi.mocked(testMcpServer).mockResolvedValue({
      serverName: "local-echo",
      toolId: "echo",
      success: true,
      status: "SUCCESS",
      content: "echo seahorse mcp health check",
      message: "SUCCESS"
    });
    vi.mocked(restartMcpServer).mockResolvedValue({
      name: "local-echo",
      transport: "STDIO",
      enabled: true,
      status: "READY",
      toolCount: 1,
      tools: [{ toolId: "echo", provider: "MCP", enabled: true }]
    });
    vi.mocked(refreshMcpServerTools).mockResolvedValue({
      name: "local-echo",
      transport: "STDIO",
      enabled: true,
      status: "READY",
      toolCount: 2,
      tools: [
        { toolId: "echo", provider: "MCP", enabled: true },
        { toolId: "local-echo.extra", provider: "MCP", enabled: true }
      ]
    });
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

  it("runs safe MCP server test call from the server panel", async () => {
    const user = userEvent.setup();
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

    await user.click(screen.getByRole("button", { name: "测试" }));

    await waitFor(() => {
      expect(testMcpServer).toHaveBeenCalledWith("local-echo");
    });
    expect(screen.getByText("echo seahorse mcp health check")).toBeInTheDocument();
  });

  it("surfaces MCP server test approval entry when the diagnostic call is gated", async () => {
    const user = userEvent.setup();
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
        tools: [{ toolId: "echo", provider: "MCP", enabled: true }]
      }
    ]);
    vi.mocked(testMcpServer).mockResolvedValueOnce({
      serverName: "local-echo",
      toolId: "echo",
      success: false,
      status: "APPROVAL_REQUIRED",
      message: "Tool requires approval",
      approvalId: "approval:mcp-diagnostic"
    });

    render(
      <MemoryRouter>
        <ToolCatalogPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("local-echo")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "测试" }));

    await waitFor(() => {
      expect(screen.getByText("APPROVAL_REQUIRED")).toBeInTheDocument();
    });
    expect(screen.getByText("approval:mcp-diagnostic")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /打开审批/ })).toBeInTheDocument();
  });

  it("restarts MCP server from the server panel and reloads statuses", async () => {
    const user = userEvent.setup();
    vi.mocked(listTools).mockResolvedValue({
      records: [],
      total: 0,
      current: 1,
      pages: 1
    });
    vi.mocked(listMcpServers)
      .mockResolvedValueOnce([
        {
          name: "local-echo",
          transport: "STDIO",
          enabled: true,
          status: "FAILED",
          toolCount: 0,
          stderrTail: "boom",
          tools: []
        }
      ])
      .mockResolvedValueOnce([
        {
          name: "local-echo",
          transport: "STDIO",
          enabled: true,
          status: "READY",
          toolCount: 1,
          stderrTail: "restarted",
          tools: [{ toolId: "echo", provider: "MCP", enabled: true }]
        }
      ]);

    render(
      <MemoryRouter>
        <ToolCatalogPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("boom")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "restart-local-echo" }));

    await waitFor(() => {
      expect(restartMcpServer).toHaveBeenCalledWith("local-echo");
    });
    await waitFor(() => {
      expect(screen.getByText("restarted")).toBeInTheDocument();
    });
  });

  it("refreshes MCP server tools from the server panel and reloads statuses", async () => {
    const user = userEvent.setup();
    vi.mocked(listTools).mockResolvedValue({
      records: [],
      total: 0,
      current: 1,
      pages: 1
    });
    vi.mocked(listMcpServers)
      .mockResolvedValueOnce([
        {
          name: "local-echo",
          transport: "STDIO",
          enabled: true,
          status: "READY",
          toolCount: 1,
          tools: [{ toolId: "echo", provider: "MCP", enabled: true }]
        }
      ])
      .mockResolvedValueOnce([
        {
          name: "local-echo",
          transport: "STDIO",
          enabled: true,
          status: "READY",
          toolCount: 2,
          tools: [
            { toolId: "echo", provider: "MCP", enabled: true },
            { toolId: "local-echo.extra", provider: "MCP", enabled: true }
          ]
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

    await user.click(screen.getByRole("button", { name: "refresh-tools-local-echo" }));

    await waitFor(() => {
      expect(refreshMcpServerTools).toHaveBeenCalledWith("local-echo");
    });
    await waitFor(() => {
      expect(screen.getByText("local-echo.extra")).toBeInTheDocument();
    });
  });

  it("filters tools by provider", async () => {
    const user = userEvent.setup();
    vi.mocked(listTools).mockResolvedValue({
      records: [],
      total: 0,
      current: 1,
      pages: 1
    });

    render(
      <MemoryRouter>
        <ToolCatalogPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(listTools).toHaveBeenCalled();
    });

    await user.click(screen.getByText("全部来源").closest("button")!);
    await user.click(await screen.findByText("MCP"));

    await waitFor(() => {
      expect(listTools).toHaveBeenLastCalledWith(expect.objectContaining({ provider: "MCP" }));
    });
  });
});
