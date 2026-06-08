import { render, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/services/auditCostService", () => ({
  aggregateCostUsage: vi.fn()
}));

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn()
  }
}));

vi.mock("@/config/productMode", () => ({
  ADVANCED_ADMIN_FEATURES: {
    COST_ANALYTICS: "COST_ANALYTICS"
  },
  getAdvancedFeatureState: vi.fn(() => ({ enabled: true }))
}));

import { CostAnalyticsPage } from "@/pages/admin/cost/CostAnalyticsPage";
import { aggregateCostUsage } from "@/services/auditCostService";

describe("CostAnalyticsPage", () => {
  it("loads aggregate data with a default tenant id", async () => {
    vi.mocked(aggregateCostUsage).mockResolvedValue({
      tenantId: "tenant-default",
      totalCost: 0,
      totalTokens: 0,
      totalCalls: 0
    });

    render(<CostAnalyticsPage />);

    await waitFor(() => {
      expect(aggregateCostUsage).toHaveBeenCalledWith({
        tenantId: "tenant-default",
        startTime: undefined,
        endTime: undefined,
        groupBy: "agent"
      });
    });
  });
});
