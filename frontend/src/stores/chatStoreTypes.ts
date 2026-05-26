import type { FeedbackReason, FeedbackValue, Message, Session, TaskTemplateId } from "@/types";

export interface SendMessageOptions {
  attachmentIds?: string[];
  conversationIdOverride?: string | null;
}

export interface SubmitFeedbackOptions {
  reason?: FeedbackReason | string | null;
  comment?: string | null;
}

export interface ChatState {
  sessions: Session[];
  currentSessionId: string | null;
  messages: Message[];
  isLoading: boolean;
  sessionsLoaded: boolean;
  inputFocusKey: number;
  isStreaming: boolean;
  isCreatingNew: boolean;
  deepThinkingEnabled: boolean;
  thinkingStartAt: number | null;
  streamTaskId: string | null;
  streamAbort: (() => void) | null;
  streamingMessageId: string | null;
  cancelRequested: boolean;
  selectedTaskTemplateId: TaskTemplateId | string | null;
  fetchSessions: () => Promise<void>;
  createSession: () => Promise<string>;
  deleteSession: (sessionId: string) => Promise<void>;
  renameSession: (sessionId: string, title: string) => Promise<void>;
  selectSession: (sessionId: string) => Promise<void>;
  updateSessionTitle: (sessionId: string, title: string) => void;
  setDeepThinkingEnabled: (enabled: boolean) => void;
  setSelectedTaskTemplateId: (templateId: TaskTemplateId | string | null) => void;
  sendMessage: (content: string, options?: SendMessageOptions) => Promise<void>;
  refreshRunSnapshot: (messageId: string, runId: string) => Promise<void>;
  cancelGeneration: () => void;
  appendStreamContent: (delta: string) => void;
  appendThinkingContent: (delta: string) => void;
  submitFeedback: (messageId: string, feedback: FeedbackValue, options?: SubmitFeedbackOptions) => Promise<void>;
}
