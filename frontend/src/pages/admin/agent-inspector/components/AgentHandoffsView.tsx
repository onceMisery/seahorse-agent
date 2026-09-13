import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ChevronDown, ChevronRight, ExternalLink, XCircle } from "lucide-react";
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

interface HandoffTreeProps {
  handoffs: AgentHandoff[];
  depth: number;
  onCancel: (handoff: AgentHandoff) => void;
}

/**
 * Handoff 树（Multi-Agent A2A 设计 §9.1）：parent run → child run 逐层下钻。
 * 每个节点可懒加载其 child run 名下的 handoff，形成完整的委托树。
 */
function HandoffTree({ handoffs, depth, onCancel }: HandoffTreeProps) {
  return (
    <ul className={depth > 0 ? "ml-4 border-l border-slate-200 pl-3" : ""}>
      {handoffs.map((handoff) => (
        <HandoffTreeNode key={handoff.handoffId ?? `${handoff.parentRunId}-${handoff.childRunId}`}
          handoff={handoff} depth={depth} onCancel={onCancel} />
      ))}
    </ul>
  );
}

function HandoffTreeNode({ handoff, depth, onCancel }: { handoff: AgentHandoff; depth: number; onCancel: (handoff: AgentHandoff) => void }) {
  const [expanded, setExpanded] = useState(false);
  const [children, setChildren] = useState<AgentHandoff[] | null>(null);
  const [childrenLoading, setChildrenLoading] = useState(false);
  const childRunId = handoff.childRunId;
  const expandable = !!childRunId;

  const toggleChildren = () => {
    if (!expandable) return;
    if (expanded) {
      setExpanded(false);
      return;
    }
    if (children === null) {
      setChildrenLoading(true);
      getAgentRunHandoffs(childRunId as string)
        .then((data) => {
          setChildren(Array.isArray(data) ? data : []);
          setExpanded(true);
        })
        .catch((error) => toast.error(getErrorMessage(error, "Failed to load child handoffs")))
        .finally(() => setChildrenLoading(false));
      return;
    }
    setExpanded(true);
  };

  return (
    <li className="py-1">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded px-1 py-1 hover:bg-slate-50">
        {expandable ? (
          <button
            type="button"
            onClick={toggleChildren}
            className="flex items-center text-slate-400 hover:text-slate-600"
            aria-label={expanded ? "Collapse child handoffs" : "Expand child handoffs"}
          >
            {childrenLoading ? (
              <span className="h-3.5 w-3.5 animate-spin rounded-full border border-slate-300 border-t-transparent" />
            ) : expanded ? (
              <ChevronDown className="h-3.5 w-3.5" />
            ) : (
              <ChevronRight className="h-3.5 w-3.5" />
            )}
          </button>
        ) : (
          <span className="h-3.5 w-3.5" />
        )}
        <span className="font-mono text-xs text-slate-400">{handoff.handoffId?.slice(0, 8)}</span>
        <span className="font-mono text-xs text-slate-600">
          {handoff.sourceAgentId?.slice(0, 12)} → {handoff.targetAgentId?.slice(0, 12)}
        </span>
        {statusBadge(handoff.status)}
        {handoff.failureCode ? (
          <span className="rounded bg-red-50 px-1.5 py-0.5 font-mono text-xs text-red-600">
            {handoff.failureCode}
          </span>
        ) : null}
        <span className="text-xs text-slate-500">{handoff.handoffReason ?? "-"}</span>
        <span className="ml-auto flex items-center gap-1">
          {expandable ? (
            <Link
              to={`/admin/agent-inspector/${encodeURIComponent(childRunId as string)}`}
              className="flex items-center text-xs text-sky-600 hover:text-sky-700"
            >
              <ExternalLink className="mr-1 h-3 w-3" />
              Child run
            </Link>
          ) : null}
          {handoff.status === "CREATED" || handoff.status === "RUNNING" ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onCancel(handoff)}
              className="text-red-600 hover:text-red-700"
            >
              <XCircle className="mr-1 h-3 w-3" />
              Cancel
            </Button>
          ) : null}
        </span>
      </div>
      {expanded && children !== null && children.length > 0 ? (
        <HandoffTree handoffs={children} depth={depth + 1} onCancel={onCancel} />
      ) : null}
      {expanded && children !== null && children.length === 0 ? (
        <div className="ml-4 border-l border-slate-200 pl-3 text-xs text-slate-400">No child handoffs</div>
      ) : null}
    </li>
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
        <HandoffTree handoffs={handoffs} depth={0} onCancel={setCancelTarget} />
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
