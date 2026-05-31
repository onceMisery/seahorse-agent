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

export interface VersionQualityDiff {
  baseVersionId?: string;
  candidateVersionId?: string;
  baseCoverage?: number;
  candidateCoverage?: number;
  diffCoverage?: number;
  degradedSamples?: Array<{ documentId?: string; baseQuality?: number; candidateQuality?: number }>;
  improvedSamples?: Array<{ documentId?: string; baseQuality?: number; candidateQuality?: number }>;
}

// ── API 调用 ──

// 数据集 CRUD
export function listDatasets(kbId: string, params?: { current?: number; size?: number; keyword?: string }) {
  return api.get<PageResult<RetrievalEvaluationDataset>>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets`,
    { params }
  );
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

// 评测与对比
export function evaluateDataset(kbId: string, datasetId: string, strategyKey: string) {
  return api.post<EvaluationRun, EvaluationRun>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}/evaluate`,
    { strategyKey }
  );
}

export function compareStrategies(kbId: string, datasetId: string, baseStrategyKey: string, candidateStrategyKey: string) {
  return api.post<EvaluationComparison, EvaluationComparison>(
    `/knowledge-base/${encodeURIComponent(kbId)}/retrieval-evaluation-datasets/${encodeURIComponent(datasetId)}/compare`,
    { baseStrategyKey, candidateStrategyKey }
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
