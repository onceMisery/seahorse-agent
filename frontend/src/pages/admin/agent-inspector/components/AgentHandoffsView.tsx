import { useEffect, useState } from "react";
import { XCircle } from "lucide-react";
import { toast } from "sonner";

import {
  getAgentRunHandoffs,
  cancelAgentHandoff,
  type AgentHandoff
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

function statusBadge(status?: string) {
  if (!status) return null;
  const colors: Record<string, string> = {
    COMPLETED: "bg-emerald-100 text-emerald-700",
    FAILED: "bg-red-100 text-red-700",
    PENDING: "bg-amber-100 text-amber-700",
    CANCELLED: "bg-slate-100 text-slate-500",
    ACTIVE: "bg-blue-100 text-blue-700"
  };
  return (
    <span className={`rounded px-1.5 py-0.5 font-mono text-xs ${colors[status] ?? "bg-slate-100 text-slate-600"}`}>
      {status}
    </span>
  );
}

export function AgentHandoffsView({ runId }: { runId: string }) {
  const [handoffs, setHandoffs] = useState<AgentHandoff[]>([]);
  const [loading, setLoading] = useState(false);
  const [cancelTarget, setCancelTarget] = useState<AgentHandoff | null>(null);
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    if (!runId) return;
    let cancelled = false;
    setLoading(true);
    getAgentRunHandoffs(runId)
      .then((data) => {
        if (!cancelled) setHandoffs(Array.isArray(data) ? data : []);
      })
      .catch((error) => {
        if (!cancelled) toast.error(getErrorMessage(error, "加载 handoff 失败"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [runId]);

  const handleCancel = async () => {
    if (!cancelTarget?.handoffId) return;
    setCancelling(true);
    try {
      await cancelAgentHandoff(cancelTarget.handoffId);
      toast.success("Handoff 已取消");
      setCancelTarget(null);
      // Refresh
      const data = await getAgentRunHandoffs(runId);
      setHandoffs(Array.isArray(data) ? data : []);
    } catch (error) {
      toast.error(getErrorMessage(error, "取消失败"));
    } finally {
      setCancelling(false);
    }
  };

  if (loading) {
    return <div className="p-6 text-center text-sm text-slate-500">加载中...</div>;
  }

  if (handoffs.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
        暂无 Handoff
      </div>
    );
  }

  return (
    <>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-xs text-slate-500">
              <th className="pb-2 pr-3 font-medium">ID</th>
              <th className="pb-2 pr-3 font-medium">来源 Agent</th>
              <th className="pb-2 pr-3 font-medium">目标 Agent</th>
              <th className="pb-2 pr-3 font-medium">状态</th>
              <th className="pb-2 pr-3 font-medium">摘要</th>
              <th className="pb-2 pr-3 font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            {handoffs.map((h) => (
              <tr key={h.handoffId} className="border-b border-slate-100">
                <td className="py-2 pr-3 font-mono text-xs text-slate-400">
                  {h.handoffId?.slice(0, 8)}
                </td>
                <td className="py-2 pr-3 font-mono text-xs text-slate-600">
                  {h.fromAgentId?.slice(0, 12)}
                </td>
                <td className="py-2 pr-3 font-mono text-xs text-slate-600">
                  {h.toAgentId?.slice(0, 12)}
                </td>
                <td className="py-2 pr-3">{statusBadge(h.status)}</td>
                <td className="py-2 pr-3 text-xs text-slate-600">{h.summary ?? "-"}</td>
                <td className="py-2 pr-3">
                  {h.status === "PENDING" || h.status === "ACTIVE" ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setCancelTarget(h)}
                      className="text-red-600 hover:text-red-700"
                    >
                      <XCircle className="mr-1 h-3 w-3" />
                      取消
                    </Button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Dialog open={!!cancelTarget} onOpenChange={() => setCancelTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认取消 Handoff</DialogTitle>
            <DialogDescription>
              取消 Handoff {cancelTarget?.handoffId?.slice(0, 8)} ({cancelTarget?.fromAgentId?.slice(0, 12)} → {cancelTarget?.toAgentId?.slice(0, 12)})？
              此操作不可撤销。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCancelTarget(null)} disabled={cancelling}>
              取消
            </Button>
            <Button variant="destructive" onClick={handleCancel} disabled={cancelling}>
              {cancelling ? "取消中..." : "确认取消"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
