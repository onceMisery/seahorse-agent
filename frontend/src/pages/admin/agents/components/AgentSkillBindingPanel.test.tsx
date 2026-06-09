import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { AgentSkillBindingPanel } from "./AgentSkillBindingPanel";
import { listAgentSkillBindings, listSkills } from "@/services/skillService";

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

vi.mock("@/services/skillService", async () => {
  const actual = await vi.importActual<typeof import("@/services/skillService")>("@/services/skillService");
  return {
    ...actual,
    listAgentSkillBindings: vi.fn(),
    listSkills: vi.fn(),
    replaceAgentSkillBindings: vi.fn()
  };
});

describe("AgentSkillBindingPanel", () => {
  it("shows advisory tool dependencies for bound skill snapshots", async () => {
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
    vi.mocked(listAgentSkillBindings).mockResolvedValue([
      {
        agentId: "agent-1",
        skillName: "research",
        revisionId: "rev-1",
        injectMode: "METADATA_ONLY"
      }
    ]);

    render(<AgentSkillBindingPanel agentId="agent-1" />);

    await waitFor(() => {
      expect(screen.getAllByText("research").length).toBeGreaterThan(0);
    });
    expect(screen.getByText("工具依赖")).toBeInTheDocument();
    expect(screen.getByText("web_search")).toBeInTheDocument();
    expect(screen.getByText("仅限制模式会收窄可用工具；默认不会新增授权。")).toBeInTheDocument();
  });
});
