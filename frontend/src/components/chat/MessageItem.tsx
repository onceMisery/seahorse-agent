import * as React from "react";
import { AnimatePresence, motion } from "motion/react";
import { Brain, ChevronDown, ScanSearch } from "lucide-react";

import { AgentLiveStatus } from "@/components/chat/AgentLiveStatus";
import { AgentTracePanel } from "@/components/chat/AgentTracePanel";
import { FeedbackButtons } from "@/components/chat/FeedbackButtons";
import { MessageContent } from "@/components/chat/MessageContent";
import { ThinkingIndicator } from "@/components/chat/ThinkingIndicator";
import { cn } from "@/lib/utils";
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
  const isThinking = Boolean(message.isThinking);
  const [thinkingExpanded, setThinkingExpanded] = React.useState(false);
  const hasThinking = Boolean(message.thinking && message.thinking.trim().length > 0);
  const hasContent = message.content.trim().length > 0;
  const isWaiting = message.status === "streaming" && !isThinking && !hasContent;
  const showEmptyResult = !hasContent && message.status === "done";

  if (isUser) {
    return (
      <div className="flex">
        <div className="user-message">
          <p className="whitespace-pre-wrap break-words">{message.content}</p>
        </div>
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

        <div className="space-y-2">
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
            <FeedbackButtons
              messageId={message.id}
              feedback={message.feedback ?? null}
              content={message.content}
              alwaysVisible={Boolean(isLast)}
            />
          ) : null}
        </div>
      </div>
    </motion.div>
  );
});
