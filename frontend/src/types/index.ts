export type Role = "user" | "assistant";

export type FeedbackValue = "like" | "dislike" | null;

export const FEEDBACK_REASONS = {
  INCORRECT: "INCORRECT",
  NO_CITATION: "NO_CITATION",
  OUTDATED_SOURCE: "OUTDATED_SOURCE",
  TOO_SLOW: "TOO_SLOW",
  FORMAT_BAD: "FORMAT_BAD",
  TASK_INCOMPLETE: "TASK_INCOMPLETE",
  UNSAFE: "UNSAFE",
  OTHER: "OTHER"
} as const;

export type FeedbackReason = (typeof FEEDBACK_REASONS)[keyof typeof FEEDBACK_REASONS];

export type MessageStatus = "streaming" | "done" | "cancelled" | "error";

export interface User {
  userId: string;
  username?: string;
  role: string;
  token: string;
  avatar?: string;
}

export type CurrentUser = Omit<User, "token">;

export interface Session {
  id: string;
  title: string;
  lastTime?: string;
}

export type ArtifactLanguage = "html" | "css" | "javascript" | "js" | "tsx" | "vue" | "markdown";

export interface ArtifactBlock {
  id: string;
  language: ArtifactLanguage;
  title: string;
  code: string;
  isComplete: boolean;
}

export const AGENT_STREAM_EVENTS = {
  TIMELINE: "agent.timeline",
  SOURCE: "agent.source",
  ARTIFACT: "agent.artifact",
  APPROVAL: "agent.approval",
  QUOTA: "agent.quota",
  MEMORY: "agent.memory",
  RUN_STARTED: "run_started",
  RUN_SNAPSHOT: "run_snapshot",
  STEP_STARTED: "step_started",
  STEP_PROGRESS: "step_progress",
  STEP_FINISHED: "step_finished",
  TOOL_CALL_STARTED: "tool_call_started",
  TOOL_CALL_WAITING_USER: "tool_call_waiting_user",
  SOURCE_FOUND: "source_found",
  ARTIFACT_CREATED: "artifact_created",
  ARTIFACT_CONTENT: "artifact_content",
  ARTIFACT_COMPLETE: "artifact_complete",
  RECOVERABLE_ERROR: "recoverable_error"
} as const;

export type AgentStreamEvent = (typeof AGENT_STREAM_EVENTS)[keyof typeof AGENT_STREAM_EVENTS];

export const AGENT_APPROVAL_STATUS = {
  PENDING: "pending",
  APPROVED: "approved",
  REJECTED: "rejected",
  MODIFIED: "modified",
  ERROR: "error"
} as const;

export type AgentApprovalStatus = (typeof AGENT_APPROVAL_STATUS)[keyof typeof AGENT_APPROVAL_STATUS];

export interface AgentTimelineItem {
  id: string;
  title: string;
  status?: string;
  detail?: string;
  timestamp?: string;
  durationMs?: number;
}

export interface AgentSource {
  id: string;
  title: string;
  url?: string;
  snippet?: string;
  score?: number;
  sourceType?: string;
}

export interface AgentApproval {
  id: string;
  title: string;
  description?: string;
  status: AgentApprovalStatus;
  requestedBy?: string;
  argumentsPreviewJson?: string;
}

export const AGENT_ARTIFACT_SCAN_STATUS = {
  PENDING: "PENDING",
  CLEAN: "CLEAN",
  BLOCKED: "BLOCKED"
} as const;

export type AgentArtifactScanStatus =
  (typeof AGENT_ARTIFACT_SCAN_STATUS)[keyof typeof AGENT_ARTIFACT_SCAN_STATUS];

export const AGENT_ARTIFACT_DISPOSITION = {
  INLINE_PREVIEW: "INLINE_PREVIEW",
  ATTACHMENT_DOWNLOAD: "ATTACHMENT_DOWNLOAD"
} as const;

export type AgentArtifactDisposition =
  (typeof AGENT_ARTIFACT_DISPOSITION)[keyof typeof AGENT_ARTIFACT_DISPOSITION];

export interface AgentArtifact {
  artifactId: string;
  runId?: string | null;
  messageId?: string | null;
  tenantId?: string | null;
  userId?: string | null;
  artifactType?: string | null;
  title?: string | null;
  mimeType?: string | null;
  storageRef?: string | null;
  previewText?: string | null;
  provenanceJson?: string | null;
  scanStatus?: AgentArtifactScanStatus | string | null;
  createdAt?: string | null;
  canPreview?: boolean;
  disposition?: AgentArtifactDisposition | string | null;
}

export interface AgentRunSnapshotStep {
  stepId: string;
  stepNo?: number;
  stepType?: string;
  status?: string;
  summary?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
}

export interface AgentRunSnapshotSource {
  itemId: string;
  contextPackId?: string;
  sourceType?: string;
  sourceId?: string;
  summary?: string | null;
  score?: number;
  confidence?: number;
  sensitivity?: string;
  citationJson?: string | null;
  title?: string | null;
  url?: string | null;
  snippet?: string | null;
  confidenceLevel?: string | null;
  supportingConclusion?: string | null;
  fetchedAt?: string | null;
  citationIndex?: number;
}

export interface AgentRunMessageSnapshot {
  assistantMessageId?: string | null;
  content?: string | null;
  thinking?: string | null;
}

export interface AgentRunSnapshot {
  run?: {
    runId?: string;
    status?: string;
    conversationId?: string | null;
    inputSummary?: string | null;
    errorCode?: string | null;
    errorMessage?: string | null;
    startedAt?: string | null;
    finishedAt?: string | null;
  };
  steps?: AgentRunSnapshotStep[];
  messageSnapshot?: AgentRunMessageSnapshot | null;
  currentStepId?: string | null;
  sources?: AgentRunSnapshotSource[];
  artifacts?: AgentArtifact[];
  pendingApprovals?: unknown[];
  lastEventSeq?: number;
  canResume?: boolean;
  canRetry?: boolean;
}

export interface AgentRunCostSummary {
  tenantId: string;
  agentId?: string | null;
  runId?: string | null;
  totalTokens: number;
  totalCalls: number;
  totalCost: number;
  recordCount: number;
}

export interface AgentQuota {
  id: string;
  label: string;
  used?: number;
  limit?: number;
  unit?: string;
  remaining?: number;
}

export interface AgentMemory {
  id: string;
  title: string;
  content?: string;
  action?: string;
}

export type ContentBlock =
  | { type: "text"; text: string }
  | { type: "artifact"; artifact: ArtifactBlock };

export interface Message {
  id: string;
  role: Role;
  content: string;
  agentRunId?: string;
  thinking?: string;
  thinkingDuration?: number;
  isDeepThinking?: boolean;
  isThinking?: boolean;
  createdAt?: string;
  feedback?: FeedbackValue;
  status?: MessageStatus;
  blocks?: ContentBlock[];
  rawText?: string;
  error?: string;
  agentRunStatus?: string;
  currentStepId?: string;
  lastEventSeq?: number;
  canResume?: boolean;
  canRetry?: boolean;
  sources?: AgentSource[];
  timeline?: AgentTimelineItem[];
  artifacts?: ArtifactBlock[];
  serverArtifacts?: AgentArtifact[];
  approvals?: AgentApproval[];
  quota?: AgentQuota[];
  memories?: AgentMemory[];
  costSummary?: AgentRunCostSummary;
}

export interface StreamMetaPayload {
  conversationId: string;
  taskId: string;
  runId?: string | null;
}

export interface MessageDeltaPayload {
  type: string;
  delta: string;
}

export interface CompletionPayload {
  messageId?: string | null;
  title?: string | null;
}

export interface StreamEventEnvelope {
  eventId: string;
  eventSeq: number;
  eventType: string;
  runId: string;
  stepId?: string | null;
  timestamp: string;
  typedPayload: unknown;
}

export type TaskTemplateId = "quick-answer" | "deep-research" | "web-summary" | "compare-analysis";

export type TaskTemplateCategory = "RESEARCH" | "WRITING" | "LEARNING" | "ANALYSIS" | "FILE_QA";

export type TaskTemplateOutputType = "PLAIN_TEXT" | "MARKDOWN_REPORT" | "SOURCE_DIGEST" | "COMPARISON_TABLE";

export type TaskTemplateAvailability = "AVAILABLE" | "UNAVAILABLE";

export type QuotaCostTier = "LOW" | "MEDIUM" | "HIGH";

export type EstimatedDurationTier = "SHORT" | "MEDIUM" | "LONG";

export type QuotaSummaryStatus = "AVAILABLE" | "NEAR_LIMIT" | "EXCEEDED";

export const CONVERSATION_ATTACHMENT_PARSE_STATUS = {
  PENDING: "PENDING",
  PARSED: "PARSED",
  FAILED: "FAILED",
  BLOCKED: "BLOCKED"
} as const;

export type ConversationAttachmentParseStatus =
  (typeof CONVERSATION_ATTACHMENT_PARSE_STATUS)[keyof typeof CONVERSATION_ATTACHMENT_PARSE_STATUS];

export interface ConversationAttachment {
  attachmentId: string;
  conversationId: string;
  messageId?: string | null;
  userId: string;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  storageRef: string;
  parseStatus: ConversationAttachmentParseStatus | string;
  resourceRefJson?: string | null;
  createdAt?: string | null;
}

export interface TaskTemplate {
  templateId: TaskTemplateId | string;
  name: string;
  description?: string | null;
  category?: TaskTemplateCategory | string | null;
  defaultAgentId?: string | null;
  defaultToolPolicyId?: string | null;
  defaultOutputType?: TaskTemplateOutputType | string | null;
  maxCostTier?: QuotaCostTier | string | null;
  estimatedDuration?: EstimatedDurationTier | string | null;
  enabled?: boolean;
  status?: TaskTemplateAvailability | string | null;
}

export interface UserQuotaSummary {
  userId: string;
  tenantId: string;
  status: QuotaSummaryStatus | string;
  callLimit?: number | null;
  usedCalls?: number | null;
  remainingCalls?: number | null;
  costLimit?: number | null;
  usedCost?: number | null;
  remainingCost?: number | null;
  defaultCostTier?: QuotaCostTier | string | null;
  estimatedDuration?: EstimatedDurationTier | string | null;
  message?: string | null;
}

export type UserMemoryType = "PROFILE" | "PREFERENCE" | "PROJECT_CONTEXT" | "LONG_TERM_FACT";

export interface UserMemory {
  memoryId: string;
  memoryType?: UserMemoryType | string | null;
  displayText: string;
  sourceConversationId?: string | null;
  sourceMessageId?: string | null;
  status?: string | null;
  sensitivity?: string | null;
  updatedAt?: string | null;
}

export interface UserMemoryCenterResponse {
  userId: string;
  privacyMode: boolean;
  memories: UserMemory[];
}
