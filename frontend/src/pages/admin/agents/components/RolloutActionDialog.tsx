import { useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { pauseRollout, promoteRollout, rollbackRollout } from "@/services/agentRolloutService";
import { getErrorMessage } from "@/utils/error";

export type RolloutActionType = "pause" | "promote" | "rollback";

const ACTION_LABELS: Record<RolloutActionType, { title: string; description: string; buttonLabel: string; destructive: boolean }> = {
  pause: { title: "暂停灰度发布", description: "暂停当前灰度发布，暂停后新用户将不会路由到新版本。", buttonLabel: "确认暂停", destructive: false },
  promote: { title: "全量发布", description: "将灰度版本提升为正式版本，所有流量将路由到新版本。此操作不可撤销。", buttonLabel: "确认全量发布", destructive: false },
  rollback: { title: "回滚灰度发布", description: "回滚到上一个稳定版本。此操作不可撤销。", buttonLabel: "确认回滚", destructive: true }
};

export function RolloutActionDialog({
  actionType,
  agentId,
  rolloutId,
  tenantId,
  onClose,
  onSuccess
}: {
  actionType: RolloutActionType;
  agentId: string;
  rolloutId: string;
  tenantId: string;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [comment, setComment] = useState("");
  const [loading, setLoading] = useState(false);

  const config = ACTION_LABELS[actionType];

  const handleSubmit = async () => {
    setLoading(true);
    try {
      const payload = { tenantId, operator: "admin", comment };
      switch (actionType) {
        case "pause":
          await pauseRollout(agentId, rolloutId, payload);
          break;
        case "promote":
          await promoteRollout(agentId, rolloutId, payload);
          break;
        case "rollback":
          await rollbackRollout(agentId, rolloutId, payload);
          break;
      }
      toast.success(`${config.title}成功`);
      onSuccess();
    } catch (error) {
      toast.error(getErrorMessage(error, `${config.title}失败`));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open onOpenChange={onClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{config.title}</DialogTitle>
          <DialogDescription>{config.description}</DialogDescription>
        </DialogHeader>
        <Textarea
          placeholder="操作备注（建议填写原因）"
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          rows={3}
        />
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={loading}>取消</Button>
          <Button
            variant={config.destructive ? "destructive" : "default"}
            onClick={handleSubmit}
            disabled={loading}
          >
            {loading ? "处理中..." : config.buttonLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
