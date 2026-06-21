import { api } from "@/services/api";

export interface McpServerTool {
  toolId?: string;
  provider?: string;
  enabled?: boolean;
}

export interface McpServerStatus {
  name: string;
  transport?: string;
  enabled?: boolean;
  status?: "READY" | "FAILED" | "DISABLED" | "STARTING" | "STOPPED" | string;
  toolCount?: number;
  lastDiscoveryAt?: string | null;
  stderrTail?: string | null;
  tools?: McpServerTool[];
}

export function listMcpServers() {
  return api.get<McpServerStatus[]>("/api/mcp/servers");
}

export function getMcpServer(serverName: string) {
  return api.get<McpServerStatus>(`/api/mcp/servers/${encodeURIComponent(serverName)}`);
}
