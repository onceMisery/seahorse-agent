import { useState } from "react";
import { XCircle, RotateCcw, Play } from "lucide-react";
import { toast } from "sonner";

import {
  cancelAgentRun,
  retryAgentRunAction,
  resumeAgentRunAction
} from "@/services/agentArtifactService";
import { getErrorMessage } from "@/utils/error";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";

type ActionType = "cancel" | "retry" | "resume";

const ACTION_CONFIG: Record<ActionType, { label: string; icon: typeof XCircle; variant: "destructive" | "default"; description: string }> = {
  cancel: { label: "取消", icon: XCircle, variant: "destructive", description: "取消运行中的 Agent Run？此操作不可撤销。" },
  retry: { label: "重试", icon: RotateCcw, variant: "default", description: "重试此 Agent Run？将从失败点重新开始。" },
  resume: { label: "恢复", icon: Play, variant: "default", description: "恢复等待中的 Agent Run？" }
};

export function AgentRunActions({
  runId,
  status,
  onActionComplete
}: {
  runId: string;
  status?: string;
  onActionComplete?: () => void;
}) {
  const [actionType, setActionType] = useState<ActionType | null>(null);
  const [comment, setComment] = useState("");
  const [loading, setLoading] = useState(false);

  const canCancel = status === "RUNNING" || status === "ACTIVE";
  const canRetry = status === "FAILED" || status === "ERROR";
  const canResume = status === "WAITING" || status === "PAUSED" || status === "SUSPENDED";

  const executeAction = async () => {
    if (!actionType) return;
    setLoading(true);
    try {
      switch (actionType) {
        case "cancel":
          await cancelAgentRun(runId);
          toast.success("运行已取消");
          break;
        case "retry":
          await retryAgentRunAction(runId);
          toast.success("运行已重试");
          break;
        case "resume":
          await resumeAgentRunAction(runId);
          toast.success("运行已恢复");
          break;
      }
      setActionType(null);
      setComment("");
      onActionComplete?.();
    } catch (error) {
      toast.error(getErrorMessage(error, `${ACTION_CONFIG[actionType].label} 失败`));
    } finally {
      setLoading(false);
    }
  };

  if (!canCancel && !canRetry && !canResume) return null;

  return (
    <>
      <div className="flex gap-2">
        {canCancel ? (
          <Button variant="outline" size="sm" onClick={() => setActionType("cancel")} className="text-red-600">
            <XCircle className="mr-1 h-3.5 w-3.5" />
            取消
          </Button>
        ) : null}
        {canRetry ? (
          <Button variant="outline" size="sm" onClick={() => setActionType("retry")}>
            <RotateCcw className="mr-1 h-3.5 w-3.5" />
            重试
          </Button>
        ) : null}
        {canResume ? (
          <Button variant="outline" size="sm" onClick={() => setActionType("resume")}>
            <Play className="mr-1 h-3.5 w-3.5" />
            恢复
          </Button>
        ) : null}
      </div>

      <Dialog open={!!actionType} onOpenChange={() => { setActionType(null); setComment(""); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {actionType ? `确认 ${ACTION_CONFIG[actionType].label}` : ""}
            </DialogTitle>
            <DialogDescription>
              {actionType ? ACTION_CONFIG[actionType].description : ""}
            </DialogDescription>
          </DialogHeader>
          <Textarea
            placeholder="备注（可选）"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            rows={3}
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => { setActionType(null); setComment(""); }} disabled={loading}>
              取消
            </Button>
            <Button
              variant={actionType === "cancel" ? "destructive" : "default"}
              onClick={executeAction}
              disabled={loading}
            >
              {loading ? "处理中..." : actionType ? `确认 ${ACTION_CONFIG[actionType].label}` : "确认"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
