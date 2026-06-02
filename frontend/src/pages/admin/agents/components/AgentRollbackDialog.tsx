import { useState } from "react";
import { toast } from "sonner";

import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { rollbackAgentVersion, type AgentVersion } from "@/services/agentDefinitionService";
import { useAuthStore } from "@/stores/authStore";
import { getErrorMessage } from "@/utils/error";

interface AgentRollbackDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  agentId: string;
  tenantId: string;
  versions: AgentVersion[];
  onSuccess: () => void;
}

const rollbackReasonOptions = [
  { value: "OPERATOR_REQUESTED", label: "人工指定" },
  { value: "GATE_FAILED", label: "发布门禁失败" },
  { value: "CANARY_FAILED", label: "灰度失败" },
  { value: "INCIDENT_RESPONSE", label: "事故响应" }
] as const;

type RollbackReasonCode = (typeof rollbackReasonOptions)[number]["value"];

export function AgentRollbackDialog({ open, onOpenChange, agentId, tenantId, versions, onSuccess }: AgentRollbackDialogProps) {
  const user = useAuthStore((state) => state.user);
  const [selectedVersionId, setSelectedVersionId] = useState<string>("");
  const [reasonCode, setReasonCode] = useState<RollbackReasonCode>("OPERATOR_REQUESTED");
  const [comment, setComment] = useState("");
  const [rolling, setRolling] = useState(false);

  const publishedVersions = versions.filter((v) => v.publishStatus === "PUBLISHED" || v.status === "PUBLISHED");
  const operator = user?.userId || user?.username || "";

  const handleRollback = async () => {
    if (!selectedVersionId) {
      toast.error("请选择要回滚的版本");
      return;
    }
    if (!tenantId.trim()) {
      toast.error("缺少 Agent 租户信息，无法回滚");
      return;
    }
    if (!operator.trim()) {
      toast.error("无法识别当前操作者，无法回滚");
      return;
    }

    try {
      setRolling(true);
      await rollbackAgentVersion(agentId, selectedVersionId, {
        tenantId: tenantId.trim(),
        operator: operator.trim(),
        reasonCode,
        comment: comment.trim() || undefined
      });
      toast.success("版本回滚成功");
      setComment("");
      setReasonCode("OPERATOR_REQUESTED");
      setSelectedVersionId("");
      onSuccess();
    } catch (error) {
      toast.error(getErrorMessage(error, "回滚失败"));
      console.error(error);
    } finally {
      setRolling(false);
    }
  };

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="sm:max-w-[480px]">
        <AlertDialogHeader>
          <AlertDialogTitle>版本回滚</AlertDialogTitle>
          <AlertDialogDescription>
            回滚操作将把 Agent 恢复到指定版本的状态。此操作不可逆，请谨慎操作。
          </AlertDialogDescription>
        </AlertDialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <label className="text-sm font-medium">目标版本</label>
            <Select value={selectedVersionId} onValueChange={setSelectedVersionId}>
              <SelectTrigger>
                <SelectValue placeholder="请选择要回滚到的版本" />
              </SelectTrigger>
              <SelectContent>
                {publishedVersions.filter((v) => !!v.versionId).map((v) => (
                  <SelectItem key={v.versionId} value={v.versionId!}>
                    v{v.versionNumber} - {v.summary || v.status}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {publishedVersions.length === 0 && (
              <p className="text-xs text-muted-foreground">暂无可回滚的已发布版本</p>
            )}
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium">回滚原因码</label>
            <Select value={reasonCode} onValueChange={(value) => setReasonCode(value as RollbackReasonCode)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {rollbackReasonOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium">补充说明（可选）</label>
            <Textarea
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="请输入回滚说明"
              rows={3}
            />
          </div>
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel onClick={() => { setComment(""); setReasonCode("OPERATOR_REQUESTED"); setSelectedVersionId(""); }}>取消</AlertDialogCancel>
          <AlertDialogAction
            onClick={handleRollback}
            disabled={rolling || !selectedVersionId || !tenantId.trim() || !operator.trim()}
          >
            {rolling ? "回滚中..." : "确认回滚"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
