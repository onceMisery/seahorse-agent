import { api } from "@/services/api";
import type { PageResult } from "@/services/metadataGovernanceService";

// ── 类型定义 ──

export interface AuditEvent {
  auditId?: string;
  actor?: string;
  eventType?: string;
  agentId?: string;
  runId?: string;
  tenantId?: string;
  resource?: string;
  action?: string;
  payload?: Record<string, unknown>;
  timestamp?: string;
}

export interface CostUsageRecord {
  recordId?: string;
  tenantId?: string;
  agentId?: string;
  runId?: string;
  model?: string;
  tool?: string;
  tokens?: number;
  cost?: number;
  unit?: string;
  timestamp?: string;
}

export interface CostAggregate {
  totalCost?: number;
  totalTokens?: number;
  totalCalls?: number;
  byAgent?: Array<{ agentId?: string; cost?: number; calls?: number }>;
  byModel?: Array<{ model?: string; cost?: number; calls?: number }>;
  byTool?: Array<{ tool?: string; cost?: number; calls?: number }>;
  byTenant?: Array<{ tenantId?: string; cost?: number; calls?: number }>;
  timeBuckets?: Array<{ bucket?: string; cost?: number; calls?: number }>;
}

// ── 审计日志 ──

export function listAuditEvents(params: {
  current?: number;
  size?: number;
  actor?: string;
  eventType?: string;
  agentId?: string;
  runId?: string;
  tenantId?: string;
  startTime?: string;
  endTime?: string;
}) {
  return api.get<PageResult<AuditEvent>>("/api/audit-events", { params });
}

export function getAuditEvent(auditId: string) {
  return api.get<AuditEvent>(`/api/audit-events/${encodeURIComponent(auditId)}`);
}

// ── 成本明细与聚合 ──

export function createCostUsageRecord(payload: Record<string, unknown>) {
  return api.post<CostUsageRecord, CostUsageRecord>("/api/cost-usage-records", payload);
}

export function aggregateCostUsage(params: {
  tenantId?: string;
  agentId?: string;
  runId?: string;
  model?: string;
  tool?: string;
  startTime?: string;
  endTime?: string;
  groupBy?: string;
}) {
  return api.get<CostAggregate>("/api/cost-usage:aggregate", { params });
}
