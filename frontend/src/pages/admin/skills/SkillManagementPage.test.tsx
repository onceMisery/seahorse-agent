import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { SkillManagementPage } from "./SkillManagementPage";
import { getSkillGateResult, listSkills } from "@/services/skillService";

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
    getSkillGateResult: vi.fn(),
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

  it("opens skill GateResult evidence from the row action", async () => {
    const user = userEvent.setup();
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
    vi.mocked(getSkillGateResult).mockResolvedValue({
      subjectType: "SKILL",
      subjectId: "research",
      status: "BLOCKED",
      passed: false,
      blockingCodes: ["SKILL_DISABLED"],
      sourceType: "SKILL_REGISTRY",
      sourceId: "research",
      checkedAt: "2026-07-06T03:45:00Z",
      items: [
        {
          code: "SKILL_ENABLED",
          status: "FAIL",
          message: "Skill must be enabled before release"
        }
      ]
    });

    render(<SkillManagementPage />);

    await waitFor(() => {
      expect(screen.getAllByText("research").length).toBeGreaterThan(0);
    });

    await user.click(screen.getByRole("button", { name: /Gate/i }));

    expect(getSkillGateResult).toHaveBeenCalledWith("research", undefined);
    expect(await screen.findByText("Skill GateResult")).toBeInTheDocument();
    expect(screen.getByText("SKILL:research")).toBeInTheDocument();
    expect(screen.getByText("BLOCKED")).toBeInTheDocument();
    expect(screen.getByText("SKILL_DISABLED")).toBeInTheDocument();
    expect(screen.getByText("SKILL_REGISTRY")).toBeInTheDocument();
    expect(screen.getByText("Skill must be enabled before release")).toBeInTheDocument();
  });
});
