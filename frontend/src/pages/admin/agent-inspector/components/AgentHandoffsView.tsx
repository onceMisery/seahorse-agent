import { useEffect, useState } from "react";
import { XCircle } from "lucide-react";
import { toast } from "sonner";

import {
  cancelAgentHandoff,
  getAgentRunHandoffs,
  type AgentHandoff
} from "@/services/agentArtifactService";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { getErrorMessage } from "@/utils/error";

function statusBadge(status?: string) {
  if (!status) return null;
  const colors: Record<string, string> = {
    SUCCEEDED: "bg-emerald-100 text-emerald-700",
    FAILED: "bg-red-100 text-red-700",
    CREATED: "bg-amber-100 text-amber-700",
    CANCELLED: "bg-slate-100 text-slate-500",
    RUNNING: "bg-blue-100 text-blue-700"
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
        if (!cancelled) toast.error(getErrorMessage(error, "Failed to load handoffs"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [runId]);

  const handleCancel = async () => {
    if (!cancelTarget?.handoffId) return;
    setCancelling(true);
    try {
      await cancelAgentHandoff(cancelTarget.handoffId);
      toast.success("Handoff cancelled");
      setCancelTarget(null);
      const data = await getAgentRunHandoffs(runId);
      setHandoffs(Array.isArray(data) ? data : []);
    } catch (error) {
      toast.error(getErrorMessage(error, "Cancel failed"));
    } finally {
      setCancelling(false);
    }
  };

  if (loading) {
    return <div className="p-6 text-center text-sm text-slate-500">Loading...</div>;
  }

  if (handoffs.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
        No handoffs
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
              <th className="pb-2 pr-3 font-medium">Source Agent</th>
              <th className="pb-2 pr-3 font-medium">Target Agent</th>
              <th className="pb-2 pr-3 font-medium">Context Pack</th>
              <th className="pb-2 pr-3 font-medium">Status</th>
              <th className="pb-2 pr-3 font-medium">Reason</th>
              <th className="pb-2 pr-3 font-medium">Action</th>
            </tr>
          </thead>
          <tbody>
            {handoffs.map((handoff) => (
              <tr key={handoff.handoffId} className="border-b border-slate-100">
                <td className="py-2 pr-3 font-mono text-xs text-slate-400">
                  {handoff.handoffId?.slice(0, 8)}
                </td>
                <td className="py-2 pr-3 font-mono text-xs text-slate-600">
                  {handoff.sourceAgentId?.slice(0, 12)}
                </td>
                <td className="py-2 pr-3 font-mono text-xs text-slate-600">
                  {handoff.targetAgentId?.slice(0, 12)}
                </td>
                <td className="py-2 pr-3 font-mono text-xs text-slate-600">
                  {handoff.contextPackId ? handoff.contextPackId.slice(0, 18) : "-"}
                </td>
                <td className="py-2 pr-3">{statusBadge(handoff.status)}</td>
                <td className="py-2 pr-3 text-xs text-slate-600">{handoff.handoffReason ?? "-"}</td>
                <td className="py-2 pr-3">
                  {handoff.status === "CREATED" || handoff.status === "RUNNING" ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setCancelTarget(handoff)}
                      className="text-red-600 hover:text-red-700"
                    >
                      <XCircle className="mr-1 h-3 w-3" />
                      Cancel
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
            <DialogTitle>Cancel Handoff</DialogTitle>
            <DialogDescription>
              Cancel handoff {cancelTarget?.handoffId?.slice(0, 8)} ({cancelTarget?.sourceAgentId?.slice(0, 12)} to{" "}
              {cancelTarget?.targetAgentId?.slice(0, 12)})? This action cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCancelTarget(null)} disabled={cancelling}>
              Keep
            </Button>
            <Button variant="destructive" onClick={handleCancel} disabled={cancelling}>
              {cancelling ? "Cancelling..." : "Cancel Handoff"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
