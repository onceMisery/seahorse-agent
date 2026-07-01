import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription
} from "@/components/ui/sheet";
import { type ApprovalItem } from "@/services/approvalService";
import { ApprovalDecisionDialog } from "./ApprovalDecisionDialog";

interface ApprovalDetailDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  approval: ApprovalItem | null;
  onDecisionComplete: () => void;
}

function normalizedStatus(status?: string) {
  return (status || "").toUpperCase();
}

export function ApprovalDetailDrawer({ open, onOpenChange, approval, onDecisionComplete }: ApprovalDetailDrawerProps) {
  const [decisionDialogState, setDecisionDialogState] = useState<{ open: boolean; action: "approve" | "reject" | "modify" }>({
    open: false,
    action: "approve"
  });

  if (!approval) return null;

  const isPending = normalizedStatus(approval.status) === "PENDING";

  const getStatusBadge = (status?: string) => {
    switch (normalizedStatus(status)) {
      case "PENDING":
        return <Badge className="bg-amber-100 text-amber-700">待审批</Badge>;
      case "APPROVED":
        return <Badge className="bg-green-100 text-green-700">已通过</Badge>;
      case "REJECTED":
        return <Badge variant="destructive">已拒绝</Badge>;
      case "MODIFIED":
        return <Badge className="bg-blue-100 text-blue-700">已修改</Badge>;
      default:
        return <Badge variant="outline">{status || "-"}</Badge>;
    }
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-[600px] overflow-y-auto">
        <SheetHeader>
          <SheetTitle>审批详情</SheetTitle>
          <SheetDescription>查看审批请求的详细信息和上下文</SheetDescription>
        </SheetHeader>

        <div className="mt-6 space-y-6">
          {/* 基本信息 */}
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-slate-700">基本信息</h3>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <div className="text-slate-500">审批 ID</div>
                <div className="font-medium">{approval.approvalId || "-"}</div>
              </div>
              <div>
                <div className="text-slate-500">状态</div>
                <div className="mt-1">{getStatusBadge(approval.status)}</div>
              </div>
              <div>
                <div className="text-slate-500">工具</div>
                <div className="font-medium">{approval.toolName || "-"}</div>
              </div>
              <div>
                <div className="text-slate-500">风险等级</div>
                <div>{approval.riskLevel || "-"}</div>
              </div>
              <div>
                <div className="text-slate-500">提交人</div>
                <div>{approval.submittedBy || "-"}</div>
              </div>
              <div>
                <div className="text-slate-500">提交时间</div>
                <div>{approval.createTime ? new Date(approval.createTime).toLocaleString("zh-CN") : "-"}</div>
              </div>
            </div>
          </div>

          {/* 关联信息 */}
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-slate-700">关联信息</h3>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <div className="text-slate-500">Agent ID</div>
                <div className="font-medium">{approval.agentId || "-"}</div>
              </div>
              <div>
                <div className="text-slate-500">Run ID</div>
                <div className="font-medium">{approval.runId || "-"}</div>
              </div>
            </div>
          </div>

          {/* 原始参数 */}
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-slate-700">原始参数</h3>
            <pre className="bg-slate-50 p-3 rounded-lg text-xs font-mono overflow-auto max-h-[200px]">
              {approval.argumentsPreviewJson
                ? (() => {
                    try {
                      return JSON.stringify(JSON.parse(approval.argumentsPreviewJson), null, 2);
                    } catch {
                      return approval.argumentsPreviewJson;
                    }
                  })()
                : "-"}
            </pre>
          </div>

          {/* 决策信息（已处理） */}
          {!isPending && (
            <div className="space-y-3">
              <h3 className="text-sm font-semibold text-slate-700">决策信息</h3>
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <div className="text-slate-500">决策人</div>
                  <div>{approval.decidedBy || "-"}</div>
                </div>
                <div>
                  <div className="text-slate-500">决策时间</div>
                  <div>{approval.decidedAt ? new Date(approval.decidedAt).toLocaleString("zh-CN") : "-"}</div>
                </div>
              </div>
              {approval.decisionComment && (
                <div>
                  <div className="text-slate-500 text-sm">备注</div>
                  <div className="text-sm mt-1">{approval.decisionComment}</div>
                </div>
              )}
              {approval.modifiedArgumentsJson && (
                <div>
                  <div className="text-slate-500 text-sm">修改后参数</div>
                  <pre className="bg-slate-50 p-3 rounded-lg text-xs font-mono overflow-auto max-h-[200px] mt-1">
                    {(() => {
                      try {
                        return JSON.stringify(JSON.parse(approval.modifiedArgumentsJson), null, 2);
                      } catch {
                        return approval.modifiedArgumentsJson;
                      }
                    })()}
                  </pre>
                </div>
              )}
            </div>
          )}

          {/* 操作按钮（仅待审批） */}
          {isPending && (
            <div className="flex gap-2 pt-4 border-t">
              <Button
                className="admin-primary-gradient"
                onClick={() => setDecisionDialogState({ open: true, action: "approve" })}
              >
                通过
              </Button>
              <Button
                variant="destructive"
                onClick={() => setDecisionDialogState({ open: true, action: "reject" })}
              >
                拒绝
              </Button>
              <Button
                variant="outline"
                onClick={() => setDecisionDialogState({ open: true, action: "modify" })}
              >
                修改参数
              </Button>
            </div>
          )}
        </div>

        <ApprovalDecisionDialog
          open={decisionDialogState.open}
          onOpenChange={(open) => setDecisionDialogState((prev) => ({ ...prev, open }))}
          approval={approval}
          action={decisionDialogState.action}
          onSuccess={onDecisionComplete}
        />
      </SheetContent>
    </Sheet>
  );
}
