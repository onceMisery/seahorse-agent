import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { PolicyAdjustmentTicketPage } from "./PolicyAdjustmentTicketPage";
import {
  batchApprovePolicyAdjustmentTickets,
  batchRejectPolicyAdjustmentTickets,
  generatePolicyAdjustmentTickets,
  listPolicyAdjustmentTicketReviews,
  listPolicyAdjustmentTickets,
  rejectPolicyAdjustmentTicket
} from "@/services/policyAdjustmentTicketService";

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
    info: vi.fn(),
    warning: vi.fn()
  }
}));

vi.mock("@/config/productMode", () => ({
  ADVANCED_ADMIN_FEATURES: {
    MEMORY_GOVERNANCE: "MEMORY_GOVERNANCE"
  },
  getAdvancedFeatureState: () => ({ enabled: true })
}));

vi.mock("@/stores/authStore", () => ({
  useAuthStore: (selector: (state: { user: { username: string } }) => unknown) =>
    selector({ user: { username: "tester" } })
}));

vi.mock("@/services/policyAdjustmentTicketService", async () => {
  const actual = await vi.importActual<typeof import("@/services/policyAdjustmentTicketService")>(
    "@/services/policyAdjustmentTicketService"
  );
  return {
    ...actual,
    listPolicyAdjustmentTickets: vi.fn(),
    generatePolicyAdjustmentTickets: vi.fn(),
    approvePolicyAdjustmentTicket: vi.fn(),
    rejectPolicyAdjustmentTicket: vi.fn(),
    applyPolicyAdjustmentTicket: vi.fn(),
    rollBackPolicyAdjustmentTicket: vi.fn(),
    batchApprovePolicyAdjustmentTickets: vi.fn(),
    batchRejectPolicyAdjustmentTickets: vi.fn(),
    listPolicyAdjustmentTicketReviews: vi.fn().mockResolvedValue([])
  };
});

const PENDING_TICKET = {
  ticketId: "pat-1",
  tenantId: "default",
  targetKey: "user:preference:language",
  policyKey: "memory.captureAcceptThreshold",
  direction: "TIGHTEN" as const,
  suggestedDelta: 0.05,
  disapprovalRate: 0.72,
  sampleCount: 18,
  rationale: "连续三轮被点踩且复核被拒",
  evidenceIds: ["msg-1", "msg-2"],
  status: "PENDING" as const,
  createdBy: "learning-loop",
  createdAt: "2026-09-16T00:00:00Z"
};

const PENDING_TICKET_2 = {
  ...PENDING_TICKET,
  ticketId: "pat-2",
  targetKey: "project.fact"
};

/** 已落地工单：不参与批量勾选，但会展示回访结论。 */
const APPLIED_TICKET = {
  ...PENDING_TICKET,
  ticketId: "pat-3",
  targetKey: "user:preference:timezone",
  status: "APPLIED" as const
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/admin/policy-tickets"]}>
      <PolicyAdjustmentTicketPage />
    </MemoryRouter>
  );
}

/** 列表响应是 { page, latestReviews }，与后端 TicketPageResponse 一致。 */
function pageResponse(records: unknown[], latestReviews: Record<string, unknown> = {}) {
  return {
    page: { records, total: records.length, size: 10, current: 1, pages: 1 },
    latestReviews
  } as never;
}

describe("PolicyAdjustmentTicketPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listPolicyAdjustmentTickets).mockResolvedValue(pageResponse([PENDING_TICKET]));
  });

  it("renders tickets and opens the drawer with the same camelCase fields", async () => {
    renderPage();

    expect(await screen.findByText("user:preference:language")).toBeInTheDocument();
    expect(screen.getByText("memory.captureAcceptThreshold")).toBeInTheDocument();
    // 不认可率 0.72 -> 72.0%
    expect(screen.getByText("72.0%")).toBeInTheDocument();

    fireEvent.click(screen.getByText("user:preference:language"));

    expect(await screen.findByText("策略调整工单详情")).toBeInTheDocument();
    expect(screen.getByText("连续三轮被点踩且复核被拒")).toBeInTheDocument();
    expect(screen.getByText("msg-1")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "通过" })).toBeInTheDocument();
  });

  it("rejects an out-of-range sample limit before calling the backend", async () => {
    renderPage();
    await screen.findByText("user:preference:language");

    const limitInput = screen.getByPlaceholderText("采样上限");
    fireEvent.change(limitInput, { target: { value: "0" } });
    fireEvent.click(screen.getByRole("button", { name: /运行闭环/ }));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("采样上限需要在 1 ~ 1000 之间");
    });
    expect(generatePolicyAdjustmentTickets).not.toHaveBeenCalled();
  });

  it("blocks a reject decision without a comment", async () => {
    renderPage();
    await screen.findByText("user:preference:language");

    fireEvent.click(screen.getByText("user:preference:language"));
    await screen.findByText("策略调整工单详情");

    fireEvent.click(screen.getByRole("button", { name: "拒绝" }));
    fireEvent.click(await screen.findByRole("button", { name: "确认拒绝" }));

    await waitFor(() => {
      expect(screen.getByText(/拒绝必须填写意见/)).toBeInTheDocument();
    });
    expect(rejectPolicyAdjustmentTicket).not.toHaveBeenCalled();
  });

  it("selects all pending tickets and keeps applied ones out of the selection", async () => {
    vi.mocked(listPolicyAdjustmentTickets).mockResolvedValue(
      pageResponse([PENDING_TICKET, PENDING_TICKET_2, APPLIED_TICKET])
    );
    renderPage();
    await screen.findByText("user:preference:language");

    fireEvent.click(screen.getByRole("checkbox", { name: "全选待审批工单" }));

    // 三张里有两张是待审批；已落地的那张不可选，不能混进批量操作里
    expect(await screen.findByText("已选 2 张")).toBeInTheDocument();
    expect(screen.getAllByRole("checkbox", { name: "选择工单" })).toHaveLength(2);
    expect(screen.getByRole("checkbox", { name: "只有待审批工单可勾选" })).toBeDisabled();
  });

  it("reports partial batch failures and keeps only the failed tickets selected", async () => {
    vi.mocked(listPolicyAdjustmentTickets).mockResolvedValue(
      pageResponse([PENDING_TICKET, PENDING_TICKET_2])
    );
    vi.mocked(batchApprovePolicyAdjustmentTickets).mockResolvedValue({
      targetStatus: "APPROVED",
      succeededTicketIds: ["pat-1"],
      failed: [{ ticketId: "pat-2", reason: "工单状态已被其他审批人变更，请刷新后重试" }]
    });
    renderPage();
    await screen.findByText("user:preference:language");

    fireEvent.click(screen.getByRole("checkbox", { name: "全选待审批工单" }));
    fireEvent.click(await screen.findByRole("button", { name: "批量通过" }));
    fireEvent.click(await screen.findByRole("button", { name: "确认批量通过" }));

    await waitFor(() => {
      expect(batchApprovePolicyAdjustmentTickets).toHaveBeenCalledWith({
        ticketIds: ["pat-1", "pat-2"],
        operator: "tester",
        comment: ""
      });
    });
    await waitFor(() => {
      expect(toast.warning).toHaveBeenCalledWith(expect.stringContaining("成功 1 张、失败 1 张"));
    });
    // 失败的留在勾选项里让人重试，成功的清掉
    expect(await screen.findByText("已选 1 张")).toBeInTheDocument();
  });

  it("clears the whole selection when a batch succeeds completely", async () => {
    vi.mocked(listPolicyAdjustmentTickets).mockResolvedValue(
      pageResponse([PENDING_TICKET, PENDING_TICKET_2])
    );
    vi.mocked(batchApprovePolicyAdjustmentTickets).mockResolvedValue({
      targetStatus: "APPROVED",
      succeededTicketIds: ["pat-1", "pat-2"],
      failed: []
    });
    renderPage();
    await screen.findByText("user:preference:language");

    fireEvent.click(screen.getByRole("checkbox", { name: "全选待审批工单" }));
    fireEvent.click(await screen.findByRole("button", { name: "批量通过" }));
    fireEvent.click(await screen.findByRole("button", { name: "确认批量通过" }));

    await waitFor(() => {
      expect(toast.success).toHaveBeenCalledWith("批量操作完成，成功 2 张");
    });
    await waitFor(() => {
      expect(screen.queryByText(/已选 \d+ 张/)).not.toBeInTheDocument();
    });
  });

  it("blocks a batch reject without a comment before calling the backend", async () => {
    vi.mocked(listPolicyAdjustmentTickets).mockResolvedValue(pageResponse([PENDING_TICKET]));
    renderPage();
    await screen.findByText("user:preference:language");

    fireEvent.click(screen.getByRole("checkbox", { name: "全选待审批工单" }));
    fireEvent.click(await screen.findByRole("button", { name: "批量驳回" }));
    fireEvent.click(await screen.findByRole("button", { name: "确认批量驳回" }));

    await waitFor(() => {
      expect(screen.getByText(/批量驳回必须填写意见/)).toBeInTheDocument();
    });
    expect(batchRejectPolicyAdjustmentTickets).not.toHaveBeenCalled();
  });

  it("surfaces the rollback hint from the latest review without a backend flag", async () => {
    vi.mocked(listPolicyAdjustmentTickets).mockResolvedValue(
      pageResponse([APPLIED_TICKET], {
        "pat-3": {
          reviewId: "rev-1",
          ticketId: "pat-3",
          outcome: "REGRESSED",
          detail: "通过率由 82% 降至 74%"
        }
      })
    );
    renderPage();

    // 后端不序列化 rollbackRecommended（Jackson 对 record 只带组件），前端自行推导
    expect(await screen.findByText("建议回滚")).toBeInTheDocument();
  });

  it("loads review history into the drawer", async () => {
    vi.mocked(listPolicyAdjustmentTicketReviews).mockResolvedValue([
      {
        reviewId: "rev-1",
        ticketId: "pat-1",
        outcome: "VERIFIED_IMPROVED",
        detail: "通过率由 74% 升至 81%",
        baselinePassRate: 0.74,
        currentPassRate: 0.81,
        source: "manual",
        reviewedAt: "2026-09-16T08:00:00Z"
      }
    ]);
    renderPage();
    await screen.findByText("user:preference:language");

    fireEvent.click(screen.getByText("user:preference:language"));
    await screen.findByText("策略调整工单详情");

    expect(await screen.findByText("确认改进")).toBeInTheDocument();
    expect(screen.getByText("通过率由 74% 升至 81%")).toBeInTheDocument();
    expect(screen.getByText(/基线 74.0% → 当前 81.0%/)).toBeInTheDocument();
    expect(listPolicyAdjustmentTicketReviews).toHaveBeenCalledWith("pat-1");
  });
});
