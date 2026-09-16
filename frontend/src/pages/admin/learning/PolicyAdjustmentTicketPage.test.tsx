import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { PolicyAdjustmentTicketPage } from "./PolicyAdjustmentTicketPage";
import {
  generatePolicyAdjustmentTickets,
  listPolicyAdjustmentTickets,
  rejectPolicyAdjustmentTicket
} from "@/services/policyAdjustmentTicketService";

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
    info: vi.fn()
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
    rollBackPolicyAdjustmentTicket: vi.fn()
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

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/admin/policy-tickets"]}>
      <PolicyAdjustmentTicketPage />
    </MemoryRouter>
  );
}

describe("PolicyAdjustmentTicketPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listPolicyAdjustmentTickets).mockResolvedValue({
      records: [PENDING_TICKET],
      total: 1,
      current: 1,
      pages: 1
    });
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
});
