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
import { createSecret } from "@/services/securityGovernanceService";
import {
  compareStrategies,
  evaluateDataset
} from "@/services/ragEvaluationService";
import {
  createQuotaPolicy,
  evaluateQuotaDecision
} from "@/services/securityGovernanceService";

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
    expect(backendEndpoints).toContain("GET /api/agent-runs/{}/snapshot");
  });

  it("publishes agents with the backend publish payload", async () => {
    await agentDefinitionService.publishAgent("agent-1", {
      instructions: "be useful",
      toolSetJson: "[]",
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
        modelConfigJson: "{}",
        memoryConfigJson: "{}",
        guardrailConfigJson: "{}",
        changeSummary: "initial publish"
      }
    );
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
});
