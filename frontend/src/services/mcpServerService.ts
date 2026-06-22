import { api } from "@/services/api";
import { optionalGet } from "@/services/optionalEndpoint";

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

export interface McpServerTestResult {
  serverName?: string;
  toolId?: string;
  success?: boolean;
  status?: string;
  content?: string;
  message?: string;
  testedAt?: string | null;
}

export function listMcpServers() {
  return optionalGet(api.get<McpServerStatus[]>("/api/mcp/servers", { suppressErrorToast: true }), [], [404, 409]);
}

export function getMcpServer(serverName: string) {
  return api.get<McpServerStatus>(`/api/mcp/servers/${encodeURIComponent(serverName)}`);
}

export function getMcpServerStderrTail(serverName: string) {
  return api.get<string>(`/api/mcp/servers/${encodeURIComponent(serverName)}/stderr-tail`);
}

export function testMcpServer(serverName: string) {
  return api.post<McpServerTestResult>(`/api/mcp/servers/${encodeURIComponent(serverName)}/test`);
}

export function restartMcpServer(serverName: string) {
  return api.post<McpServerStatus>(`/api/mcp/servers/${encodeURIComponent(serverName)}/restart`);
}

export function refreshMcpServerTools(serverName: string) {
  return api.post<McpServerStatus>(`/api/mcp/servers/${encodeURIComponent(serverName)}/refresh-tools`);
}
