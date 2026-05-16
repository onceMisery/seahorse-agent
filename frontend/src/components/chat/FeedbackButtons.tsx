import { Copy, ThumbsDown, ThumbsUp } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useChatStore } from "@/stores/chatStore";
import type { FeedbackValue } from "@/types";

interface FeedbackButtonsProps {
  messageId: string;
  feedback: FeedbackValue;
  content: string;
  className?: string;
  alwaysVisible?: boolean;
}

export function FeedbackButtons({
  messageId,
  feedback,
  content,
  className,
  alwaysVisible
}: FeedbackButtonsProps) {
  const submitFeedback = useChatStore((state) => state.submitFeedback);

  const handleFeedback = (value: FeedbackValue) => {
    const next = feedback === value ? null : value;
    submitFeedback(messageId, next).catch(() => null);
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      toast.success("复制成功");
    } catch {
      toast.error("复制失败");
    }
  };

  return (
    <div
      className={cn(
        "flex items-center gap-1 transition-opacity",
        alwaysVisible ? "opacity-100" : "opacity-0 group-hover:opacity-100",
        className
      )}
    >
      <Button
        variant="ghost"
        size="icon"
        onClick={handleCopy}
        aria-label="复制内容"
        className="h-8 w-8 transition-colors"
        style={{ color: "var(--theme-text-muted)" }}
      >
        <Copy className="h-4 w-4" />
      </Button>
      <Button
        variant="ghost"
        size="icon"
        onClick={() => handleFeedback("like")}
        aria-label="点赞"
        className="h-8 w-8 transition-colors"
        style={{ color: feedback === "like" ? "#10b981" : "var(--theme-text-muted)" }}
      >
        <ThumbsUp className="h-4 w-4" />
      </Button>
      <Button
        variant="ghost"
        size="icon"
        onClick={() => handleFeedback("dislike")}
        aria-label="点踩"
        className="h-8 w-8 transition-colors"
        style={{ color: feedback === "dislike" ? "#ef4444" : "var(--theme-text-muted)" }}
      >
        <ThumbsDown className="h-4 w-4" />
      </Button>
    </div>
  );
}
