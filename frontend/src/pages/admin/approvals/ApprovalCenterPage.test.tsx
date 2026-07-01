import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApprovalCenterPage } from "./ApprovalCenterPage";
import { getApproval, listApprovals } from "@/services/approvalService";

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

vi.mock("@/config/productMode", () => ({
  ADVANCED_ADMIN_FEATURES: {
    AGENT_RUN_MANAGEMENT: "AGENT_RUN_MANAGEMENT"
  },
  getAdvancedFeatureState: () => ({ enabled: true })
}));

vi.mock("@/services/approvalService", async () => {
  const actual = await vi.importActual<typeof import("@/services/approvalService")>(
    "@/services/approvalService"
  );
  return {
    ...actual,
    listApprovals: vi.fn(),
    getApproval: vi.fn(),
    approveApprovalRequest: vi.fn(),
    rejectApprovalRequest: vi.fn(),
    modifyApprovalRequest: vi.fn()
  };
});

describe("ApprovalCenterPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listApprovals).mockResolvedValue({
      records: [],
      total: 0,
      current: 1,
      pages: 1
    });
  });

  it("opens a direct-linked approval drawer from the approvalId query parameter", async () => {
    vi.mocked(getApproval).mockResolvedValue({
      approvalId: "approval:mcp-diagnostic",
      runId: "mcp-server-test:local-echo",
      agentId: "legacy-react-agent",
      toolId: "echo",
      toolName: "echo",
      status: "PENDING",
      riskLevel: "HIGH",
      argumentsPreviewJson: "{\"argumentKeys\":[\"text\"],\"argumentCount\":1}",
      submittedBy: "mcp-server-test",
      createTime: "2026-07-02T00:00:00Z"
    });

    render(
      <MemoryRouter initialEntries={["/admin/approvals?approvalId=approval%3Amcp-diagnostic"]}>
        <ApprovalCenterPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(getApproval).toHaveBeenCalledWith("approval:mcp-diagnostic");
    });

    expect(await screen.findByText("审批详情")).toBeInTheDocument();
    expect(screen.getByText("approval:mcp-diagnostic")).toBeInTheDocument();
    expect(screen.getByText("mcp-server-test:local-echo")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "通过" })).toBeInTheDocument();
  });
});
