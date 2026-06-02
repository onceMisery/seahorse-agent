import { api } from "@/services/api";

// ── 类型定义 ──

export interface ContextPack {
  packId?: string;
  name?: string;
  description?: string;
  tenantId?: string;
  items?: ContextPackItem[];
  createTime?: string;
}

export interface ContextPackItem {
  itemId?: string;
  packId?: string;
  key?: string;
  value?: string;
  itemType?: string;
}

// ── API 调用 ──

export function getContextPack(packId: string): Promise<ContextPack> {
  return api.get<ContextPack>(`/api/context-packs/${encodeURIComponent(packId)}`) as unknown as Promise<ContextPack>;
}

export function listContextPackItems(packId: string): Promise<ContextPackItem[]> {
  return api.get<ContextPackItem[]>(`/api/context-packs/${encodeURIComponent(packId)}/items`) as unknown as Promise<ContextPackItem[]>;
}
