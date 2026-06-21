import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    get: vi.fn()
  }
}));

import { api } from "@/services/api";
import { getMcpServer, listMcpServers } from "@/services/mcpServerService";

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

    expect(api.get).toHaveBeenCalledWith("/api/mcp/servers");
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
});
