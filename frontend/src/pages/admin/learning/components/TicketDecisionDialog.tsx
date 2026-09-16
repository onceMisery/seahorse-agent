import { useEffect, useState } from "react";
import { toast } from "sonner";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from "@/components/ui/alert-dialog";
import { Textarea } from "@/components/ui/textarea";
import { useAuthStore } from "@/stores/authStore";
import {
  applyPolicyAdjustmentTicket,
  approvePolicyAdjustmentTicket,
  rejectPolicyAdjustmentTicket,
  rollBackPolicyAdjustmentTicket,
  requiresComment,
  TICKET_ACTION_LABELS,
  type PolicyAdjustmentTicket,
  type TicketDecisionAction
} from "@/services/policyAdjustmentTicketService";
import { getErrorMessage } from "@/utils/error";

interface TicketDecisionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  ticket: PolicyAdjustmentTicket | null;
  action: TicketDecisionAction;
  onSuccess: () => void;
}

const ACTION_DESCRIPTIONS: Record<TicketDecisionAction, string> = {
  approve: "通过后进入「待落地」，仍需人工在配置侧完成调整再标记为已落地。",
  reject: "拒绝后工单进入终态，不再出现在待办里。请说明不采纳的原因。",
  apply: "标记已落地前，请确认配置侧已经改完。本系统不会替你修改任何阈值。",
  "roll-back": "回滚表示落地后判定为回归，工单进入终态。"
};

export function TicketDecisionDialog({
  open,
  onOpenChange,
  ticket,
  action,
  onSuccess
}: TicketDecisionDialogProps) {
  const currentUser = useAuthStore((state) => state.user);
  const [operator, setOperator] = useState("");
  const [comment, setComment] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setOperator(currentUser?.username || "");
      setComment("");
      setError("");
    }
  }, [open, currentUser?.username]);

  const actionLabel = TICKET_ACTION_LABELS[action];

  const handleSubmit = async () => {
    if (!ticket?.ticketId) return;

    const safeOperator = operator.trim();
    const safeComment = comment.trim();

    if (!safeOperator) {
      setError("请填写操作人，决策会进入审计记录");
      return;
    }
    if (requiresComment(action) && !safeComment) {
      setError(`${actionLabel}必须填写意见，否则工单只剩结论没有理由`);
      return;
    }
    setError("");

    try {
      setSubmitting(true);
      const payload = { operator: safeOperator, comment: safeComment };
      switch (action) {
        case "approve":
          await approvePolicyAdjustmentTicket(ticket.ticketId, payload);
          break;
        case "reject":
          await rejectPolicyAdjustmentTicket(ticket.ticketId, payload);
          break;
        case "apply":
          await applyPolicyAdjustmentTicket(ticket.ticketId, payload);
          break;
        case "roll-back":
          await rollBackPolicyAdjustmentTicket(ticket.ticketId, payload);
          break;
      }
      toast.success(`已${actionLabel}`);
      onSuccess();
    } catch (submitError) {
      toast.error(getErrorMessage(submitError, "操作失败"));
      console.error(submitError);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="sm:max-w-[540px]">
        <AlertDialogHeader>
          <AlertDialogTitle>确认{actionLabel}</AlertDialogTitle>
          <AlertDialogDescription>{ACTION_DESCRIPTIONS[action]}</AlertDialogDescription>
        </AlertDialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <label className="text-sm font-medium">操作人</label>
            <input
              value={operator}
              onChange={(e) => {
                setOperator(e.target.value);
                setError("");
              }}
              placeholder="请输入操作人"
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium">
              决策意见{requiresComment(action) ? "（必填）" : "（可选）"}
            </label>
            <Textarea
              value={comment}
              onChange={(e) => {
                setComment(e.target.value);
                setError("");
              }}
              placeholder="请说明依据，便于后续回溯"
              rows={4}
            />
            {error && <p className="text-xs text-destructive">{error}</p>}
          </div>
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction
            // Radix 的 Action 默认会在点击后关闭弹窗，校验失败时的错误提示就再也看不见了；
            // 成功路径由 onSuccess 触发的关闭负责，所以这里一律先拦下默认行为
            onClick={(event) => {
              event.preventDefault();
              handleSubmit();
            }}
            disabled={submitting}
            className={action === "reject" || action === "roll-back" ? "bg-destructive text-destructive-foreground" : ""}
          >
            {submitting ? "处理中..." : `确认${actionLabel}`}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
