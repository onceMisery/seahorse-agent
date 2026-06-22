import { api } from "@/services/api";
import type { PageResult } from "@/services/metadataGovernanceService";
import { emptyPage, optionalGet } from "@/services/optionalEndpoint";

// ── 类型定义 ──

export interface AuditEvent {
  auditId?: string;
  actor?: string;
  actorType?: string;
  actorId?: string;
  eventType?: string;
  agentId?: string;
  runId?: string;
  tenantId?: string;
  resource?: string;
  resourceType?: string;
  resourceId?: string;
  action?: string;
  payload?: Record<string, unknown>;
  redactedPayload?: string;
  timestamp?: string;
  occurredAt?: string;
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

const parsePayload = (payload?: Record<string, unknown>, redactedPayload?: string): Record<string, unknown> | undefined => {
  if (payload && typeof payload === "object") {
    return payload;
  }
  if (!redactedPayload) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(redactedPayload);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : { value: parsed };
  } catch {
    return { value: redactedPayload };
  }
};

const formatResource = (event: AuditEvent) => {
  if (event.resource) return event.resource;
  if (event.resourceType && event.resourceId) return `${event.resourceType}/${event.resourceId}`;
  return event.resourceId || event.resourceType;
};

export function normalizeAuditEvent(event: AuditEvent): AuditEvent {
  const payload = parsePayload(event.payload, event.redactedPayload);
  return {
    ...event,
    actor: event.actor || event.actorId || event.actorType,
    resource: formatResource(event),
    payload,
    timestamp: event.timestamp || event.occurredAt
  };
}

export function normalizeAuditEventPage(
  page: PageResult<AuditEvent> | AuditEvent[] | null | undefined
): PageResult<AuditEvent> {
  if (Array.isArray(page)) {
    return {
      records: page.map(normalizeAuditEvent),
      total: page.length,
      size: page.length,
      current: 1,
      pages: page.length > 0 ? 1 : 0
    };
  }
  return {
    records: (page?.records || []).map(normalizeAuditEvent),
    total: page?.total ?? page?.records?.length ?? 0,
    size: page?.size ?? 0,
    current: page?.current ?? 1,
    pages: page?.pages ?? 0
  };
}

// ── 审计日志 ──

export async function listAuditEvents(params: {
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
  const data = await optionalGet<PageResult<AuditEvent> | AuditEvent[]>(
    api.get<PageResult<AuditEvent> | AuditEvent[]>("/api/audit-events", { params, suppressErrorToast: true }),
    emptyPage<AuditEvent>(params.current, params.size)
  );
  return normalizeAuditEventPage(data);
}

export async function getAuditEvent(auditId: string) {
  const data = await api.get<AuditEvent>(`/api/audit-events/${encodeURIComponent(auditId)}`);
  return normalizeAuditEvent(data);
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
  return optionalGet(
    api.get<CostAggregate>("/api/cost-usage:aggregate", { params, suppressErrorToast: true }),
    {
      totalCost: 0,
      totalTokens: 0,
      totalCalls: 0,
      byAgent: [],
      byModel: [],
      byTool: [],
      byTenant: [],
      timeBuckets: []
    }
  );
}
