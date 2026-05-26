import { api } from "@/services/api";

export type ApprovalRequestRecord = Record<string, unknown>;

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
