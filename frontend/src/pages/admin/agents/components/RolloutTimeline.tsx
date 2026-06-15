import type { AgentRollout } from "@/services/agentRolloutService";

function formatTime(ts?: string) {
  if (!ts) return "-";
  const date = new Date(ts);
  if (Number.isNaN(date.getTime())) return ts;
  return date.toLocaleString("zh-CN", { hour12: false });
}

export function RolloutTimeline({ rollout }: { rollout: AgentRollout }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
      <h3 className="mb-3 text-sm font-medium text-slate-700">发布信息</h3>
      <div className="space-y-2 text-xs text-slate-600">
        <div className="flex gap-4">
          <span className="w-24 text-slate-400">发布 ID</span>
          <span className="font-mono">{rollout.rolloutId?.slice(0, 16) ?? "-"}</span>
        </div>
        <div className="flex gap-4">
          <span className="w-24 text-slate-400">版本</span>
          <span className="font-mono">{rollout.versionId?.slice(0, 16) ?? "-"}</span>
        </div>
        <div className="flex gap-4">
          <span className="w-24 text-slate-400">创建时间</span>
          <span>{formatTime(rollout.createTime)}</span>
        </div>
        <div className="flex gap-4">
          <span className="w-24 text-slate-400">更新时间</span>
          <span>{formatTime(rollout.updateTime)}</span>
        </div>
        {rollout.comment ? (
          <div className="flex gap-4">
            <span className="w-24 text-slate-400">备注</span>
            <span>{rollout.comment}</span>
          </div>
        ) : null}
      </div>
    </div>
  );
}
