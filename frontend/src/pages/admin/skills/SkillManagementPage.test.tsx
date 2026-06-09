import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { SkillManagementPage } from "./SkillManagementPage";
import { listSkills } from "@/services/skillService";

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

vi.mock("@/config/productMode", () => ({
  ADVANCED_ADMIN_FEATURES: {
    SKILL_MANAGEMENT: "SKILL_MANAGEMENT"
  },
  getAdvancedFeatureState: () => ({ enabled: true })
}));

vi.mock("@/services/skillService", async () => {
  const actual = await vi.importActual<typeof import("@/services/skillService")>("@/services/skillService");
  return {
    ...actual,
    listSkills: vi.fn()
  };
});

describe("SkillManagementPage", () => {
  it("labels skill allowed tools as advisory rather than executable permissions", async () => {
    vi.mocked(listSkills).mockResolvedValue({
      records: [
        {
          name: "research",
          category: "PUBLIC",
          status: "ACTIVE",
          enabled: true,
          latestRevisionId: "rev-1",
          description: "Research workflow",
          tags: ["research"],
          allowedTools: ["web_search"]
        }
      ]
    });

    render(<SkillManagementPage />);

    await waitFor(() => {
      expect(screen.getAllByText("research").length).toBeGreaterThan(0);
    });
    expect(screen.getByText("建议工具")).toBeInTheDocument();
    expect(screen.getByText("web_search")).toBeInTheDocument();
    expect(screen.getByText("默认仅作为提示元数据，不会扩大 Agent 工具授权。")).toBeInTheDocument();
  });
});
