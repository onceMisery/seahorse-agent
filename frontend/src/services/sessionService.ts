import { api } from "@/services/api";

export interface ConversationVO {
  conversationId: string;
  title: string;
  lastTime?: string;
}

export interface ConversationMessageVO {
  id: number | string;
  conversationId: string;
  userId?: number | string;
  role: string;
  content: string;
  agentRunId?: string | null;
  runId?: string | null;
  thinkingContent?: string | null;
  thinkingDuration?: number | null;
  parentId?: number | string | null;
  active?: number | null;
  branchRootId?: number | string | null;
  siblingSeq?: number | null;
  vote: number | null;
  createTime?: string;
}

export interface MessageTreeNodeVO {
  message: ConversationMessageVO;
  preSiblings: Array<number | string>;
  nextSiblings: Array<number | string>;
  branchIndex: number;
  branchTotal: number;
}

export interface ConversationForkRequest {
  anchorMessageId: number | string;
  content: string;
  role: "user" | "assistant";
  regenerate?: boolean;
}

export interface ConversationForkResult {
  newMessageId: number | string;
  parentId?: number | string | null;
}

export interface ConversationBranchCursor {
  id?: number | string;
  tenantId?: string;
  conversationId: string;
  userId?: number | string;
  leafMessageId: number | string;
  createTime?: string;
  updateTime?: string;
}

export async function createSession(): Promise<string> {
  const response = await api.post<string>("/conversations");
  return response;
}

export async function listSessions() {
  return api.get<ConversationVO[]>("/conversations");
}

export async function deleteSession(conversationId: string) {
  return api.delete<void>(`/conversations/${conversationId}`);
}

export async function renameSession(conversationId: string, title: string) {
  return api.put<void>(`/conversations/${conversationId}`, { title });
}

export async function listMessages(conversationId: string) {
  return api.get<ConversationMessageVO[]>(`/conversations/${conversationId}/messages`);
}

export async function listMessageTree(conversationId: string) {
  return api.get<MessageTreeNodeVO[]>(`/conversations/${conversationId}/messages/tree`);
}

export async function saveBranchCursor(conversationId: string, leafMessageId: string) {
  return api.post<ConversationBranchCursor>(`/conversations/${conversationId}/branch-cursor`, {
    leafMessageId
  });
}

export async function loadBranchCursor(conversationId: string) {
  return api.get<ConversationBranchCursor | undefined>(`/conversations/${conversationId}/branch-cursor`);
}

export async function switchMessageBranch(conversationId: string, targetNodeId: string) {
  await saveBranchCursor(conversationId, targetNodeId);
  return listMessageTree(conversationId);
}

export async function forkMessage(conversationId: string, request: ConversationForkRequest) {
  return api.post<ConversationForkResult>(`/conversations/${conversationId}/messages/fork`, request);
}
