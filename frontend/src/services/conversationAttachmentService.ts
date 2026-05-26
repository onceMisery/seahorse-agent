import { api } from "@/services/api";
import type { ConversationAttachment } from "@/types";

export async function uploadConversationAttachment(
  conversationId: string,
  file: File
): Promise<ConversationAttachment> {
  const formData = new FormData();
  formData.append("file", file);
  return api.post<ConversationAttachment, ConversationAttachment>(
    `/api/conversations/${encodeURIComponent(conversationId)}/attachments`,
    formData
  );
}

export async function listConversationAttachments(conversationId: string): Promise<ConversationAttachment[]> {
  return api.get<ConversationAttachment[], ConversationAttachment[]>(
    `/api/conversations/${encodeURIComponent(conversationId)}/attachments`
  );
}

export async function deleteConversationAttachment(
  conversationId: string,
  attachmentId: string
): Promise<void> {
  await api.delete(
    `/api/conversations/${encodeURIComponent(conversationId)}/attachments/${encodeURIComponent(attachmentId)}`
  );
}
