import { useState } from "react";
import { toast } from "sonner";

import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { publishAgent, type AgentPublishCheck } from "@/services/agentDefinitionService";
import { getErrorMessage } from "@/utils/error";

interface AgentPublishDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  agentId: string;
  publishCheck: AgentPublishCheck | null;
  onSuccess: () => void;
}

export function AgentPublishDialog({ open, onOpenChange, agentId, publishCheck, onSuccess }: AgentPublishDialogProps) {
  const [publishing, setPublishing] = useState(false);
  const [reason, setReason] = useState("");

  const hasFailedChecks = publishCheck?.checks?.some((c) => !c.passed);

  const handlePublish = async () => {
    try {
      setPublishing(true);
      await publishAgent(agentId);
      toast.success("Agent 发布成功");
      setReason("");
      onSuccess();
    } catch (error) {
      toast.error(getErrorMessage(error, "发布失败"));
      console.error(error);
    } finally {
      setPublishing(false);
    }
  };

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="sm:max-w-[520px]">
        <AlertDialogHeader>
          <AlertDialogTitle>确认发布 Agent</AlertDialogTitle>
          <AlertDialogDescription>
            发布后 Agent 将进入生产状态。请确认发布检查已通过。
          </AlertDialogDescription>
        </AlertDialogHeader>

        {publishCheck?.checks && publishCheck.checks.length > 0 && (
          <div className="space-y-2 mb-4">
            <div className="text-sm font-medium">发布检查结果：</div>
            {publishCheck.checks.map((check, idx) => (
              <div key={idx} className={`p-2 rounded text-sm ${check.passed ? "bg-green-50 text-green-700" : "bg-red-50 text-red-700"}`}>
                <Badge variant={check.passed ? "default" : "destructive"} className="mr-2">
                  {check.passed ? "通过" : "失败"}
                </Badge>
                {check.checkType}: {check.message || (check.passed ? "OK" : "未通过")}
              </div>
            ))}
          </div>
        )}

        <div className="space-y-2">
          <label className="text-sm font-medium">发布备注（可选）</label>
          <Textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="请输入发布原因或备注"
            rows={3}
          />
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction
            onClick={handlePublish}
            disabled={publishing || !!hasFailedChecks}
            className={hasFailedChecks ? "opacity-50 cursor-not-allowed" : ""}
          >
            {publishing ? "发布中..." : "确认发布"}
          </AlertDialogAction>
        </AlertDialogFooter>

        {hasFailedChecks && (
          <p className="text-xs text-red-500 mt-2">存在未通过的检查项，请修复后再发布</p>
        )}
      </AlertDialogContent>
    </AlertDialog>
  );
}
