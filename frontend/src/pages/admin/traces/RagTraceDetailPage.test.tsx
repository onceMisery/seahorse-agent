import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

vi.mock("@/services/ragTraceService", () => ({
  getRagTraceDetail: vi.fn()
}));

import { RagTraceDetailPage } from "@/pages/admin/traces/RagTraceDetailPage";
import { getRagTraceDetail, type RagTraceDetail } from "@/services/ragTraceService";

describe("RagTraceDetailPage", () => {
  it("renders agent-grade trace context beyond the waterfall", async () => {
    const detail: RagTraceDetail = {
      run: {
        traceId: "trace-1",
        traceName: "stream-chat",
        entryMethod: "KernelChatInboundService#streamChat",
        conversationId: "conversation-1",
        taskId: "task-1",
        username: "admin",
        status: "SUCCESS",
        durationMs: 3200,
        startTime: "2026-06-06T10:00:00.000Z",
        endTime: "2026-06-06T10:00:03.200Z",
        extraData: JSON.stringify({
          input: "用户问：解释 Seahorse Agent 的检索流程",
          output: "已回答检索流程",
          model: "deepseek-chat",
          tokenUsage: { prompt: 120, completion: 240 }
        })
      },
      nodes: [
        {
          traceId: "trace-1",
          nodeId: "node-1",
          depth: 0,
          nodeType: "CHAT_STAGE",
          nodeName: "query-rewrite",
          className: "KernelChatPipeline",
          methodName: "rewriteQuery",
          status: "SUCCESS",
          durationMs: 80,
          startTime: "2026-06-06T10:00:00.100Z",
          endTime: "2026-06-06T10:00:00.180Z",
          extraData: JSON.stringify({ input: "原始问题", output: "改写问题" })
        },
        {
          traceId: "trace-1",
          nodeId: "node-2",
          depth: 0,
          nodeType: "RETRIEVAL_CHANNEL",
          nodeName: "search-channel:VectorGlobalSearch",
          className: "VectorGlobalSearchFeature",
          methodName: "search",
          status: "SUCCESS",
          durationMs: 2700,
          startTime: "2026-06-06T10:00:00.300Z",
          endTime: "2026-06-06T10:00:03.000Z",
          extraData: JSON.stringify({
            query: "Seahorse Agent 检索流程",
            topK: 5,
            hits: [{ title: "检索设计文档", score: 0.87 }]
          })
        }
      ]
    };

    vi.mocked(getRagTraceDetail).mockResolvedValue(detail);

    render(
      <MemoryRouter initialEntries={["/admin/traces/trace-1"]}>
        <Routes>
          <Route path="/admin/traces/:traceId" element={<RagTraceDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("Agent 调用概览")).toBeInTheDocument();
    });

    expect(screen.getByText("Span 详情")).toBeInTheDocument();
    expect(screen.getAllByText("输入 / 输出").length).toBeGreaterThan(0);
    expect(screen.getByText("属性")).toBeInTheDocument();
    expect(screen.getByText("原始数据")).toBeInTheDocument();
    expect(screen.getAllByText("检索阶段").length).toBeGreaterThan(0);
    expect(screen.getAllByText("search-channel:VectorGlobalSearch").length).toBeGreaterThan(0);
    expect(screen.getByText("deepseek-chat")).toBeInTheDocument();
  });
});
