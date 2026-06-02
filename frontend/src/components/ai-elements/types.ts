import type { AgentArtifact, AgentSource, ArtifactBlock, FeedbackValue, Message } from "@/types";

export type AiMessage = Message;
export type AiSource = AgentSource;
export type AiArtifactBlock = ArtifactBlock;
export type AiServerArtifact = AgentArtifact;
export type AiFeedbackValue = FeedbackValue;

export type CodeEditorLanguage =
  | "javascript"
  | "typescript"
  | "python"
  | "json"
  | "html"
  | "css"
  | "markdown"
  | "text";

export type WorkflowStepStatus =
  | "PENDING"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "SKIPPED"
  | "SUCCESS"
  | "ERROR"
  | string;

export interface WorkflowStepNodeData extends Record<string, unknown> {
  label: string;
  status?: WorkflowStepStatus;
  description?: string | null;
  duration?: string;
  stepType?: string;
  stepNo?: number;
  errorMessage?: string | null;
}

export interface WorkflowStep {
  stepId?: string;
  stepNo?: number;
  stepType?: string;
  status?: string;
  summary?: string | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
}
