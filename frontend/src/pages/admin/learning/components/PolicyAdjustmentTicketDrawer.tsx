import { useEffect, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import {
  listPolicyAdjustmentTicketReviews,
  isRollbackRecommended,
  REVIEW_OUTCOME_LABELS,
  TICKET_DIRECTION_LABELS,
  TICKET_STATUS_LABELS,
  type PolicyAdjustmentTicket,
  type PolicyAdjustmentTicketReview,
  type PolicyAdjustmentTicketStatus,
  type TicketDecisionAction
} from "@/services/policyAdjustmentTicketService";
import { TicketDecisionDialog } from "./TicketDecisionDialog";

interface PolicyAdjustmentTicketDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  ticket: PolicyAdjustmentTicket | null;
  onDecisionComplete: () => void;
}

const STATUS_STYLES: Record<PolicyAdjustmentTicketStatus, string> = {
  PENDING: "bg-amber-100 text-amber-700",
  APPROVED: "bg-green-100 text-green-700",
  REJECTED: "bg-red-100 text-red-700",
  APPLIED: "bg-blue-100 text-blue-700",
  ROLLED_BACK: "bg-slate-100 text-slate-700"
};

/** 每个状态允许的动作，与后端 canTransitionTo 保持一致，避免前端给出点了必错的按钮。 */
const ALLOWED_ACTIONS: Record<PolicyAdjustmentTicketStatus, TicketDecisionAction[]> = {
  PENDING: ["approve", "reject"],
  APPROVED: ["apply", "reject"],
  APPLIED: ["roll-back"],
  REJECTED: [],
  ROLLED_BACK: []
};

function formatPercent(value?: number) {
  if (typeof value !== "number" || Number.isNaN(value)) return "-";
  return `${(value * 100).toFixed(1)}%`;
}

function formatTime(value?: string) {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN");
}

export function PolicyAdjustmentTicketDrawer({
  open,
  onOpenChange,
  ticket,
  onDecisionComplete
}: PolicyAdjustmentTicketDrawerProps) {
  const [dialogState, setDialogState] = useState<{ open: boolean; action: TicketDecisionAction }>({
    open: false,
    action: "approve"
  });
  const [reviews, setReviews] = useState<PolicyAdjustmentTicketReview[]>([]);

  const ticketId = ticket?.ticketId;
  useEffect(() => {
    if (!open || !ticketId) return;
    let cancelled = false;
    // 回访历史是非关键信息：列表页已从 latestReviews 拿到最近一条，
    // 这里拉取失败不该让整个抽屉不可用，所以静默降级为空列表
    listPolicyAdjustmentTicketReviews(ticketId)
      .then((data) => {
        if (!cancelled) setReviews(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (!cancelled) setReviews([]);
      });
    return () => {
      cancelled = true;
    };
  }, [open, ticketId]);

  if (!ticket) return null;

  const status = ticket.status ?? "PENDING";
  const actions = ALLOWED_ACTIONS[status] ?? [];

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-[600px] overflow-y-auto">
        <SheetHeader>
          <SheetTitle>策略调整工单详情</SheetTitle>
          <SheetDescription>查看建议依据，并决定是否在配置侧落地</SheetDescription>
        </SheetHeader>

        <div className="mt-6 space-y-6">
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-slate-700">基本信息</h3>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <div className="text-slate-500">工单 ID</div>
                <div className="font-medium">{ticket.ticketId || "-"}</div>
              </div>
              <div>
                <div className="text-slate-500">状态</div>
                <div className="mt-1">
                  <Badge className={STATUS_STYLES[status]}>
                    {TICKET_STATUS_LABELS[status] || status}
                  </Badge>
                </div>
              </div>
              <div>
                <div className="text-slate-500">目标</div>
                <div className="font-medium">{ticket.targetKey || "-"}</div>
              </div>
              <div>
                <div className="text-slate-500">配置键</div>
                <div className="font-medium">{ticket.policyKey || "-"}</div>
              </div>
              <div>
                <div className="text-slate-500">调整方向</div>
                <div>
                  {ticket.direction ? TICKET_DIRECTION_LABELS[ticket.direction] || ticket.direction : "-"}
                </div>
              </div>
              <div>
                <div className="text-slate-500">建议幅度</div>
                <div>{typeof ticket.suggestedDelta === "number" ? ticket.suggestedDelta : "-"}</div>
              </div>
              <div>
                <div className="text-slate-500">不认可率</div>
                <div>{formatPercent(ticket.disapprovalRate)}</div>
              </div>
              <div>
                <div className="text-slate-500">样本量</div>
                <div>{ticket.sampleCount ?? "-"}</div>
              </div>
              <div>
                <div className="text-slate-500">创建人</div>
                <div>{ticket.createdBy || "-"}</div>
              </div>
              <div>
                <div className="text-slate-500">创建时间</div>
                <div>{formatTime(ticket.createdAt)}</div>
              </div>
            </div>
          </div>

          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-slate-700">建议依据</h3>
            <p className="text-sm text-slate-700 whitespace-pre-wrap">{ticket.rationale || "-"}</p>
            {ticket.evidenceIds && ticket.evidenceIds.length > 0 && (
              <div className="flex flex-wrap gap-1">
                {ticket.evidenceIds.map((id) => (
                  <Badge key={id} variant="outline" className="font-mono text-xs">
                    {id}
                  </Badge>
                ))}
              </div>
            )}
          </div>

          {ticket.decidedBy && (
            <div className="space-y-3">
              <h3 className="text-sm font-semibold text-slate-700">决策信息</h3>
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <div className="text-slate-500">决策人</div>
                  <div>{ticket.decidedBy}</div>
                </div>
                <div>
                  <div className="text-slate-500">决策时间</div>
                  <div>{formatTime(ticket.decidedAt)}</div>
                </div>
              </div>
              {ticket.decisionComment && (
                <div>
                  <div className="text-slate-500 text-sm">决策意见</div>
                  <div className="text-sm mt-1 whitespace-pre-wrap">{ticket.decisionComment}</div>
                </div>
              )}
            </div>
          )}

          {ticket.appliedBy && (
            <div className="space-y-3">
              <h3 className="text-sm font-semibold text-slate-700">落地信息</h3>
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <div className="text-slate-500">落地人</div>
                  <div>{ticket.appliedBy}</div>
                </div>
                <div>
                  <div className="text-slate-500">落地时间</div>
                  <div>{formatTime(ticket.appliedAt)}</div>
                </div>
              </div>
              {ticket.rollbackComment && (
                <div>
                  <div className="text-slate-500 text-sm">回滚说明</div>
                  <div className="text-sm mt-1 whitespace-pre-wrap">{ticket.rollbackComment}</div>
                </div>
              )}
            </div>
          )}

          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-slate-700">回访记录</h3>
            {reviews.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                还没有回访记录。工单落地后，执行回访才会留下「这次改动到底有没有用」的结论。
              </p>
            ) : (
              <ul className="space-y-2">
                {reviews.map((review) => {
                  const rollback = isRollbackRecommended(review);
                  return (
                    <li
                      key={review.reviewId || `${review.ticketId}-${review.reviewedAt}`}
                      className="rounded-md border border-slate-200 px-3 py-2 text-sm"
                    >
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge
                          variant="outline"
                          className={rollback ? "bg-red-50 text-red-700 border-red-200" : ""}
                        >
                          {rollback
                            ? "建议回滚"
                            : REVIEW_OUTCOME_LABELS[review.outcome ?? "AWAITING_MEASUREMENT"]}
                        </Badge>
                        <span className="text-xs text-slate-500">{formatTime(review.reviewedAt)}</span>
                        {review.source && (
                          <span className="text-xs text-slate-400">来源：{review.source}</span>
                        )}
                      </div>
                      {review.detail && (
                        <p className="mt-1 text-slate-700 whitespace-pre-wrap">{review.detail}</p>
                      )}
                      {typeof review.baselinePassRate === "number" &&
                        typeof review.currentPassRate === "number" && (
                          <p className="mt-1 text-xs text-slate-500">
                            基线 {formatPercent(review.baselinePassRate)} → 当前{" "}
                            {formatPercent(review.currentPassRate)}
                          </p>
                        )}
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          {actions.length > 0 && (
            <div className="flex gap-2 pt-4 border-t">
              <Button
                className="admin-primary-gradient"
                onClick={() => setDialogState({ open: true, action: actions[0] })}
              >
                {actions[0] === "approve" ? "通过" : actions[0] === "apply" ? "标记已落地" : "回滚"}
              </Button>
              {actions[1] && (
                <Button variant="destructive" onClick={() => setDialogState({ open: true, action: actions[1] })}>
                  拒绝
                </Button>
              )}
            </div>
          )}
        </div>

        <TicketDecisionDialog
          open={dialogState.open}
          onOpenChange={(next) => setDialogState((prev) => ({ ...prev, open: next }))}
          ticket={ticket}
          action={dialogState.action}
          onSuccess={onDecisionComplete}
        />
      </SheetContent>
    </Sheet>
  );
}
