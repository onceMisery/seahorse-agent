import { api } from "@/services/api";

// ── 类型定义 ──

export interface PluginStatus {
  name?: string;
  portType?: string;
  featureType?: string;
  version?: string;
  enabled?: boolean;
  healthy?: boolean;
  capabilities?: string[];
  message?: string;
  lastError?: string;
  details?: Record<string, unknown>;
  updatedBy?: string;
  updatedAt?: string;
}

export interface PluginHealthSummary {
  totalFeatures?: number;
  healthyCount?: number;
  unhealthyCount?: number;
  features?: Record<string, unknown>[];
}

// ── API 调用 ──

export function getPluginHealth() {
  return api.get<PluginHealthSummary>("/agent/plugins/health");
}

export function listPluginStatuses() {
  return api.get<PluginStatus[]>("/agent/plugins/status");
}

export function getPluginRegistry() {
  return api.get<Record<string, unknown>[]>("/agent/plugins/registry");
}

export function savePluginStatus(status: PluginStatus) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    "/agent/plugins/status",
    status
  );
}
