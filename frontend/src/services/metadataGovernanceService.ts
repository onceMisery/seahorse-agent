import { api } from "./api";

export interface PageResult<T> {
  records?: T[];
  total?: number;
  size?: number;
  current?: number;
  pages?: number;
}

export interface MetadataSchemaField {
  id?: string;
  fieldId?: string;
  fieldKey?: string;
  displayName?: string;
  valueType?: string;
  schemaVersion?: number;
  required?: boolean;
  filterable?: boolean;
  sortable?: boolean;
  indexed?: boolean;
  minConfidence?: number;
}

export interface MetadataReviewItem {
  id: string;
  tenantId?: string;
  kbId?: string;
  documentId?: string;
  docId?: string;
  status?: string;
  reasonCode?: string;
  confidence?: number;
  createTime?: string;
  updateTime?: string;
}

export interface MetadataQuarantineItem {
  id: string;
  tenantId?: string;
  kbId?: string;
  documentId?: string;
  docId?: string;
  stage?: string;
  reasonCode?: string;
  resolved?: boolean;
  retryCount?: number;
  nextRetryTime?: string;
  createTime?: string;
}

export interface MetadataQualityReport {
  totalDocuments?: number;
  reviewedItems?: number;
  quarantinedItems?: number;
  reviewPassRate?: number;
  averageCoverage?: number;
  lowConfidenceCount?: number;
  reasonTopN?: Array<{ reasonCode?: string; count?: number }>;
  fieldCoverage?: Array<{ fieldKey?: string; coverage?: number; missingCount?: number }>;
}

export function listMetadataSchemaFields(tenantId: string, kbId: string) {
  return api.get<MetadataSchemaField[]>(`/knowledge-base/${kbId}/metadata-schema/fields`, {
    params: { tenantId }
  }) as unknown as Promise<MetadataSchemaField[]>;
}

export function pageMetadataReviewItems(params: {
  tenantId: string;
  kbId?: string;
  status?: string;
  current?: number;
  size?: number;
}) {
  return api.get<PageResult<MetadataReviewItem>>("/metadata-review/items", { params }) as unknown as Promise<PageResult<MetadataReviewItem>>;
}

export function approveMetadataReviewItem(itemId: string) {
  return api.post(`/metadata-review/items/${itemId}/approve`, {});
}

export function rejectMetadataReviewItem(itemId: string) {
  return api.post(`/metadata-review/items/${itemId}/reject`, {});
}

export function quarantineMetadataReviewItem(itemId: string) {
  return api.post(`/metadata-review/items/${itemId}/quarantine`, {});
}

export function pageMetadataQuarantineItems(params: {
  tenantId: string;
  kbId?: string;
  resolved?: boolean;
  current?: number;
  size?: number;
}) {
  return api.get<PageResult<MetadataQuarantineItem>>("/metadata-quarantine/items", { params }) as unknown as Promise<PageResult<MetadataQuarantineItem>>;
}

export function resolveMetadataQuarantineItem(itemId: string) {
  return api.post(`/metadata-quarantine/items/${itemId}/resolve`, {});
}

export function retryMetadataQuarantineItem(itemId: string) {
  return api.post(`/metadata-quarantine/items/${itemId}/retry`, {});
}

export function getMetadataQualityReport(tenantId: string, kbId: string) {
  return api.get<MetadataQualityReport>(`/knowledge-base/${kbId}/metadata-quality/report`, {
    params: { tenantId, topN: 5 }
  }) as unknown as Promise<MetadataQualityReport>;
}

// ── Schema 字段 CRUD ──

export function createMetadataSchemaField(tenantId: string, kbId: string, payload: Partial<MetadataSchemaField>) {
  return api.post<MetadataSchemaField, MetadataSchemaField>(
    `/knowledge-base/${encodeURIComponent(kbId)}/metadata-schema/fields`,
    payload,
    { params: { tenantId } }
  );
}

export function updateMetadataSchemaField(fieldId: string, payload: Partial<MetadataSchemaField>) {
  return api.put<MetadataSchemaField, MetadataSchemaField>(
    `/metadata-schema/fields/${encodeURIComponent(fieldId)}`,
    payload
  );
}

export function deleteMetadataSchemaField(fieldId: string) {
  return api.delete(`/metadata-schema/fields/${encodeURIComponent(fieldId)}`);
}

export function getMetadataSchemaFieldCapabilities(tenantId: string, kbId: string) {
  return api.get<Record<string, unknown>[]>(
    `/knowledge-base/${encodeURIComponent(kbId)}/metadata-schema/field-capabilities`,
    { params: { tenantId } }
  ) as unknown as Promise<Record<string, unknown>[]>;
}

// ── 抽取结果 ──

export function listMetadataExtractionResults(params: {
  tenantId?: string;
  kbId?: string;
  documentId?: string;
  current?: number;
  size?: number;
}) {
  return api.get<PageResult<Record<string, unknown>>>("/metadata-extraction/results", { params }) as unknown as Promise<PageResult<Record<string, unknown>>>;
}

export function getMetadataExtractionResult(resultId: string) {
  return api.get<Record<string, unknown>>(`/metadata-extraction/results/${encodeURIComponent(resultId)}`) as unknown as Promise<Record<string, unknown>>;
}

// ── Review 补全 ──

export function getMetadataReviewItemDetail(itemId: string) {
  return api.get<MetadataReviewItem>(`/metadata-review/items/${encodeURIComponent(itemId)}`) as unknown as Promise<MetadataReviewItem>;
}

export function getMetadataReviewAudits(itemId: string) {
  return api.get<Record<string, unknown>[]>(
    `/metadata-review/items/${encodeURIComponent(itemId)}/audits`
  ) as unknown as Promise<Record<string, unknown>[]>;
}

export function correctMetadataReviewItem(itemId: string, payload: Record<string, unknown>) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/metadata-review/items/${encodeURIComponent(itemId)}/correct`,
    payload
  );
}

export function ignoreMetadataReviewField(itemId: string, payload: { fieldKey?: string; reason?: string }) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/metadata-review/items/${encodeURIComponent(itemId)}/ignore-field`,
    payload
  );
}

export function reExtractMetadataReviewItem(itemId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/metadata-review/items/${encodeURIComponent(itemId)}/re-extract`
  );
}

// ── Backfill ──

export function listMetadataBackfillJobs(tenantId: string, kbId: string, params?: { current?: number; size?: number }) {
  return api.get<PageResult<Record<string, unknown>>>(
    `/knowledge-base/${encodeURIComponent(kbId)}/metadata-backfill/jobs`,
    { params: { tenantId, ...params } }
  ) as unknown as Promise<PageResult<Record<string, unknown>>>;
}

export function createMetadataBackfillJob(tenantId: string, kbId: string, payload: Record<string, unknown>) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/knowledge-base/${encodeURIComponent(kbId)}/metadata-backfill/jobs`,
    payload,
    { params: { tenantId } }
  );
}

export function getMetadataBackfillOverview(tenantId: string, kbId: string) {
  return api.get<Record<string, unknown>>(
    `/knowledge-base/${encodeURIComponent(kbId)}/metadata-backfill/overview`,
    { params: { tenantId } }
  ) as unknown as Promise<Record<string, unknown>>;
}

export function runNextMetadataBackfillJob(jobId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/metadata-backfill/jobs/${encodeURIComponent(jobId)}/run-next`
  );
}

export function pauseMetadataBackfillJob(jobId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/metadata-backfill/jobs/${encodeURIComponent(jobId)}/pause`
  );
}

export function resumeMetadataBackfillJob(jobId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/metadata-backfill/jobs/${encodeURIComponent(jobId)}/resume`
  );
}

export function cancelMetadataBackfillJob(jobId: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/metadata-backfill/jobs/${encodeURIComponent(jobId)}/cancel`
  );
}

// ── 质量对比 ──

export function compareMetadataQuality(tenantId: string, kbId: string, params: {
  baseVersion?: string;
  candidateVersion?: string;
}) {
  return api.get<Record<string, unknown>>(
    `/knowledge-base/${encodeURIComponent(kbId)}/metadata-quality/compare`,
    { params: { tenantId, ...params } }
  ) as unknown as Promise<Record<string, unknown>>;
}
