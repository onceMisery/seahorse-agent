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

/** 回访结论；与后端 PreviousProposalOutcome.Status 一致。 */
export type ReviewOutcome =
  | "NO_PROPOSAL"
  | "AWAITING_MEASUREMENT"
  | "VERIFIED_IMPROVED"
  | "REGRESSED"
  | "INSUFFICIENT_EVIDENCE";

/**
 * 一条回访记录："上一次改动到底有没有用"。
 *
 * 注意：后端 {@code rollbackRecommended()} 是派生布尔值，Jackson 对 record
 * 只序列化组件、不带派生方法，因此前端必须自己从 outcome 推导，不能读该字段。
 */
export interface PolicyAdjustmentTicketReview {
  reviewId?: string;
  ticketId?: string;
  tenantId?: string;
  batchId?: string;
  outcome?: ReviewOutcome;
  detail?: string;
  baselinePassRate?: number | null;
  currentPassRate?: number | null;
  source?: string;
  reviewedAt?: string;
}

/** 列表页响应：分页数据 + 本页每张工单最近一次回访。 */
export interface TicketPageResponse {
  page?: PageResult<PolicyAdjustmentTicket>;
  latestReviews?: Record<string, PolicyAdjustmentTicketReview>;
}

/** 批量审批结果：部分失败时仍返回 200，失败明细在 failed 里。 */
export interface TicketBatchDecisionResult {
  targetStatus?: PolicyAdjustmentTicketStatus;
  succeededTicketIds?: string[];
  failed?: Array<{ ticketId?: string; reason?: string }>;
}

/** 一次回访批次的执行结果。 */
export interface TicketReviewRunResult {
  reviews?: PolicyAdjustmentTicketReview[];
  scanned?: number;
  skipped?: number;
  note?: string;
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
  /** 与 currentPassRate 同时给出时，跑闭环之外还会顺带回访已落地工单。 */
  baselinePassRate?: number;
  currentPassRate?: number;
}

export interface SubmitProposalsPayload {
  report?: LearningLoopReport | null;
  tenantId?: string;
  createdBy?: string;
}

/**
 * 手动触发一次回访。
 *
 * batchId 与工单一同构成幂等键：不传则服务端现生成一个，此时重复提交只能靠
 * 冷却期兜底。定时任务传按时间分桶的稳定值。
 */
export interface ReviewRunPayload {
  tenantId?: string;
  batchId?: string;
  baselinePassRate?: number;
  currentPassRate?: number;
  source?: string;
}

export interface BatchDecisionPayload {
  ticketIds?: string[];
  operator?: string;
  comment?: string;
}

// ── API ──

/** 兜底值保持与后端结构一致，避免调用方在"未接数据源"时拿到另一套形状。 */
function emptyTicketPage(current?: number, size?: number): TicketPageResponse {
  return { page: emptyPage<PolicyAdjustmentTicket>(current, size), latestReviews: {} };
}

export function listPolicyAdjustmentTickets(params: ListTicketsParams) {
  return optionalGet(
    api.get<TicketPageResponse>("/api/policy-adjustment-tickets", {
      params,
      suppressErrorToast: true
    }),
    emptyTicketPage(params.current, params.size)
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

/**
 * 批量通过。
 *
 * 部分失败时仍返回 200（失败明细在 failed 里），因此不能用 catch 判定"整批失败"——
 * 要读 failed 长度。整批拒绝会让一次误操作毁掉整批，前端也拿不到哪几张失败了。
 */
export function batchApprovePolicyAdjustmentTickets(payload: BatchDecisionPayload) {
  return api.post<TicketBatchDecisionResult>(
    "/api/policy-adjustment-tickets/batch-approve",
    payload
  );
}

/** 批量驳回。驳回必须填写意见，缺意见时后端整批拒绝并返回 400。 */
export function batchRejectPolicyAdjustmentTickets(payload: BatchDecisionPayload) {
  return api.post<TicketBatchDecisionResult>(
    "/api/policy-adjustment-tickets/batch-reject",
    payload
  );
}

/** 手动触发一次回访：对所有 APPLIED 工单记录"上一次改动到底有没有用"。 */
export function runPolicyAdjustmentTicketReviews(payload: ReviewRunPayload) {
  return api.post<TicketReviewRunResult>("/api/policy-adjustment-tickets/reviews/run", payload);
}

/** 某张工单的回访历史，供详情抽屉展示。 */
export function listPolicyAdjustmentTicketReviews(ticketId: string, limit = 20) {
  return api.get<PolicyAdjustmentTicketReview[]>(
    `/api/policy-adjustment-tickets/${encodeURIComponent(ticketId)}/reviews`,
    { params: { limit } }
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

export const REVIEW_OUTCOME_LABELS: Record<ReviewOutcome, string> = {
  NO_PROPOSAL: "无历史建议",
  AWAITING_MEASUREMENT: "缺少评测数据",
  VERIFIED_IMPROVED: "确认改进",
  REGRESSED: "判定回归",
  INSUFFICIENT_EVIDENCE: "证据不足"
};

/**
 * 是否建议回滚。
 *
 * 后端 PolicyAdjustmentTicketReview.rollbackRecommended() 是派生布尔值，
 * Jackson 只序列化 record 组件、不带派生方法（后端契约测试已断言该字段不存在），
 * 所以这里必须自己从 outcome 推导。
 */
export function isRollbackRecommended(review?: PolicyAdjustmentTicketReview | null) {
  return review?.outcome === "REGRESSED";
}
