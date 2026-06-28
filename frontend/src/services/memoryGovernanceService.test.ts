import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    get: vi.fn(),
    post: vi.fn()
  }
}));

vi.mock("@/utils/storage", () => ({
  storage: {
    getUser: vi.fn()
  }
}));

import { api } from "@/services/api";
import { resolveMemoryConflictInteractive } from "@/services/memoryGovernanceService";
import { storage } from "@/utils/storage";

describe("memoryGovernanceService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("sends the current user id when resolving chat-time memory conflicts", async () => {
    vi.mocked(storage.getUser).mockReturnValue({ userId: "user-123", username: "admin", role: "admin" } as any);
    vi.mocked(api.post).mockResolvedValueOnce({ resolved: true });

    await resolveMemoryConflictInteractive({ conflictId: "conflict-1", action: "keep_a" });

    expect(api.post).toHaveBeenCalledWith(
      "/memories/conflicts/interactive-resolve",
      { conflictId: "conflict-1", action: "keep_a" },
      { headers: { "X-User-Id": "user-123" } }
    );
  });
});
