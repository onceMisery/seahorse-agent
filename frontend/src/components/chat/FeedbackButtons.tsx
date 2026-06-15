import * as React from "react";
import { Copy, ThumbsDown, ThumbsUp } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { useChatStore } from "@/stores/chatStore";
import { FEEDBACK_REASONS, type FeedbackReason, type FeedbackValue } from "@/types";

interface FeedbackButtonsProps {
  messageId: string;
  feedback: FeedbackValue;
  content: string;
  className?: string;
  alwaysVisible?: boolean;
}

const FEEDBACK_REASON_LABELS: Record<FeedbackReason, string> = {
  INCORRECT: "回答不正确",
  NO_CITATION: "缺少引用",
  OUTDATED_SOURCE: "来源过期",
  TOO_SLOW: "响应太慢",
  FORMAT_BAD: "格式不佳",
  TASK_INCOMPLETE: "任务未完成",
  UNSAFE: "响应不安全",
  OTHER: "其他"
};

export function FeedbackButtons({
  messageId,
  feedback,
  content,
  className,
  alwaysVisible
}: FeedbackButtonsProps) {
  const submitFeedback = useChatStore((state) => state.submitFeedback);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [reason, setReason] = React.useState<FeedbackReason>(FEEDBACK_REASONS.INCORRECT);
  const [comment, setComment] = React.useState("");
  const [submitting, setSubmitting] = React.useState(false);

  const handleLike = () => {
    const next = feedback === "like" ? null : "like";
    submitFeedback(messageId, next).catch(() => null);
  };

  const handleDislike = () => {
    if (feedback === "dislike") {
      submitFeedback(messageId, null).catch(() => null);
      return;
    }
    setDialogOpen(true);
  };

  const handleDislikeSubmit = async () => {
    setSubmitting(true);
    try {
      await submitFeedback(messageId, "dislike", {
        reason,
        comment: comment.trim() || null
      });
      setDialogOpen(false);
      setComment("");
    } finally {
      setSubmitting(false);
    }
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      toast.success("已复制");
    } catch {
      toast.error("复制失败");
    }
  };

  return (
    <>
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
          aria-label="复制回答"
          className="h-8 w-8 transition-colors"
          style={{ color: "var(--theme-text-muted)" }}
        >
          <Copy className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          onClick={handleLike}
          aria-label="点赞回答"
          className="h-8 w-8 transition-colors"
          style={{ color: feedback === "like" ? "#10b981" : "var(--theme-text-muted)" }}
        >
          <ThumbsUp className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          onClick={handleDislike}
          aria-label="点踩回答"
          className="h-8 w-8 transition-colors"
          style={{ color: feedback === "dislike" ? "#ef4444" : "var(--theme-text-muted)" }}
        >
          <ThumbsDown className="h-4 w-4" />
        </Button>
      </div>
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:rounded-2xl">
          <DialogHeader>
            <DialogTitle>告诉我们哪里有问题</DialogTitle>
            <DialogDescription>
              这条反馈会随消息保存，便于评测定位真实失败。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor={`feedback-reason-${messageId}`}>
                原因
              </label>
              <Select value={reason} onValueChange={(next) => setReason(next as FeedbackReason)}>
                <SelectTrigger id={`feedback-reason-${messageId}`}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {Object.values(FEEDBACK_REASONS).map((item) => (
                    <SelectItem key={item} value={item}>
                      {FEEDBACK_REASON_LABELS[item]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor={`feedback-comment-${messageId}`}>
                备注
              </label>
              <Textarea
                id={`feedback-comment-${messageId}`}
                value={comment}
                onChange={(event) => setComment(event.target.value)}
                placeholder="可选补充说明"
                rows={4}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setDialogOpen(false)} disabled={submitting}>
              取消
            </Button>
            <Button onClick={handleDislikeSubmit} disabled={submitting}>
              {submitting ? "保存中" : "提交"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
