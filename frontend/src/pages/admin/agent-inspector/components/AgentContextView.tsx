import type { AgentRunSnapshot } from "@/types";

export function AgentContextView({ snapshot }: { snapshot: AgentRunSnapshot | null }) {
  if (!snapshot) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
        暂未加载快照
      </div>
    );
  }

  const sources = snapshot.sources ?? [];
  const artifacts = snapshot.artifacts ?? [];
  const pendingApprovals = snapshot.pendingApprovals ?? [];

  return (
    <div className="space-y-4">
      <div>
        <h3 className="mb-2 text-sm font-medium text-slate-700">
          来源 ({sources.length})
        </h3>
        {sources.length === 0 ? (
          <p className="text-sm text-slate-400">暂无来源</p>
        ) : (
          <div className="space-y-1.5">
            {sources.map((source) => (
              <div
                key={source.itemId}
                className="rounded border border-slate-200 bg-white px-3 py-2 text-sm"
              >
                <div className="flex items-center gap-2">
                  <span className="font-medium text-slate-800">
                    {source.title ?? source.sourceId ?? source.itemId}
                  </span>
                  {source.sourceType ? (
                    <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500">
                      {source.sourceType}
                    </span>
                  ) : null}
                  {source.score != null ? (
                    <span className="ml-auto text-xs text-slate-400">
                      分数：{source.score.toFixed(3)}
                    </span>
                  ) : null}
                </div>
                {source.snippet ? (
                  <p className="mt-1 line-clamp-2 text-xs text-slate-500">{source.snippet}</p>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </div>

      <div>
        <h3 className="mb-2 text-sm font-medium text-slate-700">
          产物 ({artifacts.length})
        </h3>
        {artifacts.length === 0 ? (
          <p className="text-sm text-slate-400">暂无产物</p>
        ) : (
          <div className="space-y-1.5">
            {artifacts.map((artifact) => (
              <div
                key={artifact.artifactId}
                className="flex items-center gap-3 rounded border border-slate-200 bg-white px-3 py-2 text-sm"
              >
                <span className="font-mono text-xs text-slate-400">{artifact.artifactId}</span>
                <span className="flex-1 text-slate-700">{artifact.title ?? artifact.artifactType ?? "-"}</span>
                {artifact.scanStatus ? (
                  <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500">
                    {String(artifact.scanStatus)}
                  </span>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </div>

      <div>
        <h3 className="mb-2 text-sm font-medium text-slate-700">
          待处理审批 ({pendingApprovals.length})
        </h3>
        {pendingApprovals.length === 0 ? (
          <p className="text-sm text-slate-400">暂无待处理审批</p>
        ) : (
          <pre className="max-h-[200px] overflow-auto rounded-lg border border-slate-200 bg-slate-950 p-3 text-xs text-slate-100">
            {JSON.stringify(pendingApprovals, null, 2)}
          </pre>
        )}
      </div>
    </div>
  );
}
