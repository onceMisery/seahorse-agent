export type TaskType = "quick_chat" | "agent_run" | "document_qa" | "knowledge_qa";
export type TaskStatus = "pending" | "running" | "succeeded" | "failed" | "cancelled";

export interface Task {
  taskId: string;
  type: TaskType;
  status: TaskStatus;
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
  type: TaskType;
  question: string;
  conversationId?: string;
  agentId?: string;
  title?: string;
  knowledgeBaseId?: string;
  attachmentIds?: string[];
  mode?: string;
}

export interface TaskEvent {
  seq: number;
  type: string;
  message: string;
  data: Record<string, unknown>;
  at: string;
}

export interface TaskArtifact {
  artifactId: string;
  taskId: string;
  runId: string | null;
  type: "markdown" | "mermaid" | "file" | "citation" | "trace" | "image";
  title: string;
  mimeType: string;
  content: string | null;
  canPreview: boolean;
  downloadUrl: string;
  createdAt: string;
}
