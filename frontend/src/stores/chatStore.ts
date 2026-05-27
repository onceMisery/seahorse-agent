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
  "compare-analysis"
]);

function chatModeForTaskTemplate(templateId?: TaskTemplateId | string | null) {
  return templateId && CONTROLLED_WEB_AGENT_TEMPLATE_IDS.has(templateId as TaskTemplateId)
    ? CONTROLLED_WEB_AGENT_CHAT_MODE
    : undefined;
}

function mergeById<T extends { id: string }>(current: T[] | undefined, incoming: T[]) {
  const map = new Map<string, T>();
  for (const item of current ?? []) map.set(item.id, item);
  for (const item of incoming) map.set(item.id, { ...map.get(item.id), ...item });
  return Array.from(map.values());
}

function mergeArtifacts(current: ArtifactBlock[] | undefined, incoming: ArtifactBlock[]) {
  return mergeById(current, incoming);
}

function mergeServerArtifacts(current: AgentArtifact[] | undefined, incoming: AgentArtifact[]) {
  const map = new Map<string, AgentArtifact>();
  for (const item of current ?? []) map.set(item.artifactId, item);
  for (const item of incoming) map.set(item.artifactId, { ...map.get(item.artifactId), ...item });
  return Array.from(map.values());
}

function durationMs(startedAt?: string | null, finishedAt?: string | null) {
  if (!startedAt || !finishedAt) return undefined;
  const started = new Date(startedAt).getTime();
  const finished = new Date(finishedAt).getTime();
  if (!Number.isFinite(started) || !Number.isFinite(finished) || finished < started) return undefined;
  return finished - started;
}

function snapshotTimeline(snapshot: AgentRunSnapshot): AgentTimelineItem[] {
  const runId = snapshot.run?.runId;
  const items: AgentTimelineItem[] = [];
  if (runId) {
    items.push({
      id: `run-started-${runId}`,
      title: "Run started",
      status: snapshot.run?.status,
      detail: snapshot.run?.inputSummary ?? undefined,
      timestamp: snapshot.run?.startedAt ?? undefined,
      durationMs: durationMs(snapshot.run?.startedAt, snapshot.run?.finishedAt)
    });
  }
  for (const step of snapshot.steps ?? []) {
    const title = step.stepType?.replace(/_/g, " ") || `Step ${step.stepNo ?? items.length + 1}`;
    items.push({
      id: step.stepId,
      title,
      status: step.status,
      detail: step.summary ?? step.errorMessage ?? step.errorCode ?? undefined,
      timestamp: step.finishedAt ?? step.startedAt ?? undefined,
      durationMs: durationMs(step.startedAt, step.finishedAt)
    });
  }
  return items;
}

function snapshotSources(snapshot: AgentRunSnapshot): AgentSource[] {
  return (snapshot.sources ?? []).map((source, index) => ({
    id: source.itemId || source.sourceId || `snapshot-source-${index}`,
    title: source.sourceId || source.sourceType || source.itemId || `Source ${index + 1}`,
    snippet: source.summary ?? undefined,
    score: typeof source.confidence === "number" ? source.confidence : source.score,
    sourceType: source.sourceType
  }));
}

function snapshotServerArtifacts(snapshot: AgentRunSnapshot): AgentArtifact[] {
  return snapshot.artifacts ?? [];
}

function snapshotApprovals(snapshot: AgentRunSnapshot): AgentApproval[] {
  const normalized = normalizeAgentStreamEvent(AGENT_STREAM_EVENTS.APPROVAL, snapshot.pendingApprovals ?? []);
  return normalized?.type === AGENT_STREAM_EVENTS.APPROVAL ? normalized.items : [];
}

function mergeRunSnapshot(message: Message, snapshot: AgentRunSnapshot) {
  const messageSnapshot = snapshot.messageSnapshot;
  if (messageSnapshot?.assistantMessageId) {
    message.id = String(messageSnapshot.assistantMessageId);
  }
  if (messageSnapshot?.content && !message.rawText?.trim()) {
    message.rawText = messageSnapshot.content;
    message.content = messageSnapshot.content;
    const parsed = parseMessageBlocks(message.rawText, message.id);
    message.blocks = parsed.blocks;
    message.artifacts = mergeArtifacts(message.artifacts, parsed.artifacts);
  }
  if (messageSnapshot?.thinking && !message.thinking?.trim()) {
    message.thinking = messageSnapshot.thinking;
    message.isDeepThinking = true;
    message.isThinking = false;
  }
  message.agentRunId = snapshot.run?.runId ?? message.agentRunId;
  message.agentRunStatus = snapshot.run?.status ?? message.agentRunStatus;
  message.currentStepId = snapshot.currentStepId ?? message.currentStepId;
  message.lastEventSeq = snapshot.lastEventSeq ?? message.lastEventSeq;
  message.canResume = snapshot.canResume ?? message.canResume;
  message.canRetry = snapshot.canRetry ?? message.canRetry;
  message.timeline = mergeById(message.timeline, snapshotTimeline(snapshot));
  message.sources = mergeById(message.sources, snapshotSources(snapshot));
  message.serverArtifacts = mergeServerArtifacts(message.serverArtifacts, snapshotServerArtifacts(snapshot));
  message.approvals = mergeById(message.approvals, snapshotApprovals(snapshot));
}

function parseMessageBlocks(rawText: string | undefined, messageId: string) {
  const blocks = parseStreamingText(rawText ?? "", messageId);
  return {
    blocks,
    artifacts: extractArtifactsFromBlocks(blocks)
  };
}

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
    selectedTaskTemplateId: null,

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
        const mapped: Message[] = data.map((item) => {
          const messageId = String(item.id);
          const parsed = item.role === "assistant" ? parseMessageBlocks(item.content, messageId) : null;
          return {
            id: messageId,
            role: item.role === "assistant" ? "assistant" : "user",
            content: item.content,
            rawText: item.content,
            thinking: item.thinkingContent || undefined,
            thinkingDuration: item.thinkingDuration || undefined,
            isDeepThinking: Boolean(item.thinkingContent),
            createdAt: item.createTime,
            feedback: mapVoteToFeedback(item.vote),
            status: "done",
            blocks: parsed?.blocks,
            artifacts: parsed?.artifacts,
            agentRunId: item.agentRunId ?? item.runId ?? undefined
          };
        });
        set((s) => { s.messages = mapped; });
        mapped
          .filter((message) => message.agentRunId)
          .forEach((message) => {
            get().refreshRunSnapshot(message.id, message.agentRunId as string).catch(() => null);
          });
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

    setSelectedTaskTemplateId: (templateId) => {
      set((s) => { s.selectedTaskTemplateId = templateId; });
    },

    sendMessage: async (content, options = {}) => {
      const trimmed = content.trim();
      if (!trimmed) return;
      if (get().isStreaming) return;
      const deepThinkingEnabled = get().deepThinkingEnabled;
      const selectedTaskTemplateId = get().selectedTaskTemplateId;
      const attachmentIds = Array.from(new Set(options.attachmentIds ?? [])).filter(Boolean);
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
        attachmentIds
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
          const parsed = parseMessageBlocks(msg.rawText, msg.id);
          msg.blocks = parsed.blocks;
          msg.artifacts = mergeArtifacts(msg.artifacts, parsed.artifacts);
        });
      });

      const mergeAgentEvent = (event: string, payload: unknown) => {
        if (get().streamingMessageId !== assistantId) return;
        if (event === AGENT_STREAM_EVENTS.RUN_SNAPSHOT && payload && typeof payload === "object") {
          set((state) => {
            const msg = state.messages.find((m) => m.id === assistantId);
            if (!msg || msg.status === "cancelled" || msg.status === "error") return;
            mergeRunSnapshot(msg, payload as AgentRunSnapshot);
          });
          return;
        }
        const normalized = normalizeAgentStreamEvent(event, payload);
        if (!normalized || (normalized.items.length === 0 && normalized.type !== AGENT_STREAM_EVENTS.ARTIFACT)) {
          return;
        }
        set((state) => {
          const msg = state.messages.find((m) => m.id === assistantId);
          if (!msg || msg.status === "cancelled" || msg.status === "error") return;
          switch (normalized.type) {
            case AGENT_STREAM_EVENTS.TIMELINE:
              msg.timeline = mergeById(msg.timeline, normalized.items);
              break;
            case AGENT_STREAM_EVENTS.SOURCE:
              msg.sources = mergeById(msg.sources, normalized.items);
              break;
            case AGENT_STREAM_EVENTS.ARTIFACT:
              msg.artifacts = mergeArtifacts(msg.artifacts, normalized.items);
              msg.serverArtifacts = mergeServerArtifacts(msg.serverArtifacts, normalized.serverArtifacts);
              break;
            case AGENT_STREAM_EVENTS.APPROVAL:
              msg.approvals = mergeById(msg.approvals, normalized.items);
              break;
            case AGENT_STREAM_EVENTS.QUOTA:
              msg.quota = mergeById(msg.quota, normalized.items);
              break;
            case AGENT_STREAM_EVENTS.MEMORY:
              msg.memories = mergeById(msg.memories, normalized.items);
              break;
            default:
              break;
          }
        });
      };

      const handlers = {
        onStreamEvent: (envelope: StreamEventEnvelope) => {
          if (get().streamingMessageId !== assistantId) return;
          set((state) => {
            const msg = state.messages.find((m) => m.id === assistantId);
            if (msg) {
              msg.lastEventSeq = envelope.eventSeq;
            }
          });
          const convId = get().currentSessionId;
          if (convId && envelope.runId) {
            try {
              localStorage.setItem(`${convId}_lastRunId`, envelope.runId);
              localStorage.setItem(`${convId}_lastEventSeq`, String(envelope.eventSeq));
            } catch {
              // localStorage may be unavailable
            }
          }
        },
        onEvent: (event: string, payload: unknown) => {
          mergeAgentEvent(event, payload);
        },
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
          if (payload.runId) {
            get().refreshRunSnapshot(assistantId, payload.runId).catch(() => null);
            listPendingApprovalRequests(payload.runId)
              .then((approvals) => mergeAgentEvent(AGENT_STREAM_EVENTS.APPROVAL, approvals))
              .catch(() => null);
          }
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
            msg.artifacts = mergeArtifacts(msg.artifacts, extractArtifactsFromBlocks(msg.blocks));
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
              const parsed = parseMessageBlocks(msg.rawText, msg.id);
              msg.blocks = parsed.blocks;
              msg.artifacts = mergeArtifacts(msg.artifacts, parsed.artifacts);
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
              msg.artifacts = mergeArtifacts(msg.artifacts, extractArtifactsFromBlocks(msg.blocks));
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

    refreshRunSnapshot: async (messageId, runId) => {
      if (!runId) return;
      const [snapshot, costSummary] = await Promise.all([
        getAgentRunSnapshot(runId),
        getAgentRunCostSummary(runId).catch(() => undefined)
      ]);
      set((state) => {
        const msg = state.messages.find((m) => m.id === messageId || m.agentRunId === runId);
        if (!msg) return;
        mergeRunSnapshot(msg, snapshot);
        if (costSummary) msg.costSummary = costSummary;
      });
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
          const parsed = parseMessageBlocks(msg.rawText, msg.id);
          msg.blocks = parsed.blocks;
          msg.artifacts = mergeArtifacts(msg.artifacts, parsed.artifacts);
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

    submitFeedback: async (messageId, feedback, options = {}) => {
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
        await submitFeedback(messageId, vote, options.reason, options.comment);
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
