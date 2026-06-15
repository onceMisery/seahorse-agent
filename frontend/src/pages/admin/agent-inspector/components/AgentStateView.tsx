import type { AgentRunSnapshot } from "@/types";

function Field({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="space-y-0.5">
      <dt className="text-xs font-medium text-slate-500">{label}</dt>
      <dd className="text-sm text-slate-900">{value ?? "-"}</dd>
    </div>
  );
}

export function AgentStateView({ snapshot }: { snapshot: AgentRunSnapshot | null }) {
  if (!snapshot) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
        暂未加载快照
      </div>
    );
  }

  const run = snapshot.run;

  return (
    <div className="space-y-4">
      <dl className="grid gap-3 sm:grid-cols-2">
        <Field label="Run ID" value={run?.runId} />
        <Field label="状态" value={run?.status} />
        <Field label="当前步骤" value={snapshot.currentStepId} />
        <Field label="会话" value={run?.conversationId} />
        <Field label="输入" value={run?.inputSummary} />
        <Field label="错误代码" value={run?.errorCode} />
        <Field label="错误消息" value={run?.errorMessage} />
        <Field label="开始时间" value={run?.startedAt} />
        <Field label="结束时间" value={run?.finishedAt} />
        <Field label="最后事件序号" value={snapshot.lastEventSeq != null ? String(snapshot.lastEventSeq) : null} />
        <Field label="可恢复" value={snapshot.canResume != null ? String(snapshot.canResume) : null} />
        <Field label="可重试" value={snapshot.canRetry != null ? String(snapshot.canRetry) : null} />
      </dl>

      {snapshot.steps && snapshot.steps.length > 0 ? (
        <div>
          <h3 className="mb-2 text-sm font-medium text-slate-700">步骤 ({snapshot.steps.length})</h3>
          <div className="space-y-1.5">
            {snapshot.steps.map((step) => (
              <div
                key={step.stepId}
                className="flex items-center gap-3 rounded border border-slate-200 bg-white px-3 py-2 text-sm"
              >
                <span className="font-mono text-xs text-slate-400">#{step.stepNo ?? "-"}</span>
                <span className="flex-1 text-slate-700">{step.summary ?? step.stepType ?? step.stepId}</span>
                <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">{step.status ?? "-"}</span>
              </div>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}
