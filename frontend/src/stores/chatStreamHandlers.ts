import {
  AGENT_STREAM_EVENTS,
  type AgentArtifact,
  type AgentApproval,
  type AgentMemory,
  type AgentQuota,
  type AgentRunSnapshot,
  type AgentRunSnapshotSource,
  type AgentRunSnapshotStep,
  type AgentSource,
  type AgentTimelineItem,
  type AgentToolCallView,
  type ArtifactBlock,
  type Message,
  type StreamEventEnvelope
} from "@/types";
import { normalizeAgentStreamEvent } from "@/stores/chatStreamUtils";

type Mergeable = { id: string };
type ServerArtifactMergeable = AgentArtifact & { artifactId: string };

export function mergeById<T extends Mergeable>(
  current: T[] | undefined,
  incoming: T[],
  mergeItem: (current: T, incoming: T) => T = (existing, next) => ({ ...existing, ...next })
): T[] {
  const byId = new Map<string, T>();
  for (const item of current ?? []) byId.set(item.id, item);
  for (const item of incoming) {
    const previous = byId.get(item.id);
    byId.set(item.id, previous ? mergeItem(previous, item) : item);
  }
  return Array.from(byId.values());
}

export function applyAgentStreamEventToMessage(
  message: Message,
  envelope: StreamEventEnvelope
): void {
  if (isStale(message, envelope.eventSeq)) return;
  const normalized = normalizeAgentStreamEvent(envelope.eventType, envelope.typedPayload);
  if (!normalized) {
    message.lastEventSeq = maxSeq(message.lastEventSeq, envelope.eventSeq);
    return;
  }

  switch (normalized.type) {
    case AGENT_STREAM_EVENTS.TIMELINE:
      message.timeline = mergeById(message.timeline, normalized.items);
      break;
    case AGENT_STREAM_EVENTS.SOURCE:
      message.sources = mergeById(message.sources, normalized.items);
      break;
    case AGENT_STREAM_EVENTS.ARTIFACT:
      message.artifacts = mergeArtifacts(message.artifacts, normalized.items);
      message.serverArtifacts = mergeServerArtifacts(message.serverArtifacts, normalized.serverArtifacts);
      break;
    case AGENT_STREAM_EVENTS.APPROVAL:
      message.approvals = mergeById(message.approvals, normalized.items);
      break;
    case AGENT_STREAM_EVENTS.TOOL_CALL_STARTED:
    case AGENT_STREAM_EVENTS.TOOL_CALL_FINISHED:
      message.toolCalls = mergeById(message.toolCalls, normalized.items);
      message.timeline = mergeById(message.timeline, toolCallTimeline(normalized.items));
      break;
    case AGENT_STREAM_EVENTS.TOOL_CALL_WAITING_USER:
      message.toolCalls = mergeById(message.toolCalls, normalized.items);
      message.approvals = mergeById(message.approvals, normalized.approvals);
      message.timeline = mergeById(message.timeline, toolCallTimeline(normalized.items));
      break;
    case AGENT_STREAM_EVENTS.QUOTA:
      message.quota = mergeById(message.quota, normalized.items);
      break;
    case AGENT_STREAM_EVENTS.MEMORY:
      message.memories = mergeById(message.memories, normalized.items);
      break;
  }

  message.agentRunId = message.agentRunId ?? envelope.runId;
  message.currentStepId = envelope.stepId ?? message.currentStepId;
  message.lastEventSeq = maxSeq(message.lastEventSeq, envelope.eventSeq);
}

export function applyAgentRunSnapshotToMessage(
  message: Message,
  snapshot: AgentRunSnapshot
): void {
  const snapshotSeq = snapshot.lastEventSeq;
  const staleSnapshot = isStale(message, snapshotSeq);

  message.agentRunId = snapshot.run?.runId ?? message.agentRunId;
  message.agentRunStatus = snapshot.run?.status ?? message.agentRunStatus;
  message.currentStepId = snapshot.currentStepId ?? message.currentStepId;
  message.canResume = snapshot.canResume ?? message.canResume;
  message.canRetry = snapshot.canRetry ?? message.canRetry;
  message.costSummary = snapshot.costSummary ?? message.costSummary;

  if (!staleSnapshot) {
    const content = snapshot.messageSnapshot?.content;
    if (content) {
      message.content = content;
      message.rawText = content;
    }
    message.timeline = mergeById(message.timeline, snapshotTimeline(snapshot.steps));
    message.sources = mergeById(message.sources, snapshotSources(snapshot.sources));
    message.lastEventSeq = maxSeq(message.lastEventSeq, snapshotSeq);
  }

  const thinking = snapshot.messageSnapshot?.thinking;
  if (thinking) message.thinking = thinking;
  message.serverArtifacts = mergeServerArtifacts(message.serverArtifacts, snapshot.artifacts ?? []);
  message.approvals = mergeById(message.approvals, snapshotApprovals(snapshot.pendingApprovals));
  message.status = snapshotMessageStatus(message.status, snapshot.run?.status);
}

function mergeArtifacts(current: ArtifactBlock[] | undefined, incoming: ArtifactBlock[]) {
  return mergeById(current, incoming, (existing, next) => {
    if (next.append) {
      return stripAppend({
        ...existing,
        ...next,
        language: existing.language,
        title: existing.title,
        code: `${existing.code ?? ""}${next.code ?? ""}`,
        isComplete: next.isComplete
      });
    }
    return stripAppend({
      ...existing,
      ...next,
      language: next.language === "javascript" ? existing.language : next.language,
      title: /^Artifact(?: \d+)?$/.test(next.title) ? existing.title : next.title,
      code: next.code || existing.code,
      isComplete: next.isComplete
    });
  }).map(stripAppend);
}

function mergeServerArtifacts(current: AgentArtifact[] | undefined, incoming: AgentArtifact[]) {
  const byId = new Map<string, ServerArtifactMergeable>();
  for (const item of current ?? []) {
    if (item.artifactId) byId.set(item.artifactId, item as ServerArtifactMergeable);
  }
  for (const item of incoming) {
    if (!item.artifactId) continue;
    const previous = byId.get(item.artifactId);
    byId.set(item.artifactId, { ...previous, ...item } as ServerArtifactMergeable);
  }
  return Array.from(byId.values());
}

function snapshotTimeline(steps?: AgentRunSnapshotStep[]): AgentTimelineItem[] {
  return (steps ?? []).map((step) => ({
    id: step.stepId,
    title: step.stepType ?? `Step ${step.stepNo ?? step.stepId}`,
    status: step.status,
    detail: step.summary ?? step.errorMessage ?? undefined,
    timestamp: step.finishedAt ?? step.startedAt ?? undefined
  }));
}

function snapshotSources(sources?: AgentRunSnapshotSource[]): AgentSource[] {
  return (sources ?? []).map((source) => ({
    id: source.itemId,
    title: source.title ?? source.sourceId ?? source.itemId,
    url: source.url ?? undefined,
    snippet: source.snippet ?? source.summary ?? undefined,
    score: source.score ?? source.confidence,
    sourceType: source.sourceType,
    trustLevel: source.trustLevel ?? source.confidenceLevel ?? undefined
  }));
}

function snapshotApprovals(approvals?: unknown[]): AgentApproval[] {
  return (approvals ?? []).flatMap((item, index) => {
    if (!item || typeof item !== "object" || Array.isArray(item)) return [];
    const record = item as Record<string, unknown>;
    const id = stringValue(record.approvalId) ?? stringValue(record.id) ?? `approval-${index}`;
    return [{
      id,
      title: stringValue(record.title) ?? stringValue(record.approvalType) ?? id,
      description: stringValue(record.description) ?? stringValue(record.reason),
      status: "pending",
      requestedBy: stringValue(record.requestedBy) ?? stringValue(record.creator),
      argumentsPreviewJson: stringValue(record.argumentsPreviewJson)
    }];
  });
}

function toolCallTimeline(toolCalls: AgentToolCallView[]): AgentTimelineItem[] {
  return toolCalls.map((toolCall) => ({
    id: `tool-call-${toolCall.id}`,
    title: toolCall.toolId,
    status: toolCall.status,
    detail: toolCall.resultSummary ?? toolCall.error,
    timestamp: toolCall.finishedAt ?? toolCall.startedAt,
    durationMs: toolCall.durationMs
  }));
}

function isStale(message: Message, incomingSeq?: number): boolean {
  return typeof incomingSeq === "number" &&
    typeof message.lastEventSeq === "number" &&
    incomingSeq < message.lastEventSeq;
}

function maxSeq(current: number | undefined, incoming: number | undefined): number | undefined {
  if (typeof incoming !== "number") return current;
  if (typeof current !== "number") return incoming;
  return Math.max(current, incoming);
}

function snapshotMessageStatus(current: Message["status"], runStatus?: string): Message["status"] {
  if (runStatus === "FAILED") return "error";
  if (runStatus === "CANCELLED" || runStatus === "CANCELED") return "cancelled";
  if (runStatus === "COMPLETED" || runStatus === "DONE" || runStatus === "SUCCEEDED") return "done";
  return current ?? "done";
}

function stripAppend<T extends ArtifactBlock>(artifact: T): ArtifactBlock {
  const { append: _append, ...rest } = artifact;
  return rest;
}

function stringValue(value: unknown): string | undefined {
  if (typeof value === "string" && value.trim()) return value.trim();
  if (typeof value === "number" && Number.isFinite(value)) return String(value);
  return undefined;
}
