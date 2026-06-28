import * as React from "react";
import { AnimatePresence, motion } from "motion/react";
import { Brain, Check, ChevronDown, ChevronLeft, ChevronRight, Pencil, RotateCcw, ScanSearch, X } from "lucide-react";

import { AgentLiveStatus } from "@/components/chat/AgentLiveStatus";
import { AgentTracePanel } from "@/components/chat/AgentTracePanel";
import { FeedbackButtons } from "@/components/chat/FeedbackButtons";
import { MemoryConflictInteractiveCard } from "@/components/chat/MemoryConflictInteractiveCard";
import { MessageContent } from "@/components/chat/MessageContent";
import { ThinkingIndicator } from "@/components/chat/ThinkingIndicator";
import { cn } from "@/lib/utils";
import { useChatStore } from "@/stores/chatStore";
import { useWorkbenchStore } from "@/stores/workbenchStore";
import type { Message } from "@/types";

interface MessageItemProps {
  message: Message;
  isLast?: boolean;
}

const EMPTY_RESULT_TEXT = "本次对话没有返回有效内容，请稍后重试。";

function hasRunData(message: Message) {
  return Boolean(
    message.timeline?.length ||
    message.sources?.length ||
    message.artifacts?.length ||
    message.serverArtifacts?.length ||
    message.approvals?.length ||
    message.quota?.length ||
    message.memories?.length ||
    message.costSummary
  );
}

export const MessageItem = React.memo(function MessageItem({ message, isLast }: MessageItemProps) {
  const isUser = message.role === "user";
  const openInspector = useWorkbenchStore((s) => s.openInspector);
  const showFeedback =
    message.role === "assistant" &&
    message.status !== "streaming" &&
    message.id &&
    !message.id.startsWith("assistant-");
  const canRegenerate =
    message.role === "assistant" &&
    message.status !== "streaming" &&
    Boolean(message.parentId) &&
    !message.id.startsWith("assistant-");
  const isThinking = Boolean(message.isThinking);
  const [thinkingExpanded, setThinkingExpanded] = React.useState(false);
  const hasThinking = Boolean(message.thinking && message.thinking.trim().length > 0);
  const hasContent = message.content.trim().length > 0;
  const isWaiting = message.status === "streaming" && !isThinking && !hasContent;
  const showEmptyResult = !hasContent && message.status === "done";
  const editUserMessageBranch = useChatStore((state) => state.editUserMessageBranch);
  const regenerateAssistantMessageBranch = useChatStore((state) => state.regenerateAssistantMessageBranch);
  const markMemoryConflictPromptResolved = useChatStore((state) => state.markMemoryConflictPromptResolved);
  const [editingUserMessage, setEditingUserMessage] = React.useState(false);
  const [editedUserContent, setEditedUserContent] = React.useState(message.content);

  React.useEffect(() => {
    setEditedUserContent(message.content);
    setEditingUserMessage(false);
  }, [message.id, message.content]);

  if (isUser) {
    const canEdit = message.status !== "streaming" && !message.id.startsWith("user-");
    const saveEdit = () => {
      const trimmed = editedUserContent.trim();
      if (!trimmed || trimmed === message.content.trim()) {
        setEditingUserMessage(false);
        setEditedUserContent(message.content);
        return;
      }
      void editUserMessageBranch(message.id, trimmed);
      setEditingUserMessage(false);
    };
    return (
      <div className="flex flex-col items-end gap-2">
        <div className="flex max-w-full items-end gap-2">
          {canEdit ? (
            <button
              type="button"
              aria-label="Edit message"
              title="Edit message"
              onClick={() => setEditingUserMessage(true)}
              className="rounded p-1 text-xs opacity-70 transition hover:bg-[var(--theme-accent-alpha-10)] hover:opacity-100"
              style={{ color: "var(--theme-text-secondary)" }}
            >
              <Pencil className="h-3.5 w-3.5" />
            </button>
          ) : null}
          <div className="user-message">
            {editingUserMessage ? (
              <div className="flex min-w-[240px] flex-col gap-2">
                <textarea
                  aria-label="Edit message content"
                  value={editedUserContent}
                  onChange={(event) => setEditedUserContent(event.target.value)}
                  className="min-h-20 resize-y rounded-md border bg-transparent p-2 text-sm outline-none"
                  style={{
                    borderColor: "var(--theme-accent-alpha-20)",
                    color: "var(--theme-text-primary)"
                  }}
                />
                <div className="flex justify-end gap-1">
                  <button
                    type="button"
                    aria-label="Cancel edit"
                    title="Cancel edit"
                    onClick={() => {
                      setEditingUserMessage(false);
                      setEditedUserContent(message.content);
                    }}
                    className="rounded p-1 transition hover:bg-[var(--theme-accent-alpha-10)]"
                  >
                    <X className="h-4 w-4" />
                  </button>
                  <button
                    type="button"
                    aria-label="Save edit"
                    title="Save edit"
                    onClick={saveEdit}
                    className="rounded p-1 transition hover:bg-[var(--theme-accent-alpha-10)]"
                  >
                    <Check className="h-4 w-4" />
                  </button>
                </div>
              </div>
            ) : (
              <p className="whitespace-pre-wrap break-words">{message.content}</p>
            )}
          </div>
        </div>
        <BranchSwitcher message={message} />
      </div>
    );
  }

  const thinkingDuration = message.thinkingDuration ? `${message.thinkingDuration}秒` : "";

  return (
    <motion.div
      className="group flex"
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: "easeOut" }}
    >
      <div className="min-w-0 flex-1 space-y-4">
        {isThinking ? (
          <ThinkingIndicator content={message.thinking} duration={message.thinkingDuration} />
        ) : null}
        <AnimatePresence initial={false}>
          {!isThinking && hasThinking ? (
            <motion.div
              key="thinking-collapse"
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: "auto", opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.25 }}
              className="overflow-hidden rounded-lg glass"
              style={{ borderColor: "var(--theme-accent-alpha-30)" }}
            >
              <button
                type="button"
                onClick={() => setThinkingExpanded((prev) => !prev)}
                className="flex w-full items-center gap-2 px-4 py-3 text-left transition-colors"
                style={{ color: "var(--theme-accent)" }}
              >
                <div className="flex flex-1 items-center gap-2">
                  <div
                    className="flex h-7 w-7 items-center justify-center rounded-lg"
                    style={{ backgroundColor: "var(--theme-accent-alpha-20)" }}
                  >
                    <Brain className="h-4 w-4" style={{ color: "var(--theme-accent)" }} />
                  </div>
                  <span className="text-sm font-medium" style={{ color: "var(--theme-accent)" }}>
                    深度思考
                  </span>
                  {thinkingDuration ? (
                    <span
                      className="rounded-full px-2 py-0.5 text-xs"
                      style={{
                        backgroundColor: "var(--theme-accent-alpha-20)",
                        color: "var(--theme-accent)"
                      }}
                    >
                      {thinkingDuration}
                    </span>
                  ) : null}
                </div>
                <ChevronDown
                  className={cn("h-4 w-4 transition-transform", thinkingExpanded && "rotate-180")}
                  style={{ color: "var(--theme-accent)" }}
                />
              </button>
              <AnimatePresence initial={false}>
                {thinkingExpanded ? (
                  <motion.div
                    key="thinking-content"
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: "auto", opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    transition={{ duration: 0.2 }}
                    className="overflow-hidden"
                  >
                    <div
                      className="px-4 pb-4"
                      style={{ borderTop: "1px solid var(--theme-accent-alpha-20)" }}
                    >
                      <div
                        className="mt-3 whitespace-pre-wrap text-sm leading-relaxed"
                        style={{ color: "var(--theme-accent-light)" }}
                      >
                        {message.thinking}
                      </div>
                    </div>
                  </motion.div>
                ) : null}
              </AnimatePresence>
            </motion.div>
          ) : null}
        </AnimatePresence>

        {/* Agent live execution status — shown during streaming */}
        {message.status === "streaming" && (message.timeline?.length ?? 0) > 0 ? (
          <AgentLiveStatus
            timeline={message.timeline!}
            currentStepId={message.currentStepId}
            toolCalls={message.toolCalls}
          />
        ) : null}

        <div className="assistant-message">
          <div className="space-y-3">
            {isWaiting ? (
              <div className="ai-wait" aria-label="思考中">
                <span className="ai-wait-dots" aria-hidden="true">
                  <span className="ai-wait-dot" />
                  <span className="ai-wait-dot" />
                  <span className="ai-wait-dot" />
                </span>
              </div>
            ) : null}
            {hasContent ? <MessageContent blocks={message.blocks ?? []} rawText={message.content} sources={message.sources} /> : null}
            {!isUser ? <AgentTracePanel message={message} /> : null}
            {message.memoryConflictPrompts?.map((prompt) => (
              <MemoryConflictInteractiveCard
                key={prompt.conflictId}
                prompt={prompt}
                onResolved={(conflictId, action) =>
                  markMemoryConflictPromptResolved(message.id, conflictId, action)
                }
              />
            ))}
            <AnimatePresence>
              {!isUser && hasRunData(message) && message.status !== "streaming" ? (
                <motion.button
                  key="run-details-btn"
                  initial={{ opacity: 0, y: 8, scale: 0.95 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: -4, scale: 0.95 }}
                  transition={{ type: "spring", stiffness: 400, damping: 25 }}
                  type="button"
                  onClick={() => openInspector(message.id, "timeline")}
                  className="flex items-center gap-1.5 rounded-md px-2 py-1 text-xs transition-colors"
                  style={{
                    color: "var(--sh-workbench-accent)",
                    backgroundColor: "var(--sh-workbench-accent-soft)"
                  }}
                  aria-label="查看运行详情"
                >
                  <ScanSearch className="h-3.5 w-3.5" />
                  查看运行详情
                </motion.button>
              ) : null}
            </AnimatePresence>
            {showEmptyResult ? (
              <p className="text-sm" style={{ color: "var(--theme-text-muted)" }}>
                {EMPTY_RESULT_TEXT}
              </p>
            ) : null}
            {message.status === "error" ? (
              <p className="text-xs text-rose-400">生成已中断。</p>
            ) : null}
            {showFeedback ? (
              <div className="flex items-center gap-2">
                {canRegenerate ? (
                  <button
                    type="button"
                    aria-label="Regenerate response"
                    title="Regenerate response"
                    onClick={() => regenerateAssistantMessageBranch(message.id)}
                    className="rounded p-1 transition hover:bg-[var(--theme-accent-alpha-10)]"
                    style={{ color: "var(--theme-text-secondary)" }}
                  >
                    <RotateCcw className="h-4 w-4" />
                  </button>
                ) : null}
                <FeedbackButtons
                  messageId={message.id}
                  feedback={message.feedback ?? null}
                  content={message.content}
                  alwaysVisible={Boolean(isLast)}
                />
              </div>
            ) : null}
            <BranchSwitcher message={message} />
          </div>
        </div>
      </div>
    </motion.div>
  );
});

function BranchSwitcher({ message }: { message: Message }) {
  const switchMessageBranch = useChatStore((state) => state.switchMessageBranch);
  const branchTotal = message.branchTotal ?? 1;
  if (branchTotal <= 1 || message.status === "streaming") {
    return null;
  }

  const branchIndex = message.branchIndex ?? 1;
  const previousId = message.preSiblings?.at(-1);
  const nextId = message.nextSiblings?.[0];
  const switchTo = (targetMessageId?: string) => {
    if (!targetMessageId) return;
    void switchMessageBranch(message.id, targetMessageId);
  };

  return (
    <div
      className="flex w-fit items-center gap-1 rounded-md border px-1.5 py-1 text-xs"
      style={{
        borderColor: "var(--theme-accent-alpha-20)",
        backgroundColor: "var(--theme-bg-elevated)",
        color: "var(--theme-text-secondary)"
      }}
    >
      <button
        type="button"
        aria-label="Previous branch"
        title="Previous branch"
        disabled={!previousId}
        onClick={() => switchTo(previousId)}
        className="rounded p-0.5 transition hover:bg-[var(--theme-accent-alpha-10)] active:scale-95 disabled:pointer-events-none disabled:opacity-40"
      >
        <ChevronLeft className="h-3.5 w-3.5" />
      </button>
      <span className="min-w-10 text-center tabular-nums">
        {branchIndex} / {branchTotal}
      </span>
      <button
        type="button"
        aria-label="Next branch"
        title="Next branch"
        disabled={!nextId}
        onClick={() => switchTo(nextId)}
        className="rounded p-0.5 transition hover:bg-[var(--theme-accent-alpha-10)] active:scale-95 disabled:pointer-events-none disabled:opacity-40"
      >
        <ChevronRight className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}
