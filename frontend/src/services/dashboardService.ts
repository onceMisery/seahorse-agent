import { api } from "@/services/api";

export type DashboardKpi = {
  value: number;
  delta?: number;
  deltaPct?: number;
};

export type DashboardOverview = {
  window: string;
  compareWindow: string;
  updatedAt: number;
  kpis: {
    totalUsers: DashboardKpi;
    activeUsers: DashboardKpi;
    totalSessions: DashboardKpi;
    sessions24h: DashboardKpi;
    totalMessages: DashboardKpi;
    messages24h: DashboardKpi;
  };
};

export type DashboardPerformance = {
  window: string;
  avgLatencyMs: number;
  p95LatencyMs: number;
  successRate: number;
  errorRate: number;
  noDocRate: number;
  slowRate: number;
};

export type DashboardTrendPoint = {
  ts: number;
  value: number;
};

export type DashboardTrendSeries = {
  name: string;
  data: DashboardTrendPoint[];
};

export type DashboardTrends = {
  metric: string;
  window: string;
  granularity: string;
  series: DashboardTrendSeries[];
};

type RawDashboardTrendPoint = {
  ts?: unknown;
  value?: unknown;
};

type RawDashboardTrendSeries = {
  name?: string;
  data?: Array<RawDashboardTrendPoint | null> | null;
};

type RawDashboardTrends = {
  metric?: string;
  window?: string;
  granularity?: string;
  series?: Array<RawDashboardTrendSeries | null> | null;
};

const toFiniteNumber = (value: unknown): number | null => {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : null;
  }
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (!trimmed) return null;
    const parsed = Number(trimmed);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
};

const toTimestamp = (value: unknown): number | null => {
  const numeric = toFiniteNumber(value);
  if (numeric !== null) return numeric;

  if (typeof value === "string") {
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  return null;
};

const normalizeDashboardTrends = (
  payload: RawDashboardTrends | null | undefined
): DashboardTrends => {
  const safePayload = payload || {};

  return {
    metric: safePayload.metric || "",
    window: safePayload.window || "",
    granularity: safePayload.granularity || "",
    series: Array.isArray(safePayload.series)
      ? safePayload.series.flatMap((item) =>
          item
            ? [
                {
                  name: item.name || "",
                  data: Array.isArray(item.data)
                    ? item.data.flatMap((point) => {
                        if (!point) return [];
                        const ts = toTimestamp(point.ts);
                        const value = toFiniteNumber(point.value);
                        return ts === null || value === null ? [] : [{ ts, value }];
                      })
                    : []
                }
              ]
            : []
        )
      : []
  };
};

export async function getDashboardOverview(window: string = "24h"): Promise<DashboardOverview> {
  return api.get<DashboardOverview, DashboardOverview>("/admin/dashboard/overview", {
    params: { window }
  });
}

export async function getDashboardPerformance(
  window: string = "24h"
): Promise<DashboardPerformance> {
  return api.get<DashboardPerformance, DashboardPerformance>("/admin/dashboard/performance", {
    params: { window }
  });
}

export async function getDashboardTrends(
  metric: string,
  window: string = "7d",
  granularity: string = "day"
): Promise<DashboardTrends> {
  const response = await api.get<RawDashboardTrends, RawDashboardTrends>(
    "/admin/dashboard/trends",
    {
      params: { metric, window, granularity }
    }
  );
  return normalizeDashboardTrends(response);
}
