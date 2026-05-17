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
  });
}

export function pageMetadataReviewItems(params: {
  tenantId: string;
  kbId?: string;
  status?: string;
  current?: number;
  size?: number;
}) {
  return api.get<PageResult<MetadataReviewItem>>("/metadata-review/items", { params });
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
  return api.get<PageResult<MetadataQuarantineItem>>("/metadata-quarantine/items", { params });
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
  });
}
