import { create } from "zustand";
import { immer } from "zustand/middleware/immer";
import { nanoid } from "nanoid";
import { toast } from "sonner";

import {
  AGENT_STREAM_EVENTS,
  type AgentArtifact,
  type AgentApproval,
  type AgentRunSnapshot,
  type AgentSource,
  type AgentTimelineItem,
  type ArtifactBlock,
  type CompletionPayload,
  type Message,
  type MessageDeltaPayload,
  type StreamEventEnvelope,
  type StreamMetaPayload,
  type TaskTemplateId
} from "@/types";
import {
  createSession as createSessionRequest,
  listMessages,
  listSessions,
  deleteSession as deleteSessionRequest,
  renameSession as renameSessionRequest
} from "@/services/sessionService";
import { listPendingApprovalRequests } from "@/services/approvalService";
import { getAgentRunCostSummary, getAgentRunSnapshot } from "@/services/agentRunService";
import { stopTask, submitFeedback } from "@/services/chatService";
import { buildQuery } from "@/utils/helpers";
import { createStreamResponse } from "@/hooks/useStreamResponse";
import { handleUnauthorizedSession } from "@/utils/authSession";
import { storage } from "@/utils/storage";
import { useArtifactStore } from "@/stores/artifactStore";
import type { ChatState } from "@/stores/chatStoreTypes";
import { mapVoteToFeedback, upsertSession } from "@/stores/chatSessionUtils";
import {
  API_BASE_URL,
  computeThinkingDuration,
  extractArtifactsFromBlocks,
  normalizeAgentStreamEvent
} from "@/stores/chatStreamUtils";
import { RenderBuffer } from "@/lib/stream/renderBuffer";
import { parseStreamingText } from "@/lib/parser/streamingParser";

const EMPTY_ASSISTANT_MESSAGE = "本次对话没有返回有效内容，请稍后重试。";
const CONTROLLED_WEB_AGENT_CHAT_MODE = "agent";
const CONTROLLED_WEB_AGENT_TEMPLATE_IDS = new Set<TaskTemplateId>([
  "deep-research",
  "web-summary",
  "agent-summary",
  "web-search"
]);
const MAX_ATTACHMENT_COUNT = 20;

export const useChatStore = create<ChatState>()(
  immer((set, get) => ({
    sessions: [],
    currentSessionId: null,
    messages: [],
    isLoading: true,
    isStreaming: false,
    isCreatingNew: true,
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
        const sessions = await listSessions();
        set((state) => {
          state.sessions = sessions.map((s) => ({ id: s.conversationId, title: s.title, updatedAt: s.lastTime }));
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
      try {
        const conversationId = await createSessionRequest();
        set((s) => {
          s.currentSessionId = conversationId;
          s.messages = [];
          s.isStreaming = false;
          s.isLoading = false;
          s.isCreatingNew = true;
          s.deepThinkingEnabled = false;
          s.thinkingStartAt = null;
          s.streamTaskId = null;
          s.streamAbort = null;
          s.streamingMessageId = null;
          s.cancelRequested = false;
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
      if (state.isStreaming) {
        get().cancelGeneration();
      }
      try {
        set((s) => {
          s.currentSessionId = sessionId;
          s.isCreatingNew = false;
          s.isLoading = true;
        });
        const messages = await listMessages(sessionId);
        set((s) => {
          s.messages = messages.map((m) => ({
            id: String(m.id),
            role: m.role,
            content: m.content,
            status: "done" as const,
            thinking: m.thinkingContent || undefined,
            thinkingDuration: m.thinkingDuration || undefined,
            feedback: m.vote !== null ? { score: m.vote > 0 ? 1 : -1 } : null,
            createdAt: m.createTime || new Date().toISOString(),
            agentRunId: m.agentRunId || undefined
          }));
          s.isLoading = false;
        });
      } catch (error) {
        console.error("Failed to load session:", error);
        toast.error("加载会话失败");
        set((s) => {
          s.currentSessionId = null;
          s.messages = [];
          s.isLoading = false;
        });
      }
    },

    sendMessage: async (options) => {
      const { message, attachmentIds = [] } = options || {};
      const trimmed = message?.trim() || "";
      if (!trimmed) {
        if (attachmentIds.length === 0) return;
      }

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

      const conversationId = options.conversationIdOverride || get().currentSessionId;
      const query = buildQuery({
        question: trimmed,
        conversationId: conversationId || undefined,
        deepThinking: deepThinkingEnabled ? true : undefined,
        chatMode: chatModeForTaskTemplate(selectedTaskTemplateId),
        taskTemplateId: selectedTaskTemplateId || undefined,
        attachmentIds: attachmentIdsFiltered
      });
      const url = `${API_BASE_URL}/rag/v3/chat${query}`;
      const token = storage.getToken();

      let currentAssistantMessageId = assistantId;
      let stagedApprovals: AgentApproval[] = [];

      const buffer = new RenderBuffer((flushedText) => {
        set((state) => {
          const msg = currentAssistantMessage(state.messages, currentAssistantMessageId, assistantId);
          if (!msg) return;
          msg.rawText = (msg.rawText ?? "") + flushedText;
          msg.content = msg.rawText;
        });
      });

      try {
        const response = await createStreamResponse({ url, token });
        const abortController = response.controller;
        set((s) => { s.streamAbort = abortController; });

        for await (const chunk of response.stream) {
          if (get().cancelRequested) {
            abortController.abort();
            throw new Error("User cancelled");
          }

          const event = normalizeAgentStreamEvent(chunk);
          if (!event) continue;

          switch (event.type) {
            case AGENT_STREAM_EVENTS.TEXT: {
              const text = event.data?.text || "";
              buffer.append(text);
              break;
            }
            case AGENT_STREAM_EVENTS.BLOCK: {
              const block = event.data?.block;
              if (block) {
                buffer.flush();
                set((state) => {
                  const msg = currentAssistantMessage(state.messages, currentAssistantMessageId, assistantId);
                  if (msg) {
                    msg.blocks = [...(msg.blocks || []), block];
                  }
                });
              }
              break;
            }
            case AGENT_STREAM_EVENTS.APPROVAL: {
              const approval = event.data?.approval;
              if (approval) {
                stagedApprovals.push(approval);
              }
              break;
            }
            case AGENT_STREAM_EVENTS.TASK_ID: {
              const taskId = event.data?.taskId;
              if (taskId) {
                set((s) => { s.streamTaskId = taskId; });
              }
              break;
            }
            case AGENT_STREAM_EVENTS.THOUGHT: {
              const thought = event.data?.text || "";
              set((state) => {
                const msg = currentAssistantMessage(state.messages, currentAssistantMessageId, assistantId);
                if (msg) {
                  msg.thinking = (msg.thinking || "") + thought;
                  msg.isThinking = true;
                  if (!msg.thinkingStartAt) {
                    msg.thinkingStartAt = Date.now();
                  }
                }
              });
              break;
            }
            case AGENT_STREAM_EVENTS.ERROR: {
              const error = event.data?.error || "未知错误";
              buffer.flush();
              set((state) => {
                const msg = currentAssistantMessage(state.messages, currentAssistantMessageId, assistantId);
                if (msg) {
                  msg.status = "error";
                  msg.content = error;
                }
              });
              throw new Error(error);
            }
            case AGENT_STREAM_EVENTS.DONE: {
              buffer.flush();
              set((state) => {
                const msg = currentAssistantMessage(state.messages, currentAssistantMessageId, assistantId);
                if (msg) {
                  msg.status = "done";
                  msg.isStreaming = false;
                  msg.isThinking = false;
                  msg.thinkingDuration = computeThinkingDuration(msg.thinkingStartAt);
                }
              });
              break;
            }
          }
        }
      } catch (error) {
        buffer.flush();
        console.error("Stream error:", error);
        set((state) => {
          const msg = currentAssistantMessage(state.messages, currentAssistantMessageId, assistantId);
          if (msg) {
            msg.status = "error";
            if (!msg.content) {
              msg.content = error instanceof Error ? error.message : "对话失败";
            }
          }
        });
      } finally {
        set((s) => {
          s.isStreaming = false;
          s.streamAbort = null;
          s.streamTaskId = null;
        });
        await get().loadSessions();
      }
    },

    cancelGeneration: () => {
      set((s) => { s.cancelRequested = true; });
      const abort = get().streamAbort;
      if (abort) {
        abort.abort();
      }
    },

    setDeepThinking: (enabled) => {
      set((s) => { s.deepThinkingEnabled = enabled; });
    },

    setSelectedTaskTemplateId: (taskTemplateId) => {
      set((s) => { s.selectedTaskTemplateId = taskTemplateId; });
    },

    submitFeedback: async (messageId, score) => {
      try {
        await submitFeedback(messageId, score);
        set((state) => {
          const msg = state.messages.find((m) => m.id === messageId);
          if (msg) {
            msg.feedback = { score };
          }
        });
      } catch (error) {
        console.error("Failed to submit feedback:", error);
        toast.error("提交反馈失败");
      }
    },

    updateSessionTitle: async (sessionId, title) => {
      try {
        await renameSessionRequest(sessionId, title);
        set((state) => {
          const session = state.sessions.find((s) => s.id === sessionId);
          if (session) {
            session.title = title;
          }
        });
      } catch (error) {
        console.error("Failed to update session title:", error);
        toast.error("更新会话标题失败");
      }
    }
  }))
);

function currentAssistantMessage(messages: Message[], currentId: string, originalId: string): Message | undefined {
  return messages.find((m) => m.id === currentId || m.id === originalId);
}

function chatModeForTaskTemplate(taskTemplateId?: TaskTemplateId): string | undefined {
  if (!taskTemplateId) return undefined;
  if (CONTROLLED_WEB_AGENT_TEMPLATE_IDS.has(taskTemplateId)) {
    return CONTROLLED_WEB_AGENT_CHAT_MODE;
  }
  return undefined;
}