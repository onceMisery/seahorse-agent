import { api } from "@/services/api";
import type { FeedbackReason } from "@/types";

export async function stopTask(taskId: string) {
  return api.post<void>(`/rag/v3/stop?taskId=${encodeURIComponent(taskId)}`);
}

export async function submitFeedback(
  messageId: string,
  vote: number,
  reason?: FeedbackReason | string | null,
  comment?: string | null
) {
  return api.post<void>(`/conversations/messages/${messageId}/feedback`, {
    vote,
    reason: reason || undefined,
    comment: comment || undefined
  });
}
