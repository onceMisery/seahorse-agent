import { beforeEach, describe, expect, it, vi } from "vitest";

import { useChatStore } from "@/stores/chatStore";
import { getAgentRunCostSummary, getAgentRunSnapshot, listAgentRunEvents } from "@/services/agentRunService";
import { createStreamResponse } from "@/hooks/useStreamResponse";
import { forkMessage, listMessages, listMessageTree, switchMessageBranch } from "@/services/sessionService";
import { AGENT_STREAM_EVENTS, type AgentRunSnapshot, type Message, type StreamEventEnvelope } from "@/types";
import { storage } from "@/utils/storage";
import { toast } from "sonner";

const streamStarts: string[] = [];
const streamRequests: Array<{ url: string; headers?: Record<string, string> }> = [];

vi.mock("@/services/agentRunService", () => ({
  getAgentRunCostSummary: vi.fn(),
  getAgentRunSnapshot: vi.fn(),
  listAgentRunEvents: vi.fn()
}));

vi.mock("@/hooks/useStreamResponse", () => ({
  createStreamResponse: vi.fn(({ url, headers }) => {
    streamStarts.push(url);
    streamRequests.push({ url, headers });
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
  listMessageTree: vi.fn(),
  listSessions: vi.fn().mockResolvedValue([]),
  renameSession: vi.fn(),
  forkMessage: vi.fn(),
  switchMessageBranch: vi.fn()
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
    streamRequests.length = 0;
    storage.clearAuth();
    vi.mocked(listMessageTree).mockResolvedValue(undefined as unknown as Awaited<ReturnType<typeof listMessageTree>>);
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

  it("omits chatMode for plain chat so the backend routes through the full RAG pipeline", async () => {
    useChatStore.setState({
      currentSessionId: "conversation-1",
      selectedTaskTemplateId: null
    });

    await useChatStore.getState().sendMessage("What does the document say?");

    expect(streamStarts).toHaveLength(1);
    const url = new URL(streamStarts[0], "http://localhost");
    expect(url.pathname).toBe("/api/rag/v3/chat");
    expect(url.searchParams.has("chatMode")).toBe(false);
    expect(url.searchParams.has("agentId")).toBe(false);
  });

  it("keeps a local execution timeline for plain chat streams", async () => {
    vi.mocked(createStreamResponse).mockImplementationOnce(({ url, headers }, handlers) => {
      streamStarts.push(url);
      streamRequests.push({ url, headers });
      return {
        cancel: vi.fn(),
        start: vi.fn().mockImplementation(async () => {
          handlers.onMeta?.({ conversationId: "conversation-1", taskId: "task-1" });
          handlers.onMessage?.({ type: "response", delta: "hello" });
          handlers.onDone?.();
        })
      };
    });
    useChatStore.setState({
      currentSessionId: "conversation-1",
      selectedTaskTemplateId: null
    });

    await useChatStore.getState().sendMessage("Hello");

    const assistant = useChatStore.getState().messages.find((message) => message.role === "assistant");
    expect(assistant?.content).toBe("hello");
    expect(assistant?.timeline?.map((item) => [item.id, item.status])).toEqual([
      ["local-stream-accepted", "DONE"],
      ["local-stream-generating", "DONE"]
    ]);
  });

  it("restores a local execution timeline for historical plain chat answers", async () => {
    vi.mocked(listMessages).mockResolvedValue([{
      id: "user-history-1",
      conversationId: "conversation-history",
      role: "user",
      content: "Hello",
      vote: null,
      createTime: "2026-06-09T00:00:00Z"
    }, {
      id: "assistant-history-1",
      conversationId: "conversation-history",
      role: "assistant",
      content: "Historical answer",
      vote: null,
      createTime: "2026-06-09T00:00:01Z"
    }]);

    await useChatStore.getState().selectSession("conversation-history");

    const assistant = useChatStore.getState().messages.find((message) => message.role === "assistant");
    expect(assistant?.timeline?.map((item) => [item.id, item.status])).toEqual([
      ["local-stream-accepted", "DONE"],
      ["local-stream-generating", "DONE"]
    ]);
    expect(getAgentRunSnapshot).not.toHaveBeenCalled();
  });

  it("passes the selected role card to the chat stream request", async () => {
    useChatStore.setState({
      currentSessionId: "conversation-1",
      selectedTaskTemplateId: null
    });

    await useChatStore.getState().sendMessage("Use a role", {
      roleCardId: "99"
    });

    expect(streamStarts).toHaveLength(1);
    const url = new URL(streamStarts[0], "http://localhost");
    expect(url.pathname).toBe("/api/rag/v3/chat");
    expect(url.searchParams.get("roleCardId")).toBe("99");
  });

  it("passes the selected run profile to the chat stream request", async () => {
    useChatStore.setState({
      currentSessionId: "conversation-1",
      selectedTaskTemplateId: null
    });

    await useChatStore.getState().sendMessage("Use a profile", {
      runProfileId: "77"
    });

    expect(streamStarts).toHaveLength(1);
    const url = new URL(streamStarts[0], "http://localhost");
    expect(url.pathname).toBe("/api/rag/v3/chat");
    expect(url.searchParams.get("runProfileId")).toBe("77");
  });

  it("passes the current persisted branch leaf to the chat stream request", async () => {
    useChatStore.setState({
      currentSessionId: "conversation-1",
      selectedTaskTemplateId: null,
      messages: [
        { id: "101", role: "user", content: "First", status: "done" },
        { id: "102", role: "assistant", content: "Answer", status: "done" }
      ] as Message[]
    });

    await useChatStore.getState().sendMessage("Continue from current branch");

    expect(streamStarts).toHaveLength(1);
    const url = new URL(streamStarts[0], "http://localhost");
    expect(url.pathname).toBe("/api/rag/v3/chat");
    expect(url.searchParams.get("branchLeafMessageId")).toBe("102");
  });

  it("uses Bearer authorization for the chat stream request", async () => {
    storage.setToken("stream-token");
    useChatStore.setState({
      currentSessionId: "conversation-1"
    });

    await useChatStore.getState().sendMessage("Hello");

    expect(streamRequests).toHaveLength(1);
    expect(streamRequests[0].headers?.Authorization).toBe("Bearer stream-token");
  });

  it("attaches memory conflict prompts from custom stream events to the assistant message", async () => {
    vi.mocked(createStreamResponse).mockImplementationOnce(({ url, headers }, handlers) => {
      streamStarts.push(url);
      streamRequests.push({ url, headers });
      return {
        cancel: vi.fn(),
        start: vi.fn().mockImplementation(async () => {
          handlers.onEvent?.("memory.conflict.prompt", {
            conflictId: "mem-conflict-1",
            memoryId1: "memory-a",
            memoryId2: "memory-b",
            conflictType: "CONTRADICTION",
            severity: "HIGH",
            question: "请选择正确的记忆",
            options: [
              { value: "keep_a", label: "保留记忆 A" },
              { value: "keep_b", label: "保留记忆 B" }
            ]
          });
          handlers.onDone?.();
        })
      };
    });
    useChatStore.setState({
      currentSessionId: "conversation-1"
    });

    await useChatStore.getState().sendMessage("Hello");

    const assistant = useChatStore.getState().messages.find((message) => message.role === "assistant");
    expect(assistant?.memoryConflictPrompts).toEqual([
      expect.objectContaining({
        conflictId: "mem-conflict-1",
        memoryId1: "memory-a",
        memoryId2: "memory-b",
        status: "pending",
        options: [
          { value: "keep_a", label: "保留记忆 A" },
          { value: "keep_b", label: "保留记忆 B" }
        ]
      })
    ]);
  });

  it("treats aborted chat streams as cancellation without console errors", async () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    vi.mocked(createStreamResponse).mockImplementationOnce(({ url, headers }) => {
      streamStarts.push(url);
      streamRequests.push({ url, headers });
      const abortError = Object.assign(new Error("BodyStreamBuffer was aborted"), { name: "AbortError" });
      return {
        cancel: vi.fn(),
        start: vi.fn().mockRejectedValue(abortError)
      };
    });

    useChatStore.setState({
      currentSessionId: "conversation-1"
    });

    await useChatStore.getState().sendMessage("Stop me");

    const assistant = useChatStore.getState().messages.find((message) => message.role === "assistant");
    expect(assistant?.status).toBe("done");
    expect(useChatStore.getState().cancelRequested).toBe(false);
    expect(consoleError).not.toHaveBeenCalled();
    consoleError.mockRestore();
  });

  it("hydrates historical agent messages with snapshot, replay events, and cost after selecting a session", async () => {
    const snapshot: AgentRunSnapshot = {
      run: {
        runId: "run-history-1",
        status: "SUCCEEDED"
      },
      lastEventSeq: 42,
      steps: [{
        stepId: "step-1",
        stepType: "MODEL_TURN",
        status: "SUCCEEDED",
        summary: "finished"
      }],
      artifacts: [{
        artifactId: "artifact-1",
        runId: "run-history-1",
        title: "Generated image",
        mimeType: "image/png",
        scanStatus: "CLEAN"
      }],
      costSummary: {
        tenantId: "default",
        runId: "run-history-1",
        totalTokens: 20,
        totalCalls: 2,
        totalCost: 0.01,
        recordCount: 1
      }
    };
    vi.mocked(listMessages).mockResolvedValue([{
      id: "assistant-history-1",
      conversationId: "conversation-history",
      role: "assistant",
      content: "historical answer",
      agentRunId: "run-history-1",
      vote: null,
      createTime: "2026-06-09T00:00:00Z"
    }]);
    vi.mocked(getAgentRunSnapshot).mockResolvedValue(snapshot);
    vi.mocked(listAgentRunEvents).mockResolvedValue([
      {
        eventId: "evt-1",
        eventSeq: "2",
        eventType: AGENT_STREAM_EVENTS.SKILL_LOADED,
        runId: "run-history-1",
        timestamp: "2026-06-09T00:00:01Z",
        typedPayload: {
          name: "image-generation",
          status: "LOADED",
          revisionId: "skillrev-image",
          allowedTools: ["image_generation"]
        }
      },
      {
        eventId: "evt-2",
        eventSeq: "3",
        eventType: AGENT_STREAM_EVENTS.TOOL_CALL_STARTED,
        runId: "run-history-1",
        timestamp: "2026-06-09T00:00:02Z",
        typedPayload: {
          toolCallId: "call-1",
          toolId: "image_generation",
          riskLevel: "HIGH"
        }
      },
      {
        eventId: "evt-3",
        eventSeq: "4",
        eventType: AGENT_STREAM_EVENTS.TOOL_CALL_FINISHED,
        runId: "run-history-1",
        timestamp: "2026-06-09T00:00:03Z",
        typedPayload: {
          toolCallId: "call-1",
          toolId: "image_generation",
          message: "SUCCEEDED",
          summary: "generated"
        }
      }
    ] as unknown as StreamEventEnvelope[]);
    vi.mocked(getAgentRunCostSummary).mockResolvedValue({
      tenantId: "default",
      runId: "run-history-1",
      totalTokens: 20,
      totalCalls: 2,
      totalCost: 0.01,
      recordCount: 1
    });

    await useChatStore.getState().selectSession("conversation-history");

    expect(getAgentRunSnapshot).toHaveBeenCalledWith("run-history-1");
    expect(listAgentRunEvents).toHaveBeenCalledWith("run-history-1", 0);
    expect(getAgentRunCostSummary).toHaveBeenCalledWith("run-history-1");
    const message = useChatStore.getState().messages[0];
    expect(message.agentRunStatus).toBe("SUCCEEDED");
    expect(message.timeline).toEqual(expect.arrayContaining([
      expect.objectContaining({
        id: "tool-call-call-1",
        title: "image_generation",
        status: "SUCCEEDED",
        detail: "generated"
      }),
      expect.objectContaining({
        id: "step-1",
        title: "MODEL_TURN",
        status: "SUCCEEDED",
        detail: "finished"
      })
    ]));
    expect(message.serverArtifacts?.[0]).toMatchObject({
      artifactId: "artifact-1",
      title: "Generated image"
    });
    expect(message.skills?.[0]).toMatchObject({
      name: "image-generation",
      status: "LOADED"
    });
    expect(message.toolCalls?.[0]).toMatchObject({
      id: "call-1",
      toolId: "image_generation",
      status: "SUCCEEDED",
      resultSummary: "generated"
    });
    expect(message.costSummary).toEqual({
      tenantId: "default",
      runId: "run-history-1",
      totalTokens: 20,
      totalCalls: 2,
      totalCost: 0.01,
      recordCount: 1
    });
  });

  it("does not reload the active session when messages are already loaded", async () => {
    useChatStore.setState({
      currentSessionId: "conversation-history",
      messages: [assistantMessage({ id: "assistant-history-1", agentRunId: "run-history-1" })],
      isLoading: false
    });

    await useChatStore.getState().selectSession("conversation-history");

    expect(listMessages).not.toHaveBeenCalled();
    expect(getAgentRunSnapshot).not.toHaveBeenCalled();
    expect(listAgentRunEvents).not.toHaveBeenCalled();
    expect(getAgentRunCostSummary).not.toHaveBeenCalled();
  });

  it("dedupes overlapping active session loads", async () => {
    let resolveMessages: (messages: unknown[]) => void = () => undefined;
    vi.mocked(listMessages).mockReturnValue(new Promise((resolve) => {
      resolveMessages = resolve;
    }) as ReturnType<typeof listMessages>);

    const firstLoad = useChatStore.getState().selectSession("conversation-history");
    const secondLoad = useChatStore.getState().selectSession("conversation-history");

    resolveMessages([]);
    await Promise.all([firstLoad, secondLoad]);

    expect(listMessages).toHaveBeenCalledTimes(1);
    expect(getAgentRunSnapshot).not.toHaveBeenCalled();
    expect(listAgentRunEvents).not.toHaveBeenCalled();
    expect(getAgentRunCostSummary).not.toHaveBeenCalled();
  });

  it("loads branch tree metadata when selecting a session", async () => {
    vi.mocked(listMessageTree).mockResolvedValue([{
      message: {
        id: "2",
        conversationId: "conversation-branch",
        role: "assistant",
        content: "branch answer",
        vote: null,
        parentId: "1",
        active: 1,
        siblingSeq: 1,
        createTime: "2026-06-09T00:00:00Z"
      },
      preSiblings: ["1"],
      nextSiblings: ["3"],
      branchIndex: 2,
      branchTotal: 3
    }]);

    await useChatStore.getState().selectSession("conversation-branch");

    expect(listMessageTree).toHaveBeenCalledWith("conversation-branch");
    expect(listMessages).not.toHaveBeenCalled();
    expect(useChatStore.getState().messages[0]).toMatchObject({
      id: "2",
      parentId: "1",
      branchIndex: 2,
      branchTotal: 3,
      preSiblings: ["1"],
      nextSiblings: ["3"]
    });
  });

  it("switches a message branch and replaces the visible active path", async () => {
    useChatStore.setState({
      currentSessionId: "conversation-branch",
      messages: [assistantMessage({ id: "2", content: "old branch" })],
      isLoading: false
    });
    vi.mocked(switchMessageBranch).mockResolvedValue([{
      message: {
        id: "3",
        conversationId: "conversation-branch",
        role: "assistant",
        content: "new branch",
        vote: null,
        parentId: "1",
        active: 1,
        siblingSeq: 2,
        createTime: "2026-06-09T00:00:00Z"
      },
      preSiblings: ["1", "2"],
      nextSiblings: [],
      branchIndex: 3,
      branchTotal: 3
    }]);

    await useChatStore.getState().switchMessageBranch("2", "3");

    expect(switchMessageBranch).toHaveBeenCalledWith("conversation-branch", "3");
    expect(useChatStore.getState().messages).toHaveLength(1);
    expect(useChatStore.getState().messages[0]).toMatchObject({
      id: "3",
      content: "new branch",
      branchIndex: 3,
      branchTotal: 3
    });
  });

  it("edits a user message by forking a sibling and switching to the new branch", async () => {
    useChatStore.setState({
      currentSessionId: "conversation-branch",
      messages: [{
        id: "2",
        role: "user",
        content: "old prompt",
        status: "done",
        branchIndex: 1,
        branchTotal: 1
      }],
      isLoading: false
    });
    vi.mocked(forkMessage).mockResolvedValue({
      newMessageId: "4",
      parentId: "1"
    });
    vi.mocked(switchMessageBranch).mockResolvedValue([{
      message: {
        id: "4",
        conversationId: "conversation-branch",
        role: "user",
        content: "edited prompt",
        vote: null,
        parentId: "1",
        active: 1,
        siblingSeq: 2,
        createTime: "2026-06-09T00:00:00Z"
      },
      preSiblings: ["2"],
      nextSiblings: [],
      branchIndex: 2,
      branchTotal: 2
    }]);

    await useChatStore.getState().editUserMessageBranch("2", "edited prompt");

    expect(forkMessage).toHaveBeenCalledWith("conversation-branch", {
      anchorMessageId: "2",
      content: "edited prompt",
      role: "user",
      regenerate: false
    });
    expect(switchMessageBranch).toHaveBeenCalledWith("conversation-branch", "4");
    expect(useChatStore.getState().messages[0]).toMatchObject({
      id: "4",
      content: "edited prompt",
      branchIndex: 2,
      branchTotal: 2
    });
  });

  it("regenerates an assistant message from the parent user without appending another user message", async () => {
    useChatStore.setState({
      currentSessionId: "conversation-branch",
      selectedTaskTemplateId: null,
      messages: [
        { id: "101", role: "user", content: "original prompt", status: "done" },
        { id: "102", role: "assistant", content: "old answer", status: "done", parentId: "101" }
      ] as Message[],
      isLoading: false
    });
    vi.mocked(listMessageTree).mockResolvedValue([{
      message: {
        id: "101",
        conversationId: "conversation-branch",
        role: "user",
        content: "original prompt",
        vote: null,
        active: 1,
        siblingSeq: 0,
        createTime: "2026-06-09T00:00:00Z"
      },
      preSiblings: [],
      nextSiblings: [],
      branchIndex: 1,
      branchTotal: 1
    }, {
      message: {
        id: "103",
        conversationId: "conversation-branch",
        role: "assistant",
        content: "new answer",
        vote: null,
        parentId: "101",
        active: 1,
        siblingSeq: 1,
        createTime: "2026-06-09T00:00:01Z"
      },
      preSiblings: ["102"],
      nextSiblings: [],
      branchIndex: 2,
      branchTotal: 2
    }]);

    await useChatStore.getState().regenerateAssistantMessageBranch("102");

    expect(streamStarts).toHaveLength(1);
    const url = new URL(streamStarts[0], "http://localhost");
    expect(url.searchParams.get("question")).toBe("original prompt");
    expect(url.searchParams.get("branchLeafMessageId")).toBe("101");
    expect(url.searchParams.get("assistantParentMessageId")).toBe("101");
    expect(useChatStore.getState().messages.filter((message) => message.role === "user")).toHaveLength(1);
    expect(useChatStore.getState().messages[1]).toMatchObject({
      id: "103",
      content: "new answer",
      parentId: "101",
      branchIndex: 2,
      branchTotal: 2
    });
  });

  it("does not overwrite live auto-sent messages when session history resolves later", async () => {
    let resolveMessages: (messages: unknown[]) => void = () => undefined;
    vi.mocked(listMessages).mockReturnValue(new Promise((resolve) => {
      resolveMessages = resolve;
    }) as ReturnType<typeof listMessages>);

    const load = useChatStore.getState().selectSession("conversation-history");
    await useChatStore.getState().sendMessage("Workspace task prompt", {
      conversationIdOverride: "conversation-history"
    });

    resolveMessages([]);
    await load;

    expect(useChatStore.getState().messages.some((message) =>
      message.role === "user" && message.content === "Workspace task prompt"
    )).toBe(true);
  });

  it("does not apply a stale session response after the active session changes", async () => {
    let resolveMessages: (messages: unknown[]) => void = () => undefined;
    vi.mocked(listMessages).mockReturnValue(new Promise((resolve) => {
      resolveMessages = resolve;
    }) as ReturnType<typeof listMessages>);

    const load = useChatStore.getState().selectSession("conversation-history");
    useChatStore.setState({
      currentSessionId: "conversation-next",
      messages: [assistantMessage({ id: "assistant-next", content: "newer session" })],
      isLoading: false
    });
    resolveMessages([{
      id: "assistant-history-1",
      conversationId: "conversation-history",
      role: "assistant",
      content: "stale answer",
      vote: null,
      createTime: "2026-06-09T00:00:00Z"
    }]);
    await load;

    expect(useChatStore.getState().messages[0]).toMatchObject({
      id: "assistant-next",
      content: "newer session"
    });
    expect(getAgentRunSnapshot).not.toHaveBeenCalled();
    expect(listAgentRunEvents).not.toHaveBeenCalled();
    expect(getAgentRunCostSummary).not.toHaveBeenCalled();
  });

  it("does not clear the active session when a stale session load fails", async () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    try {
      let rejectMessages: (error: unknown) => void = () => undefined;
      vi.mocked(listMessages).mockReturnValue(new Promise((_, reject) => {
        rejectMessages = reject;
      }) as ReturnType<typeof listMessages>);

      const load = useChatStore.getState().selectSession("conversation-history");
      useChatStore.setState({
        currentSessionId: "conversation-next",
        messages: [assistantMessage({ id: "assistant-next", content: "newer session" })],
        isLoading: false
      });
      rejectMessages(new Error("stale request failed"));
      await load;

      expect(useChatStore.getState().currentSessionId).toBe("conversation-next");
      expect(useChatStore.getState().messages[0]).toMatchObject({
        id: "assistant-next",
        content: "newer session"
      });
      expect(toast.error).not.toHaveBeenCalledWith("加载会话失败");
      expect(consoleError).toHaveBeenCalledWith("Failed to load session:", expect.any(Error));
    } finally {
      consoleError.mockRestore();
    }
  });
});
