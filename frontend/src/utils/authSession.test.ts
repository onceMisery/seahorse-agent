import { beforeEach, describe, expect, it, vi } from "vitest";

import { handleUnauthorizedSession } from "@/utils/authSession";
import { storage } from "@/utils/storage";

vi.mock("@/utils/storage", () => ({
  storage: {
    clearAuth: vi.fn()
  }
}));

describe("handleUnauthorizedSession", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, "location", {
      configurable: true,
      value: {
        pathname: "/admin/knowledge",
        search: "",
        hash: "",
        replace: vi.fn()
      }
    });
  });

  it("does not leak raw token values in login redirect reason", () => {
    handleUnauthorizedSession("token invalid: 2f04865b-95e4-40ac-90b1-078f5c6ec671");

    expect(storage.clearAuth).toHaveBeenCalled();
    const target = vi.mocked(window.location.replace).mock.calls[0][0];
    expect(target).toContain("/login?");
    expect(decodeURIComponent(target)).toContain("登录已过期，请重新登录");
    expect(target).not.toContain("2f04865b");
    expect(decodeURIComponent(target)).not.toContain("token invalid");
  });
});
