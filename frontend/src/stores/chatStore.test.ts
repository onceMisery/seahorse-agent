import { beforeEach, describe, expect, it, vi } from "vitest";

import { useChatStore } from "@/stores/chatStore";
import { getAgentRunSnapshot } from "@/services/agentRunService";
import { type AgentRunSnapshot, type Message } from "@/types";

const streamStarts: string[] = [];

vi.mock("@/services/agentRunService", () => ({
  getAgentRunSnapshot: vi.fn()
}));

vi.mock("@/hooks/useStreamResponse", () => ({
  createStreamResponse: vi.fn(({ url }) => {
    streamStarts.push(url);
    return {
      cancel: vi.fn(),
      start: vi.fn().mockResolvedValue(undefined)
    };
  })
}));

vi.mock("@/services/sessionService", () => ({
  createSession: vi.fn(),
  deleteSession: vi.fn(),
  listMessages: vi.fn(),
  listSessions: vi.fn().mockResolvedValue([]),
  renameSession: vi.fn()
}));

vi.mock("@/services/chatService", () => ({
  submitFeedback: vi.fn()
}));

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn()
  }
}));

function assistantMessage(overrides: Partial<Message> = {}): Message {
  return {
    id: "assistant-1",
    role: "assistant",
    content: "",
    status: "streaming",
    ...overrides
  };
}

function setMessages(messages: Message[]) {
  useChatStore.setState({
    messages,
    isStreaming: false,
    streamingMessageId: null
  });
}

describe("chatStore snapshot hydration", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    streamStarts.length = 0;
    setMessages([]);
    useChatStore.setState({
      currentSessionId: null,
      selectedTaskTemplateId: null,
      isStreaming: false
    });
  });

  it("hydrates an interrupted message workspace from an agent run snapshot", async () => {
    const snapshot: AgentRunSnapshot = {
      run: {
        runId: "run-1",
        status: "COMPLETED"
      },
      currentStepId: "step-2",
      lastEventSeq: 12,
      canResume: false,
      canRetry: true,
      messageSnapshot: {
        content: "final answer",
        thinking: "reasoning trace"
      },
      steps: [{
        stepId: "step-1",
        stepType: "Research",
        status: "DONE",
        summary: "searched sources"
      }],
      sources: [{
        itemId: "source-1",
        title: "Official source",
        snippet: "supporting text",
        score: 0.88
      }],
      artifacts: [{
        artifactId: "artifact-1",
        runId: "run-1",
        title: "Report",
        mimeType: "text/markdown",
        previewText: "# Report",
        scanStatus: "CLEAN",
        canPreview: true
      }],
      pendingApprovals: [{
        approvalId: "approval-1",
        title: "Approve tool",
        description: "Needs review",
        requestedBy: "agent"
      }],
      costSummary: {
        tenantId: "default",
        runId: "run-1",
        totalTokens: 120,
        totalCalls: 3,
        totalCost: 0.04,
        recordCount: 2
      }
    };
    vi.mocked(getAgentRunSnapshot).mockResolvedValue(snapshot);
    setMessages([assistantMessage({ id: "assistant-1", agentRunId: "run-1" })]);

    await useChatStore.getState().refreshRunSnapshot("assistant-1", "run-1");

    const message = useChatStore.getState().messages[0];
    expect(message.content).toBe("final answer");
    expect(message.rawText).toBe("final answer");
    expect(message.thinking).toBe("reasoning trace");
    expect(message.status).toBe("done");
    expect(message.agentRunStatus).toBe("COMPLETED");
    expect(message.currentStepId).toBe("step-2");
    expect(message.lastEventSeq).toBe(12);
    expect(message.timeline).toEqual([{
      id: "step-1",
      title: "Research",
      status: "DONE",
      detail: "searched sources"
    }]);
    expect(message.sources).toEqual([{
      id: "source-1",
      title: "Official source",
      snippet: "supporting text",
      score: 0.88
    }]);
    expect(message.serverArtifacts?.[0]).toMatchObject({
      artifactId: "artifact-1",
      title: "Report",
      scanStatus: "CLEAN"
    });
    expect(message.approvals).toEqual([{
      id: "approval-1",
      title: "Approve tool",
      description: "Needs review",
      status: "pending",
      requestedBy: "agent"
    }]);
    expect(message.costSummary).toEqual(snapshot.costSummary);
    expect(message.canRetry).toBe(true);
  });

  it("keeps newer live workspace data when an older snapshot resolves later", async () => {
    let resolveSnapshot: (snapshot: AgentRunSnapshot) => void = () => undefined;
    vi.mocked(getAgentRunSnapshot).mockReturnValue(new Promise((resolve) => {
      resolveSnapshot = resolve;
    }));
    setMessages([assistantMessage({
      id: "assistant-1",
      agentRunId: "run-1",
      content: "live answer",
      rawText: "live answer",
      lastEventSeq: 10,
      timeline: [{ id: "step-1", title: "Live step", status: "DONE" }],
      sources: [{ id: "source-1", title: "Live source" }]
    })]);

    const pending = useChatStore.getState().refreshRunSnapshot("assistant-1", "run-1");
    useChatStore.setState((state) => ({
      messages: state.messages.map((message) =>
        message.id === "assistant-1"
          ? {
              ...message,
              lastEventSeq: 20,
              timeline: [{ id: "step-1", title: "New live step", status: "DONE" }],
              sources: [{ id: "source-1", title: "New live source" }]
            }
          : message
      )
    }));
    resolveSnapshot({
      run: {
        runId: "run-1",
        status: "RUNNING"
      },
      lastEventSeq: 12,
      messageSnapshot: {
        content: "older snapshot"
      },
      steps: [{ stepId: "step-1", stepType: "Old snapshot step", status: "RUNNING" }],
      sources: [{ itemId: "source-1", title: "Old snapshot source" }]
    });

    await pending;

    const message = useChatStore.getState().messages[0];
    expect(message.content).toBe("live answer");
    expect(message.rawText).toBe("live answer");
    expect(message.lastEventSeq).toBe(20);
    expect(message.timeline).toEqual([{ id: "step-1", title: "New live step", status: "DONE" }]);
    expect(message.sources).toEqual([{ id: "source-1", title: "New live source" }]);
  });

  it("uses agent chat mode for the GitHub visual intro task template", async () => {
    useChatStore.setState({
      currentSessionId: "conversation-1",
      selectedTaskTemplateId: "github-visual-project-intro"
    });

    await useChatStore.getState().sendMessage("Create a visual intro");

    expect(streamStarts).toHaveLength(1);
    const url = new URL(streamStarts[0], "http://localhost");
    expect(url.pathname).toBe("/api/rag/v3/chat");
    expect(url.searchParams.get("taskTemplateId")).toBe("github-visual-project-intro");
    expect(url.searchParams.get("chatMode")).toBe("agent");
  });
});
