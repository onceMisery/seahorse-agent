import { describe, expect, it, vi } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    get: vi.fn()
  }
}));

import { api } from "@/services/api";
import { getBackendFeatures } from "@/services/featureService";

describe("featureService", () => {
  it("loads backend feature capabilities", async () => {
    vi.mocked(api.get).mockResolvedValueOnce({
      productMode: "ENTERPRISE_PLATFORM",
      features: {}
    });

    await getBackendFeatures();

    expect(api.get).toHaveBeenCalledWith("/api/features");
  });
});
