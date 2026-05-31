import { useState } from "react";
import { toast } from "sonner";

import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { rollbackAgentVersion, type AgentVersion } from "@/services/agentDefinitionService";
import { getErrorMessage } from "@/utils/error";

interface AgentRollbackDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  agentId: string;
  versions: AgentVersion[];
  onSuccess: () => void;
}

export function AgentRollbackDialog({ open, onOpenChange, agentId, versions, onSuccess }: AgentRollbackDialogProps) {
  const [selectedVersionId, setSelectedVersionId] = useState<string>("");
  const [reason, setReason] = useState("");
  const [rolling, setRolling] = useState(false);

  const publishedVersions = versions.filter((v) => v.publishStatus === "PUBLISHED" || v.status === "PUBLISHED");

  const handleRollback = async () => {
    if (!selectedVersionId) {
      toast.error("请选择要回滚的版本");
      return;
    }
    if (!reason.trim()) {
      toast.error("请输入回滚原因");
      return;
    }

    try {
      setRolling(true);
      await rollbackAgentVersion(agentId, selectedVersionId, reason.trim());
      toast.success("版本回滚成功");
      setReason("");
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
                {publishedVersions.map((v) => (
                  <SelectItem key={v.versionId} value={v.versionId || ""}>
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
            <label className="text-sm font-medium">回滚原因</label>
            <Textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="请输入回滚原因"
              rows={3}
            />
          </div>
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel onClick={() => { setReason(""); setSelectedVersionId(""); }}>取消</AlertDialogCancel>
          <AlertDialogAction
            onClick={handleRollback}
            disabled={rolling || !selectedVersionId}
          >
            {rolling ? "回滚中..." : "确认回滚"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
