import { useEffect, useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { toast } from "sonner";

import { getAgentRunCheckpoints, type AgentCheckpoint } from "@/services/agentArtifactService";
import { getErrorMessage } from "@/utils/error";

function formatTime(ts?: string) {
  if (!ts) return "-";
  const date = new Date(ts);
  if (Number.isNaN(date.getTime())) return ts;
  return date.toLocaleString("zh-CN", { hour12: false });
}

function CheckpointItem({ checkpoint }: { checkpoint: AgentCheckpoint }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="rounded-lg border border-slate-200 bg-white">
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className="flex w-full items-center gap-2 p-3 text-left text-sm"
      >
        {expanded ? <ChevronDown className="h-4 w-4 text-slate-400" /> : <ChevronRight className="h-4 w-4 text-slate-400" />}
        <span className="font-mono text-xs text-slate-400">{checkpoint.checkpointId?.slice(0, 8)}</span>
        <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-700">
          {checkpoint.checkpointType}
        </span>
        <span className="ml-auto text-xs text-slate-400">{formatTime(checkpoint.createTime)}</span>
      </button>
      {expanded && checkpoint.data ? (
        <pre className="max-h-[240px] overflow-auto border-t border-slate-100 bg-slate-50 p-3 text-xs text-slate-700">
          {JSON.stringify(checkpoint.data, null, 2)}
        </pre>
      ) : null}
    </div>
  );
}

export function AgentCheckpointsView({ runId }: { runId: string }) {
  const [checkpoints, setCheckpoints] = useState<AgentCheckpoint[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!runId) return;
    let cancelled = false;
    setLoading(true);
    getAgentRunCheckpoints(runId)
      .then((data) => {
        if (!cancelled) setCheckpoints(Array.isArray(data) ? data : []);
      })
      .catch((error) => {
        if (!cancelled) toast.error(getErrorMessage(error, "加载检查点失败"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [runId]);

  if (loading) {
    return <div className="p-6 text-center text-sm text-slate-500">加载中...</div>;
  }

  if (checkpoints.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
        暂无检查点
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {checkpoints.map((cp) => (
        <CheckpointItem key={cp.checkpointId} checkpoint={cp} />
      ))}
    </div>
  );
}
