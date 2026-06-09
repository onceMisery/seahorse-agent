import type { StreamEventEnvelope } from "@/types";

function copyJson(payload: unknown) {
  try {
    navigator.clipboard.writeText(JSON.stringify(payload, null, 2));
  } catch {
    // ignore
  }
}

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

export function AgentEventStream({ events }: { events: StreamEventEnvelope[] }) {
  if (events.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
        No events
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {events.map((event) => (
        <div
          key={event.eventId || event.eventSeq}
          className="flex items-start justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3 text-sm"
        >
          <div className="min-w-0 flex-1 space-y-1">
            <div className="flex items-center gap-2">
              <span className="font-mono text-xs text-slate-400">#{event.eventSeq}</span>
              <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-700">
                {event.eventType}
              </span>
              {event.stepId ? (
                <span className="text-xs text-slate-400">step: {event.stepId}</span>
              ) : null}
            </div>
            <div className="text-xs text-slate-400">{formatTimestamp(event.timestamp)}</div>
          </div>
          <button
            type="button"
            onClick={() => copyJson(event.typedPayload)}
            className="shrink-0 rounded border border-slate-200 bg-slate-50 px-2 py-1 text-xs text-slate-600 hover:bg-slate-100"
          >
            Copy JSON
          </button>
        </div>
      ))}
    </div>
  );
}
