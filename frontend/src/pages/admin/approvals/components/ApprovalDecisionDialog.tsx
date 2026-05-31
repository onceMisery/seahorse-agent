import { useState } from "react";
import { toast } from "sonner";

import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Textarea } from "@/components/ui/textarea";
import { approveApprovalRequest, rejectApprovalRequest, modifyApprovalRequest, type ApprovalItem } from "@/services/approvalService";
import { getErrorMessage } from "@/utils/error";

interface ApprovalDecisionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  approval: ApprovalItem | null;
  action: "approve" | "reject" | "modify";
  onSuccess: () => void;
}

export function ApprovalDecisionDialog({ open, onOpenChange, approval, action, onSuccess }: ApprovalDecisionDialogProps) {
  const [comment, setComment] = useState("");
  const [modifiedArgs, setModifiedArgs] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [jsonError, setJsonError] = useState("");

  const actionLabel = action === "approve" ? "通过" : action === "reject" ? "拒绝" : "修改参数";

  // Initialize modified args when dialog opens
  const handleOpenChange = (isOpen: boolean) => {
    if (isOpen && approval?.argumentsPreviewJson) {
      try {
        const parsed = JSON.parse(approval.argumentsPreviewJson);
        setModifiedArgs(JSON.stringify(parsed, null, 2));
      } catch {
        setModifiedArgs(approval.argumentsPreviewJson);
      }
    }
    if (!isOpen) {
      setComment("");
      setModifiedArgs("");
      setJsonError("");
    }
    onOpenChange(isOpen);
  };

  const validateModifiedArgs = (): boolean => {
    if (action !== "modify") return true;
    if (!modifiedArgs.trim()) {
      setJsonError("修改后的参数不能为空");
      return false;
    }
    try {
      JSON.parse(modifiedArgs);
      setJsonError("");
      return true;
    } catch {
      setJsonError("参数 JSON 格式不合法，请检查");
      return false;
    }
  };

  const handleSubmit = async () => {
    if (!approval?.approvalId) return;

    if (action === "modify" && !validateModifiedArgs()) return;

    try {
      setSubmitting(true);
      switch (action) {
        case "approve":
          await approveApprovalRequest(approval.approvalId, comment.trim());
          toast.success("已通过审批");
          break;
        case "reject":
          await rejectApprovalRequest(approval.approvalId, comment.trim());
          toast.success("已拒绝审批");
          break;
        case "modify":
          await modifyApprovalRequest(approval.approvalId, modifiedArgs.trim(), comment.trim());
          toast.success("已修改参数并提交");
          break;
      }
      setComment("");
      setModifiedArgs("");
      setJsonError("");
      onSuccess();
    } catch (error) {
      toast.error(getErrorMessage(error, "操作失败"));
      console.error(error);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogContent className="sm:max-w-[540px]">
        <AlertDialogHeader>
          <AlertDialogTitle>确认{actionLabel}</AlertDialogTitle>
          <AlertDialogDescription>
            {action === "approve" && "通过后将允许工具调用继续执行。"}
            {action === "reject" && "拒绝后工具调用将被中止。"}
            {action === "modify" && "修改参数后工具将以新参数继续执行。"}
          </AlertDialogDescription>
        </AlertDialogHeader>

        <div className="space-y-4">
          {action === "modify" && (
            <div className="space-y-2">
              <label className="text-sm font-medium">修改后的参数 (JSON)</label>
              <Textarea
                value={modifiedArgs}
                onChange={(e) => {
                  setModifiedArgs(e.target.value);
                  setJsonError("");
                }}
                placeholder="请输入修改后的参数 JSON"
                rows={8}
                className="font-mono text-sm"
              />
              {jsonError && <p className="text-xs text-destructive">{jsonError}</p>}
            </div>
          )}

          <div className="space-y-2">
            <label className="text-sm font-medium">备注</label>
            <Textarea
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="请输入决策备注（可选）"
              rows={3}
            />
          </div>
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction
            onClick={handleSubmit}
            disabled={submitting || !!jsonError}
            className={action === "reject" ? "bg-destructive text-destructive-foreground" : ""}
          >
            {submitting ? "处理中..." : `确认${actionLabel}`}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
