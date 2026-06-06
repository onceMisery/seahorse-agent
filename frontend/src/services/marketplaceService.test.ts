import { describe, expect, it, vi, beforeEach } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}));

import { api } from "@/services/api";
import {
  getMySubscriptions,
  listMarketplaceAgents,
  listPendingReviews
} from "@/services/marketplaceService";

describe("marketplaceService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("unwraps marketplace agents from a paginated envelope", async () => {
    vi.mocked(api.get).mockResolvedValueOnce({
      records: [{ agentId: "agent-1", name: "Agent One" }]
    });

    await expect(listMarketplaceAgents({ page: 1, size: 12 })).resolves.toEqual([
      { agentId: "agent-1", name: "Agent One" }
    ]);
    expect(api.get).toHaveBeenCalledWith("/api/marketplace/agents", {
      params: { page: 1, size: 12 }
    });
  });

  it("unwraps subscriptions from a paginated envelope", async () => {
    vi.mocked(api.get).mockResolvedValueOnce({
      records: [{ agentId: "agent-1", agentName: "Agent One", subscribedAt: "2026-06-06T00:00:00Z", active: true }]
    });

    await expect(getMySubscriptions()).resolves.toEqual([
      { agentId: "agent-1", agentName: "Agent One", subscribedAt: "2026-06-06T00:00:00Z", active: true }
    ]);
    expect(api.get).toHaveBeenCalledWith("/api/marketplace/agents/my-subscriptions");
  });

  it("unwraps pending reviews from a paginated envelope", async () => {
    vi.mocked(api.get).mockResolvedValueOnce({
      records: [{ id: 1, agentId: "agent-1", submittedBy: "admin", status: "PENDING", submittedAt: "2026-06-06T00:00:00Z" }]
    });

    await expect(listPendingReviews()).resolves.toEqual([
      { id: 1, agentId: "agent-1", submittedBy: "admin", status: "PENDING", submittedAt: "2026-06-06T00:00:00Z" }
    ]);
    expect(api.get).toHaveBeenCalledWith("/api/marketplace/reviews/pending");
  });
});
