import * as React from "react";
import { Brain, ChevronDown } from "lucide-react";

import { FeedbackButtons } from "@/components/chat/FeedbackButtons";
import { MessageContent } from "@/components/chat/MessageContent";
import { ThinkingIndicator } from "@/components/chat/ThinkingIndicator";
import { cn } from "@/lib/utils";
import type { Message } from "@/types";

interface MessageItemProps {
  message: Message;
  isLast?: boolean;
}

const EMPTY_RESULT_TEXT = "本次对话没有返回有效内容，请稍后重试。";

export const MessageItem = React.memo(function MessageItem({ message, isLast }: MessageItemProps) {
  const isUser = message.role === "user";
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
    <div className="group flex">
      <div className="min-w-0 flex-1 space-y-4">
        {isThinking ? (
          <ThinkingIndicator content={message.thinking} duration={message.thinkingDuration} />
        ) : null}
        {!isThinking && hasThinking ? (
          <div
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
            {thinkingExpanded ? (
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
            ) : null}
          </div>
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
          {hasContent ? <MessageContent blocks={message.blocks ?? []} rawText={message.content} /> : null}
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
    </div>
  );
});
