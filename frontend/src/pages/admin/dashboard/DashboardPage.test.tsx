import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/services/dashboardService", () => ({
  getDashboardOverview: vi.fn(),
  getDashboardPerformance: vi.fn(),
  getDashboardTrends: vi.fn()
}));

import { DashboardPage } from "@/pages/admin/dashboard/DashboardPage";
import {
  getDashboardOverview,
  getDashboardPerformance,
  getDashboardTrends,
  type DashboardOverview,
  type DashboardPerformance,
  type DashboardTrends
} from "@/services/dashboardService";

class ResizeObserverStub {
  observe() {
  }

  disconnect() {
  }
}

const kpi = (value: number) => ({ value, delta: 0, deltaPct: 0 });

describe("DashboardPage", () => {
  it("shows the latest traffic bucket as an x-axis label", async () => {
    vi.stubGlobal("ResizeObserver", ResizeObserverStub);

    const lastHour = new Date(2026, 4, 30, 11, 0, 0, 0).getTime();
    const hourMs = 60 * 60 * 1000;
    const messagePoints = Array.from({ length: 24 }, (_, index) => ({
      ts: lastHour - (23 - index) * hourMs,
      value: index + 1
    }));
    const overview: DashboardOverview = {
      window: "24h",
      compareWindow: "prev_24h",
      updatedAt: lastHour,
      kpis: {
        totalUsers: kpi(1),
        activeUsers: kpi(1),
        totalSessions: kpi(1),
        sessions24h: kpi(1),
        totalMessages: kpi(24),
        messages24h: kpi(24)
      }
    };
    const performance: DashboardPerformance = {
      window: "24h",
      avgLatencyMs: 1000,
      p95LatencyMs: 1000,
      successRate: 100,
      errorRate: 0,
      noDocRate: 0,
      slowRate: 0
    };
    const emptyTrend = (metric: string): DashboardTrends => ({
      metric,
      window: "24h",
      granularity: "hour",
      series: []
    });

    vi.mocked(getDashboardOverview).mockResolvedValue(overview);
    vi.mocked(getDashboardPerformance).mockResolvedValue(performance);
    vi.mocked(getDashboardTrends).mockImplementation(async (metric) => {
      if (metric === "messages") {
        return {
          metric,
          window: "24h",
          granularity: "hour",
          series: [{ name: "messages", data: messagePoints }]
        };
      }
      return emptyTrend(metric);
    });

    render(<DashboardPage />);

    await waitFor(() => {
      expect(screen.getByText("11:00")).toBeInTheDocument();
    });
  });
});
