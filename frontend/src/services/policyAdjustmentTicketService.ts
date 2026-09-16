import { api } from "@/services/api";
import type { PageResult } from "@/services/metadataGovernanceService";
import { emptyPage, optionalGet } from "@/services/optionalEndpoint";

// ── 类型定义：字段名与后端 PolicyAdjustmentTicket / LearningLoopReport record 完全一致 ──

export type PolicyAdjustmentTicketStatus =
  | "PENDING"
  | "APPROVED"
  | "REJECTED"
  | "APPLIED"
  | "ROLLED_BACK";

export type PolicyAdjustmentDirection = "TIGHTEN" | "LOOSEN" | "QUARANTINE_TARGET";

/** 工单动作；与后端 @PostMapping 的子路径一致。 */
export type TicketDecisionAction = "approve" | "reject" | "apply" | "roll-back";

export interface PolicyAdjustmentTicket {
  ticketId?: string;
  tenantId?: string;
  targetKey?: string;
  policyKey?: string;
  direction?: PolicyAdjustmentDirection;
  suggestedDelta?: number;
  disapprovalRate?: number;
  sampleCount?: number;
  rationale?: string;
  evidenceIds?: string[];
  fingerprint?: string;
  status?: PolicyAdjustmentTicketStatus;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  decidedBy?: string;
  decidedAt?: string;
  decisionComment?: string;
  appliedBy?: string;
  appliedAt?: string;
  rollbackComment?: string;
}

export interface LearningSignal {
  targetKey?: string;
  targetKind?: string;
  positiveVotes?: number;
  negativeVotes?: number;
  approvedReviews?: number;
  rejectedReviews?: number;
  evidenceIds?: string[];
}

export interface PolicyAdjustmentProposal {
  targetKey?: string;
  policyKey?: string;
  direction?: PolicyAdjustmentDirection;
  suggestedDelta?: number;
  disapprovalRate?: number;
  sampleCount?: number;
  rationale?: string;
  evidenceIds?: string[];
}

export interface PreviousProposalOutcome {
  status?: string;
  detail?: string;
}

export interface LearningLoopReport {
  generatedAt?: string;
  signals?: LearningSignal[];
  proposals?: PolicyAdjustmentProposal[];
  previousOutcome?: PreviousProposalOutcome;
  summary?: string;
}

/** 一次"跑闭环并开工单"的结果：报告与工单一并返回。 */
export interface LearningLoopRunResult {
  report?: LearningLoopReport;
  tickets?: PolicyAdjustmentTicket[];
}

export interface ListTicketsParams {
  tenantId?: string;
  status?: PolicyAdjustmentTicketStatus;
  current?: number;
  size?: number;
}

export interface GenerateTicketsPayload {
  tenantId?: string;
  createdBy?: string;
  sampleLimit?: number;
}

export interface SubmitProposalsPayload {
  report?: LearningLoopReport | null;
  tenantId?: string;
  createdBy?: string;
}

// ── API ──

export function listPolicyAdjustmentTickets(params: ListTicketsParams) {
  return optionalGet(
    api.get<PageResult<PolicyAdjustmentTicket>>("/api/policy-adjustment-tickets", {
      params,
      suppressErrorToast: true
    }),
    emptyPage<PolicyAdjustmentTicket>(params.current, params.size)
  );
}

export function getPolicyAdjustmentTicket(ticketId: string) {
  return api.get<PolicyAdjustmentTicket>(
    `/api/policy-adjustment-tickets/${encodeURIComponent(ticketId)}`
  );
}

export function generatePolicyAdjustmentTickets(payload: GenerateTicketsPayload) {
  return api.post<LearningLoopRunResult>("/api/policy-adjustment-tickets/generate", payload);
}

export function submitPolicyAdjustmentProposals(payload: SubmitProposalsPayload) {
  return api.post<PolicyAdjustmentTicket[]>(
    "/api/policy-adjustment-tickets/from-report",
    payload
  );
}

export function approvePolicyAdjustmentTicket(ticketId: string, payload: { operator?: string; comment?: string }) {
  return api.post<PolicyAdjustmentTicket>(
    `/api/policy-adjustment-tickets/${encodeURIComponent(ticketId)}/approve`,
    payload
  );
}

export function rejectPolicyAdjustmentTicket(ticketId: string, payload: { operator?: string; comment?: string }) {
  return api.post<PolicyAdjustmentTicket>(
    `/api/policy-adjustment-tickets/${encodeURIComponent(ticketId)}/reject`,
    payload
  );
}

export function applyPolicyAdjustmentTicket(ticketId: string, payload: { operator?: string; comment?: string }) {
  return api.post<PolicyAdjustmentTicket>(
    `/api/policy-adjustment-tickets/${encodeURIComponent(ticketId)}/apply`,
    payload
  );
}

export function rollBackPolicyAdjustmentTicket(ticketId: string, payload: { operator?: string; comment?: string }) {
  return api.post<PolicyAdjustmentTicket>(
    `/api/policy-adjustment-tickets/${encodeURIComponent(ticketId)}/roll-back`,
    payload
  );
}

// ── 展示辅助：状态与方向的中文标签，页面与抽屉共用同一套 ──

export const TICKET_STATUS_LABELS: Record<PolicyAdjustmentTicketStatus, string> = {
  PENDING: "待审批",
  APPROVED: "已通过",
  REJECTED: "已拒绝",
  APPLIED: "已落地",
  ROLLED_BACK: "已回滚"
};

export const TICKET_DIRECTION_LABELS: Record<PolicyAdjustmentDirection, string> = {
  TIGHTEN: "收紧阈值",
  LOOSEN: "放宽阈值",
  QUARANTINE_TARGET: "隔离目标"
};

/** 后端要求：拒绝与落地必须填写意见，否则 400。 */
export function requiresComment(action: TicketDecisionAction) {
  return action === "reject" || action === "apply";
}

export const TICKET_ACTION_LABELS: Record<TicketDecisionAction, string> = {
  approve: "通过",
  reject: "拒绝",
  apply: "标记已落地",
  "roll-back": "回滚"
};
