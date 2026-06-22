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
import { queryAuditLogs } from "@/services/adminService";
import {
  getAiInfraCostUsageAggregate,
  getAiInfraSreHealth,
  getFeedbackEvaluationCandidates
} from "@/services/aiInfraService";
import { listApprovals } from "@/services/approvalService";
import { aggregateCostUsage, listAuditEvents } from "@/services/auditCostService";
import { getActiveSubscription, listBills, listPlans } from "@/services/billingService";
import { listMcpServers } from "@/services/mcpServerService";
import { listConnectors } from "@/services/openApiConnectorService";
import { listAclRules, listAccessDecisions } from "@/services/securityGovernanceService";
import { listSkills } from "@/services/skillService";
import { listToolInvocations, listTools } from "@/services/toolCatalogService";

const notFound = { response: { status: 404 } };

describe("admin optional endpoint fallbacks", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("falls back to empty page results for optional admin list endpoints returning 404", async () => {
    vi.mocked(api.get).mockRejectedValue(notFound);

    await expect(listSkills({ current: 2, size: 20 })).resolves.toMatchObject({
      records: [],
      total: 0,
      current: 2,
      size: 20
    });
    await expect(listApprovals({ current: 3, size: 5 })).resolves.toMatchObject({
      records: [],
      total: 0,
      current: 3,
      size: 5
    });
    await expect(listTools({ current: 4, size: 6 })).resolves.toMatchObject({
      records: [],
      total: 0,
      current: 4,
      size: 6
    });
    await expect(listToolInvocations({ current: 5, size: 7 })).resolves.toMatchObject({
      records: [],
      total: 0,
      current: 5,
      size: 7
    });
    await expect(listConnectors({ current: 6, size: 8 })).resolves.toMatchObject({
      records: [],
      total: 0,
      current: 6,
      size: 8
    });
    await expect(listAclRules({ current: 7, size: 9 })).resolves.toMatchObject({
      records: [],
      total: 0,
      current: 7,
      size: 9
    });
    await expect(listAccessDecisions({ current: 8, size: 10 })).resolves.toMatchObject({
      records: [],
      total: 0,
      current: 8,
      size: 10
    });
    await expect(listAuditEvents({ current: 9, size: 11 })).resolves.toMatchObject({
      records: [],
      total: 0,
      current: 9,
      size: 11
    });
    await expect(getFeedbackEvaluationCandidates({ current: 10, size: 12 })).resolves.toMatchObject({
      records: [],
      total: 0,
      current: 10,
      size: 12
    });
  });

  it("falls back to empty scalar results for optional admin summary endpoints returning 404", async () => {
    vi.mocked(api.get).mockRejectedValue(notFound);

    await expect(listPlans()).resolves.toEqual([]);
    await expect(getActiveSubscription()).resolves.toBeNull();
    await expect(listBills()).resolves.toEqual([]);
    await expect(queryAuditLogs({ page: 1, size: 20 })).resolves.toEqual([]);
    await expect(listMcpServers()).resolves.toEqual([]);
    await expect(getAiInfraSreHealth()).resolves.toMatchObject({ status: "WARN", items: [] });
    await expect(aggregateCostUsage({ groupBy: "agent" })).resolves.toMatchObject({
      totalCost: 0,
      totalTokens: 0,
      totalCalls: 0
    });
    await expect(getAiInfraCostUsageAggregate({ tenantId: "tenant-default" })).resolves.toMatchObject({
      tenantId: "tenant-default",
      totalTokens: 0,
      totalCalls: 0,
      totalCost: 0
    });
  });

  it("also treats disabled MCP list status as an empty optional capability", async () => {
    vi.mocked(api.get).mockRejectedValue({ response: { status: 409 } });

    await expect(listMcpServers()).resolves.toEqual([]);
  });

  it("does not hide non-404 admin list failures", async () => {
    vi.mocked(api.get).mockRejectedValue({ response: { status: 500 } });

    await expect(listTools({ current: 1, size: 10 })).rejects.toMatchObject({
      response: { status: 500 }
    });
  });
});
