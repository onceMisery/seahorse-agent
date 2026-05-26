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
  INCORRECT: "Incorrect answer",
  NO_CITATION: "Missing citations",
  OUTDATED_SOURCE: "Outdated source",
  TOO_SLOW: "Too slow",
  FORMAT_BAD: "Poor format",
  TASK_INCOMPLETE: "Incomplete task",
  UNSAFE: "Unsafe response",
  OTHER: "Other"
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
      toast.success("Copied");
    } catch {
      toast.error("Copy failed");
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
          aria-label="Copy answer"
          className="h-8 w-8 transition-colors"
          style={{ color: "var(--theme-text-muted)" }}
        >
          <Copy className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          onClick={handleLike}
          aria-label="Like answer"
          className="h-8 w-8 transition-colors"
          style={{ color: feedback === "like" ? "#10b981" : "var(--theme-text-muted)" }}
        >
          <ThumbsUp className="h-4 w-4" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          onClick={handleDislike}
          aria-label="Dislike answer"
          className="h-8 w-8 transition-colors"
          style={{ color: feedback === "dislike" ? "#ef4444" : "var(--theme-text-muted)" }}
        >
          <ThumbsDown className="h-4 w-4" />
        </Button>
      </div>
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:rounded-2xl">
          <DialogHeader>
            <DialogTitle>Tell us what went wrong</DialogTitle>
            <DialogDescription>
              This feedback is saved with the message so evaluation can target real failures.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor={`feedback-reason-${messageId}`}>
                Reason
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
                Comment
              </label>
              <Textarea
                id={`feedback-comment-${messageId}`}
                value={comment}
                onChange={(event) => setComment(event.target.value)}
                placeholder="Optional detail"
                rows={4}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setDialogOpen(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button onClick={handleDislikeSubmit} disabled={submitting}>
              {submitting ? "Saving" : "Submit"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
