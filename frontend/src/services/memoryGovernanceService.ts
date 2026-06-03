import { api } from "@/services/api";
import type { PageResult } from "@/services/metadataGovernanceService";

// ── 类型定义 ──

export interface MemoryItem {
  memoryId?: string;
  userId?: string;
  tenantId?: string;
  layer?: string;
  content?: string;
  quality?: number;
  status?: string;
  sourceConversationId?: string;
  sourceMessageId?: string;
  createTime?: string;
  updateTime?: string;
}

export interface MemoryQualitySnapshot {
  snapshotId?: string;
  kbId?: string;
  tenantId?: string;
  totalMemories?: number;
  highQualityCount?: number;
  lowQualityCount?: number;
  averageQuality?: number;
  governanceSuggestions?: string[];
  snapshotTime?: string;
}

export interface MemoryConflict {
  conflictId?: string;
  memoryIdA?: string;
  memoryIdB?: string;
  contentA?: string;
  contentB?: string;
  layer?: string;
  status?: string;
  createTime?: string;
}

export interface MemoryMaintenanceRun {
  runId?: string;
  type?: string;
  status?: string;
  startedAt?: string;
  finishedAt?: string;
  summary?: Record<string, unknown>;
}

export interface MemoryReviewItem {
  itemId?: string;
  memoryId?: string;
  layer?: string;
  content?: string;
  status?: string;
  submittedBy?: string;
  createTime?: string;
}

export interface MemoryTrace {
  traceId?: string;
  memoryId?: string;
  runId?: string;
  userId?: string;
  operation?: string;
  detail?: string;
  createTime?: string;
}

export interface MemoryPolicyConfig {
  decayEnabled?: boolean;
  decayThresholdDays?: number;
  qualityThreshold?: number;
  autoReviewEnabled?: boolean;
  conflictResolutionPolicy?: string;
  [key: string]: unknown;
}

// ── 记忆检索 ──

export function listMemories(params: {
  current?: number;
  size?: number;
  userId?: string;
  tenantId?: string;
  layer?: string;
  keyword?: string;
  quality?: string;
}) {
  return api.get<PageResult<MemoryItem>>("/memories", { params });
}

export function getMemory(layer: string, memoryId: string) {
  return api.get<MemoryItem>(`/memories/${encodeURIComponent(layer)}/${encodeURIComponent(memoryId)}`);
}

export function deleteMemory(layer: string, memoryId: string) {
  return api.delete(`/memories/${encodeURIComponent(layer)}/${encodeURIComponent(memoryId)}`);
}

// ── 质量快照 ──

export function getMemoryQualitySnapshots(params?: { tenantId?: string; kbId?: string }) {
  return api.get<MemoryQualitySnapshot[]>("/memories/quality-snapshots", { params });
}

// ── 冲突处理 ──

export function listMemoryConflicts(params?: { status?: string; layer?: string }) {
  return api.get<MemoryConflict[]>("/memories/conflicts", { params });
}

export function resolveMemoryConflict(conflictId: string, resolution: string, mergedContent?: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/memories/conflicts/${encodeURIComponent(conflictId)}/resolve`,
    { resolution, mergedContent }
  );
}

// ── Profile facts / Corrections / Operations / Outbox ──

export function getMemoryProfileFacts(params?: { userId?: string; tenantId?: string }) {
  return api.get<Record<string, unknown>[]>("/memories/profile-facts", { params });
}

export function getMemoryCorrections(params?: { status?: string }) {
  return api.get<Record<string, unknown>[]>("/memories/corrections", { params });
}

export function getMemoryOperations(params?: { memoryId?: string; runId?: string }) {
  return api.get<Record<string, unknown>[]>("/memories/operations", { params });
}

export function getMemoryOutbox(params?: { status?: string }) {
  return api.get<Record<string, unknown>[]>("/memories/outbox", { params });
}

// ── 健康与就绪 ──

export function getMemoryHealth() {
  return api.get<Record<string, unknown>>("/memories/health");
}

export function getMemoryReadiness() {
  return api.get<Record<string, unknown>>("/memories/readiness");
}

// ── 策略配置 ──

export function getMemoryPolicyConfig() {
  return api.get<MemoryPolicyConfig>("/memories/policy-config");
}

export function updateMemoryPolicyConfig(config: MemoryPolicyConfig) {
  return api.post<MemoryPolicyConfig, MemoryPolicyConfig>("/memories/policy-config", config);
}

// ── 治理操作 ──

export function runMemoryGovernance(type: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>("/memories/governance/run", { type });
}

export function runMemoryDecay() {
  return api.post<Record<string, unknown>, Record<string, unknown>>("/memories/governance/decay");
}

export function runMemoryQuality() {
  return api.post<Record<string, unknown>, Record<string, unknown>>("/memories/governance/quality");
}

// ── 维护任务 ──

export function runMemoryMaintenance() {
  return api.post<Record<string, unknown>, Record<string, unknown>>("/memories/maintenance/run");
}

export function listMemoryMaintenanceRuns(params?: { current?: number; size?: number }) {
  return api.get<PageResult<MemoryMaintenanceRun>>("/memories/maintenance-runs", { params });
}

export function getMemoryMaintenanceAggregate() {
  return api.get<Record<string, unknown>>("/memories/maintenance-runs/aggregate");
}

// ── Review ──

export function listMemoryReviewItems(params?: {
  current?: number;
  size?: number;
  status?: string;
  layer?: string;
}) {
  return api.get<PageResult<MemoryReviewItem>>("/memory-review/items", { params });
}

export function getMemoryReviewPendingSummary() {
  return api.get<Record<string, unknown>>("/memory-review/pending-summary");
}

export function getMemoryReviewItem(itemId: string) {
  return api.get<MemoryReviewItem>(`/memory-review/items/${encodeURIComponent(itemId)}`);
}

export function getMemoryReviewFeedbackSamples(itemId: string) {
  return api.get<Record<string, unknown>[]>(
    `/memory-review/items/${encodeURIComponent(itemId)}/feedback-samples`
  );
}

export function exportFeedbackSamples(params?: {
  tenantId?: string;
  userId?: string;
  status?: string;
  targetKind?: string;
  targetKey?: string;
  limit?: number;
}) {
  return api.get<unknown[]>("/memory-review/feedback-samples/export", { params });
}

export function approveMemoryReviewItem(itemId: string, comment?: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/memory-review/items/${encodeURIComponent(itemId)}/approve`,
    { comment }
  );
}

export function modifyMemoryReviewItem(itemId: string, modifiedContent: string, comment?: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/memory-review/items/${encodeURIComponent(itemId)}/modify`,
    { modifiedContent, comment }
  );
}

export function rejectMemoryReviewItem(itemId: string, comment?: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/memory-review/items/${encodeURIComponent(itemId)}/reject`,
    { comment }
  );
}

// ── Trace ──

export function listMemoryTraces(params?: {
  memoryId?: string;
  runId?: string;
  userId?: string;
  current?: number;
  size?: number;
}) {
  return api.get<PageResult<MemoryTrace>>("/memories/traces", { params });
}
