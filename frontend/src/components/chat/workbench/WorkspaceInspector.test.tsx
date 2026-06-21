import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WorkspaceInspector } from "@/components/chat/workbench/WorkspaceInspector";
import { getAgentRunContextSnapshot } from "@/services/runContextSnapshotService";
import { useWorkbenchStore } from "@/stores/workbenchStore";
import type { Message } from "@/types";

vi.mock("@/services/runContextSnapshotService", () => ({
  getAgentRunContextSnapshot: vi.fn()
}));

const message: Message = {
  id: "assistant-1",
  role: "assistant",
  content: "done",
  timeline: [{ id: "step-1", title: "PLAN", status: "DONE" }],
  sources: [{ id: "source-1", title: "Source one" }],
  artifacts: [{ id: "artifact-1", title: "Report", language: "markdown", code: "# Report", isComplete: true }],
  toolCalls: [{
    id: "call-1",
    toolId: "web_search",
    status: "SUCCEEDED",
    argumentsPreviewJson: "{\"query\":\"seahorse\"}",
    resultSummary: "2 sources",
    durationMs: 1200
  }],
  skills: [{
    id: "deep-research",
    name: "deep-research",
    status: "LOADED",
    injectMode: "METADATA_ONLY",
    category: "PUBLIC",
    allowedTools: ["web_search"],
    description: "Research workflow",
    resourcePath: "SKILL.md"
  }]
};

describe("WorkspaceInspector", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useWorkbenchStore.getState().resetWorkbench();
  });

  it("shows tab counts for active message data", () => {
    render(<WorkspaceInspector message={message} open onClose={() => undefined} />);
    expect(screen.getByText("产物")).toBeInTheDocument();
    expect(screen.getAllByText("1").length).toBeGreaterThan(0);
  });

  it("renders tool call details in the workbench", async () => {
    useWorkbenchStore.setState({ activeTab: "tools", inspectorOpen: true });
    render(<WorkspaceInspector message={message} open onClose={() => undefined} />);
    expect(screen.getByText("工具调用")).toBeInTheDocument();
    expect(await screen.findByText("web_search")).toBeInTheDocument();
    expect(screen.getByText(/"query": "seahorse"/)).toBeInTheDocument();
    expect(screen.getByText("2 sources")).toBeInTheDocument();
  });

  it("renders skill diagnostics without exposing skill body content", async () => {
    useWorkbenchStore.setState({ activeTab: "skills", inspectorOpen: true });
    render(<WorkspaceInspector message={message} open onClose={() => undefined} />);
    expect(screen.getByText("Skill")).toBeInTheDocument();
    expect(await screen.findByText("deep-research")).toBeInTheDocument();
    expect(screen.getByText("METADATA_ONLY")).toBeInTheDocument();
    expect(screen.getByText("SKILL.md")).toBeInTheDocument();
    expect(screen.getByText("web_search")).toBeInTheDocument();
    expect(screen.queryByText("Instructions:")).not.toBeInTheDocument();
  });

  it("loads and renders the run context snapshot tab", async () => {
    vi.mocked(getAgentRunContextSnapshot).mockResolvedValueOnce({
      id: "snapshot-1",
      runId: "run-1",
      executorEngine: "agentscope",
      snapshotJson: JSON.stringify({
        executorEngine: "agentscope",
        modelConfig: { modelId: "qwen-max" },
        toolIds: ["web_search", "memory_read"]
      }),
      traceContextJson: JSON.stringify({ traceId: "trace-1" })
    });

    useWorkbenchStore.setState({ activeTab: "context", inspectorOpen: true });
    render(<WorkspaceInspector message={{ ...message, agentRunId: "run-1" }} open onClose={() => undefined} />);

    await screen.findByText("上下文");
    expect(await screen.findByText("agentscope")).toBeInTheDocument();
    expect(getAgentRunContextSnapshot).toHaveBeenCalledWith("run-1");
    expect(screen.getByText("qwen-max")).toBeInTheDocument();
    expect(screen.getByText("web_search")).toBeInTheDocument();
    expect(screen.getByText("trace-1")).toBeInTheDocument();
  });

  it("renders run profile, branch, MCP, A2A, and AgentScope trace details from the context snapshot", async () => {
    vi.mocked(getAgentRunContextSnapshot).mockResolvedValueOnce({
      id: "snapshot-2",
      runId: "run-2",
      conversationId: 100,
      branchLeafMessageId: 200,
      roleCardId: 9,
      runProfileId: 77,
      executorEngine: "agentscope",
      traceContextJson: JSON.stringify({ traceId: "trace-2", studioTraceId: "studio-trace-1" }),
      snapshotJson: JSON.stringify({
        executorEngine: "agentscope",
        runProfileId: 77,
        branchLeafMessageId: 200,
        roleCard: { name: "Researcher", higherPerm: false },
        toolIds: ["get_current_datetime"],
        mcpToolIds: ["filesystem.read_file"],
        a2aAgentIds: ["seahorse-researcher"],
        agentScope: {
          studioTraceId: "studio-trace-1",
          nacosNamespace: "public",
          nacosGroup: "DEFAULT_GROUP"
        }
      })
    });

    useWorkbenchStore.setState({ activeTab: "context", inspectorOpen: true });
    render(<WorkspaceInspector message={{ ...message, agentRunId: "run-2" }} open onClose={() => undefined} />);

    expect(await screen.findByText("运行画像")).toBeInTheDocument();
    expect(screen.getAllByText("77").length).toBeGreaterThan(0);
    expect(screen.getByText("分支叶子")).toBeInTheDocument();
    expect(screen.getAllByText("200").length).toBeGreaterThan(0);
    expect(screen.getByText("MCP 工具")).toBeInTheDocument();
    expect(screen.getByText("filesystem.read_file")).toBeInTheDocument();
    expect(screen.getByText("A2A Agent")).toBeInTheDocument();
    expect(screen.getByText("seahorse-researcher")).toBeInTheDocument();
    expect(screen.getAllByText("Studio Trace").length).toBeGreaterThan(0);
    expect(screen.getAllByText("studio-trace-1").length).toBeGreaterThan(0);
  });

  it("renders run profile configuration snapshots as structured context fields", async () => {
    vi.mocked(getAgentRunContextSnapshot).mockResolvedValueOnce({
      id: "snapshot-3",
      runId: "run-3",
      runProfileId: 88,
      executorEngine: "agentscope",
      executorConfigJson: JSON.stringify({ studioTraceEnabled: true, nacosNamespace: "profile-ns" }),
      snapshotJson: JSON.stringify({
        executorEngine: "agentscope",
        runProfile: { id: 88, name: "Research profile", executorEngine: "agentscope" },
        profileModelConfig: { model: "gpt-4.1-mini", temperature: 0.3 },
        memoryScope: { longTerm: true, knowledgeBaseIds: ["kb-001"] },
        guardrailConfig: { highRiskToolApproval: true }
      })
    });

    useWorkbenchStore.setState({ activeTab: "context", inspectorOpen: true });
    render(<WorkspaceInspector message={{ ...message, agentRunId: "run-3" }} open onClose={() => undefined} />);

    expect(await screen.findByText("Run Profile")).toBeInTheDocument();
    expect(screen.getByText("Research profile")).toBeInTheDocument();
    expect(screen.getByText("Executor Config")).toBeInTheDocument();
    expect(screen.getByText("studioTraceEnabled")).toBeInTheDocument();
    expect(screen.getByText("profile-ns")).toBeInTheDocument();
    expect(screen.getByText("Profile Model")).toBeInTheDocument();
    expect(screen.getByText("gpt-4.1-mini")).toBeInTheDocument();
    expect(screen.getByText("Memory Scope")).toBeInTheDocument();
    expect(screen.getByText("kb-001")).toBeInTheDocument();
    expect(screen.getByText("Guardrail")).toBeInTheDocument();
    expect(screen.getByText("highRiskToolApproval")).toBeInTheDocument();
  });
});
