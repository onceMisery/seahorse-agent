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
import { backendEndpointManifest } from "@/services/backendEndpointManifest";
import * as agentDefinitionService from "@/services/agentDefinitionService";
import * as skillService from "@/services/skillService";
import { createSecret } from "@/services/securityGovernanceService";
import {
  compareStrategies,
  evaluateDataset
} from "@/services/ragEvaluationService";
import {
  createQuotaPolicy,
  evaluateQuotaDecision
} from "@/services/securityGovernanceService";
import {
  createSandboxSession,
  executeInSandbox
} from "@/services/sandboxService";

const mockedApi = vi.mocked(api);

const backendEndpoints = new Set(
  backendEndpointManifest.map((endpoint) => `${endpoint.method} ${endpoint.path}`)
);

function normalizePath(path: string) {
  return path
    .replace(/\$\{encodeURIComponent\([^}]+\)\}/g, "{}")
    .replace(/\$\{[^}]+\}/g, "{}")
    .replace(/\{[^}]+\}/g, "{}")
    .replace(/\/(?:agent|run|version|kb|doc|item|approval|tool|policy|dataset)-[A-Za-z0-9_-]+/g, "/{}");
}

describe("frontend capability service contracts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("keeps critical frontend endpoints present in the backend manifest", () => {
    expect(backendEndpoints).toContain("GET /api/features");
    expect(backendEndpoints).toContain("GET /api/agents");
    expect(backendEndpoints).toContain("POST /api/agents/{}/publish");
    expect(backendEndpoints).toContain("GET /api/skills");
    expect(backendEndpoints).toContain("GET /api/skills/{}");
    expect(backendEndpoints).toContain("POST /api/skills/custom");
    expect(backendEndpoints).toContain("PUT /api/skills/custom/{}");
    expect(backendEndpoints).toContain("DELETE /api/skills/custom/{}");
    expect(backendEndpoints).toContain("GET /api/skills/custom/{}/history");
    expect(backendEndpoints).toContain("POST /api/skills/custom/{}/rollback");
    expect(backendEndpoints).toContain("POST /api/skills/install");
    expect(backendEndpoints).toContain("POST /api/skills/{}/enable");
    expect(backendEndpoints).toContain("POST /api/skills/{}/disable");
    expect(backendEndpoints).toContain("GET /api/agents/{}/skills");
    expect(backendEndpoints).toContain("PUT /api/agents/{}/skills");
    expect(backendEndpoints).toContain("GET /api/agents/{}/skills/snapshot");
    expect(backendEndpoints).toContain("GET /api/agent-runs/{}/snapshot");
    expect(backendEndpoints).toContain("GET /admin/ai-config");
    expect(backendEndpoints).toContain("POST /admin/ai-config");
    expect(backendEndpoints).toContain("GET /admin/dashboard/overview");
    expect(backendEndpoints).toContain("GET /admin/dashboard/performance");
    expect(backendEndpoints).toContain("GET /admin/dashboard/trends");
  });

  it("publishes agents with the backend publish payload", async () => {
    await agentDefinitionService.publishAgent("agent-1", {
      instructions: "be useful",
      toolSetJson: "[]",
      skillSetJson: "{\"version\":1,\"skills\":[]}",
      modelConfigJson: "{}",
      memoryConfigJson: "{}",
      guardrailConfigJson: "{}",
      changeSummary: "initial publish"
    });

    expect(mockedApi.post).toHaveBeenCalledWith(
      "/api/agents/agent-1/publish",
      {
        instructions: "be useful",
        toolSetJson: "[]",
        skillSetJson: "{\"version\":1,\"skills\":[]}",
        modelConfigJson: "{}",
        memoryConfigJson: "{}",
        guardrailConfigJson: "{}",
        changeSummary: "initial publish"
      }
    );
  });

  it("manages skills with backend skill endpoints", async () => {
    await skillService.listSkills({ current: 1, size: 20, keyword: "research" });
    await skillService.getSkill("research");
    await skillService.createCustomSkill({ content: "---\nname: research\n---\nbody" });
    await skillService.updateCustomSkill("research", { content: "---\nname: research\n---\nupdated" });
    await skillService.installSkill({ content: "---\nname: research\n---\nbody" });
    await skillService.enableSkill("research");
    await skillService.disableSkill("research");
    await skillService.listSkillHistory("research");
    await skillService.rollbackCustomSkill("research", { revisionId: "rev-1" });
    await skillService.deleteCustomSkill("research");
    await skillService.replaceAgentSkillBindings("agent-1", {
      bindings: [{ skillName: "research", revisionId: "rev-1", injectMode: "METADATA_AND_BODY" }]
    });
    await skillService.getAgentSkillSnapshot("agent-1");

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, "/api/skills", {
      params: { current: 1, size: 20, keyword: "research" }
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, "/api/skills/research", {
      params: { tenantId: undefined }
    });
    expect(mockedApi.post).toHaveBeenCalledWith("/api/skills/custom", {
      content: "---\nname: research\n---\nbody"
    });
    expect(mockedApi.put).toHaveBeenCalledWith("/api/skills/custom/research", {
      content: "---\nname: research\n---\nupdated"
    });
    expect(mockedApi.post).toHaveBeenCalledWith("/api/skills/install", {
      content: "---\nname: research\n---\nbody"
    });
    expect(mockedApi.post).toHaveBeenCalledWith("/api/skills/research/enable", {
      tenantId: undefined
    });
    expect(mockedApi.post).toHaveBeenCalledWith("/api/skills/research/disable", {
      tenantId: undefined
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(3, "/api/skills/custom/research/history", {
      params: { tenantId: undefined }
    });
    expect(mockedApi.post).toHaveBeenCalledWith("/api/skills/custom/research/rollback", {
      revisionId: "rev-1"
    });
    expect(mockedApi.delete).toHaveBeenCalledWith("/api/skills/custom/research", {
      params: { tenantId: undefined }
    });
    expect(mockedApi.put).toHaveBeenCalledWith("/api/agents/agent-1/skills", {
      bindings: [{ skillName: "research", revisionId: "rev-1", injectMode: "METADATA_AND_BODY" }]
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(4, "/api/agents/agent-1/skills/snapshot", {
      params: { tenantId: undefined }
    });
  });

  it("does not expose a missing agent versions endpoint", () => {
    expect("getAgentVersions" in agentDefinitionService).toBe(false);
  });

  it("rolls back agents with the backend rollback payload", async () => {
    await agentDefinitionService.rollbackAgentVersion("agent-1", "version-1", {
      tenantId: "tenant-a",
      operator: "admin",
      reasonCode: "OPERATOR_REQUESTED",
      comment: "restore stable version"
    });

    expect(mockedApi.post).toHaveBeenCalledWith(
      "/api/agents/agent-1/versions/version-1/rollback",
      {
        tenantId: "tenant-a",
        operator: "admin",
        reasonCode: "OPERATOR_REQUESTED",
        comment: "restore stable version"
      }
    );
  });

  it("creates secrets with the credential vault contract", async () => {
    await createSecret({
      tenantId: "tenant-a",
      secretValue: "sk-test",
      metadataJson: "{\"name\":\"primary\"}"
    });

    expect(mockedApi.post).toHaveBeenCalledWith("/api/secrets", {
      tenantId: "tenant-a",
      secretValue: "sk-test",
      metadataJson: "{\"name\":\"primary\"}"
    });
  });

  it("runs retrieval evaluations with strategyName/topK/options", async () => {
    await evaluateDataset("kb-1", "dataset-1", {
      strategyName: "hybrid",
      topK: 10,
      options: { rerank: true }
    });

    expect(mockedApi.post).toHaveBeenCalledWith(
      "/knowledge-base/kb-1/retrieval-evaluation-datasets/dataset-1/evaluate",
      {
        strategyName: "hybrid",
        topK: 10,
        options: { rerank: true }
      }
    );
  });

  it("compares retrieval datasets with baselineStrategyName and strategy list", async () => {
    await compareStrategies("kb-1", "dataset-1", {
      baselineStrategyName: "vector",
      topK: 5,
      strategies: [
        { strategyName: "vector", topK: 5, options: {} },
        { strategyName: "hybrid", topK: 5, options: { rerank: true } }
      ]
    });

    expect(mockedApi.post).toHaveBeenCalledWith(
      "/knowledge-base/kb-1/retrieval-evaluation-datasets/dataset-1/compare",
      {
        baselineStrategyName: "vector",
        topK: 5,
        strategies: [
          { strategyName: "vector", topK: 5, options: {} },
          { strategyName: "hybrid", topK: 5, options: { rerank: true } }
        ]
      }
    );
  });

  it("uses quota backend field names", async () => {
    await createQuotaPolicy({
      policyId: "policy-1",
      tenantId: "tenant-a",
      scope: "AGENT",
      subjectId: "agent-1",
      status: "ACTIVE",
      tokenLimit: 1000,
      callLimit: 20,
      costLimit: 3.5,
      warnRatio: 0.8
    });
    await evaluateQuotaDecision({
      tenantId: "tenant-a",
      agentId: "agent-1",
      userId: "user-1",
      toolId: "tool-1",
      modelId: "model-1",
      runId: "run-1",
      riskLevel: "LOW",
      tokens: 10,
      calls: 1,
      cost: 0.2
    });

    expect(mockedApi.post).toHaveBeenNthCalledWith(1, "/api/quotas/policies", {
      policyId: "policy-1",
      tenantId: "tenant-a",
      scope: "AGENT",
      subjectId: "agent-1",
      status: "ACTIVE",
      tokenLimit: 1000,
      callLimit: 20,
      costLimit: 3.5,
      warnRatio: 0.8
    });
    expect(mockedApi.post).toHaveBeenNthCalledWith(2, "/api/quotas/decisions:evaluate", {
      tenantId: "tenant-a",
      agentId: "agent-1",
      userId: "user-1",
      toolId: "tool-1",
      modelId: "model-1",
      runId: "run-1",
      riskLevel: "LOW",
      tokens: 10,
      calls: 1,
      cost: 0.2
    });
  });

  it("creates and executes sandbox sessions with backend required fields", async () => {
    await createSandboxSession();
    await executeInSandbox("session-1", {
      input: "{\"hello\":\"world\"}"
    });

    expect(mockedApi.post).toHaveBeenNthCalledWith(1, "/api/sandbox/sessions", {
      tenantId: "default",
      runId: expect.stringMatching(/^sandbox-\d+-[a-z0-9]+$/),
      runtimeType: "CODE_INTERPRETER",
      networkRequested: false,
      requestedHosts: []
    });
    expect(mockedApi.post).toHaveBeenNthCalledWith(2, "/api/sandbox/sessions/session-1/execute", {
      input: "{\"hello\":\"world\"}",
      networkRequested: false,
      requestedHosts: []
    });
  });
});
