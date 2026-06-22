import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RoleCardPage } from "@/pages/admin/role-cards/RoleCardPage";

const serviceMocks = vi.hoisted(() => ({
  activateRoleCard: vi.fn(),
  createRoleCard: vi.fn(),
  deleteRoleCard: vi.fn(),
  listRoleCards: vi.fn(),
  updateRoleCard: vi.fn()
}));

vi.mock("@/services/roleCardService", () => ({
  activateRoleCard: serviceMocks.activateRoleCard,
  createRoleCard: serviceMocks.createRoleCard,
  deleteRoleCard: serviceMocks.deleteRoleCard,
  listRoleCards: serviceMocks.listRoleCards,
  updateRoleCard: serviceMocks.updateRoleCard
}));

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

describe("RoleCardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    serviceMocks.listRoleCards.mockResolvedValue([
      {
        id: 12,
        name: "Analyst",
        definition: "Be precise.",
        higherPerm: true,
        enabled: false,
        shareScope: "TEAM",
        approvalStatus: "APPROVED",
        published: true
      },
      {
        id: 13,
        name: "Default Helper",
        definition: "Be concise.",
        higherPerm: false,
        enabled: true,
        shareScope: "PRIVATE",
        approvalStatus: "PENDING",
        published: false
      }
    ]);
    serviceMocks.activateRoleCard.mockResolvedValue(undefined);
    serviceMocks.createRoleCard.mockResolvedValue(99);
    serviceMocks.updateRoleCard.mockResolvedValue(undefined);
    serviceMocks.deleteRoleCard.mockResolvedValue(undefined);
  });

  it("renders role card governance fields from the list API", async () => {
    render(<RoleCardPage />);

    expect(await screen.findByText("Analyst")).toBeInTheDocument();
    expect(screen.getByText("Be precise.")).toBeInTheDocument();
    expect(screen.getByText("TEAM")).toBeInTheDocument();
    expect(screen.getByText("APPROVED")).toBeInTheDocument();
    expect(screen.getByText("高权限")).toBeInTheDocument();
    expect(screen.getByText("Default Helper")).toBeInTheDocument();
  });

  it("creates and updates role cards from the management form", async () => {
    const user = userEvent.setup();
    render(<RoleCardPage />);

    await screen.findByText("Analyst");
    await user.click(screen.getByRole("button", { name: "新建角色卡" }));
    await user.type(screen.getByLabelText("名称"), "Reviewer");
    await user.type(screen.getByLabelText("定义"), "Review strictly.");
    await user.click(screen.getByLabelText("高权限"));
    await user.selectOptions(screen.getByLabelText("共享范围"), "TEAM");
    await user.selectOptions(screen.getByLabelText("审批状态"), "APPROVED");
    await user.click(screen.getByLabelText("发布"));
    await user.click(screen.getByRole("button", { name: "保存角色卡" }));

    await waitFor(() => {
      expect(serviceMocks.createRoleCard).toHaveBeenCalledWith({
        name: "Reviewer",
        definition: "Review strictly.",
        higherPerm: true,
        avatarRef: null,
        shareScope: "TEAM",
        approvalStatus: "APPROVED",
        published: true
      });
    });

    await user.click(screen.getAllByRole("button", { name: "编辑" })[0]);
    await user.clear(screen.getByLabelText("名称"));
    await user.type(screen.getByLabelText("名称"), "Analyst v2");
    await user.click(screen.getByRole("button", { name: "保存角色卡" }));

    await waitFor(() => {
      expect(serviceMocks.updateRoleCard).toHaveBeenCalledWith(12, expect.objectContaining({
        name: "Analyst v2",
        higherPerm: true,
        shareScope: "TEAM",
        approvalStatus: "APPROVED",
        published: true
      }));
    });
  });

  it("activates and deletes role cards", async () => {
    const user = userEvent.setup();
    render(<RoleCardPage />);

    await screen.findByText("Analyst");
    await user.click(screen.getByRole("button", { name: "启用-12" }));
    await user.click(screen.getAllByRole("button", { name: "删除" })[0]);

    await waitFor(() => {
      expect(serviceMocks.activateRoleCard).toHaveBeenCalledWith(12);
      expect(serviceMocks.deleteRoleCard).toHaveBeenCalledWith(12);
      expect(serviceMocks.listRoleCards).toHaveBeenCalledTimes(3);
    });
  });

  it("shows system preset role cards as readonly while keeping activation available", async () => {
    serviceMocks.listRoleCards.mockResolvedValueOnce([
      {
        id: 21,
        name: "需求分析师",
        definition: "Clarify goals.",
        higherPerm: false,
        enabled: false,
        shareScope: "ORG",
        approvalStatus: "APPROVED",
        published: true,
        assetSource: "SYSTEM",
        presetKey: "role.requirement-analyst",
        presetVersion: 1,
        readonly: 1
      }
    ]);

    render(<RoleCardPage />);

    const row = (await screen.findByText("需求分析师")).closest("tr");
    expect(row).not.toBeNull();
    const scope = within(row as HTMLElement);
    expect(scope.getByText("系统预设")).toBeInTheDocument();
    expect(scope.getByRole("button", { name: "启用-21" })).not.toBeDisabled();
    expect(scope.getByRole("button", { name: "编辑" })).toBeDisabled();
    expect(scope.getByRole("button", { name: "删除" })).toBeDisabled();
  });
});
