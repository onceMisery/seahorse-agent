import { describe, expect, it, vi } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    get: vi.fn()
  }
}));

import { api } from "@/services/api";
import { getDashboardTrends } from "@/services/dashboardService";

describe("dashboardService", () => {
  it("normalizes trend timestamps returned as strings", async () => {
    vi.mocked(api.get).mockResolvedValueOnce({
      metric: "messages",
      window: "24h",
      granularity: "hour",
      series: [
        {
          name: "messages",
          data: [
            { ts: "1781708400000", value: "12" },
            { ts: "2026-06-18T12:00:00.000Z", value: 3 },
            { ts: "not-a-date", value: 99 }
          ]
        }
      ]
    });

    const result = await getDashboardTrends("messages", "24h", "hour");

    expect(result.series[0].data).toEqual([
      { ts: 1781708400000, value: 12 },
      { ts: Date.parse("2026-06-18T12:00:00.000Z"), value: 3 }
    ]);
  });
});
