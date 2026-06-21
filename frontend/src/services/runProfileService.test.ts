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
  activateRunProfile,
  approveRunProfile,
  checkRunProfileProductionGate,
  createRunProfile,
  deleteRunProfile,
  getRunProfile,
  getRunProfileAuditSummary,
  getRunProfileRiskSummary,
  getAppliedRunProfileForConversation,
  listRunProfileExecutorEngines,
  listRunProfiles,
  applyRunProfileToConversation,
  resolveRunProfilePreview,
  rejectRunProfile,
  submitRunProfileApproval,
  updateRunProfile
} from "@/services/runProfileService";

describe("runProfileService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("lists and gets run profiles from the API endpoints", async () => {
    vi.mocked(api.get)
      .mockResolvedValueOnce([{ id: 1, name: "Research AgentScope", executorEngine: "agentscope" }])
      .mockResolvedValueOnce({
        profile: {
          id: 1,
          name: "Research AgentScope",
          executorEngine: "agentscope"
        },
        toolBindings: [{ toolId: "clock", provider: "BUILT_IN", enabled: true }]
      });

    await expect(listRunProfiles()).resolves.toEqual([
      { id: 1, name: "Research AgentScope", executorEngine: "agentscope" }
    ]);
    await expect(getRunProfile(1)).resolves.toEqual({
      profile: {
        id: 1,
        name: "Research AgentScope",
        executorEngine: "agentscope"
      },
      toolBindings: [{ toolId: "clock", provider: "BUILT_IN", enabled: true }]
    });

    expect(api.get).toHaveBeenCalledWith("/api/run-profiles");
    expect(api.get).toHaveBeenCalledWith("/api/run-profiles/1");
  });

  it("resolves a run profile preview from the API endpoint", async () => {
    vi.mocked(api.post).mockResolvedValueOnce({
      runProfileId: 12,
      roleCardId: 9,
      executorEngine: "agentscope",
      explicitToolAllowlist: true,
      toolIds: ["get_current_datetime"],
      mcpToolIds: ["filesystem.read_file"],
      a2aAgentIds: ["seahorse-researcher"]
    });

    await expect(resolveRunProfilePreview(12)).resolves.toEqual({
      runProfileId: 12,
      roleCardId: 9,
      executorEngine: "agentscope",
      explicitToolAllowlist: true,
      toolIds: ["get_current_datetime"],
      mcpToolIds: ["filesystem.read_file"],
      a2aAgentIds: ["seahorse-researcher"]
    });

    expect(api.post).toHaveBeenCalledWith("/api/run-profiles/12/resolve-preview");
  });

  it("lists supported executor engines from the API endpoint", async () => {
    vi.mocked(api.get).mockResolvedValueOnce(["kernel", "agentscope"]);

    await expect(listRunProfileExecutorEngines()).resolves.toEqual(["kernel", "agentscope"]);

    expect(api.get).toHaveBeenCalledWith("/api/run-profiles/executor-engines");
  });

  it("gets a run profile risk summary from the API endpoint", async () => {
    const summary = {
      runProfileId: 12,
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
    };
    vi.mocked(api.get).mockResolvedValueOnce(summary);

    await expect(getRunProfileRiskSummary(12)).resolves.toEqual(summary);

    expect(api.get).toHaveBeenCalledWith("/api/run-profiles/12/risk-summary");
  });

  it("checks a run profile production gate from the API endpoint", async () => {
    const check = {
      runProfileId: 12,
      passed: false,
      riskLevel: "HIGH",
      blockingCodes: ["APPROVAL_NOT_ENFORCED"],
      checkItems: [
        {
          code: "APPROVAL_NOT_ENFORCED",
          status: "BLOCK",
          message: "High-risk tool approval must be enabled before production"
        }
      ]
    };
    vi.mocked(api.post).mockResolvedValueOnce(check);

    await expect(checkRunProfileProductionGate(12)).resolves.toEqual(check);

    expect(api.post).toHaveBeenCalledWith("/api/run-profiles/12/production-gate/check");
  });

  it("runs run profile governance actions from the API endpoints", async () => {
    const audit = {
      runProfileId: 12,
      approvalStatus: "APPROVED",
      riskLevel: "HIGH",
      runCount: 3,
      failureCount: 1,
      estimatedCost: 0.42,
      enabledToolCount: 2,
      highRiskToolCount: 1,
      highRiskToolIds: ["filesystem.read_file"]
    };
    vi.mocked(api.post)
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce(undefined);
    vi.mocked(api.get).mockResolvedValueOnce(audit);

    await submitRunProfileApproval(12, "request production share");
    await approveRunProfile(12, "approved");
    await rejectRunProfile(12, "narrow tools");
    await expect(getRunProfileAuditSummary(12)).resolves.toEqual(audit);

    expect(api.post).toHaveBeenCalledWith("/api/run-profiles/12/submit-approval", {
      comment: "request production share"
    });
    expect(api.post).toHaveBeenCalledWith("/api/run-profiles/12/approve", {
      comment: "approved"
    });
    expect(api.post).toHaveBeenCalledWith("/api/run-profiles/12/reject", {
      comment: "narrow tools"
    });
    expect(api.get).toHaveBeenCalledWith("/api/run-profiles/12/audit-summary");
  });

  it("applies a run profile to a conversation", async () => {
    vi.mocked(api.post).mockResolvedValueOnce({
      runProfileId: 12,
      roleCardId: 9,
      executorEngine: "agentscope",
      explicitToolAllowlist: true,
      toolIds: ["get_current_datetime"],
      mcpToolIds: [],
      a2aAgentIds: []
    });

    await expect(applyRunProfileToConversation("conversation/1", 12)).resolves.toEqual({
      runProfileId: 12,
      roleCardId: 9,
      executorEngine: "agentscope",
      explicitToolAllowlist: true,
      toolIds: ["get_current_datetime"],
      mcpToolIds: [],
      a2aAgentIds: []
    });

    expect(api.post).toHaveBeenCalledWith("/api/conversations/conversation%2F1/run-profile/12/apply");
  });

  it("gets the run profile applied to a conversation", async () => {
    vi.mocked(api.get).mockResolvedValueOnce({
      profile: { id: 12, name: "Research AgentScope", executorEngine: "agentscope" },
      toolBindings: [{ toolId: "clock", provider: "BUILT_IN", enabled: true }]
    });

    await expect(getAppliedRunProfileForConversation("conversation/1")).resolves.toEqual({
      profile: { id: 12, name: "Research AgentScope", executorEngine: "agentscope" },
      toolBindings: [{ toolId: "clock", provider: "BUILT_IN", enabled: true }]
    });

    expect(api.get).toHaveBeenCalledWith("/api/conversations/conversation%2F1/run-profile");
  });

  it("creates, updates, activates, and deletes run profiles", async () => {
    const request = {
      name: "Research AgentScope",
      description: "Long research",
      roleCardId: 9,
      executorEngine: "agentscope",
      executorConfig: { studioTraceEnabled: true },
      modelConfig: { model: "gpt-4.1-mini" },
      memoryScope: { longTerm: true },
      guardrailConfig: { highRiskToolApproval: true },
      toolBindings: [{ toolId: "filesystem.read_file", provider: "MCP", enabled: true }]
    };
    vi.mocked(api.post).mockResolvedValueOnce(12).mockResolvedValueOnce(undefined);
    vi.mocked(api.put).mockResolvedValueOnce(12);
    vi.mocked(api.delete).mockResolvedValue(undefined);

    await expect(createRunProfile(request)).resolves.toBe(12);
    await expect(updateRunProfile(12, request)).resolves.toBe(12);
    await activateRunProfile(12);
    await deleteRunProfile(12);

    expect(api.post).toHaveBeenCalledWith("/api/run-profiles", request);
    expect(api.put).toHaveBeenCalledWith("/api/run-profiles/12", request);
    expect(api.post).toHaveBeenCalledWith("/api/run-profiles/12/activate");
    expect(api.delete).toHaveBeenCalledWith("/api/run-profiles/12");
  });
});
