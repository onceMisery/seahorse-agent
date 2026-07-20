import { api } from "@/services/api";
import type { GateResult } from "@/services/toolCatalogService";

export type { GateResult, GateResultItem } from "@/services/toolCatalogService";

/**
 * 查询某个受治理对象的最新统一门禁结果。
 * subjectType 例如 TOOL / SKILL / MODEL_CONFIG / INGESTION_PIPELINE / RAG_STRATEGY / AGENT / RUN_PROFILE。
 */
export function getLatestGateResult(subjectType: string, subjectId: string) {
  return api.get<GateResult, GateResult>(
    `/api/gate-results/${encodeURIComponent(subjectType)}/${encodeURIComponent(subjectId)}`
  );
}

/**
 * 查询某个受治理对象的门禁历史，按 checkedAt 倒序返回，用于审计追溯与回归对比。
 * limit 可选，服务端默认 20、上限 100。
 */
export function getGateResultHistory(subjectType: string, subjectId: string, limit?: number) {
  return api.get<GateResult[], GateResult[]>(
    `/api/gate-results/${encodeURIComponent(subjectType)}/${encodeURIComponent(subjectId)}/history`,
    typeof limit === "number" ? { params: { limit } } : undefined
  );
}
