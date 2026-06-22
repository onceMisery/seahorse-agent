import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ReadinessPage from "@/pages/admin/ReadinessPage";

const readinessMocks = vi.hoisted(() => ({
  getReadinessSummary: vi.fn(),
  runReadinessCheck: vi.fn()
}));

vi.mock("@/services/readinessService", () => ({
  getReadinessSummary: readinessMocks.getReadinessSummary,
  runReadinessCheck: readinessMocks.runReadinessCheck
}));

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

describe("ReadinessPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    readinessMocks.getReadinessSummary.mockResolvedValue({
      overall: "degraded",
      mode: "ENTERPRISE",
      passedCount: 1,
      failedCount: 1,
      totalCount: 2,
      checks: [
        {
          id: "nacos",
          name: "Nacos 服务发现",
          status: "failed",
          severity: "error",
          message: "Nacos unavailable",
          impact: "A2A discovery is blocked",
          suggestion: "Restart Nacos"
        }
      ]
    });
    readinessMocks.runReadinessCheck.mockResolvedValue(undefined);
  });

  it("names each icon-only rerun button with its readiness check", async () => {
    render(<ReadinessPage />);

    expect(await screen.findByRole("button", { name: "重新检查 Nacos 服务发现" })).toBeInTheDocument();
  });
});
