import {
  AGENT_APPROVAL_STATUS,
  AGENT_STREAM_EVENTS,
  type AgentArtifact,
  type AgentApproval,
  type AgentMemory,
  type AgentQuota,
  type AgentSource,
  type AgentSkillRuntimeView,
  type AgentStreamEvent,
  type AgentTimelineItem,
  type AgentToolCallView,
  type ArtifactBlock,
  type ArtifactLanguage,
  type ContentBlock
} from "@/types";

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

export function computeThinkingDuration(startAt?: number | null) {
  if (!startAt) return undefined;
  const seconds = Math.round((Date.now() - startAt) / 1000);
  return Math.max(1, seconds);
}

type NormalizedArtifactPayload = {
  blocks: ArtifactBlock[];
  serverArtifacts: AgentArtifact[];
};

type AgentEventPayload =
  | { type: typeof AGENT_STREAM_EVENTS.TIMELINE; items: AgentTimelineItem[] }
  | { type: typeof AGENT_STREAM_EVENTS.SOURCE; items: AgentSource[] }
  | { type: typeof AGENT_STREAM_EVENTS.ARTIFACT; items: ArtifactBlock[]; serverArtifacts: AgentArtifact[] }
  | { type: typeof AGENT_STREAM_EVENTS.APPROVAL; items: AgentApproval[] }
  | { type: typeof AGENT_STREAM_EVENTS.TOOL_CALL_STARTED; items: AgentToolCallView[] }
  | { type: typeof AGENT_STREAM_EVENTS.TOOL_CALL_WAITING_USER; items: AgentToolCallView[]; approvals: AgentApproval[] }
  | { type: typeof AGENT_STREAM_EVENTS.TOOL_CALL_FINISHED; items: AgentToolCallView[] }
  | { type: typeof AGENT_STREAM_EVENTS.QUOTA; items: AgentQuota[] }
  | { type: typeof AGENT_STREAM_EVENTS.MEMORY; items: AgentMemory[] }
  | { type: typeof AGENT_STREAM_EVENTS.SKILL_SELECTED; items: AgentSkillRuntimeView[] }
  | { type: typeof AGENT_STREAM_EVENTS.SKILL_LOADED; items: AgentSkillRuntimeView[] }
  | { type: typeof AGENT_STREAM_EVENTS.SKILL_SKIPPED; items: AgentSkillRuntimeView[] }
  | { type: typeof AGENT_STREAM_EVENTS.SKILL_RESOURCE_LOADED; items: AgentSkillRuntimeView[] };

const ARTIFACT_LANGUAGES: ArtifactLanguage[] = ["html", "css", "javascript", "js", "tsx", "vue", "markdown"];

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === "object" && !Array.isArray(value));
}

function payloadItems(payload: unknown): unknown[] {
  if (Array.isArray(payload)) return payload;
  if (!isRecord(payload)) return [payload];
  if (Array.isArray(payload.items)) return payload.items;
  if (Array.isArray(payload.records)) return payload.records;
  if (Array.isArray(payload.sources)) return payload.sources;
  if (Array.isArray(payload.timeline)) return payload.timeline;
  if (Array.isArray(payload.approvals)) return payload.approvals;
  if (Array.isArray(payload.quota)) return payload.quota;
  if (Array.isArray(payload.memories)) return payload.memories;
  if (Array.isArray(payload.artifacts)) return payload.artifacts;
  return [payload];
}

function stringValue(record: Record<string, unknown>, keys: string[]): string | undefined {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value.trim();
    if (typeof value === "number" && Number.isFinite(value)) return String(value);
  }
  return undefined;
}

function rawStringValue(record: Record<string, unknown>, keys: string[]): string | undefined {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.length > 0) return value;
  }
  return undefined;
}

function numberValue(record: Record<string, unknown>, keys: string[]): number | undefined {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string" && value.trim() && Number.isFinite(Number(value))) {
      return Number(value);
    }
  }
  return undefined;
}

function jsonStringValue(record: Record<string, unknown>, keys: string[]): string | undefined {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value.trim();
    if (Array.isArray(value) || isRecord(value)) {
      try {
        return JSON.stringify(value, null, 2);
      } catch {
        return undefined;
      }
    }
  }
  return undefined;
}

function normalizeArtifactLanguage(value: string | undefined): ArtifactLanguage {
  const normalized = value?.toLowerCase();
  if (normalized === "markdown_report" || normalized === "report" || normalized === "text/markdown") {
    return "markdown";
  }
  if (normalized && ARTIFACT_LANGUAGES.includes(normalized as ArtifactLanguage)) {
    return normalized as ArtifactLanguage;
  }
  return "javascript";
}

function fallbackId(prefix: string, index: number, record: Record<string, unknown>) {
  return stringValue(record, ["id", "itemId", "sourceId", "approvalId", "artifactId", "memoryId"]) ?? `${prefix}-${index}`;
}

function normalizeRunStarted(payload: unknown): AgentTimelineItem[] {
  if (!isRecord(payload)) return [];
  const runId = stringValue(payload, ["runId", "id"]);
  if (!runId) return [];
  return [{
    id: `run-started-${runId}`,
    title: stringValue(payload, ["title"]) ?? "Run started",
    status: stringValue(payload, ["status", "state"]) ?? "RUNNING",
    detail: stringValue(payload, ["detail", "summary", "message"]) ?? `Agent run ${runId} started`,
    timestamp: stringValue(payload, ["startedAt", "timestamp", "time", "createdAt"])
  }];
}

function normalizeStepTimeline(payload: unknown): AgentTimelineItem[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const stepId = stringValue(item, ["stepId", "id"]) ?? `step-${index}`;
    const title = stringValue(item, ["title", "stepType", "summary"]) ?? stringValue(item, ["event"]);
    if (!title) return [];
    return [{
      id: stepId,
      title,
      status: stringValue(item, ["status", "state"]),
      detail: stringValue(item, ["detail", "summary", "message"]),
      timestamp: stringValue(item, ["startedAt", "finishedAt", "timestamp", "time", "createdAt"]),
      durationMs: numberValue(item, ["durationMs", "elapsedMs", "latencyMs"])
    }];
  });
}

function normalizeToolWaitingApproval(payload: unknown): AgentApproval[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const id = stringValue(item, ["approvalId", "id"]);
    if (!id) return [];
    const toolId = stringValue(item, ["toolId", "toolName"]);
    return [{
      id,
      title: stringValue(item, ["title", "summary"]) ?? (toolId ? `Approve ${toolId}` : id),
      description: stringValue(item, ["description", "detail"])
        ?? jsonStringValue(item, ["argumentsPreview", "argumentsPreviewJson"]),
      status: AGENT_APPROVAL_STATUS.PENDING,
      requestedBy: stringValue(item, ["requestedBy", "operator", "userId"]),
      argumentsPreviewJson: jsonStringValue(item, ["argumentsPreviewJson", "argumentsPreview"])
    }];
  });
}

function stringArrayValue(record: Record<string, unknown>, keys: string[]): string[] | undefined {
  for (const key of keys) {
    const value = record[key];
    if (Array.isArray(value)) {
      const values = value
        .map((item) => (typeof item === "string" ? item.trim() : ""))
        .filter(Boolean);
      if (values.length > 0) return values;
    }
    if (typeof value === "string" && value.trim()) {
      const values = value.split(",").map((item) => item.trim()).filter(Boolean);
      if (values.length > 0) return values;
    }
  }
  return undefined;
}

function normalizeToolCalls(payload: unknown, statusFallback: string, includeSummaryAsResult = false): AgentToolCallView[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const id = stringValue(item, ["toolCallId", "toolInvocationId", "id"]) ?? `tool-call-${index}`;
    const toolId = stringValue(item, ["toolId", "toolName", "name"]);
    if (!toolId) return [];
    const resultSummary =
      stringValue(item, ["resultSummary", "result", "outputSummary"]) ??
      (includeSummaryAsResult ? stringValue(item, ["summary"]) : undefined);
    return [{
      id,
      toolId,
      status: stringValue(item, ["status", "state", "message"]) ?? statusFallback,
      toolInvocationId: stringValue(item, ["toolInvocationId", "invocationId"]),
      approvalId: stringValue(item, ["approvalId"]),
      riskLevel: stringValue(item, ["riskLevel", "risk"]),
      argumentsPreviewJson: jsonStringValue(item, ["argumentsPreviewJson", "argumentsPreview", "arguments"]),
      resultSummary,
      durationMs: numberValue(item, ["durationMs", "elapsedMs", "latencyMs"]),
      error: stringValue(item, ["error", "errorMessage", "errorCode"]),
      startedAt: stringValue(item, ["startedAt", "timestamp", "time", "createdAt"]),
      finishedAt: stringValue(item, ["finishedAt", "completedAt"])
    }];
  });
}

function normalizeRecoverableError(payload: unknown): AgentTimelineItem[] {
  if (!isRecord(payload)) return [];
  const stepId = stringValue(payload, ["stepId", "id"]) ?? "recoverable-error";
  return [{
    id: stepId,
    title: stringValue(payload, ["title"]) ?? "Recoverable error",
    status: "FAILED",
    detail: stringValue(payload, ["message", "detail", "errorCode"]),
    timestamp: stringValue(payload, ["timestamp", "time", "createdAt"])
  }];
}

function normalizeTimeline(payload: unknown): AgentTimelineItem[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const title = stringValue(item, ["title", "name", "step", "event", "phase"]);
    if (!title) return [];
    return [{
      id: fallbackId("timeline", index, item),
      title,
      status: stringValue(item, ["status", "state"]),
      detail: stringValue(item, ["detail", "description", "message", "summary"]),
      timestamp: stringValue(item, ["timestamp", "time", "createdAt"]),
      durationMs: numberValue(item, ["durationMs", "elapsedMs", "latencyMs"])
    }];
  });
}

function normalizeSources(payload: unknown): AgentSource[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const title = stringValue(item, ["title", "name", "fileName", "source", "url", "sourceId", "itemId"]);
    if (!title) return [];
    return [{
      id: fallbackId("source", index, item),
      title,
      url: stringValue(item, ["url", "href", "sourceLocation"]),
      snippet: stringValue(item, ["snippet", "content", "text", "summary"]),
      score: numberValue(item, ["score", "relevance", "similarity", "confidence"]),
      sourceType: stringValue(item, ["sourceType", "type", "kind"]),
      trustLevel: stringValue(item, ["trustLevel", "trust", "confidenceLevel"])
    }];
  });
}

function normalizeArtifacts(payload: unknown): ArtifactBlock[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const code = stringValue(item, ["code", "content", "text"]);
    if (!code) return [];
    return [{
      id: fallbackId("artifact", index, item),
      language: normalizeArtifactLanguage(stringValue(item, ["language", "lang", "type"])),
      title: stringValue(item, ["title", "name"]) ?? "Artifact",
      code,
      isComplete: true
    }];
  });
}

function normalizeArtifactStarts(payload: unknown): ArtifactBlock[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const id = stringValue(item, ["artifactId", "id"]) ?? `artifact-stream-${index}`;
    return [{
      id,
      language: normalizeArtifactLanguage(stringValue(item, ["language", "lang", "artifactType", "mimeType"])),
      title: stringValue(item, ["title", "name"]) ?? "Artifact",
      code: "",
      isComplete: false
    }];
  });
}

function normalizeArtifactContent(payload: unknown): ArtifactBlock[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const delta = rawStringValue(item, ["delta"]);
    const code = delta ?? stringValue(item, ["code", "content", "text"]);
    if (!code) return [];
    return [{
      id: fallbackId("artifact", index, item),
      language: normalizeArtifactLanguage(stringValue(item, ["language", "lang", "artifactType", "mimeType"])),
      title: stringValue(item, ["title", "name"]) ?? "Artifact",
      code,
      isComplete: false,
      append: Boolean(delta)
    }];
  });
}

function normalizeArtifactEnds(payload: unknown): ArtifactBlock[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const id = stringValue(item, ["artifactId", "id"]);
    if (!id) return [];
    return [{
      id,
      language: normalizeArtifactLanguage(stringValue(item, ["language", "lang", "artifactType", "mimeType"])),
      title: stringValue(item, ["title", "name"]) ?? `Artifact ${index + 1}`,
      code: stringValue(item, ["code", "content", "text", "previewText", "preview"]) ?? "",
      isComplete: true
    }];
  });
}

function normalizeServerArtifacts(payload: unknown): AgentArtifact[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const artifactId = stringValue(item, ["artifactId", "id"]);
    if (!artifactId) return [];
    return [{
      artifactId,
      runId: stringValue(item, ["runId"]),
      messageId: stringValue(item, ["messageId"]),
      tenantId: stringValue(item, ["tenantId"]),
      userId: stringValue(item, ["userId"]),
      artifactType: stringValue(item, ["artifactType", "type", "kind"]),
      title: stringValue(item, ["title", "name"]) ?? `Artifact ${index + 1}`,
      mimeType: stringValue(item, ["mimeType", "contentType"]),
      storageRef: stringValue(item, ["storageRef", "url"]),
      previewText: stringValue(item, ["previewText", "preview", "summary"]),
      provenanceJson: stringValue(item, ["provenanceJson", "provenance"]),
      scanStatus: stringValue(item, ["scanStatus", "status"]),
      createdAt: stringValue(item, ["createdAt", "timestamp"]),
      canPreview: Boolean(item.canPreview),
      disposition: stringValue(item, ["disposition"])
    }];
  });
}

function normalizeArtifactPayload(payload: unknown): NormalizedArtifactPayload {
  return {
    blocks: normalizeArtifacts(payload),
    serverArtifacts: normalizeServerArtifacts(payload)
  };
}

function normalizeApprovals(payload: unknown): AgentApproval[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const id = fallbackId("approval", index, item);
    const title = stringValue(item, ["title", "name", "approvalType", "resourceType"]) ?? id;
    const rawStatus = stringValue(item, ["status", "state"])?.toLowerCase();
    const status =
      rawStatus === AGENT_APPROVAL_STATUS.APPROVED ||
      rawStatus === AGENT_APPROVAL_STATUS.REJECTED ||
      rawStatus === AGENT_APPROVAL_STATUS.MODIFIED ||
      rawStatus === AGENT_APPROVAL_STATUS.ERROR
        ? rawStatus
        : AGENT_APPROVAL_STATUS.PENDING;
    return [{
      id,
      title,
      description: stringValue(item, ["description", "detail", "reason", "summary"]),
      status,
      requestedBy: stringValue(item, ["requestedBy", "operator", "creator"]),
      argumentsPreviewJson: stringValue(item, ["argumentsPreviewJson", "argumentsPreview"])
    }];
  });
}

function normalizeQuota(payload: unknown): AgentQuota[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const label = stringValue(item, ["label", "name", "metric", "type"]);
    if (!label) return [];
    return [{
      id: fallbackId("quota", index, item),
      label,
      used: numberValue(item, ["used", "usage", "current"]),
      limit: numberValue(item, ["limit", "total", "max"]),
      remaining: numberValue(item, ["remaining", "left"]),
      unit: stringValue(item, ["unit"])
    }];
  });
}

function normalizeMemories(payload: unknown): AgentMemory[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const title = stringValue(item, ["title", "name", "key", "action"]);
    if (!title) return [];
    return [{
      id: fallbackId("memory", index, item),
      title,
      content: stringValue(item, ["content", "value", "summary", "text"]),
      action: stringValue(item, ["action", "operation", "type"])
    }];
  });
}

function normalizeSkills(payload: unknown, statusFallback: string): AgentSkillRuntimeView[] {
  return payloadItems(payload).flatMap((item, index) => {
    if (!isRecord(item)) return [];
    const name = stringValue(item, ["name", "skillName", "id"]);
    if (!name) return [];
    return [{
      id: name,
      name,
      status: stringValue(item, ["status", "state"]) ?? statusFallback,
      revisionId: stringValue(item, ["revisionId", "revision"]),
      injectMode: stringValue(item, ["injectMode", "mode"]),
      category: stringValue(item, ["category"]),
      description: stringValue(item, ["description", "summary"]),
      allowedTools: stringArrayValue(item, ["allowedTools", "tools"]),
      resourcePath: stringValue(item, ["resourcePath", "path"]),
      reason: stringValue(item, ["reason", "error", "message"])
    }];
  });
}

export function isAgentStreamEvent(event: string): event is AgentStreamEvent {
  return Object.values(AGENT_STREAM_EVENTS).includes(event as AgentStreamEvent);
}

export function normalizeAgentStreamEvent(event: string, payload: unknown): AgentEventPayload | null {
  if (!isAgentStreamEvent(event)) return null;
  switch (event) {
    case AGENT_STREAM_EVENTS.TIMELINE:
      return { type: event, items: normalizeTimeline(payload) };
    case AGENT_STREAM_EVENTS.RUN_STARTED:
      return { type: AGENT_STREAM_EVENTS.TIMELINE, items: normalizeRunStarted(payload) };
    case AGENT_STREAM_EVENTS.RUN_SNAPSHOT:
    case AGENT_STREAM_EVENTS.STEP_STARTED:
    case AGENT_STREAM_EVENTS.STEP_PROGRESS:
    case AGENT_STREAM_EVENTS.STEP_FINISHED:
      return { type: AGENT_STREAM_EVENTS.TIMELINE, items: normalizeStepTimeline(payload) };
    case AGENT_STREAM_EVENTS.TOOL_CALL_STARTED:
      return { type: event, items: normalizeToolCalls(payload, "RUNNING") };
    case AGENT_STREAM_EVENTS.TOOL_CALL_FINISHED:
      return { type: event, items: normalizeToolCalls(payload, "SUCCEEDED", true) };
    case AGENT_STREAM_EVENTS.RECOVERABLE_ERROR:
      return { type: AGENT_STREAM_EVENTS.TIMELINE, items: normalizeRecoverableError(payload) };
    case AGENT_STREAM_EVENTS.SOURCE:
      return { type: event, items: normalizeSources(payload) };
    case AGENT_STREAM_EVENTS.SOURCE_FOUND:
      return { type: AGENT_STREAM_EVENTS.SOURCE, items: normalizeSources(payload) };
    case AGENT_STREAM_EVENTS.ARTIFACT: {
      const artifacts = normalizeArtifactPayload(payload);
      return { type: event, items: artifacts.blocks, serverArtifacts: artifacts.serverArtifacts };
    }
    case AGENT_STREAM_EVENTS.ARTIFACT_CREATED: {
      const artifacts = normalizeArtifactPayload(payload);
      return { type: AGENT_STREAM_EVENTS.ARTIFACT, items: artifacts.blocks, serverArtifacts: artifacts.serverArtifacts };
    }
    case AGENT_STREAM_EVENTS.ARTIFACT_START:
      return { type: AGENT_STREAM_EVENTS.ARTIFACT, items: normalizeArtifactStarts(payload), serverArtifacts: [] };
    case AGENT_STREAM_EVENTS.ARTIFACT_CONTENT: {
      return {
        type: AGENT_STREAM_EVENTS.ARTIFACT,
        items: normalizeArtifactContent(payload),
        serverArtifacts: normalizeServerArtifacts(payload)
      };
    }
    case AGENT_STREAM_EVENTS.ARTIFACT_END: {
      return {
        type: AGENT_STREAM_EVENTS.ARTIFACT,
        items: normalizeArtifactEnds(payload),
        serverArtifacts: normalizeServerArtifacts(payload)
      };
    }
    case AGENT_STREAM_EVENTS.ARTIFACT_COMPLETE: {
      return {
        type: AGENT_STREAM_EVENTS.ARTIFACT,
        items: normalizeArtifactEnds(payload),
        serverArtifacts: normalizeServerArtifacts(payload)
      };
    }
    case AGENT_STREAM_EVENTS.APPROVAL:
      return { type: event, items: normalizeApprovals(payload) };
    case AGENT_STREAM_EVENTS.TOOL_CALL_WAITING_USER:
      return {
        type: event,
        items: normalizeToolCalls(payload, "WAITING_USER"),
        approvals: normalizeToolWaitingApproval(payload)
      };
    case AGENT_STREAM_EVENTS.QUOTA:
      return { type: event, items: normalizeQuota(payload) };
    case AGENT_STREAM_EVENTS.MEMORY:
      return { type: event, items: normalizeMemories(payload) };
    case AGENT_STREAM_EVENTS.SKILL_SELECTED:
      return { type: event, items: normalizeSkills(payload, "SELECTED") };
    case AGENT_STREAM_EVENTS.SKILL_LOADED:
      return { type: event, items: normalizeSkills(payload, "LOADED") };
    case AGENT_STREAM_EVENTS.SKILL_SKIPPED:
      return { type: event, items: normalizeSkills(payload, "SKIPPED") };
    case AGENT_STREAM_EVENTS.SKILL_RESOURCE_LOADED:
      return { type: event, items: normalizeSkills(payload, "LOADED") };
    default:
      return null;
  }
}

export function extractArtifactsFromBlocks(blocks?: ContentBlock[]): ArtifactBlock[] {
  return (blocks ?? []).flatMap((block) => (block.type === "artifact" ? [block.artifact] : []));
}
