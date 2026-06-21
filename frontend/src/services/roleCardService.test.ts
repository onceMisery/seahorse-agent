import { beforeEach, describe, expect, it, vi } from "vitest";

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
  activateRoleCard,
  createRoleCard,
  deleteRoleCard,
  listRoleCards,
  updateRoleCard
} from "@/services/roleCardService";

describe("roleCardService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("lists role cards from the API endpoint", async () => {
    vi.mocked(api.get).mockResolvedValueOnce([{ id: 1, name: "Analyst", enabled: true }]);

    await expect(listRoleCards()).resolves.toEqual([{ id: 1, name: "Analyst", enabled: true }]);
    expect(api.get).toHaveBeenCalledWith("/api/role-cards");
  });

  it("creates, updates, activates, and deletes role cards", async () => {
    vi.mocked(api.post).mockResolvedValueOnce(12);
    vi.mocked(api.put).mockResolvedValue(undefined);
    vi.mocked(api.delete).mockResolvedValue(undefined);

    await expect(createRoleCard({
      name: "Analyst",
      definition: "Be concise.",
      higherPerm: false
    })).resolves.toBe(12);
    await updateRoleCard(12, {
      name: "Analyst v2",
      definition: "Be precise.",
      higherPerm: true,
      avatarRef: "avatar://1"
    });
    await activateRoleCard(12);
    await deleteRoleCard(12);

    expect(api.post).toHaveBeenCalledWith("/api/role-cards", {
      name: "Analyst",
      definition: "Be concise.",
      higherPerm: false
    });
    expect(api.put).toHaveBeenCalledWith("/api/role-cards/12", {
      name: "Analyst v2",
      definition: "Be precise.",
      higherPerm: true,
      avatarRef: "avatar://1"
    });
    expect(api.put).toHaveBeenCalledWith("/api/role-cards/12/activate");
    expect(api.delete).toHaveBeenCalledWith("/api/role-cards/12");
  });
});
