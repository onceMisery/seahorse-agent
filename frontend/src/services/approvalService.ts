import { api } from "@/services/api";
import type { PageResult } from "@/services/metadataGovernanceService";

// ── 类型定义 ──

export type ApprovalRequestRecord = Record<string, unknown>;

export interface ApprovalItem {
  approvalId?: string;
  runId?: string;
  agentId?: string;
  toolId?: string;
  toolName?: string;
  status?: string;
  riskLevel?: string;
  argumentsPreviewJson?: string;
  submittedBy?: string;
  decisionComment?: string;
  decidedBy?: string;
  decidedAt?: string;
  modifiedArgumentsJson?: string;
  createTime?: string;
}

// ── 原有 API（保留） ──

export async function listPendingApprovalRequests(runId: string) {
  return api.get<ApprovalRequestRecord[], ApprovalRequestRecord[]>(
    `/api/agent-runs/${encodeURIComponent(runId)}/pending-approvals`
  );
}

export async function approveApprovalRequest(approvalId: string, decisionComment: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/approvals/${encodeURIComponent(approvalId)}/approve`,
    { decisionComment }
  );
}

export async function rejectApprovalRequest(approvalId: string, decisionComment: string) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/approvals/${encodeURIComponent(approvalId)}/reject`,
    { decisionComment }
  );
}

export async function modifyApprovalRequest(
  approvalId: string,
  argumentsPreviewJson: string,
  decisionComment: string
) {
  return api.post<Record<string, unknown>, Record<string, unknown>>(
    `/api/approvals/${encodeURIComponent(approvalId)}/modify`,
    { argumentsPreviewJson, decisionComment }
  );
}

// ── 审批中心新增 API ──

export function listApprovals(params: {
  current?: number;
  size?: number;
  status?: string;
  riskLevel?: string;
  toolId?: string;
  agentId?: string;
  runId?: string;
  submittedBy?: string;
  startTime?: string;
  endTime?: string;
}) {
  return api.get<PageResult<ApprovalItem>>("/api/approvals", { params });
}

export function getApproval(approvalId: string) {
  return api.get<ApprovalItem>(`/api/approvals/${encodeURIComponent(approvalId)}`);
}
