import { api } from "@/services/api";

// ── 类型定义 ──

export interface ContextPack {
  contextPackId?: string;
  runId?: string;
  agentId?: string;
  versionId?: string;
  tenantId?: string;
  userId?: string;
  taskGoal?: string;
  budgetTokens?: number;
  items?: ContextPackItem[];
  createdAt?: string;
}

export interface ContextPackItem {
  itemId?: string;
  contextPackId?: string;
  sourceType?: string;
  sourceId?: string;
  content?: string;
  summary?: string;
  score?: number;
  confidence?: number;
  sensitivity?: string;
  aclDecisionId?: string;
  citationJson?: string;
  estimatedTokens?: number;
  expiresAt?: string;
  createdAt?: string;
}

export interface ContextPackRetentionCleanupResult {
  contextPackId?: string;
  cutoff?: string;
  deletedItemCount?: number;
}

export interface ContextPackDiffEntry {
  itemKey?: string;
  sourceType?: string;
  sourceId?: string;
  leftItem?: ContextPackItem;
  rightItem?: ContextPackItem;
  changedFields?: string[];
}

export interface ContextPackDiffResult {
  leftContextPackId?: string;
  rightContextPackId?: string;
  addedItemCount?: number;
  removedItemCount?: number;
  changedItemCount?: number;
  unchangedItemCount?: number;
  addedItems?: ContextPackDiffEntry[];
  removedItems?: ContextPackDiffEntry[];
  changedItems?: ContextPackDiffEntry[];
}

// ── API 调用 ──

export function getContextPack(packId: string): Promise<ContextPack> {
  return api.get<ContextPack>(`/api/context-packs/${encodeURIComponent(packId)}`) as unknown as Promise<ContextPack>;
}

export function listContextPackItems(packId: string): Promise<ContextPackItem[]> {
  return api.get<ContextPackItem[]>(`/api/context-packs/${encodeURIComponent(packId)}/items`) as unknown as Promise<ContextPackItem[]>;
}

export function cleanupExpiredContextPackItems(packId: string): Promise<ContextPackRetentionCleanupResult> {
  return api.post<ContextPackRetentionCleanupResult>(
    `/api/context-packs/${encodeURIComponent(packId)}/items:cleanup-expired`
  ) as unknown as Promise<ContextPackRetentionCleanupResult>;
}

export function diffContextPacks(leftPackId: string, rightPackId: string): Promise<ContextPackDiffResult> {
  return api.get<ContextPackDiffResult>(
    `/api/context-packs/${encodeURIComponent(leftPackId)}/diff`,
    { params: { rightContextPackId: rightPackId } }
  ) as unknown as Promise<ContextPackDiffResult>;
}
