import type { StreamEventEnvelope } from "@/types";

function formatTimestamp(ts: string) {
  const date = new Date(ts);
  if (Number.isNaN(date.getTime())) return ts;
  return date.toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false
  });
}

function isToolOrApprovalEvent(eventType: string) {
  const lower = eventType.toLowerCase();
  return lower.includes("tool") || lower.includes("approval");
}

export function AgentToolsView({ events }: { events: StreamEventEnvelope[] }) {
  const toolEvents = events.filter((e) => isToolOrApprovalEvent(e.eventType));

  if (toolEvents.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
        暂无工具或审批事件
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {toolEvents.map((event) => (
        <div
          key={event.eventSeq}
          className="rounded-lg border border-slate-200 bg-white p-3 text-sm"
        >
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs text-slate-400">#{event.eventSeq}</span>
            <span className="rounded bg-amber-100 px-1.5 py-0.5 font-mono text-xs text-amber-700">
              {event.eventType}
            </span>
            {event.stepId ? (
              <span className="text-xs text-slate-400">步骤：{event.stepId}</span>
            ) : null}
            <span className="ml-auto text-xs text-slate-400">{formatTimestamp(event.timestamp)}</span>
          </div>
          {event.typedPayload != null ? (
            <pre className="mt-2 max-h-[160px] overflow-auto rounded border border-slate-100 bg-slate-50 p-2 text-xs text-slate-700">
              {JSON.stringify(event.typedPayload, null, 2)}
            </pre>
          ) : null}
        </div>
      ))}
    </div>
  );
}
