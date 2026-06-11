import { create } from "zustand";
import { immer } from "zustand/middleware/immer";
import { nanoid } from "nanoid";
import { toast } from "sonner";

import {
  type Message,
  type Role,
  type TaskTemplateId
} from "@/types";
import {
  createSession as createSessionRequest,
  listMessages,
  listSessions,
  deleteSession as deleteSessionRequest,
  renameSession as renameSessionRequest
} from "@/services/sessionService";
import { getAgentRunCostSummary, getAgentRunSnapshot, listAgentRunEvents } from "@/services/agentRunService";
import { submitFeedback } from "@/services/chatService";
import { buildQuery } from "@/utils/helpers";
import { createStreamResponse, type StreamHandlers } from "@/hooks/useStreamResponse";
import { storage } from "@/utils/storage";
import { parseStreamingText } from "@/lib/parser/streamingParser";
import type {
  ConversationMessageVO,
  ConversationVO
} from "@/services/sessionService";
import type { ChatState } from "@/stores/chatStoreTypes";
import { upsertSession } from "@/stores/chatSessionUtils";
import {
  API_BASE_URL,
  computeThinkingDuration,
  extractArtifactsFromBlocks
} from "@/stores/chatStreamUtils";
import {
  applyAgentRunSnapshotToMessage,
  applyAgentStreamEventToMessage
} from "@/stores/chatStreamHandlers";
import { RenderBuffer } from "@/lib/stream/renderBuffer";
import type { StreamEventEnvelope } from "@/types";

const CONTROLLED_WEB_AGENT_CHAT_MODE = "agent";
const CONTROLLED_WEB_AGENT_TEMPLATE_IDS = new Set<TaskTemplateId>([
  "deep-research",
  "web-summary",
  "github-visual-project-intro"
]);
const MAX_ATTACHMENT_COUNT = 20;
const selectedSessionLoads = new Map<string, Promise<void>>();

export const useChatStore = create<ChatState>()(
  immer((set, get) => ({
    sessions: [],
    currentSessionId: null,
    messages: [],
    isLoading: true,
    isStreaming: false,
    isCreatingNew: true,
    isCreating: false,
    deepThinkingEnabled: false,
    thinkingStartAt: null,
    streamTaskId: null,
    streamAbort: null,
    streamingMessageId: null,
    cancelRequested: false,
    inputFocusKey: Date.now(),
    selectedTaskTemplateId: null,
    sessionsLoaded: false,

    fetchSessions: async () => {
      try {
        const sessions = await listSessions() as unknown as ConversationVO[];
        set((state) => {
          state.sessions = sessions.map((s) => ({ id: s.conversationId, title: s.title, lastTime: s.lastTime }));
        });
      } catch (error) {
        console.error("Failed to load sessions:", error);
        toast.error("加载会话列表失败");
      } finally {
        set((state) => { state.isLoading = false; state.sessionsLoaded = true; });
      }
    },

    createSession: async () => {
      const state = get();
      if (state.isCreating) {
        return state.currentSessionId || "";
      }
      if (state.messages.length === 0 && !state.currentSessionId) {
        set((s) => {
          s.isCreatingNew = true;
          s.isLoading = false;
          s.thinkingStartAt = null;
          s.deepThinkingEnabled = false;
        });
        return "";
      }
      if (state.isStreaming) {
        get().cancelGeneration();
      }
      set((s) => { s.isCreating = true; });
      try {
        const conversationId = await createSessionRequest();
        set((s) => {
          s.currentSessionId = conversationId;
          s.messages = [];
          s.isStreaming = false;
          s.isLoading = false;
          s.isCreatingNew = true;
          s.isCreating = false;
          s.deepThinkingEnabled = false;
          s.thinkingStartAt = null;
          s.streamTaskId = null;
          s.streamAbort = null;
          s.streamingMessageId = null;
          s.cancelRequested = false;
          s.sessions = upsertSession(s.sessions, { id: conversationId, title: "New conversation", lastTime: new Date().toISOString() });
        });
        return conversationId;
      } catch (error) {
        console.error("Failed to create session:", error);
        toast.error("创建会话失败");
        set((s) => {
          s.currentSessionId = null;
          s.messages = [];
          s.isStreaming = false;
          s.isLoading = false;
          s.isCreatingNew = true;
          s.isCreating = false;
          s.deepThinkingEnabled = false;
          s.thinkingStartAt = null;
          s.streamTaskId = null;
          s.streamAbort = null;
          s.streamingMessageId = null;
          s.cancelRequested = false;
        });
        return "";
      }
    },

    deleteSession: async (sessionId) => {
      try {
        await deleteSessionRequest(sessionId);
        set((state) => {
          state.sessions = state.sessions.filter((s) => s.id !== sessionId);
          if (state.currentSessionId === sessionId) {
            state.messages = [];
            state.currentSessionId = null;
          }
        });
      } catch (error) {
        console.error("Failed to delete session:", error);
        toast.error("删除会话失败");
      }
    },

    selectSession: async (sessionId) => {
      const state = get();
      if (state.currentSessionId === sessionId && (state.isLoading || state.messages.length > 0)) {
        return;
      }
      const existingLoad = selectedSessionLoads.get(sessionId);
      if (existingLoad) return existingLoad;

      const load = (async () => {
        if (state.isStreaming) {
          get().cancelGeneration();
        }
        try {
          set((s) => {
            s.currentSessionId = sessionId;
            s.isCreatingNew = false;
            s.isLoading = true;
          });
          const messages = await listMessages(sessionId) as unknown as ConversationMessageVO[];
          const nextMessages: Message[] = messages.map((m) => ({
            id: String(m.id),
            role: m.role as Role,
            content: m.content,
            status: "done" as const,
            thinking: m.thinkingContent || undefined,
            thinkingDuration: m.thinkingDuration || undefined,
            feedback: m.vote !== null ? (m.vote > 0 ? "like" as const : "dislike" as const) : null,
            createdAt: m.createTime || new Date().toISOString(),
            agentRunId: m.agentRunId || undefined
          }));
          if (get().currentSessionId !== sessionId) return;
          set((s) => {
            s.messages = nextMessages;
            s.isLoading = false;
          });
          await hydrateSelectedSessionAgentRuns(sessionId, nextMessages, set);
        } catch (error) {
          console.error("Failed to load session:", error);
          if (get().currentSessionId !== sessionId) return;
          toast.error("加载会话失败");
          set((s) => {
            s.currentSessionId = null;
            s.messages = [];
            s.isLoading = false;
          });
        }
      })().finally(() => {
        if (selectedSessionLoads.get(sessionId) === load) {
          selectedSessionLoads.delete(sessionId);
        }
      });
      selectedSessionLoads.set(sessionId, load);
      return load;
    },

    sendMessage: async (content, options) => {
      const { attachmentIds = [] } = options || {};
      const trimmed = content?.trim() || "";
      if (!trimmed && attachmentIds.length === 0) return;

      if (attachmentIds.length > MAX_ATTACHMENT_COUNT) {
        toast.error(`最多支持${MAX_ATTACHMENT_COUNT}个附件`);
        return;
      }

      if (get().isStreaming) return;
      const deepThinkingEnabled = get().deepThinkingEnabled;
      const selectedTaskTemplateId = get().selectedTaskTemplateId;
      const attachmentIdsFiltered = Array.from(new Set(attachmentIds)).filter(Boolean);
      const inputFocusKey = Date.now();

      const userMessage: Message = {
        id: nanoid(),
        role: "user",
        content: trimmed,
        status: "done",
        createdAt: new Date().toISOString()
      };
      const assistantId = nanoid();
      const assistantMessage: Message = {
        id: assistantId,
        role: "assistant",
        content: "",
        rawText: "",
        blocks: [],
        thinking: deepThinkingEnabled ? "" : undefined,
        isDeepThinking: deepThinkingEnabled,
        isThinking: deepThinkingEnabled,
        status: "streaming",
        feedback: null,
        createdAt: new Date().toISOString()
      };

      set((s) => {
        s.messages.push(userMessage, assistantMessage);
        s.isStreaming = true;
        s.streamingMessageId = assistantId;
        s.thinkingStartAt = null;
        s.inputFocusKey = inputFocusKey;
        s.streamTaskId = null;
        s.cancelRequested = false;
      });

      const conversationId = options?.conversationIdOverride || get().currentSessionId;
      const selectedSkillNames = options?.selectedSkillNames?.length ? options.selectedSkillNames : undefined;
      const agentId = options?.agentId || undefined;
      const versionId = options?.versionId || undefined;
      const query = buildQuery({
        question: trimmed,
        conversationId: conversationId || undefined,
        deepThinking: deepThinkingEnabled ? true : undefined,
        chatMode: agentId ? "agent" : chatModeForTaskTemplate(selectedTaskTemplateId as TaskTemplateId | undefined),
        agentId,
        versionId,
        taskTemplateId: selectedTaskTemplateId || undefined,
        attachmentIds: attachmentIdsFiltered,
        selectedSkillNames
      });
      const url = `${API_BASE_URL}/rag/v3/chat${query}`;
      const token = storage.getToken();
      const headers: Record<string, string> = { Accept: "text/event-stream" };
      if (token) headers.Authorization = token;

      let currentAssistantMessageId = assistantId;
      const buffer = new RenderBuffer((flushedText) => {
        set((state) => {
          const msg = currentAssistantMessage(state.messages, currentAssistantMessageId, assistantId);
          if (!msg) return;
          msg.rawText = (msg.rawText ?? "") + flushedText;
          msg.content = msg.rawText;
          msg.blocks = parseStreamingText(msg.rawText, msg.id);
          msg.artifacts = mergeArtifactsById(msg.artifacts, extractArtifactsFromBlocks(msg.blocks));
        });
      });

      const markDone = () => {
        buffer.flushImmediate();
        set((state) => {
          const msg = currentAssistantMessage(state.messages, currentAssistantMessageId, assistantId);
          if (msg) {
            msg.status = "done";
            msg.isThinking = false;
            msg.thinkingDuration = computeThinkingDuration(msg.thinkingStartAt);
          }
        });
      };

      const markError = (errorText: string) => {
        buffer.flushImmediate();
        set((state) => {
          const msg = currentAssistantMessage(state.messages, currentAssistantMessageId, assistantId);
          if (msg) {
            msg.status = "error";
            if (!msg.content) msg.content = errorText;
          }
        });
      };

      const handlers: StreamHandlers = {
        onMeta: (payload) => {
          if (payload?.taskId) {
            set((s) => { s.streamTaskId = payload.taskId!; });
          }
        },
        onMessage: (payload) => {
          buffer.push(payload?.delta || "");
        },
        onThinking: (payload) => {
          const thought = payload?.delta || "";
          set((state) => {
            const msg = currentAssistantMessage(state.messages, currentAssistantMessageId, assistantId);
            if (msg) {
              msg.thinking = (msg.thinking || "") + thought;
              msg.isThinking = true;
              if (!msg.thinkingStartAt) msg.thinkingStartAt = Date.now();
            }
          });
        },
        onFinish: () => { markDone(); },
        onDone: () => { markDone(); },
        onCancel: () => { markDone(); },
        onStreamEvent: (envelope) => {
          set((state) => {
            const msg = currentAssistantMessage(state.messages, currentAssistantMessageId, assistantId);
            if (!msg) return;
            applyAgentStreamEventToMessage(msg, envelope);
            currentAssistantMessageId = msg.id;
          });
        },
        onError: (error) => { markError(error.message || "对话失败"); }
      };

      try {
        const stream = createStreamResponse({ url, headers }, handlers);
        set((s) => { s.streamAbort = stream.cancel; });
        await stream.start();
      } catch (error) {
        console.error("Stream error:", error);
        markError(error instanceof Error ? error.message : "对话失败");
      } finally {
        set((s) => {
          s.isStreaming = false;
          s.streamAbort = null;
          s.streamTaskId = null;
        });
        await get().fetchSessions();
      }
    },

    cancelGeneration: () => {
      set((s) => { s.cancelRequested = true; });
      const abort = get().streamAbort;
      if (abort) {
        abort();
      }
    },

    setDeepThinkingEnabled: (enabled) => {
      set((s) => { s.deepThinkingEnabled = enabled; });
    },

    setSelectedTaskTemplateId: (taskTemplateId) => {
      set((s) => { s.selectedTaskTemplateId = taskTemplateId; });
    },

    submitFeedback: async (messageId, feedback, options) => {
      try {
        const vote = feedback === "like" ? 1 : feedback === "dislike" ? -1 : 0;
        await submitFeedback(messageId, vote, options?.reason, options?.comment);
        set((state) => {
          const msg = state.messages.find((m) => m.id === messageId);
          if (msg) {
            msg.feedback = feedback;
          }
        });
      } catch (error) {
        console.error("Failed to submit feedback:", error);
        toast.error("提交反馈失败");
      }
    },

    renameSession: async (sessionId, title) => {
      try {
        await renameSessionRequest(sessionId, title);
        set((state) => {
          const session = state.sessions.find((s) => s.id === sessionId);
          if (session) {
            session.title = title;
          }
        });
      } catch (error) {
        console.error("Failed to rename session:", error);
        toast.error("重命名会话失败");
        throw error;
      }
    },

    updateSessionTitle: (sessionId, title) => {
      get().renameSession(sessionId, title).catch(() => null);
    },

    refreshRunSnapshot: async (messageId, runId) => {
      try {
        const snapshot = await getAgentRunSnapshot(runId);
        set((state) => {
          const msg = state.messages.find((m) => m.id === messageId);
          if (msg) applyAgentRunSnapshotToMessage(msg, snapshot);
        });
      } catch (error) {
        console.error("Failed to refresh run snapshot:", error);
      }
    },

    appendStreamContent: (delta) => {
      set((state) => {
        const msg = state.messages.find(
          (m) => m.id === state.streamingMessageId
        );
        if (msg) {
          msg.rawText = (msg.rawText ?? "") + delta;
          msg.content = msg.rawText;
          msg.blocks = parseStreamingText(msg.rawText, msg.id);
          msg.artifacts = mergeArtifactsById(msg.artifacts, extractArtifactsFromBlocks(msg.blocks));
        }
      });
    },

    appendThinkingContent: (delta) => {
      set((state) => {
        const msg = state.messages.find(
          (m) => m.id === state.streamingMessageId
        );
        if (msg) {
          msg.thinking = (msg.thinking || "") + delta;
          msg.isThinking = true;
          if (!msg.thinkingStartAt) msg.thinkingStartAt = Date.now();
        }
      });
    }
  }))
);

function currentAssistantMessage(messages: Message[], currentId: string, originalId: string): Message | undefined {
  return messages.find((m) => m.id === currentId || m.id === originalId);
}

function mergeArtifactsById(current: Message["artifacts"], incoming: Message["artifacts"]): NonNullable<Message["artifacts"]> {
  if (!incoming || incoming.length === 0) return current ?? [];
  const map = new Map<string, NonNullable<Message["artifacts"]>[number]>();
  for (const item of current ?? []) map.set(item.id, item);
  for (const item of incoming) {
    const existing = map.get(item.id);
    if (existing && item.append) {
      map.set(item.id, {
        ...existing,
        ...item,
        language: existing.language,
        title: existing.title,
        code: `${existing.code ?? ""}${item.code ?? ""}`,
        isComplete: item.isComplete
      });
      continue;
    }
    map.set(item.id, {
      ...existing,
      ...item,
      language: item.language === "javascript" && existing ? existing.language : item.language,
      title: /^Artifact(?: \d+)?$/.test(item.title) && existing ? existing.title : item.title,
      code: item.code || existing?.code || "",
      isComplete: item.isComplete
    });
  }
  return Array.from(map.values()).map(({ append, ...item }) => item);
}

function chatModeForTaskTemplate(taskTemplateId?: TaskTemplateId): string | undefined {
  if (!taskTemplateId) return undefined;
  if (CONTROLLED_WEB_AGENT_TEMPLATE_IDS.has(taskTemplateId)) {
    return CONTROLLED_WEB_AGENT_CHAT_MODE;
  }
  return undefined;
}

async function hydrateSelectedSessionAgentRuns(
  sessionId: string,
  messages: Message[],
  set: Parameters<typeof useChatStore.setState>[0]
): Promise<void> {
  const targets = messages.filter((message) =>
    message.role === "assistant" && Boolean(message.agentRunId)
  );

  await Promise.all(targets.map(async (message) => {
    try {
      const [snapshot, events, costSummary] = await Promise.all([
        getAgentRunSnapshot(message.agentRunId!),
        listAgentRunEvents(message.agentRunId!, 0).catch(() => []),
        getAgentRunCostSummary(message.agentRunId!).catch(() => null)
      ]);
      set((state) => {
        if (state.currentSessionId !== sessionId) return;
        const target = state.messages.find((item) =>
          item.id === message.id && item.agentRunId === message.agentRunId
        );
        if (!target) return;
        for (const event of normalizeReplayEvents(events)) {
          applyAgentStreamEventToMessage(target, event);
        }
        applyAgentRunSnapshotToMessage(target, snapshot);
        if (costSummary) {
          target.costSummary = costSummary;
        }
      });
    } catch (error) {
      console.error("Failed to hydrate historical agent run:", error);
    }
  }));
}

function normalizeReplayEvents(events: StreamEventEnvelope[]): StreamEventEnvelope[] {
  const bySeq = new Map<number, StreamEventEnvelope>();
  for (const event of events) {
    const eventSeq = normalizeEventSeq(event.eventSeq);
    if (eventSeq == null || bySeq.has(eventSeq)) continue;
    bySeq.set(eventSeq, { ...event, eventSeq });
  }
  return Array.from(bySeq.values()).sort((a, b) => a.eventSeq - b.eventSeq);
}

function normalizeEventSeq(eventSeq: StreamEventEnvelope["eventSeq"] | string): number | null {
  if (typeof eventSeq === "string" && !/^\d+$/.test(eventSeq)) return null;
  const seq = typeof eventSeq === "string" ? Number(eventSeq) : eventSeq;
  return Number.isSafeInteger(seq) && seq >= 0 ? seq : null;
}
