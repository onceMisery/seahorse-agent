import { create } from "zustand";
import { immer } from "zustand/middleware/immer";
import { nanoid } from "nanoid";
import { toast } from "sonner";

import type { CompletionPayload, Message, MessageDeltaPayload, StreamMetaPayload } from "@/types";
import {
  listMessages,
  listSessions,
  deleteSession as deleteSessionRequest,
  renameSession as renameSessionRequest
} from "@/services/sessionService";
import { stopTask, submitFeedback } from "@/services/chatService";
import { buildQuery } from "@/utils/helpers";
import { createStreamResponse } from "@/hooks/useStreamResponse";
import { handleUnauthorizedSession } from "@/utils/authSession";
import { storage } from "@/utils/storage";
import type { ChatState } from "@/stores/chatStoreTypes";
import { mapVoteToFeedback, upsertSession } from "@/stores/chatSessionUtils";
import { API_BASE_URL, computeThinkingDuration } from "@/stores/chatStreamUtils";
import { RenderBuffer } from "@/lib/stream/renderBuffer";
import { parseStreamingText } from "@/lib/parser/streamingParser";

const EMPTY_ASSISTANT_MESSAGE = "本次对话没有返回有效内容，请稍后重试。";

export const useChatStore = create<ChatState>()(
  immer((set, get) => ({
    sessions: [],
    currentSessionId: null,
    messages: [],
    isLoading: false,
    sessionsLoaded: false,
    inputFocusKey: 0,
    isStreaming: false,
    isCreatingNew: false,
    deepThinkingEnabled: false,
    thinkingStartAt: null,
    streamTaskId: null,
    streamAbort: null,
    streamingMessageId: null,
    cancelRequested: false,

    fetchSessions: async () => {
      set((state) => { state.isLoading = true; });
      try {
        const data = await listSessions();
        const sessions = data
          .map((item) => ({
            id: item.conversationId,
            title: item.title || "New Chat",
            lastTime: item.lastTime
          }))
          .sort((a, b) => {
            const timeA = a.lastTime ? new Date(a.lastTime).getTime() : 0;
            const timeB = b.lastTime ? new Date(b.lastTime).getTime() : 0;
            return timeB - timeA;
          });
        set((state) => { state.sessions = sessions; });
      } catch (error) {
        toast.error((error as Error).message || "Failed to load conversations");
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
        toast.success("Conversation deleted");
      } catch (error) {
        toast.error((error as Error).message || "Failed to delete conversation");
      }
    },

    renameSession: async (sessionId, title) => {
      const nextTitle = title.trim();
      if (!nextTitle) return;
      try {
        await renameSessionRequest(sessionId, nextTitle);
        set((state) => {
          const session = state.sessions.find((s) => s.id === sessionId);
          if (session) session.title = nextTitle;
        });
        toast.success("Conversation renamed");
      } catch (error) {
        toast.error((error as Error).message || "Failed to rename conversation");
      }
    },

    selectSession: async (sessionId) => {
      if (!sessionId) return;
      if (get().currentSessionId === sessionId && get().messages.length > 0) return;
      if (get().isStreaming) {
        get().cancelGeneration();
      }
      set((s) => {
        s.isLoading = true;
        s.currentSessionId = sessionId;
        s.isCreatingNew = false;
        s.thinkingStartAt = null;
      });
      try {
        const data = await listMessages(sessionId);
        if (get().currentSessionId !== sessionId) return;
        const mapped: Message[] = data.map((item) => ({
          id: String(item.id),
          role: item.role === "assistant" ? "assistant" : "user",
          content: item.content,
          rawText: item.content,
          thinking: item.thinkingContent || undefined,
          thinkingDuration: item.thinkingDuration || undefined,
          isDeepThinking: Boolean(item.thinkingContent),
          createdAt: item.createTime,
          feedback: mapVoteToFeedback(item.vote),
          status: "done"
        }));
        set((s) => { s.messages = mapped; });
      } catch (error) {
        toast.error((error as Error).message || "Failed to load messages");
      } finally {
        if (get().currentSessionId !== sessionId) {
          set((s) => { s.isLoading = false; });
          return;
        }
        set((s) => {
          s.isLoading = false;
          s.isStreaming = false;
          s.streamTaskId = null;
          s.streamAbort = null;
          s.streamingMessageId = null;
          s.cancelRequested = false;
        });
      }
    },

    updateSessionTitle: (sessionId, title) => {
      set((state) => {
        const session = state.sessions.find((s) => s.id === sessionId);
        if (session) session.title = title;
      });
    },

    setDeepThinkingEnabled: (enabled) => {
      set((s) => { s.deepThinkingEnabled = enabled; });
    },

    sendMessage: async (content) => {
      const trimmed = content.trim();
      if (!trimmed) return;
      if (get().isStreaming) return;
      const deepThinkingEnabled = get().deepThinkingEnabled;
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

      const conversationId = get().currentSessionId;
      const query = buildQuery({
        question: trimmed,
        conversationId: conversationId || undefined,
        deepThinking: deepThinkingEnabled ? true : undefined
      });
      const url = `${API_BASE_URL}/rag/v3/chat${query}`;
      const token = storage.getToken();

      // RenderBuffer：rAF 批量刷盘，解决流式渲染闪烁
      const buffer = new RenderBuffer((flushedText) => {
        set((state) => {
          const msg = state.messages.find((m) => m.id === assistantId);
          if (!msg) return;
          msg.rawText = (msg.rawText ?? "") + flushedText;
          msg.content = msg.rawText;
          msg.blocks = parseStreamingText(msg.rawText, msg.id);
        });
      });

      const handlers = {
        onMeta: (payload: StreamMetaPayload) => {
          if (get().streamingMessageId !== assistantId) return;
          const nextId = payload.conversationId || get().currentSessionId;
          if (!nextId) return;
          const lastTime = new Date().toISOString();
          const existing = get().sessions.find((session) => session.id === nextId);
          set((state) => {
            state.currentSessionId = nextId;
            state.isCreatingNew = false;
            state.streamTaskId = payload.taskId;
            const idx = state.sessions.findIndex((s) => s.id === nextId);
            if (idx >= 0) {
              state.sessions[idx] = { ...state.sessions[idx], title: existing?.title || "New Chat", lastTime };
            } else {
              state.sessions.unshift({ id: nextId, title: existing?.title || "New Chat", lastTime });
            }
            if (payload.runId) {
              const msg = state.messages.find((m) => m.id === state.streamingMessageId);
              if (msg) msg.agentRunId = payload.runId || undefined;
            }
          });
          if (get().cancelRequested) {
            stopTask(payload.taskId).catch(() => null);
          }
        },
        onMessage: (payload: MessageDeltaPayload) => {
          if (!payload || typeof payload !== "object") return;
          if (payload.type !== "response") return;
          buffer.push(payload.delta);
          // 防积压保护
          if (buffer.getLength() > 2000) {
            buffer.flushImmediate();
          }
        },
        onThinking: (payload: MessageDeltaPayload) => {
          if (!payload || typeof payload !== "object") return;
          if (payload.type !== "think") return;
          get().appendThinkingContent(payload.delta);
        },
        onReject: (payload: MessageDeltaPayload) => {
          if (!payload || typeof payload !== "object") return;
          buffer.push(payload.delta);
        },
        onFinish: (payload: CompletionPayload) => {
          if (get().streamingMessageId !== assistantId) return;
          if (!payload) return;
          buffer.flushImmediate();
          if (payload.title && get().currentSessionId) {
            get().updateSessionTitle(get().currentSessionId as string, payload.title);
          }
          const currentId = get().currentSessionId;
          if (currentId) {
            const lastTime = new Date().toISOString();
            const existingTitle =
              get().sessions.find((session) => session.id === currentId)?.title || "New Chat";
            const nextTitle = payload.title || existingTitle;
            set((state) => {
              const idx = state.sessions.findIndex((s) => s.id === currentId);
              if (idx >= 0) {
                state.sessions[idx] = { ...state.sessions[idx], title: nextTitle, lastTime };
              } else {
                state.sessions.unshift({ id: currentId, title: nextTitle, lastTime });
              }
            });
          }
          // 终态解析，锁定 artifact 完成状态
          set((state) => {
            const msg = state.messages.find((m) => m.id === assistantId);
            if (!msg) return;
            if (payload.messageId) msg.id = String(payload.messageId);
            msg.status = "done";
            msg.isThinking = false;
            msg.thinkingDuration = msg.thinkingDuration ?? computeThinkingDuration(state.thinkingStartAt);
            msg.blocks = (msg.blocks ?? []).map((block) => {
              if (block.type === "artifact" && block.artifact) {
                return { ...block, artifact: { ...block.artifact, isComplete: true } };
              }
              return block;
            });
          });
        },
        onCancel: (payload: CompletionPayload) => {
          if (get().streamingMessageId !== assistantId) return;
          buffer.flushImmediate();
          buffer.dispose();
          if (payload?.title && get().currentSessionId) {
            get().updateSessionTitle(get().currentSessionId as string, payload.title);
          }
          set((state) => {
            const msg = state.messages.find((m) => m.id === state.streamingMessageId);
            if (msg) {
              const suffix = msg.rawText?.includes("(Stopped)") ? "" : "\n\n(Stopped)";
              if (payload?.messageId) msg.id = String(payload.messageId);
              msg.rawText = (msg.rawText ?? "") + suffix;
              msg.content = msg.rawText;
              msg.blocks = parseStreamingText(msg.rawText, msg.id);
              msg.status = "cancelled";
              msg.isThinking = false;
              msg.thinkingDuration = msg.thinkingDuration ?? computeThinkingDuration(state.thinkingStartAt);
            }
            state.isStreaming = false;
            state.thinkingStartAt = null;
            state.streamTaskId = null;
            state.streamAbort = null;
            state.streamingMessageId = null;
            state.cancelRequested = false;
          });
        },
        onDone: () => {
          if (get().streamingMessageId !== assistantId) return;
          buffer.flushImmediate();
          buffer.dispose();
          set((state) => {
            state.isStreaming = false;
            state.thinkingStartAt = null;
            state.streamTaskId = null;
            state.streamAbort = null;
            state.streamingMessageId = null;
            state.cancelRequested = false;
            const msg = state.messages.find((m) => m.id === assistantId);
            if (msg) {
              if (!msg.content.trim() && msg.status !== "cancelled" && msg.status !== "error") {
                msg.content = EMPTY_ASSISTANT_MESSAGE;
                msg.rawText = EMPTY_ASSISTANT_MESSAGE;
                msg.blocks = [{ type: "text", text: EMPTY_ASSISTANT_MESSAGE }];
              }
              msg.status = "done";
              msg.isThinking = false;
              msg.thinkingDuration = msg.thinkingDuration ?? computeThinkingDuration(state.thinkingStartAt);
            }
          });
        },
        onTitle: (payload: { title: string }) => {
          if (get().streamingMessageId !== assistantId) return;
          if (payload?.title && get().currentSessionId) {
            get().updateSessionTitle(get().currentSessionId as string, payload.title);
          }
        },
        onError: (error: Error) => {
          if (get().streamingMessageId !== assistantId) return;
          buffer.flushImmediate();
          buffer.dispose();
          const unauthorized = (error as Error & { status?: number }).status === 401;
          set((state) => {
            const msg = state.messages.find((m) => m.id === assistantId);
            if (msg) {
              msg.status = "error";
              msg.isThinking = false;
              msg.thinkingDuration = msg.thinkingDuration ?? computeThinkingDuration(state.thinkingStartAt);
              msg.error = error.message;
            }
            state.isStreaming = false;
            state.thinkingStartAt = null;
            state.streamTaskId = null;
            state.streamAbort = null;
            state.cancelRequested = false;
          });
          if (unauthorized) {
            handleUnauthorizedSession(error.message);
            return;
          }
          toast.error(error.message || "Generation failed");
        }
      };

      const { start, cancel } = createStreamResponse(
        {
          url,
          headers: token ? { Authorization: token } : undefined,
          retryCount: 1
        },
        handlers
      );

      set((s) => { s.streamAbort = cancel; });

      try {
        await start();
      } catch (error) {
        if ((error as Error).name === "AbortError") {
          return;
        }
        handlers.onError?.(error as Error);
      } finally {
        if (get().streamingMessageId === assistantId) {
          buffer.dispose();
          set((s) => {
            s.isStreaming = false;
            s.streamTaskId = null;
            s.streamAbort = null;
            s.streamingMessageId = null;
            s.cancelRequested = false;
          });
        }
      }
    },

    cancelGeneration: () => {
      const { isStreaming, streamTaskId } = get();
      if (!isStreaming) return;
      set((s) => { s.cancelRequested = true; });
      if (streamTaskId) {
        stopTask(streamTaskId).catch(() => null);
      }
    },

    appendStreamContent: (delta) => {
      if (!delta) return;
      set((state) => {
        const shouldFinalizeThinking = state.thinkingStartAt != null;
        const duration = computeThinkingDuration(state.thinkingStartAt);
        state.thinkingStartAt = shouldFinalizeThinking ? null : state.thinkingStartAt;
        const msg = state.messages.find((m) => m.id === state.streamingMessageId);
        if (msg && msg.status !== "cancelled" && msg.status !== "error") {
          msg.rawText = (msg.rawText ?? "") + delta;
          msg.content = msg.rawText;
          msg.blocks = parseStreamingText(msg.rawText, msg.id);
          if (shouldFinalizeThinking) {
            msg.isThinking = false;
            if (!msg.thinkingDuration) msg.thinkingDuration = duration;
          }
        }
      });
    },

    appendThinkingContent: (delta) => {
      if (!delta) return;
      set((state) => {
        state.thinkingStartAt = state.thinkingStartAt ?? Date.now();
        const msg = state.messages.find((m) => m.id === state.streamingMessageId);
        if (msg && msg.status !== "cancelled" && msg.status !== "error") {
          msg.thinking = `${msg.thinking ?? ""}${delta}`;
          msg.isThinking = true;
        }
      });
    },

    submitFeedback: async (messageId, feedback) => {
      const vote = feedback === "like" ? 1 : feedback === "dislike" ? -1 : null;
      const prev = get().messages.find((message) => message.id === messageId)?.feedback ?? null;
      set((state) => {
        const msg = state.messages.find((m) => m.id === messageId);
        if (msg) msg.feedback = feedback;
      });
      if (vote === null) {
        toast.success("Feedback cleared");
        return;
      }
      try {
        await submitFeedback(messageId, vote);
        toast.success(feedback === "like" ? "Liked" : "Disliked");
      } catch (error) {
        set((state) => {
          const msg = state.messages.find((m) => m.id === messageId);
          if (msg) msg.feedback = prev;
        });
        toast.error((error as Error).message || "Failed to save feedback");
      }
    }
  }))
);
