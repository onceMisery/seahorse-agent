import { useEffect, useState } from "react";

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
  batchApprovePolicyAdjustmentTickets,
  batchRejectPolicyAdjustmentTickets,
  type TicketBatchDecisionResult
} from "@/services/policyAdjustmentTicketService";
import { getErrorMessage } from "@/utils/error";

/** 批量只支持通过/驳回：落地与回滚意味着"配置侧真的改了/还原了"，逐张确认才安全。 */
export type TicketBatchAction = "approve" | "reject";

interface BatchDecisionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  ticketIds: string[];
  action: TicketBatchAction;
  /**
   * 无论成功、部分失败还是整批异常，都把结果交回页面处理。
   * 部分失败时后端仍返回 200，靠抛异常无法感知，必须读 failed。
   */
  onResult: (result: TicketBatchDecisionResult | null, error?: unknown) => void;
}

const ACTION_DESCRIPTIONS: Record<TicketBatchAction, string> = {
  approve: "通过后进入「待落地」，仍需人工在配置侧完成调整再标记为已落地。",
  reject: "驳回必须说明理由——否则会留下一批「被驳回但不知道为什么」的工单。"
};

/**
 * 批量场景的措辞与单条不同：列表操作说"驳回"，
 * 而 TICKET_ACTION_LABELS 面向抽屉里的单条决策说"拒绝"。
 * 两处不统一会让同一个动作看起来像两个功能，这里显式给批量一套标签。
 */
const BATCH_ACTION_LABELS: Record<TicketBatchAction, string> = {
  approve: "通过",
  reject: "驳回"
};

export function BatchDecisionDialog({
  open,
  onOpenChange,
  ticketIds,
  action,
  onResult
}: BatchDecisionDialogProps) {
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

  const actionLabel = BATCH_ACTION_LABELS[action];

  const handleSubmit = async () => {
    if (ticketIds.length === 0) {
      setError("请先勾选工单");
      return;
    }
    const safeOperator = operator.trim();
    const safeComment = comment.trim();

    if (!safeOperator) {
      setError("请填写操作人，决策会进入审计记录");
      return;
    }
    // 与后端一致：批量驳回缺意见时是整批拒绝（400），不能逐张去试
    if (action === "reject" && !safeComment) {
      setError("批量驳回必须填写意见，否则工单只剩结论没有理由");
      return;
    }
    setError("");

    try {
      setSubmitting(true);
      const payload = { ticketIds, operator: safeOperator, comment: safeComment };
      const result =
        action === "approve"
          ? await batchApprovePolicyAdjustmentTickets(payload)
          : await batchRejectPolicyAdjustmentTickets(payload);
      onOpenChange(false);
      onResult(result ?? null);
    } catch (submitError) {
      // 整批失败（如缺意见、未接数据源）在这里；部分失败走上面的 onResult
      setError(getErrorMessage(submitError, `批量${actionLabel}失败`));
      onResult(null, submitError);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="sm:max-w-[540px]">
        <AlertDialogHeader>
          <AlertDialogTitle>
            批量{actionLabel}（{ticketIds.length} 张）
          </AlertDialogTitle>
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
              决策意见{action === "reject" ? "（必填）" : "（可选，会写入每张工单）"}
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
            // 同单条对话框：Radix 的 Action 点击即关闭，会吞掉校验错误
            onClick={(event) => {
              event.preventDefault();
              handleSubmit();
            }}
            disabled={submitting}
            className={action === "reject" ? "bg-destructive text-destructive-foreground" : ""}
          >
            {submitting ? "处理中..." : `确认批量${actionLabel}`}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
