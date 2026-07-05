import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RetrievalDatasetDetailPage } from "./RetrievalDatasetDetailPage";
import {
  getDataset,
  getRetrievalComparisonGateResult,
  listEvaluationComparisons,
  listEvaluationRuns,
  listStrategyTemplates
} from "@/services/ragEvaluationService";

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

vi.mock("@/config/productMode", () => ({
  ADVANCED_ADMIN_FEATURES: {
    RAG_EVALUATION: "RAG_EVALUATION"
  },
  getAdvancedFeatureState: () => ({ enabled: true })
}));

vi.mock("@/services/ragEvaluationService", async () => {
  const actual = await vi.importActual<typeof import("@/services/ragEvaluationService")>(
    "@/services/ragEvaluationService"
  );
  return {
    ...actual,
    compareStrategies: vi.fn(),
    evaluateDataset: vi.fn(),
    getDataset: vi.fn(),
    getRetrievalComparisonGateResult: vi.fn(),
    listEvaluationComparisons: vi.fn(),
    listEvaluationRuns: vi.fn(),
    listStrategyTemplates: vi.fn(),
    promoteStrategyFromComparison: vi.fn()
  };
});

describe("RetrievalDatasetDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getDataset).mockResolvedValue({
      datasetId: "dataset-1",
      kbId: "kb-1",
      name: "Retrieval Regression"
    });
    vi.mocked(listEvaluationRuns).mockResolvedValue([]);
    vi.mocked(listStrategyTemplates).mockResolvedValue([
      {
        templateKey: "hybrid",
        name: "Hybrid",
        displayName: "Hybrid",
        options: { rerank: true }
      }
    ]);
    vi.mocked(listEvaluationComparisons).mockResolvedValue([
      {
        comparisonId: "comparison-1",
        baselineStrategyName: "vector",
        winnerStrategyName: "hybrid",
        status: "COMPLETED",
        baseHitRate: 0.5,
        candidateHitRate: 0.7,
        diffHitRate: 0.2
      }
    ]);
    vi.mocked(getRetrievalComparisonGateResult).mockResolvedValue({
      subjectType: "RAG_STRATEGY",
      subjectId: "comparison-1",
      status: "BLOCKED",
      passed: false,
      blockingCodes: ["RAG_RECALL_REGRESSED"],
      sourceType: "RETRIEVAL_EVALUATION_COMPARISON",
      sourceId: "comparison-1",
      checkedAt: "2026-07-06T06:10:00Z",
      items: [
        {
          code: "RAG_RECALL_REGRESSED",
          status: "FAIL",
          message: "Candidate recall must not regress before promotion"
        }
      ]
    });
  });

  it("opens RAG Strategy GateResult evidence from a comparison row", async () => {
    render(
      <MemoryRouter initialEntries={["/admin/rag-evaluation/kb-1/dataset-1"]}>
        <Routes>
          <Route path="/admin/rag-evaluation/:kbId/:datasetId" element={<RetrievalDatasetDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText("Retrieval Regression")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "策略对比" }));
    fireEvent.click(await screen.findByRole("button", { name: "rag-comparison-gate-result-comparison-1" }));

    await waitFor(() => {
      expect(getRetrievalComparisonGateResult).toHaveBeenCalledWith("kb-1", "dataset-1", "comparison-1");
    });
    expect(await screen.findByText("RAG Strategy GateResult")).toBeInTheDocument();
    expect(screen.getByText("RAG_STRATEGY:comparison-1")).toBeInTheDocument();
    expect(screen.getByText("RETRIEVAL_EVALUATION_COMPARISON")).toBeInTheDocument();
    expect(screen.getAllByText("RAG_RECALL_REGRESSED").length).toBeGreaterThan(0);
    expect(screen.getByText("Candidate recall must not regress before promotion")).toBeInTheDocument();
  });
});
