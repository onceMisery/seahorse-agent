import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";

import { AgentTeamsPage } from "@/pages/admin/teams/AgentTeamsPage";
import {
  getAgentTeam,
  listAgentTeams,
  startAgentTeamRun,
  type AgentTeamDefinition,
  type AgentTeamRun
} from "@/services/agentTeamService";

vi.mock("@/services/agentTeamService", () => ({
  listAgentTeams: vi.fn(),
  getAgentTeam: vi.fn(),
  createAgentTeam: vi.fn(),
  startAgentTeamRun: vi.fn()
}));

function team(overrides: Partial<AgentTeamDefinition> = {}): AgentTeamDefinition {
  return {
    teamId: "team-1",
    tenantId: "tenant-1",
    name: "research-team",
    mode: "SUPERVISOR",
    ownerTeam: "org",
    supervisorMemberId: "sup",
    members: [
      { memberId: "sup", agentId: "agent-sup", role: "supervisor", instruction: "" },
      { memberId: "researcher", agentId: "agent-r", role: "researcher", instruction: "先调研" }
    ],
    edges: [],
    failurePolicy: "FAIL_FAST",
    maxRetries: 0,
    active: true,
    createdAt: "2026-09-13T00:00:00Z",
    updatedAt: "2026-09-13T00:00:00Z",
    ...overrides
  };
}

function teamRun(overrides: Partial<AgentTeamRun> = {}): AgentTeamRun {
  return {
    teamRunId: "teamrun-1",
    teamId: "team-1",
    tenantId: "tenant-1",
    userId: "user-1",
    mode: "SUPERVISOR",
    objective: "调研并输出报告",
    status: "SUCCEEDED",
    parentRunId: "run-parent-1",
    summary: "最终汇总",
    nodeRuns: [
      {
        nodeRunId: "node-1",
        memberId: "researcher",
        agentId: "agent-r",
        instruction: "调查主题",
        status: "SUCCEEDED",
        handoffId: "handoff-1",
        childRunId: "run-child-1",
        outputSummary: "调研完成",
        startedAt: "2026-09-13T00:00:00Z",
        finishedAt: "2026-09-13T00:01:00Z"
      }
    ],
    startedAt: "2026-09-13T00:00:00Z",
    finishedAt: "2026-09-13T00:02:00Z",
    ...overrides
  };
}

describe("AgentTeamsPage", () => {
  beforeEach(() => {
    vi.mocked(listAgentTeams).mockReset();
    vi.mocked(getAgentTeam).mockReset();
    vi.mocked(startAgentTeamRun).mockReset();
  });

  it("renders the team list from the service", async () => {
    vi.mocked(listAgentTeams).mockResolvedValue([team()]);
    render(
      <MemoryRouter>
        <AgentTeamsPage />
      </MemoryRouter>
    );

    expect(await screen.findByText("research-team")).toBeInTheDocument();
    expect(screen.getByText("SUPERVISOR")).toBeInTheDocument();
    expect(screen.getByText("FAIL_FAST")).toBeInTheDocument();
  });

  it("renders the empty state when no teams exist", async () => {
    vi.mocked(listAgentTeams).mockResolvedValue([]);
    render(
      <MemoryRouter>
        <AgentTeamsPage />
      </MemoryRouter>
    );

    expect(await screen.findByText("暂无团队定义")).toBeInTheDocument();
  });

  it("starts a team run and renders node runs with statuses", async () => {
    const user = userEvent.setup();
    vi.mocked(listAgentTeams).mockResolvedValue([team()]);
    vi.mocked(startAgentTeamRun).mockResolvedValue(teamRun());
    render(
      <MemoryRouter>
        <AgentTeamsPage />
      </MemoryRouter>
    );

    await user.click(await screen.findByRole("button", { name: /运行/i }));
    await user.type(await screen.findByLabelText("任务目标"), "调研并输出报告");
    await user.click(screen.getByRole("button", { name: /启动运行/i }));

    expect(await screen.findByText("最终汇总")).toBeInTheDocument();
    expect(screen.getAllByText("SUCCEEDED").length).toBeGreaterThan(0);
    expect(screen.getByText("调研完成")).toBeInTheDocument();
    expect(startAgentTeamRun).toHaveBeenCalledWith("team-1", { objective: "调研并输出报告" });
    const inspectorLink = screen.getByRole("link", { name: /在检视器中查看运行/i });
    expect(inspectorLink).toHaveAttribute("href", "/admin/agent-inspector/run-parent-1");
  });

  it("opens team detail with members and edges", async () => {
    const user = userEvent.setup();
    vi.mocked(listAgentTeams).mockResolvedValue([team()]);
    vi.mocked(getAgentTeam).mockResolvedValue(
      team({
        edges: [{ sourceMemberId: "sup", targetMemberId: "researcher", condition: "ALWAYS" }]
      })
    );
    render(
      <MemoryRouter>
        <AgentTeamsPage />
      </MemoryRouter>
    );

    await user.click(await screen.findByRole("button", { name: /详情/i }));

    expect(await screen.findByText("先调研")).toBeInTheDocument();
    expect(screen.getByText("协作边")).toBeInTheDocument();
  });

  it("keeps node run statuses honest when a run partially fails", async () => {
    const user = userEvent.setup();
    vi.mocked(listAgentTeams).mockResolvedValue([team()]);
    vi.mocked(startAgentTeamRun).mockResolvedValue(
      teamRun({
        status: "FAILED",
        summary: "部分输出",
        errorCode: "TEAM_PARTIAL_FAILURE",
        errorMessage: "部分节点失败，其余分支已按策略执行完毕",
        nodeRuns: [
          {
            nodeRunId: "node-1",
            memberId: "researcher",
            agentId: "agent-r",
            status: "SUCCEEDED",
            outputSummary: "调研完成"
          },
          {
            nodeRunId: "node-2",
            memberId: "writer",
            agentId: "agent-w",
            status: "FAILED",
            errorCode: "MEMBER_RUN_FAILED",
            errorMessage: "成员执行失败"
          }
        ]
      })
    );
    render(
      <MemoryRouter>
        <AgentTeamsPage />
      </MemoryRouter>
    );

    await user.click(await screen.findByRole("button", { name: /运行/i }));
    await user.type(await screen.findByLabelText("任务目标"), "目标");
    await user.click(screen.getByRole("button", { name: /启动运行/i }));

    expect(await screen.findByText(/TEAM_PARTIAL_FAILURE/)).toBeInTheDocument();
    expect(screen.getByText("调研完成")).toBeInTheDocument();
    expect(screen.getByText("成员执行失败")).toBeInTheDocument();
    // 失败节点不得伪造成成功
    expect(screen.getAllByText("FAILED").length).toBeGreaterThan(0);
  });
});
