export interface Task {
  taskId: string;
  type: "quick_chat" | "agent_run";
  status: "pending" | "running" | "succeeded" | "failed" | "cancelled";
  conversationId: string | null;
  runId: string | null;
  agentId: string | null;
  title: string | null;
  question: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface CreateTaskRequest {
  type: "quick_chat" | "agent_run";
  question: string;
  conversationId?: string;
  agentId?: string;
  title?: string;
}
