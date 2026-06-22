import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    get: vi.fn(),
    post: vi.fn()
  }
}));

import { api } from "@/services/api";
import {
  getMcpServer,
  getMcpServerStderrTail,
  listMcpServers,
  refreshMcpServerTools,
  restartMcpServer,
  testMcpServer
} from "@/services/mcpServerService";

describe("mcpServerService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("lists MCP server runtime status from the API endpoint", async () => {
    const servers = [
      {
        name: "local-echo",
        transport: "STDIO",
        enabled: true,
        status: "READY",
        toolCount: 1,
        stderrTail: "",
        tools: [{ toolId: "echo", provider: "MCP", enabled: true }]
      }
    ];
    vi.mocked(api.get).mockResolvedValueOnce(servers);

    await expect(listMcpServers()).resolves.toEqual(servers);

    expect(api.get).toHaveBeenCalledWith("/api/mcp/servers", { suppressErrorToast: true });
  });

  it("gets a single MCP server by encoded name", async () => {
    const server = {
      name: "filesystem local",
      transport: "STDIO",
      enabled: false,
      status: "FAILED",
      toolCount: 0,
      stderrTail: "command missing",
      tools: []
    };
    vi.mocked(api.get).mockResolvedValueOnce(server);

    await expect(getMcpServer("filesystem local")).resolves.toEqual(server);

    expect(api.get).toHaveBeenCalledWith("/api/mcp/servers/filesystem%20local");
  });

  it("gets stderr tail for a single MCP server by encoded name", async () => {
    vi.mocked(api.get).mockResolvedValueOnce("command missing");

    await expect(getMcpServerStderrTail("filesystem local")).resolves.toEqual("command missing");

    expect(api.get).toHaveBeenCalledWith("/api/mcp/servers/filesystem%20local/stderr-tail");
  });

  it("runs safe MCP server test call by encoded name", async () => {
    const result = {
      serverName: "filesystem local",
      toolId: "echo",
      success: true,
      status: "SUCCESS",
      content: "echo seahorse mcp health check",
      message: "SUCCESS"
    };
    vi.mocked(api.post).mockResolvedValueOnce(result);

    await expect(testMcpServer("filesystem local")).resolves.toEqual(result);

    expect(api.post).toHaveBeenCalledWith("/api/mcp/servers/filesystem%20local/test");
  });

  it("restarts an MCP server by encoded name", async () => {
    const server = {
      name: "filesystem local",
      transport: "STDIO",
      enabled: true,
      status: "READY",
      toolCount: 1,
      tools: [{ toolId: "echo", provider: "MCP", enabled: true }]
    };
    vi.mocked(api.post).mockResolvedValueOnce(server);

    await expect(restartMcpServer("filesystem local")).resolves.toEqual(server);

    expect(api.post).toHaveBeenCalledWith("/api/mcp/servers/filesystem%20local/restart");
  });

  it("refreshes MCP server tools by encoded name", async () => {
    const server = {
      name: "filesystem local",
      transport: "STDIO",
      enabled: true,
      status: "READY",
      toolCount: 2,
      tools: [
        { toolId: "echo", provider: "MCP", enabled: true },
        { toolId: "filesystem local.extra", provider: "MCP", enabled: true }
      ]
    };
    vi.mocked(api.post).mockResolvedValueOnce(server);

    await expect(refreshMcpServerTools("filesystem local")).resolves.toEqual(server);

    expect(api.post).toHaveBeenCalledWith("/api/mcp/servers/filesystem%20local/refresh-tools");
  });
});
