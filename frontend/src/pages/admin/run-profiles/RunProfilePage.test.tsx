import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RunProfilePage } from "@/pages/admin/run-profiles/RunProfilePage";

const serviceMocks = vi.hoisted(() => ({
  activateRunProfile: vi.fn(),
  approveRunProfile: vi.fn(),
  checkRunProfileProductionGate: vi.fn(),
  createRunProfile: vi.fn(),
  deleteRunProfile: vi.fn(),
  getRunProfile: vi.fn(),
  getRunProfileAuditSummary: vi.fn(),
  getRunProfileRiskSummary: vi.fn(),
  listRunProfileExecutorEngines: vi.fn(),
  listRunProfiles: vi.fn(),
  resolveRunProfilePreview: vi.fn(),
  rejectRunProfile: vi.fn(),
  submitRunProfileApproval: vi.fn(),
  updateRunProfile: vi.fn()
}));

const toolMocks = vi.hoisted(() => ({
  listTools: vi.fn()
}));

vi.mock("@/services/runProfileService", () => ({
  activateRunProfile: serviceMocks.activateRunProfile,
  approveRunProfile: serviceMocks.approveRunProfile,
  checkRunProfileProductionGate: serviceMocks.checkRunProfileProductionGate,
  createRunProfile: serviceMocks.createRunProfile,
  deleteRunProfile: serviceMocks.deleteRunProfile,
  getRunProfile: serviceMocks.getRunProfile,
  getRunProfileAuditSummary: serviceMocks.getRunProfileAuditSummary,
  getRunProfileRiskSummary: serviceMocks.getRunProfileRiskSummary,
  listRunProfileExecutorEngines: serviceMocks.listRunProfileExecutorEngines,
  listRunProfiles: serviceMocks.listRunProfiles,
  resolveRunProfilePreview: serviceMocks.resolveRunProfilePreview,
  rejectRunProfile: serviceMocks.rejectRunProfile,
  submitRunProfileApproval: serviceMocks.submitRunProfileApproval,
  updateRunProfile: serviceMocks.updateRunProfile
}));

vi.mock("@/services/toolCatalogService", () => ({
  listTools: toolMocks.listTools
}));

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

describe("RunProfilePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    serviceMocks.listRunProfiles.mockResolvedValue([
      {
        id: 77,
        name: "Research AgentScope",
        description: "Long research tasks",
        roleCardId: 9,
        executorEngine: "agentscope",
        enabled: false,
        createTime: "2026-06-21T10:00:00"
      },
      {
        id: 88,
        name: "Kernel Default",
        description: "Conservative executor",
        executorEngine: "kernel",
        enabled: true
      }
    ]);
    serviceMocks.activateRunProfile.mockResolvedValue(undefined);
    serviceMocks.createRunProfile.mockResolvedValue(99);
    serviceMocks.deleteRunProfile.mockResolvedValue(undefined);
    serviceMocks.submitRunProfileApproval.mockResolvedValue(undefined);
    serviceMocks.approveRunProfile.mockResolvedValue(undefined);
    serviceMocks.rejectRunProfile.mockResolvedValue(undefined);
    serviceMocks.getRunProfileAuditSummary.mockResolvedValue({
      runProfileId: 77,
      approvalStatus: "APPROVED",
      riskLevel: "HIGH",
      runCount: 3,
      failureCount: 1,
      estimatedCost: 0.42,
      enabledToolCount: 2,
      highRiskToolCount: 1,
      highRiskToolIds: ["filesystem.read_file"]
    });
    serviceMocks.getRunProfile.mockResolvedValue({
      profile: {
        id: 77,
        name: "Research AgentScope",
        description: "Long research tasks",
        roleCardId: 9,
        executorEngine: "agentscope",
        executorConfigJson: "{\"studioTraceEnabled\":true}",
        modelConfigJson: "{\"model\":\"gpt-4.1-mini\"}",
        memoryScopeJson: "{\"longTerm\":true}",
        guardrailConfigJson: "{\"highRiskToolApproval\":true}"
      },
      toolBindings: [
        { toolId: "filesystem.read_file", provider: "MCP", enabled: true }
      ]
    });
    serviceMocks.listRunProfileExecutorEngines.mockResolvedValue(["kernel", "agentscope"]);
    serviceMocks.getRunProfileRiskSummary.mockResolvedValue({
      runProfileId: 77,
      riskLevel: "HIGH",
      riskCodes: ["EXECUTOR_AGENTSCOPE", "TOOL_MCP"],
      riskItems: [
        {
          code: "EXECUTOR_AGENTSCOPE",
          level: "MEDIUM",
          message: "AgentScope execution engine is enabled"
        },
        {
          code: "TOOL_MCP",
          level: "HIGH",
          message: "MCP tool is enabled: filesystem.read_file"
        }
      ]
    });
    serviceMocks.checkRunProfileProductionGate.mockResolvedValue({
      runProfileId: 77,
      passed: false,
      riskLevel: "HIGH",
      blockingCodes: ["APPROVAL_NOT_ENFORCED"],
      checkItems: [
        {
          code: "APPROVAL_NOT_ENFORCED",
          status: "BLOCK",
          message: "High-risk tool approval must be enabled before production"
        },
        {
          code: "AGENTSCOPE_NACOS_NAMESPACE_MISSING",
          status: "BLOCK",
          message: "AgentScope Nacos namespace is required before production"
        }
      ]
    });
    serviceMocks.resolveRunProfilePreview.mockResolvedValue({
      runProfileId: 77,
      roleCardId: 9,
      executorEngine: "agentscope",
      executorConfigJson: "{\"studioTraceEnabled\":true}",
      explicitToolAllowlist: true,
      toolIds: ["get_current_datetime"],
      mcpToolIds: ["filesystem.read_file"],
      a2aAgentIds: ["seahorse-researcher"]
    });
    serviceMocks.updateRunProfile.mockResolvedValue(77);
    toolMocks.listTools.mockResolvedValue({
      records: [
        {
          toolId: "get_current_datetime",
          name: "Clock",
          provider: "BUILT_IN",
          enabled: true,
          riskLevel: "LOW"
        },
        {
          toolId: "filesystem.read_file",
          name: "Filesystem Read",
          provider: "MCP",
          enabled: true,
          riskLevel: "HIGH"
        }
      ],
      total: 2,
      current: 1,
      pages: 1
    });
  });

  it("renders run profile governance fields from the list API", async () => {
    render(<RunProfilePage />);

    expect(await screen.findByText("Research AgentScope")).toBeInTheDocument();
    expect(screen.getByText("Long research tasks")).toBeInTheDocument();
    expect(screen.getByText("agentscope")).toBeInTheDocument();
    expect(screen.getByText("角色卡 9")).toBeInTheDocument();
    expect(screen.getByText("Kernel Default")).toBeInTheDocument();
  });

  it("activates a selected run profile and refreshes the list", async () => {
    render(<RunProfilePage />);

    await screen.findByText("Research AgentScope");
    fireEvent.click(screen.getByRole("button", { name: "设为默认" }));

    await waitFor(() => {
      expect(serviceMocks.activateRunProfile).toHaveBeenCalledWith(77);
      expect(serviceMocks.listRunProfiles).toHaveBeenCalledTimes(2);
    });
  });

  it("creates a run profile with selected tool bindings from the management form", async () => {
    render(<RunProfilePage />);

    await screen.findByText("Research AgentScope");
    fireEvent.click(screen.getByRole("button", { name: "新建方案" }));
    expect(document.querySelector('label[for="run-profile-executor-engine"]')).toBeInTheDocument();
    expect(document.querySelector("#run-profile-executor-engine")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("名称"), { target: { value: "Tool Safe Profile" } });
    fireEvent.change(screen.getByLabelText("描述"), { target: { value: "No high risk tools" } });
    fireEvent.change(screen.getByLabelText("执行引擎"), { target: { value: "agentscope" } });
    fireEvent.change(screen.getByLabelText("角色卡 ID"), { target: { value: "12" } });
    fireEvent.change(screen.getByLabelText("Executor Config JSON"), {
      target: { value: "{\"studioTraceEnabled\":true,\"nacosNamespace\":\"public\"}" }
    });
    fireEvent.change(screen.getByLabelText("Model Config JSON"), {
      target: { value: "{\"model\":\"gpt-4.1-mini\",\"temperature\":0.3}" }
    });
    fireEvent.change(screen.getByLabelText("Memory Scope JSON"), {
      target: { value: "{\"longTerm\":true,\"knowledgeBaseIds\":[\"kb-001\"]}" }
    });
    fireEvent.change(screen.getByLabelText("Guardrail Config JSON"), {
      target: { value: "{\"highRiskToolApproval\":true}" }
    });
    fireEvent.click(await screen.findByLabelText("Clock"));
    fireEvent.click(screen.getByRole("button", { name: "保存方案" }));

    await waitFor(() => {
      expect(serviceMocks.createRunProfile).toHaveBeenCalledWith(expect.objectContaining({
        name: "Tool Safe Profile",
        description: "No high risk tools",
        executorEngine: "agentscope",
        roleCardId: 12,
        executorConfig: { studioTraceEnabled: true, nacosNamespace: "public" },
        modelConfig: { model: "gpt-4.1-mini", temperature: 0.3 },
        memoryScope: { longTerm: true, knowledgeBaseIds: ["kb-001"] },
        guardrailConfig: { highRiskToolApproval: true },
        toolBindings: [
          { toolId: "get_current_datetime", provider: "BUILT_IN", enabled: true }
        ]
      }));
      expect(serviceMocks.listRunProfiles).toHaveBeenCalledTimes(2);
    });
  });

  it("only renders executor engines returned by backend capabilities", async () => {
    serviceMocks.listRunProfileExecutorEngines.mockResolvedValue(["kernel"]);
    render(<RunProfilePage />);

    await screen.findByText("Research AgentScope");
    fireEvent.click(screen.getByRole("button", { name: "新建方案" }));
    await screen.findByLabelText("Clock");

    const selector = screen.getByLabelText("执行引擎");
    expect(selector).toHaveTextContent("kernel");
    expect(selector).not.toHaveTextContent("agentscope");
  });

  it("loads existing tool bindings before updating an existing run profile", async () => {
    render(<RunProfilePage />);

    await screen.findByText("Research AgentScope");
    fireEvent.click(screen.getAllByRole("button", { name: "编辑" })[0]);

    await waitFor(() => {
      expect(serviceMocks.getRunProfile).toHaveBeenCalledWith(77);
    });
    expect(screen.getByLabelText("Filesystem Read")).toBeChecked();

    fireEvent.change(screen.getByLabelText("名称"), { target: { value: "Research AgentScope v2" } });
    fireEvent.change(screen.getByLabelText("执行引擎"), { target: { value: "kernel" } });
    fireEvent.click(screen.getByRole("button", { name: "保存方案" }));

    await waitFor(() => {
      expect(serviceMocks.updateRunProfile).toHaveBeenCalledWith(77, expect.objectContaining({
        name: "Research AgentScope v2",
        executorEngine: "kernel",
        roleCardId: 9,
        toolBindings: [
          { toolId: "filesystem.read_file", provider: "MCP", enabled: true }
        ]
      }));
      expect(serviceMocks.listRunProfiles).toHaveBeenCalledTimes(2);
    });
  });

  it("previews the resolved runtime context for a run profile", async () => {
    render(<RunProfilePage />);

    await screen.findByText("Research AgentScope");
    fireEvent.click(screen.getAllByRole("button", { name: "预览" })[0]);

    expect(await screen.findByText("生效上下文预览")).toBeInTheDocument();
    expect(serviceMocks.resolveRunProfilePreview).toHaveBeenCalledWith(77);
    expect(serviceMocks.getRunProfileRiskSummary).toHaveBeenCalledWith(77);
    expect(screen.getAllByText("HIGH").length).toBeGreaterThan(0);
    expect(screen.getByText("AgentScope execution engine is enabled")).toBeInTheDocument();
    expect(screen.getByText("MCP tool is enabled: filesystem.read_file")).toBeInTheDocument();
    expect(screen.getByText("filesystem.read_file")).toBeInTheDocument();
    expect(screen.getByText("seahorse-researcher")).toBeInTheDocument();
    expect(screen.getByText("显式工具白名单")).toBeInTheDocument();
  });

  it("checks the production gate for a run profile", async () => {
    render(<RunProfilePage />);

    await screen.findByText("Research AgentScope");
    fireEvent.click(screen.getAllByRole("button", { name: "发布门禁" })[0]);

    expect(await screen.findByText("生产门禁")).toBeInTheDocument();
    expect(serviceMocks.checkRunProfileProductionGate).toHaveBeenCalledWith(77);
    expect(screen.getByText("APPROVAL_NOT_ENFORCED")).toBeInTheDocument();
    expect(screen.getByText("High-risk tool approval must be enabled before production")).toBeInTheDocument();
    expect(screen.getByText("AGENTSCOPE_NACOS_NAMESPACE_MISSING")).toBeInTheDocument();
  });

  it("runs governance approval actions and loads audit summary", async () => {
    render(<RunProfilePage />);

    await screen.findByText("Research AgentScope");
    fireEvent.click(screen.getByRole("button", { name: "submit-approval-77" }));
    fireEvent.click(screen.getByRole("button", { name: "approve-run-profile-77" }));
    fireEvent.click(screen.getByRole("button", { name: "reject-run-profile-77" }));
    fireEvent.click(screen.getByRole("button", { name: "audit-summary-77" }));

    await waitFor(() => {
      expect(serviceMocks.submitRunProfileApproval).toHaveBeenCalledWith(77, "submit from Run Profile governance panel");
      expect(serviceMocks.approveRunProfile).toHaveBeenCalledWith(77, "approved from Run Profile governance panel");
      expect(serviceMocks.rejectRunProfile).toHaveBeenCalledWith(77, "rejected from Run Profile governance panel");
      expect(serviceMocks.getRunProfileAuditSummary).toHaveBeenCalledWith(77);
    });
    expect(await screen.findByText("filesystem.read_file")).toBeInTheDocument();
    expect(screen.getByText("APPROVED")).toBeInTheDocument();
  });

  it("deletes a run profile after confirmation and refreshes the list", async () => {
    render(<RunProfilePage />);

    await screen.findByText("Research AgentScope");
    fireEvent.click(screen.getAllByRole("button", { name: "删除" })[0]);

    await waitFor(() => {
      expect(window.confirm).toHaveBeenCalled();
      expect(serviceMocks.deleteRunProfile).toHaveBeenCalledWith(77);
      expect(serviceMocks.listRunProfiles).toHaveBeenCalledTimes(2);
    });
  });
});
