import { api } from "@/services/api";
import type { PageResult } from "@/services/metadataGovernanceService";

// ── 类型定义 ──

export interface RetrievalEvaluationDataset {
  datasetId?: string;
  kbId?: string;
  name?: string;
  description?: string;
  sampleCount?: number;
  createTime?: string;
  updateTime?: string;
}

export interface EvaluationSample {
  sampleId?: string;
  datasetId?: string;
  query?: string;
  expectedDocumentIds?: string[];
  expectedChunkIds?: string[];
  tenantId?: string;
  remark?: string;
  createTime?: string;
}

export interface EvaluationRun {
  runId?: string;
  datasetId?: string;
  kbId?: string;
  strategyKey?: string;
  status?: string;
  hitRate?: number;
  mrr?: number;
  ndcg?: number;
  failCount?: number;
  createTime?: string;
  finishedAt?: string;
}

export interface EvaluationComparison {
  comparisonId?: string;
  datasetId?: string;
  baseStrategyKey?: string;
  candidateStrategyKey?: string;
  status?: string;
  baseHitRate?: number;
  candidateHitRate?: number;
  diffHitRate?: number;
  baseMrr?: number;
  candidateMrr?: number;
  failSamples?: Array<{
    query?: string;
    baseHit?: boolean;
    candidateHit?: boolean;
    details?: string;
  }>;
  gainSamples?: Array<{
    query?: string;
    baseHit?: boolean;
    candidateHit?: boolean;
  }>;
  createTime?: string;
}

export interface RetrievalStrategyTemplate {
  templateKey?: string;
  kbId?: string;
  name?: string;
  topK?: number;
  rerank?: boolean;
  metadataFilter?: Record<string, unknown>;
  options?: Record<string, unknown>;
  createTime?: string;
  updateTime?: string;
}

export interface RetrievalEvaluationStrategy {
  strategyName?: string;
  topK?: number;
  options?: Record<string, unknown>;
}

export interface DatasetEvaluatePayload {
  strategyName: string;
  topK?: number;
  options?: Record<string, unknown>;
}

export interface DatasetComparePayload {
  baselineStrategyName: string;
  topK?: number;
  strategies: RetrievalEvaluationStrategy[];
}

export interface VersionQualityDiff {
  baseVersionId?: string;
  candidateVersionId?: string;
  baseCoverage?: number;
  candidateCoverage?: number;
  diffCoverage?: number;
  degradedSamples?: Array<{ documentId?: string; baseQuality?: number; candidateQuality?: number }>;
  improvedSamples?: Array<{ documentId?: string; baseQuality?: number; candidateQuality?: number }>;
}

function normalizePage<T>(data: PageResult<T> | T[] | null | undefined, current = 1, size = 50): PageResult<T> {
  if (Array.isArray(data)) {
    return {
      records: data,
      total: data.length,
      size,
      current,
      pages: data.length === 0 ? 0 : Math.ceil(data.length / size)
    };
  }
  return {
    records: data?.records ?? [],
    total: data?.total ?? data?.records?.length ?? 0,
    size: data?.size ?? size,
    current: data?.current ?? current,
    pages: data?.pages ?? 0
  };
}

// ── API 调用 ──

// 数据集 CRUD
export async function listDatasets(kbId: string, params?: { current?: number; size?: number; keyword?: string }) {
  const data = await api.get<PageResult<RetrievalEvaluationDataset> | RetrievalEvaluationDataset[]>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets`,
    { params }
  );
  return normalizePage(data, params?.current ?? 1, params?.size ?? 50);
}

export function getDataset(kbId: string, datasetId: string) {
  return api.get<RetrievalEvaluationDataset>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}`
  );
}

export function createDataset(kbId: string, payload: { name?: string; description?: string }) {
  return api.post<RetrievalEvaluationDataset, RetrievalEvaluationDataset>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets`,
    payload
  );
}

export function updateDataset(kbId: string, datasetId: string, payload: { name?: string; description?: string }) {
  return api.put<RetrievalEvaluationDataset, RetrievalEvaluationDataset>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}`,
    payload
  );
}

export function deleteDataset(kbId: string, datasetId: string) {
  return api.delete(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}`
  );
}

// 样本管理
export function listDatasetSamples(kbId: string, datasetId: string, params?: { current?: number; size?: number }) {
  return api.get<PageResult<EvaluationSample>>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}/samples`,
    { params }
  );
}

// 样本导出（客户端下载 JSON）
export async function exportDatasetSamples(kbId: string, datasetId: string) {
  const allSamples = await listDatasetSamples(kbId, datasetId, { current: 1, size: 10000 });
  const blob = new Blob([JSON.stringify(allSamples.records, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `evaluation-dataset-${datasetId}-samples.json`;
  a.click();
  URL.revokeObjectURL(url);
}

// 样本导入（解析 JSON 文件后逐条创建，需要后端支持批量样本写入）
export async function importDatasetSamples(kbId: string, datasetId: string, file: File): Promise<number> {
  const text = await file.text();
  const samples: EvaluationSample[] = JSON.parse(text);
  if (!Array.isArray(samples) || samples.length === 0) {
    throw new Error("文件格式无效：需要包含评测样本数组");
  }
  // 使用批量导入端点（如后端已实现）
  return api.post<number, number>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}/samples/import`,
    samples
  );
}

// 评测与对比
export function evaluateDataset(kbId: string, datasetId: string, payload: DatasetEvaluatePayload) {
  return api.post<EvaluationRun, EvaluationRun>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}/evaluate`,
    payload
  );
}

export function compareStrategies(kbId: string, datasetId: string, payload: DatasetComparePayload) {
  return api.post<EvaluationComparison, EvaluationComparison>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}/compare`,
    payload
  );
}

export function listEvaluationRuns(kbId: string, datasetId: string) {
  return api.get<EvaluationRun[]>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}/runs`
  );
}

export function listEvaluationComparisons(kbId: string, datasetId: string) {
  return api.get<EvaluationComparison[]>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}/comparisons`
  );
}

// 策略模板 CRUD
export function listStrategyTemplates(kbId: string) {
  return api.get<RetrievalStrategyTemplate[]>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-strategy-templates`
  );
}

export function createStrategyTemplate(kbId: string, payload: Omit<RetrievalStrategyTemplate, "createTime" | "updateTime">) {
  return api.post<RetrievalStrategyTemplate, RetrievalStrategyTemplate>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-strategy-templates`,
    payload
  );
}

export function updateStrategyTemplate(kbId: string, templateKey: string, payload: Partial<RetrievalStrategyTemplate>) {
  return api.put<RetrievalStrategyTemplate, RetrievalStrategyTemplate>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-strategy-templates/${encodeURIComponent(templateKey)}`,
    payload
  );
}

export function deleteStrategyTemplate(kbId: string, templateKey: string) {
  return api.delete(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-strategy-templates/${encodeURIComponent(templateKey)}`
  );
}

// 版本质量对比
export function compareVersionQuality(kbId: string, baseVersionId: string, candidateVersionId: string) {
  return api.post<VersionQualityDiff, VersionQualityDiff>(
    `/knowledge-base/${encodeURIComponent(kbId)}/version-quality/compare`,
    { baseVersionId, candidateVersionId }
  );
}

// 知识库级别的评测（快捷入口）
export function evaluateRetrievalQuality(kbId: string, payload: Record<string, unknown>) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-quality/evaluate`,
    payload
  );
}

export function compareRetrievalQuality(kbId: string, payload: Record<string, unknown>) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-quality/compare`,
    payload
  );
}

// 策略推广（将评测对比通过的策略标记为推荐模板）
export function promoteStrategyFromComparison(
  kbId: string,
  datasetId: string,
  comparisonId: string,
  payload: { strategyKey: string; templateKey?: string }
) {
  return api.post<RetrievalStrategyTemplate, RetrievalStrategyTemplate>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}/comparisons/${encodeURIComponent(comparisonId)}/promote`,
    payload
  );
}
