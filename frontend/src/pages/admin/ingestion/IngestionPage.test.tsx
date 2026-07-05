import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { IngestionPage } from "@/pages/admin/ingestion/IngestionPage";
import {
  getIngestionPipelineGateResult,
  getIngestionPipelines,
  getIngestionTasks
} from "@/services/ingestionService";

const ingestionMocks = vi.hoisted(() => ({
  createIngestionPipeline: vi.fn(),
  createIngestionTask: vi.fn(),
  deleteIngestionPipeline: vi.fn(),
  getIngestionPipeline: vi.fn(),
  getIngestionPipelineGateResult: vi.fn(),
  getIngestionPipelines: vi.fn(),
  getIngestionTask: vi.fn(),
  getIngestionTaskNodes: vi.fn(),
  getIngestionTasks: vi.fn(),
  updateIngestionPipeline: vi.fn(),
  uploadIngestionTask: vi.fn()
}));

vi.mock("@/services/ingestionService", async () => {
  const actual = await vi.importActual<typeof import("@/services/ingestionService")>(
    "@/services/ingestionService"
  );
  return {
    ...actual,
    createIngestionPipeline: ingestionMocks.createIngestionPipeline,
    createIngestionTask: ingestionMocks.createIngestionTask,
    deleteIngestionPipeline: ingestionMocks.deleteIngestionPipeline,
    getIngestionPipeline: ingestionMocks.getIngestionPipeline,
    getIngestionPipelineGateResult: ingestionMocks.getIngestionPipelineGateResult,
    getIngestionPipelines: ingestionMocks.getIngestionPipelines,
    getIngestionTask: ingestionMocks.getIngestionTask,
    getIngestionTaskNodes: ingestionMocks.getIngestionTaskNodes,
    getIngestionTasks: ingestionMocks.getIngestionTasks,
    updateIngestionPipeline: ingestionMocks.updateIngestionPipeline,
    uploadIngestionTask: ingestionMocks.uploadIngestionTask
  };
});

vi.mock("@/services/userService", () => ({
  fetchUserMap: vi.fn(() => Promise.resolve(new Map([["42", "Ada Operator"]]))),
  resolveUserName: (userMap: Map<string, string>, userId?: string | null) =>
    userId ? userMap.get(userId) || userId : "-"
}));

vi.mock("@/services/settingsService", () => ({
  getSystemSettings: vi.fn(() => Promise.resolve({
    upload: {
      maxFileSize: 50 * 1024 * 1024,
      maxRequestSize: 50 * 1024 * 1024
    }
  }))
}));

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

describe("IngestionPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getIngestionPipelines).mockResolvedValue({
      records: [
        {
          id: "pipeline-a",
          name: "Policy Import Pipeline",
          description: "Normalize policy documents",
          createdBy: "42",
          nodes: [
            {
              id: 1,
              nodeId: "fetch",
              nodeType: "fetcher"
            }
          ],
          updateTime: "2026-07-06T00:00:00Z"
        }
      ],
      total: 1,
      size: 10,
      current: 1,
      pages: 1
    });
    vi.mocked(getIngestionTasks).mockResolvedValue({
      records: [],
      total: 0,
      size: 10,
      current: 1,
      pages: 0
    });
    vi.mocked(getIngestionPipelineGateResult).mockResolvedValue({
      subjectType: "INGESTION_PIPELINE",
      subjectId: "pipeline-a",
      status: "FAIL",
      passed: false,
      blockingCodes: ["INGESTION_PIPELINE_INDEXER_PRESENT"],
      checkedAt: "2026-07-06T00:00:00Z",
      sourceType: "IngestionPipelineRecord",
      sourceId: "pipeline-a",
      items: [
        {
          code: "INGESTION_PIPELINE_INDEXER_PRESENT",
          status: "FAIL",
          message: "Pipeline must include an indexer node before production"
        }
      ]
    });
  });

  it("loads and renders an ingestion pipeline GateResult from the list row", async () => {
    render(
      <MemoryRouter initialEntries={["/admin/ingestion?tab=pipelines"]}>
        <IngestionPage />
      </MemoryRouter>
    );

    expect(await screen.findByText("Policy Import Pipeline")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Gate" }));

    await waitFor(() => {
      expect(getIngestionPipelineGateResult).toHaveBeenCalledWith("pipeline-a");
    });
    expect(await screen.findByText("Pipeline GateResult")).toBeInTheDocument();
    expect(screen.getByText("INGESTION_PIPELINE:pipeline-a")).toBeInTheDocument();
    expect(screen.getAllByText("FAIL").length).toBeGreaterThan(0);
    expect(screen.getAllByText("INGESTION_PIPELINE_INDEXER_PRESENT").length).toBeGreaterThan(0);
    expect(screen.getByText("IngestionPipelineRecord")).toBeInTheDocument();
    expect(screen.getByText("Pipeline must include an indexer node before production")).toBeInTheDocument();
  });
});
